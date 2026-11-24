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

  update public.sync_error_logs e
  set status = 'superseded', resolved_at = now()
  where e.device_id = p_device_id and e.status = 'pending'
    and exists (
      select 1 from public.data_change_log newer
      where newer.entity_type = e.entity_type
        and newer.entity_id = e.entity_id
        and newer.revision > e.revision
    );

  select coalesce(jsonb_agg(to_jsonb(q) order by q.failure_revision, q.entity_type, q.entity_id), '[]'::jsonb)
  into v_rows
  from (
    select
      coalesce(c.revision, e.revision) as revision,
      e.entity_type,
      e.entity_id,
      coalesce(c.operation, 'snapshot_retry') as operation,
      c.payload,
      e.revision as failure_revision
    from public.sync_error_logs e
    left join lateral (
      select c0.revision, c0.operation, c0.payload
      from public.data_change_log c0
      where c0.entity_type = e.entity_type
        and c0.entity_id = e.entity_id
        and c0.revision <= e.revision
      order by c0.revision desc
      limit 1
    ) c on true
    where e.device_id = p_device_id
      and e.status = 'pending'
      and e.next_retry_at <= now()
    order by e.revision, e.entity_type, e.entity_id
    limit greatest(1, least(coalesce(p_limit, 100), 100))
  ) q;

  return v_rows;
end;
$$;

revoke all on function public.get_bustime_sync_retries(uuid, integer) from public, anon, authenticated;
grant execute on function public.get_bustime_sync_retries(uuid, integer) to service_role;
