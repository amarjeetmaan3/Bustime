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
