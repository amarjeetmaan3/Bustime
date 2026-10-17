-- Recycle Bin for published Bus places/routes. No existing data is removed.
create table if not exists public.data_recycle_bin (
  id bigint generated always as identity primary key,
  entity_type text not null check (entity_type in ('place','route')),
  entity_id text not null,
  payload jsonb, -- nullable only after explicit permanent purge
  deleted_at timestamptz not null default now(),
  deleted_revision bigint,
  restored_at timestamptz,
  permanently_deleted_at timestamptz
);
create index if not exists data_recycle_bin_active_deleted_at_idx
  on public.data_recycle_bin (deleted_at desc)
  where restored_at is null and permanently_deleted_at is null;
create unique index if not exists data_recycle_bin_one_active_entity_idx
  on public.data_recycle_bin (entity_type, entity_id)
  where restored_at is null and permanently_deleted_at is null;
alter table public.data_recycle_bin enable row level security;
revoke all on public.data_recycle_bin from anon, authenticated;
grant all on public.data_recycle_bin to service_role;
