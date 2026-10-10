-- Bustime: safe setup/repair for route submissions and free-text routes.
-- Run once in Supabase Dashboard > SQL Editor. Keeps RLS enabled.

create table if not exists public.route_submissions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  from_id text,
  to_id text,
  start_time text,
  service_name text,
  stops jsonb not null default '[]'::jsonb,
  phone text not null,
  status text not null default 'pending',
  long_route boolean not null default false,
  start_date date
);

alter table public.route_submissions add column if not exists created_at timestamptz not null default now();
alter table public.route_submissions add column if not exists from_id text;
alter table public.route_submissions add column if not exists to_id text;
alter table public.route_submissions add column if not exists start_time text;
alter table public.route_submissions add column if not exists service_name text;
alter table public.route_submissions add column if not exists stops jsonb not null default '[]'::jsonb;
alter table public.route_submissions add column if not exists phone text;
alter table public.route_submissions add column if not exists status text not null default 'pending';
alter table public.route_submissions add column if not exists long_route boolean not null default false;
alter table public.route_submissions add column if not exists start_date date;
alter table public.route_submissions add column if not exists from_text text;
alter table public.route_submissions add column if not exists to_text text;
alter table public.route_submissions add column if not exists route_text text;
alter table public.route_submissions add column if not exists submission_type text not null default 'route';

alter table public.route_submissions alter column from_id drop not null;
alter table public.route_submissions alter column to_id drop not null;
alter table public.route_submissions alter column start_time drop not null;

-- Remove older inline checks that reject null IDs/times for a free-text route.
alter table public.route_submissions drop constraint if exists route_submissions_from_id_check;
alter table public.route_submissions drop constraint if exists route_submissions_to_id_check;
alter table public.route_submissions drop constraint if exists route_submissions_start_time_check;
alter table public.route_submissions drop constraint if exists route_submission_has_route;
alter table public.route_submissions drop constraint if exists route_submissions_stops_check;
alter table public.route_submissions add constraint route_submissions_start_time_check
  check (start_time is null or start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
alter table public.route_submissions add constraint route_submission_has_route check (
  coalesce(nullif(trim(from_text), ''), nullif(trim(from_id), '')) is not null and
  coalesce(nullif(trim(to_text), ''), nullif(trim(to_id), '')) is not null
);
alter table public.route_submissions add constraint route_submissions_stops_check
  check (jsonb_typeof(stops) = 'array' and jsonb_array_length(stops) <= 80);

create or replace function public.validate_route_submission_timing() returns trigger
language plpgsql as $$
begin
  if new.start_time is null and not exists (
    select 1 from jsonb_array_elements(coalesce(new.stops, '[]'::jsonb)) s
    where nullif(s->>'time', '') is not null
  ) then
    raise exception 'At least one time is required (start time or a stop time)';
  end if;
  if new.status is null then new.status := 'pending'; end if;
  return new;
end $$;
drop trigger if exists trg_route_submission_timing on public.route_submissions;
create trigger trg_route_submission_timing before insert or update on public.route_submissions
for each row execute function public.validate_route_submission_timing();

alter table public.route_submissions enable row level security;
grant usage on schema public to anon;
grant insert on public.route_submissions to anon;
revoke select, update, delete on public.route_submissions from anon;
drop policy if exists "app can submit" on public.route_submissions;
create policy "app can submit" on public.route_submissions
  for insert to anon with check (status = 'pending');

create index if not exists route_submissions_status_idx on public.route_submissions (status, created_at desc);
notify pgrst, 'reload schema';
