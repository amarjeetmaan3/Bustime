-- Bustime: route submission times are optional.
-- Keeps stop times available when supplied, but never blocks a submission
-- because every stop and the start time are blank.
create or replace function public.validate_route_submission_timing() returns trigger
language plpgsql as $$
begin
  if new.status is null then
    new.status := 'pending';
  end if;
  return new;
end $$;

drop trigger if exists trg_route_submission_timing on public.route_submissions;
create trigger trg_route_submission_timing
before insert or update on public.route_submissions
for each row execute function public.validate_route_submission_timing();

notify pgrst, 'reload schema';
