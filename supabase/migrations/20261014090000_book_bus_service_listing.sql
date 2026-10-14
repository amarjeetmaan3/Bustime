-- Book Bus is a company/service contact directory, not seat reservation.
alter table public.bus_operators
  add column if not exists bus_types jsonb not null default '[]'::jsonb;
alter table public.bus_operator_submissions
  add column if not exists bus_types jsonb not null default '[]'::jsonb;

-- Backfill older listings so they remain readable while old app builds are phased out.
update public.bus_operators
set bus_types = case
  when nullif(trim(coalesce(custom_category, '')), '') is not null
    then jsonb_build_array(coalesce(nullif(trim(bus_type), ''), 'Bus'), trim(custom_category))
  else jsonb_build_array(coalesce(nullif(trim(bus_type), ''), 'Bus'))
end
where bus_types = '[]'::jsonb;

update public.bus_operator_submissions
set bus_types = case
  when nullif(trim(coalesce(custom_category, '')), '') is not null
    then jsonb_build_array(coalesce(nullif(trim(bus_type), ''), 'Ordinary'), trim(custom_category))
  else jsonb_build_array(coalesce(nullif(trim(bus_type), ''), 'Ordinary'))
end
where bus_types = '[]'::jsonb;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'bus_operators_bus_types_array_check') then
    alter table public.bus_operators add constraint bus_operators_bus_types_array_check
      check (jsonb_typeof(bus_types) = 'array' and jsonb_array_length(bus_types) <= 20);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'bus_operator_submissions_bus_types_array_check') then
    alter table public.bus_operator_submissions add constraint bus_operator_submissions_bus_types_array_check
      check (jsonb_typeof(bus_types) = 'array' and jsonb_array_length(bus_types) <= 20);
  end if;
end $$;
