-- Store the four-digit vehicle year separately from the vehicle name.
alter table public.vehicle_submissions
  add column if not exists year text;

-- Enforce a four-digit numeric year for new submissions; old rows may remain null.
do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'vehicle_submissions_year_format_check'
      and conrelid = 'public.vehicle_submissions'::regclass
  ) then
    alter table public.vehicle_submissions
      add constraint vehicle_submissions_year_format_check
      check (year is null or year ~ '^[0-9]{4}$');
  end if;
end $$;

-- Existing vehicle_model_number values remain available for historical submissions.
