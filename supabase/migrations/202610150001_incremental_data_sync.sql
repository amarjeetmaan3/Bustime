-- Bustime incremental sync metadata and change log.
create table if not exists public.data_sync_state (
  id smallint primary key default 1 check (id = 1),
  revision bigint not null default 0,
  snapshot_version integer not null default 0,
  snapshot_sha256 text,
  published_at timestamptz not null default now()
);

insert into public.data_sync_state (id, revision, snapshot_version)
values (1, 0, 0)
on conflict (id) do nothing;

create table if not exists public.data_change_log (
  revision bigint not null,
  entity_type text not null check (entity_type in ('place', 'route', 'driver', 'seater')),
  entity_id text not null,
  operation text not null check (operation in ('upsert', 'delete')),
  payload jsonb,
  created_at timestamptz not null default now(),
  primary key (revision, entity_type, entity_id),
  check ((operation = 'delete' and payload is null) or (operation = 'upsert' and payload is not null))
);

create index if not exists data_change_log_revision_idx
  on public.data_change_log (revision, entity_type);

alter table public.data_sync_state enable row level security;
alter table public.data_change_log enable row level security;

drop policy if exists "Public can read sync state" on public.data_sync_state;
create policy "Public can read sync state"
  on public.data_sync_state for select to anon, authenticated using (true);

drop policy if exists "Public can read sync changes" on public.data_change_log;
create policy "Public can read sync changes"
  on public.data_change_log for select to anon, authenticated using (true);

revoke insert, update, delete on public.data_sync_state from anon, authenticated;
revoke insert, update, delete on public.data_change_log from anon, authenticated;
grant select on public.data_sync_state to anon, authenticated;
grant select on public.data_change_log to anon, authenticated;
