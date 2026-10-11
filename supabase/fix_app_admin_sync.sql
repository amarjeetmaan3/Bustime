-- Bustime: ONE idempotent script (safe to run many times) in Supabase > SQL Editor.
-- Fixes: "Add Your Bus" / Taxi-Auto listing not reaching the admin, and the admin Community tab 404.

-- 1) Bus listing requests table (Add Your Bus)
create table if not exists public.bus_operator_submissions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  operator_name text not null,
  service_name text,
  bus_type text not null default 'Ordinary',
  seat_type text,
  sleeper_count integer,
  seater_count integer,
  from_text text,
  to_text text,
  location text,
  phone text not null,
  whatsapp text,
  details text,
  status text not null default 'pending' check (status in ('pending','approved','rejected'))
);
alter table public.bus_operator_submissions add column if not exists seat_type text;
alter table public.bus_operator_submissions add column if not exists sleeper_count integer;
alter table public.bus_operator_submissions add column if not exists seater_count integer;
alter table public.bus_operator_submissions add column if not exists from_text text;
alter table public.bus_operator_submissions add column if not exists to_text text;
alter table public.bus_operator_submissions add column if not exists location text;
alter table public.bus_operator_submissions add column if not exists whatsapp text;
alter table public.bus_operator_submissions add column if not exists details text;
alter table public.bus_operator_submissions enable row level security;
revoke all on public.bus_operator_submissions from anon, authenticated;
grant insert on public.bus_operator_submissions to anon;
grant select, update, delete on public.bus_operator_submissions to authenticated;
drop policy if exists "public submit bus listing" on public.bus_operator_submissions;
create policy "public submit bus listing" on public.bus_operator_submissions for insert to anon with check (status = 'pending');
drop policy if exists "admin manage bus listings" on public.bus_operator_submissions;
create policy "admin manage bus listings" on public.bus_operator_submissions for all to authenticated using (true) with check (true);

-- 2) Published buses table: columns the app/admin use
alter table public.bus_operators add column if not exists seat_type text;
alter table public.bus_operators add column if not exists sleeper_count integer;
alter table public.bus_operators add column if not exists seater_count integer;
alter table public.bus_operators add column if not exists whatsapp text;
alter table public.bus_operators add column if not exists location text;
alter table public.bus_operators add column if not exists from_text text;
alter table public.bus_operators add column if not exists to_text text;
alter table public.bus_operators add column if not exists details text;

-- 3) Auto / Taxi listing requests: area, union, fuel (the app sends area_name)
alter table public.vehicle_submissions add column if not exists area_name text;
alter table public.vehicle_submissions add column if not exists union_name text;
alter table public.vehicle_submissions add column if not exists fuel text;

-- 4) Tell the API to reload its table list (fixes 404 / PGRST204)
notify pgrst, 'reload schema';
