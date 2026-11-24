-- Bustime reliable per-device sync tracking. The existing data_change_log remains
-- the permanent source of truth; this migration adds only device progress and error tracking.

create table if not exists public.sync_device_state (
  device_id uuid primary key,
  app_version text not null default '',
  last_success_revision bigint not null default 0 check (last_success_revision >= 0),
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  last_error_at timestamptz,
  last_error text
);

create table if not exists public.sync_error_logs (
  device_id uuid not null references public.sync_device_state(device_id) on delete cascade,
  revision bigint not null,
  entity_type text not null check (entity_type in ('place', 'route')),
  entity_id text not null,
  error_code text not null,
  error_message text not null default '',
  attempts integer not null default 1 check (attempts >= 1),
  status text not null default 'pending' check (status in ('pending', 'resolved', 'superseded')),
  first_failed_at timestamptz not null default now(),
  last_failed_at timestamptz not null default now(),
  next_retry_at timestamptz not null default now() + interval '1 minute',
  resolved_at timestamptz,
  primary key (device_id, revision, entity_type, entity_id)
);

create index if not exists sync_device_state_last_seen_idx
  on public.sync_device_state (last_seen_at desc);
create index if not exists sync_error_logs_pending_retry_idx
  on public.sync_error_logs (device_id, next_retry_at, revision)
  where status = 'pending';
create index if not exists sync_error_logs_status_idx
  on public.sync_error_logs (status, last_failed_at desc);

alter table public.sync_device_state enable row level security;
alter table public.sync_error_logs enable row level security;
revoke all on public.sync_device_state, public.sync_error_logs from anon, authenticated;
grant select, insert, update on public.sync_device_state to service_role;
grant select, insert, update on public.sync_error_logs to service_role;

create or replace function public.get_bustime_snapshot()
returns jsonb
language sql
stable
set search_path = public
as $$
  select jsonb_build_object(
    'dataVersion', s.snapshot_version,
    'snapshotRevision', s.revision,
    'places', coalesce((select jsonb_agg(p.payload order by p.id) from public.master_places p), '[]'::jsonb),
    'routes', coalesce((select jsonb_agg(r.payload order by r.id) from public.master_routes r), '[]'::jsonb)
  )
  from public.data_sync_state s
  where s.id = 1;
$$;

create or replace function public.record_bustime_sync_report(
  p_device_id uuid,
  p_app_version text,
  p_revision bigint,
  p_failures jsonb default '[]'::jsonb,
  p_resolved jsonb default '[]'::jsonb,
  p_snapshot_recovered boolean default false
)
returns jsonb
language plpgsql
set search_path = public
as $$
declare
  v_server_revision bigint;
  v_item jsonb;
  v_revision bigint;
  v_type text;
  v_id text;
  v_error_code text;
  v_error_message text;
  v_failure_count integer := 0;
  v_resolved_count integer := 0;
