-- Supabase Master Data for Bustime transport data.
-- Master tables hold the latest full state; data_change_log remains the append-only history.
-- This trigger keeps both in sync for every trusted publisher.

create table if not exists public.master_places (
  id text primary key,
  payload jsonb not null,
  updated_revision bigint not null,
  updated_at timestamptz not null default now(),
  constraint master_places_payload_id_check check (
    jsonb_typeof(payload) = 'object' and payload->>'id' = id
  )
);

create table if not exists public.master_routes (
  id text primary key,
  payload jsonb not null,
  updated_revision bigint not null,
  updated_at timestamptz not null default now(),
  constraint master_routes_payload_id_check check (
    jsonb_typeof(payload) = 'object' and payload->>'id' = id
  )
);

alter table public.master_places enable row level security;
alter table public.master_routes enable row level security;
revoke all on public.master_places, public.master_routes from anon, authenticated;
grant select, insert, update, delete on public.master_places, public.master_routes to service_role;

create or replace function public.sync_master_data_from_change_log()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.entity_type = 'place' then
    if new.operation = 'delete' then
      delete from public.master_places
      where id = new.entity_id and updated_revision <= new.revision;
    else
      insert into public.master_places (id, payload, updated_revision, updated_at)
      values (new.entity_id, new.payload, new.revision, new.created_at)
      on conflict (id) do update
        set payload = excluded.payload,
            updated_revision = excluded.updated_revision,
            updated_at = excluded.updated_at
        where excluded.updated_revision >= public.master_places.updated_revision;
    end if;
  elsif new.entity_type = 'route' then
    if new.operation = 'delete' then
      delete from public.master_routes
      where id = new.entity_id and updated_revision <= new.revision;
    else
      insert into public.master_routes (id, payload, updated_revision, updated_at)
      values (new.entity_id, new.payload, new.revision, new.created_at)
      on conflict (id) do update
        set payload = excluded.payload,
            updated_revision = excluded.updated_revision,
            updated_at = excluded.updated_at
        where excluded.updated_revision >= public.master_routes.updated_revision;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists data_change_log_sync_master on public.data_change_log;
create trigger data_change_log_sync_master
after insert or update of operation, payload on public.data_change_log
for each row execute function public.sync_master_data_from_change_log();

revoke all on function public.sync_master_data_from_change_log() from public, anon, authenticated;