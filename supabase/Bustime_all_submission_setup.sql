-- ==================== setup.sql ====================
-- Bustime: यूज़र से आने वाले रूट रखने की टेबल
-- Supabase Dashboard -> SQL Editor -> New query में पूरा चिपकाकर RUN दबाओ (एक ही बार)।

create table if not exists public.route_submissions (
  id            uuid primary key default gen_random_uuid(),
  created_at    timestamptz not null default now(),
  from_id       text not null check (char_length(from_id) between 1 and 100),
  to_id         text not null check (char_length(to_id)   between 1 and 100),
  start_time    text not null check (start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  service_name  text check (service_name is null or char_length(service_name) <= 60),
  long_route    boolean not null default false,
  start_date    date,
  stops         jsonb not null default '[]'::jsonb
                check (jsonb_typeof(stops) = 'array' and jsonb_array_length(stops) <= 80),
  phone         text not null check (phone ~ '^[0-9]{10,13}$'),
  status        text not null default 'pending' check (status in ('pending','approved','rejected'))
);

create index if not exists route_submissions_status_idx on public.route_submissions (status, created_at desc);

alter table public.route_submissions add column if not exists long_route boolean not null default false;
alter table public.route_submissions add column if not exists start_date date;

-- सुरक्षा: Row Level Security चालू
alter table public.route_submissions enable row level security;

-- ऐप (anon) को सिर्फ़ INSERT की इजाज़त; पढ़ना, बदलना, मिटाना नहीं
revoke all on public.route_submissions from anon, authenticated;
grant insert on public.route_submissions to anon;
grant select, update, delete on public.route_submissions to authenticated;

drop policy if exists "app can submit"    on public.route_submissions;
drop policy if exists "admin can read"    on public.route_submissions;
drop policy if exists "admin can update"  on public.route_submissions;
drop policy if exists "admin can delete rejected" on public.route_submissions;

create policy "app can submit" on public.route_submissions
  for insert to anon with check (status = 'pending');

-- लॉगिन किया हुआ एडमिन (authenticated) देख और बदल सकता है
create policy "admin can read" on public.route_submissions
  for select to authenticated using (true);

create policy "admin can update" on public.route_submissions
  for update to authenticated using (true) with check (true);

create policy "admin can delete rejected" on public.route_submissions
  for delete to authenticated using (status = 'rejected');

-- स्पैम रोक: एक नंबर से 24 घंटे में 5 से ज़्यादा नहीं, और कुल मिलाकर घंटे में 200 से ज़्यादा नहीं
create or replace function public.limit_route_submissions() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.route_submissions
        where phone = new.phone and created_at > now() - interval '24 hours') >= 5 then
    raise exception 'too many submissions from this number';
  end if;
  if (select count(*) from public.route_submissions
        where created_at > now() - interval '1 hour') >= 200 then
    raise exception 'too many submissions, try later';
  end if;
  return new;
end $$;

drop trigger if exists trg_limit_route_submissions on public.route_submissions;
create trigger trg_limit_route_submissions before insert on public.route_submissions
  for each row execute function public.limit_route_submissions();


-- ==================== community_setup.sql ====================
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


-- ==================== migration_free_text_new_routes.sql ====================
-- Enables free-text new-route submissions in any script/language.
-- Run once in Supabase SQL Editor. Existing route submissions remain compatible.
alter table public.route_submissions alter column start_time drop not null;
alter table public.route_submissions drop constraint if exists route_submissions_start_time_check;
alter table public.route_submissions add constraint route_submissions_start_time_check
  check (start_time is null or start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
alter table public.route_submissions alter column from_id drop not null;
alter table public.route_submissions alter column to_id drop not null;
alter table public.route_submissions add column if not exists from_text text;
alter table public.route_submissions add column if not exists to_text text;
alter table public.route_submissions add column if not exists route_text text;
alter table public.route_submissions add column if not exists submission_type text not null default 'route';
alter table public.route_submissions drop constraint if exists route_submissions_from_id_check;
alter table public.route_submissions drop constraint if exists route_submissions_to_id_check;
alter table public.route_submissions drop constraint if exists route_submission_has_route;
alter table public.route_submissions add constraint route_submission_has_route check (
  (coalesce(nullif(trim(from_text),''), nullif(trim(from_id),'')) is not null) and
  (coalesce(nullif(trim(to_text),''), nullif(trim(to_id),'')) is not null)
);
create or replace function public.validate_route_submission_timing() returns trigger
language plpgsql as $$
begin
  if new.start_time is null and not exists (
    select 1 from jsonb_array_elements(coalesce(new.stops, '[]'::jsonb)) s
    where nullif(s->>'time', '') is not null
  ) then raise exception 'at least one timing is required'; end if;
  return new;
end $$;
drop trigger if exists trg_route_submission_timing on public.route_submissions;
create trigger trg_route_submission_timing before insert or update on public.route_submissions
for each row execute function public.validate_route_submission_timing();



-- Ensure API roles have the expected table privileges after migrations.
grant usage on schema public to anon, authenticated;
grant insert on public.route_submissions to anon;
grant insert on public.correction_submissions, public.vehicle_submissions, public.feedback_submissions to anon;
grant select on public.bus_operators to anon, authenticated;