begin
  if p_device_id is null or p_revision is null or p_revision < 0 then
    raise exception 'invalid_sync_report';
  end if;
  if jsonb_typeof(coalesce(p_failures, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_failures, '[]'::jsonb)) > 500 then
    raise exception 'invalid_failure_list';
  end if;
  if jsonb_typeof(coalesce(p_resolved, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_resolved, '[]'::jsonb)) > 500 then
    raise exception 'invalid_resolved_list';
  end if;

  select revision into v_server_revision
  from public.data_sync_state where id = 1;
  if v_server_revision is null or p_revision > v_server_revision then
    raise exception 'sync_revision_ahead_of_server';
  end if;

  insert into public.sync_device_state (
    device_id, app_version, last_success_revision, first_seen_at, last_seen_at
  ) values (
    p_device_id, left(coalesce(p_app_version, ''), 40), p_revision, now(), now()
  )
  on conflict (device_id) do update set
    app_version = excluded.app_version,
    last_success_revision = greatest(public.sync_device_state.last_success_revision, excluded.last_success_revision),
    last_seen_at = now();

  for v_item in select value from jsonb_array_elements(coalesce(p_failures, '[]'::jsonb))
  loop
    v_revision := nullif(v_item->>'revision', '')::bigint;
    v_type := coalesce(v_item->>'entity_type', '');
    v_id := left(coalesce(v_item->>'entity_id', ''), 180);
    v_error_code := left(coalesce(v_item->>'error_code', 'apply_failed'), 80);
    v_error_message := left(coalesce(v_item->>'error_message', ''), 300);
    if v_revision is null or v_revision < 1 or v_revision > p_revision
       or v_type not in ('place', 'route') or v_id = '' then
      raise exception 'invalid_sync_failure_item';
    end if;

    insert into public.sync_error_logs (
      device_id, revision, entity_type, entity_id, error_code, error_message,
      attempts, status, first_failed_at, last_failed_at, next_retry_at, resolved_at
    ) values (
      p_device_id, v_revision, v_type, v_id, v_error_code, v_error_message,
      1, 'pending', now(), now(), now() + interval '1 minute', null
    )
    on conflict (device_id, revision, entity_type, entity_id) do update set
      error_code = excluded.error_code,
      error_message = excluded.error_message,
      attempts = public.sync_error_logs.attempts + 1,
      status = 'pending',
      last_failed_at = now(),
      next_retry_at = now() + make_interval(secs => least(3600, (60 * power(2, least(public.sync_error_logs.attempts, 6)))::integer)),
      resolved_at = null;
    v_failure_count := v_failure_count + 1;
  end loop;

  for v_item in select value from jsonb_array_elements(coalesce(p_resolved, '[]'::jsonb))
  loop
    v_revision := nullif(v_item->>'revision', '')::bigint;
    v_type := coalesce(v_item->>'entity_type', '');
    v_id := left(coalesce(v_item->>'entity_id', ''), 180);
    if v_revision is not null and v_type in ('place', 'route') and v_id <> '' then
      update public.sync_error_logs
      set status = 'resolved', resolved_at = now()
      where device_id = p_device_id and revision = v_revision
        and entity_type = v_type and entity_id = v_id and status = 'pending';
      if found then v_resolved_count := v_resolved_count + 1; end if;
    end if;
  end loop;

  if p_snapshot_recovered then
    update public.sync_error_logs
    set status = 'superseded', resolved_at = now()
    where device_id = p_device_id and status = 'pending' and revision <= p_revision;
  end if;

  update public.sync_device_state
  set last_error_at = case when v_failure_count > 0 then now() else last_error_at end,
      last_error = case when v_failure_count > 0
        then left(coalesce(p_failures->0->>'error_message', 'A change could not be applied'), 300)
        else null end
  where device_id = p_device_id;

  return jsonb_build_object(
    'ok', true,
    'revision', p_revision,
    'recorded_failures', v_failure_count,
    'resolved_failures', v_resolved_count,
    'snapshot_recovered', coalesce(p_snapshot_recovered, false)
  );
end;
$$;

create or replace function public.get_bustime_sync_retries(
  p_device_id uuid,
  p_limit integer default 100
)
returns jsonb
language plpgsql
set search_path = public
as $$
declare
  v_rows jsonb;
begin
  if p_device_id is null then raise exception 'invalid_device_id'; end if;

  -- An older failed event must never overwrite a newer event for the same entity.
  update public.sync_error_logs e
  set status = 'superseded', resolved_at = now()
  where e.device_id = p_device_id and e.status = 'pending'
    and exists (
      select 1 from public.data_change_log newer
      where newer.entity_type = e.entity_type
        and newer.entity_id = e.entity_id
        and newer.revision > e.revision
    );

  select coalesce(jsonb_agg(to_jsonb(q) order by q.revision, q.entity_type, q.entity_id), '[]'::jsonb)
  into v_rows
  from (
    select c.revision, c.entity_type, c.entity_id, c.operation, c.payload
    from public.sync_error_logs e
    join public.data_change_log c
      on c.revision = e.revision
     and c.entity_type = e.entity_type
     and c.entity_id = e.entity_id
    where e.device_id = p_device_id
      and e.status = 'pending'
      and e.next_retry_at <= now()
    order by e.revision, e.entity_type, e.entity_id
    limit greatest(1, least(coalesce(p_limit, 100), 100))
  ) q;

  return v_rows;
end;
$$;

revoke all on function public.get_bustime_snapshot() from public, anon, authenticated;
revoke all on function public.record_bustime_sync_report(uuid, text, bigint, jsonb, jsonb, boolean) from public, anon, authenticated;
revoke all on function public.get_bustime_sync_retries(uuid, integer) from public, anon, authenticated;
grant execute on function public.get_bustime_snapshot() to service_role;
grant execute on function public.record_bustime_sync_report(uuid, text, bigint, jsonb, jsonb, boolean) to service_role;
grant execute on function public.get_bustime_sync_retries(uuid, integer) to service_role;
