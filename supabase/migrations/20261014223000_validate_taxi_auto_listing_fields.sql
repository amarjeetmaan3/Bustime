-- Enforce the same required Taxi/Auto form rules at the database boundary.
-- This affects only the Taxi and Auto submission tables.
alter table public.taxi_submissions
  add constraint taxi_year_four_digits check (year ~ '^[0-9]{4}$'),
  add constraint taxi_seats_positive check (seats > 0),
  add constraint taxi_phone_digits check (phone ~ '^[0-9]{10,13}$'),
  add constraint taxi_required_text_nonblank check (
    length(btrim(person_name)) > 0 and length(btrim(vehicle_name)) > 0 and
    length(btrim(vehicle_number)) > 0 and length(btrim(location)) > 0 and
    length(btrim(union_name)) > 0
  );

alter table public.auto_submissions
  add constraint auto_year_four_digits check (year ~ '^[0-9]{4}$'),
  add constraint auto_seats_positive check (seats > 0),
  add constraint auto_phone_digits check (phone ~ '^[0-9]{10,13}$'),
  add constraint auto_fuel_type_valid check (lower(fuel_type) in ('ev','oil','cng','other')),
  add constraint auto_required_text_nonblank check (
    length(btrim(person_name)) > 0 and length(btrim(vehicle_name)) > 0 and
    length(btrim(vehicle_number)) > 0 and length(btrim(location)) > 0 and
    length(btrim(union_name)) > 0
  );

notify pgrst, 'reload schema';
