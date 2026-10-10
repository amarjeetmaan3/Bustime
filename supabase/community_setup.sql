-- Run in Supabase SQL Editor after the existing supabase/setup.sql.
-- Every request gets its own UUID row; intentionally no uniqueness/deduplication by route.

create table if not exists public.correction_submissions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  route_id text not null,
  request_type text not null check (request_type in ('missing_stop','correct_time')),
  before_stop_id text,
  after_stop_id text,
  missing_place_name text,
  missing_place_id text,
  missing_time text check (missing_time is null or missing_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  stop_id text,
  corrected_time text check (corrected_time is null or corrected_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  notes text,
  status text not null default 'pending' check (status in ('pending','approved','rejected'))
);
alter table public.correction_submissions add column if not exists missing_place_id text;
create index if not exists correction_submissions_status_idx on public.correction_submissions(status, created_at desc);

create table if not exists public.vehicle_submissions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  vehicle_type text not null check (vehicle_type in ('auto','taxi')),
  person_name text not null,
  vehicle_name text not null,
  seats integer check (seats is null or seats between 1 and 100),
  location text not null,
  phone text not null check (phone ~ '^[0-9]{10,13}$'),
  details text,
  status text not null default 'pending' check (status in ('pending','approved','rejected'))
);
create index if not exists vehicle_submissions_status_idx on public.vehicle_submissions(status, created_at desc);

create table if not exists public.feedback_submissions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  category text not null,
  related_route text,
  message text not null check (char_length(message) between 4 and 5000),
  contact text,
  status text not null default 'pending' check (status in ('pending','approved','rejected'))
);
create index if not exists feedback_submissions_status_idx on public.feedback_submissions(status, created_at desc);

create table if not exists public.bus_operators (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  operator_name text not null check (char_length(operator_name) between 1 and 120),
  service_name text,
  bus_type text not null default 'Bus',
  from_text text,
  to_text text,
  phone text not null check (phone ~ '^[+0-9][+0-9 -]{8,18}$'),
  details text,
  active boolean not null default true
);
create index if not exists bus_operators_active_idx on public.bus_operators(active, operator_name);

alter table public.correction_submissions enable row level security;
alter table public.vehicle_submissions enable row level security;
alter table public.feedback_submissions enable row level security;
alter table public.bus_operators enable row level security;

revoke all on public.correction_submissions, public.vehicle_submissions, public.feedback_submissions, public.bus_operators from anon, authenticated;
grant insert on public.correction_submissions, public.vehicle_submissions, public.feedback_submissions to anon;
grant select, update, delete on public.correction_submissions, public.vehicle_submissions, public.feedback_submissions to authenticated;
grant select on public.bus_operators to anon, authenticated;
grant insert, update, delete on public.bus_operators to authenticated;

drop policy if exists "public submit correction" on public.correction_submissions;
create policy "public submit correction" on public.correction_submissions for insert to anon with check (status = 'pending');
drop policy if exists "admin manage corrections" on public.correction_submissions;
create policy "admin manage corrections" on public.correction_submissions for all to authenticated using (true) with check (true);

drop policy if exists "public submit vehicle" on public.vehicle_submissions;
create policy "public submit vehicle" on public.vehicle_submissions for insert to anon with check (status = 'pending');
drop policy if exists "admin manage vehicles" on public.vehicle_submissions;
create policy "admin manage vehicles" on public.vehicle_submissions for all to authenticated using (true) with check (true);

drop policy if exists "public submit feedback" on public.feedback_submissions;
create policy "public submit feedback" on public.feedback_submissions for insert to anon with check (status = 'pending');
drop policy if exists "admin manage feedback" on public.feedback_submissions;
create policy "admin manage feedback" on public.feedback_submissions for all to authenticated using (true) with check (true);

drop policy if exists "public read active bus operators" on public.bus_operators;
create policy "public read active bus operators" on public.bus_operators for select to anon, authenticated using (active = true);
drop policy if exists "admin manage bus operators" on public.bus_operators;
create policy "admin manage bus operators" on public.bus_operators for all to authenticated using (true) with check (true);
