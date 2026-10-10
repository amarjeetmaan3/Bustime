-- Bustime migration: user-created routes, arbitrary stand names, optional stop times, admin review.
-- Run in Supabase SQL Editor after the existing setup.sql.

alter table public.route_submissions alter column start_time drop not null;
alter table public.route_submissions drop constraint if exists route_submissions_start_time_check;
alter table public.route_submissions add constraint route_submissions_start_time_check
  check (start_time is null or start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$');
alter table public.route_submissions drop column if exists long_route;
alter table public.route_submissions drop column if exists start_date;
alter table public.route_submissions add column if not exists from_text text;
alter table public.route_submissions add column if not exists to_text text;
alter table public.route_submissions add column if not exists route_text text;
alter table public.route_submissions add column if not exists submission_type text not null default 'route';
alter table public.route_submissions add column if not exists vehicle_type text;

-- New routes may use typed place names instead of an existing place ID.
alter table public.route_submissions alter column from_id drop not null;
alter table public.route_submissions alter column to_id drop not null;
alter table public.route_submissions drop constraint if exists route_submissions_from_id_check;
alter table public.route_submissions drop constraint if exists route_submissions_to_id_check;
alter table public.route_submissions drop constraint if exists route_submission_has_route;
alter table public.route_submissions add constraint route_submission_has_route check (
  (coalesce(nullif(trim(from_text),''), nullif(trim(from_id),'')) is not null) and
  (coalesce(nullif(trim(to_text),''), nullif(trim(to_id),'')) is not null)
);

-- At least one timing must be supplied: departure time or any stop's time.
create or replace function public.validate_route_submission_timing() returns trigger
language plpgsql as $$
begin
  if new.start_time is null and not exists (
    select 1 from jsonb_array_elements(coalesce(new.stops, '[]'::jsonb)) s
    where nullif(s->>'time', '') is not null
  ) then
    raise exception 'at least one bus stop timing is required';
  end if;
  return new;
end $$;
drop trigger if exists trg_route_submission_timing on public.route_submissions;
create trigger trg_route_submission_timing before insert or update on public.route_submissions
for each row execute function public.validate_route_submission_timing();

-- Store EV/Oil choice for auto listings; harmless when the table is not yet created.
alter table if exists public.vehicle_submissions add column if not exists fuel_type text;
alter table if exists public.vehicle_submissions add column if not exists seat_type text;
alter table if exists public.bus_operators add column if not exists seat_type text;
alter table if exists public.bus_operators add column if not exists location text;
alter table if exists public.bus_operators add column if not exists whatsapp text;

-- Additional fields used by the multilingual free-text route submission flow.
alter table public.route_submissions add column if not exists from_normalized text;
alter table public.route_submissions add column if not exists to_normalized text;
alter table public.route_submissions add column if not exists source_language text not null default 'en';
alter table public.route_submissions drop constraint if exists route_submissions_source_language_check;
alter table public.route_submissions add constraint route_submissions_source_language_check
  check (source_language in ('en','hi','pa'));

-- Normalize whitespace/case for duplicate detection and search. Script transliteration/translation
-- is deliberately completed in the authenticated admin review workflow, not trusted from clients.
create or replace function public.normalize_route_submission_names() returns trigger
language plpgsql as $$
begin
  if new.from_text is not null then
    new.from_normalized := coalesce(nullif(new.from_normalized, ''), lower(regexp_replace(trim(new.from_text), '\s+', ' ', 'g')));
  end if;
  if new.to_text is not null then
    new.to_normalized := coalesce(nullif(new.to_normalized, ''), lower(regexp_replace(trim(new.to_text), '\s+', ' ', 'g')));
  end if;
  return new;
end $$;
drop trigger if exists trg_normalize_route_submission_names on public.route_submissions;
create trigger trg_normalize_route_submission_names before insert or update on public.route_submissions
for each row execute function public.normalize_route_submission_names();

-- The admin form may publish these optional operator details.
alter table public.bus_operators add column if not exists seat_type text;
alter table public.bus_operators add column if not exists location text;
alter table public.bus_operators add column if not exists whatsapp text;
