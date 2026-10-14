-- Bustime Taxi / Auto submission split (applied to Supabase 2026-10-14)
-- Only Taxi/Auto submission storage is affected. Bus, places, routes, corrections,
-- analytics and advertising tables are intentionally untouched.

create table if not exists public.taxi_submissions (
 id uuid primary key default gen_random_uuid(),
 created_at timestamptz not null default now(),
 person_name text not null,
 vehicle_name text not null,
 year text not null check (year ~ '^[0-9]{4}$'),
 seats integer not null check (seats > 0),
 vehicle_number text not null,
 location text not null,
 area_name text,
 union_name text not null,
 phone text not null check (phone ~ '^[0-9]{10,13}$'),
 details text,
 status text not null default 'pending' check (status in ('pending','approved','rejected')),
 reviewed_data jsonb,
 reviewed_at timestamptz,
 approved_at timestamptz
);
create table if not exists public.auto_submissions (
 id uuid primary key default gen_random_uuid(),
 created_at timestamptz not null default now(),
 person_name text not null,
 vehicle_name text not null,
 year text not null check (year ~ '^[0-9]{4}$'),
 seats integer not null check (seats > 0),
 vehicle_number text not null,
 location text not null,
 area_name text,
 union_name text not null,
 phone text not null check (phone ~ '^[0-9]{10,13}$'),
 fuel_type text not null check (fuel_type in ('ev','oil','cng','other')),
 details text,
 status text not null default 'pending' check (status in ('pending','approved','rejected')),
 reviewed_data jsonb,
 reviewed_at timestamptz,
 approved_at timestamptz
);
alter table public.taxi_submissions enable row level security;
alter table public.auto_submissions enable row level security;
drop policy if exists "public submit taxi listing" on public.taxi_submissions;
create policy "public submit taxi listing" on public.taxi_submissions for insert to anon with check (status='pending');
drop policy if exists "single owner manages taxi submissions" on public.taxi_submissions;
create policy "single owner manages taxi submissions" on public.taxi_submissions for all to authenticated using (auth.uid()='b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid) with check (auth.uid()='b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid);
drop policy if exists "public submit auto listing" on public.auto_submissions;
create policy "public submit auto listing" on public.auto_submissions for insert to anon with check (status='pending');
drop policy if exists "single owner manages auto submissions" on public.auto_submissions;
create policy "single owner manages auto submissions" on public.auto_submissions for all to authenticated using (auth.uid()='b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid) with check (auth.uid()='b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid);

-- The legacy vehicle_submissions rows were confirmed to be test records and were cleared.
-- Drop the legacy combined table only after switching the app and Admin Panel to the new tables.
drop table if exists public.vehicle_submissions;
