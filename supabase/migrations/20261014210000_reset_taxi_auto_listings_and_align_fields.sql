-- Taxi / Auto reset and schema alignment (2026-10-14)
-- Published Taxi/Auto records were removed from routes.json (dataVersion 58).
-- Only Taxi/Auto submission tables are affected here; other application tables remain untouched.

alter table public.taxi_submissions
  add column if not exists location_id text;

alter table public.auto_submissions
  add column if not exists location_id text;

-- Keep these fields mandatory so incomplete listings cannot be submitted/published.
alter table public.taxi_submissions
  alter column person_name set not null,
  alter column vehicle_name set not null,
  alter column year set not null,
  alter column seats set not null,
  alter column vehicle_number set not null,
  alter column location set not null,
  alter column union_name set not null,
  alter column phone set not null;

alter table public.auto_submissions
  alter column person_name set not null,
  alter column vehicle_name set not null,
  alter column year set not null,
  alter column seats set not null,
  alter column vehicle_number set not null,
  alter column location set not null,
  alter column union_name set not null,
  alter column phone set not null,
  alter column fuel_type set not null;

notify pgrst, 'reload schema';
