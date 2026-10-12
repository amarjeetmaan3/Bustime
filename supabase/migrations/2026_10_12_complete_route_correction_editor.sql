-- Bustime: complete route correction editor
-- Apply once to the existing Supabase project. Safe to re-run.
alter table public.correction_submissions
  drop constraint if exists correction_submissions_request_type_check;
alter table public.correction_submissions
  add constraint correction_submissions_request_type_check
  check (request_type in ('missing_stop','correct_time','route_edit'));
alter table public.correction_submissions
  add column if not exists original_stops jsonb not null default '[]'::jsonb,
  add column if not exists proposed_stops jsonb not null default '[]'::jsonb,
  add column if not exists change_summary text;
alter table public.correction_submissions
  drop constraint if exists correction_submissions_proposed_stops_array;
alter table public.correction_submissions
  add constraint correction_submissions_proposed_stops_array
  check (jsonb_typeof(proposed_stops) = 'array' and jsonb_array_length(proposed_stops) <= 100);
create index if not exists correction_submissions_route_edit_idx
  on public.correction_submissions (route_id, created_at desc)
  where request_type = 'route_edit';
notify pgrst, 'reload schema';
