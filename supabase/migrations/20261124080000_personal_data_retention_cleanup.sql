-- Automatic retention: only feedback and data-deletion requests expire.
-- Route, correction and operator submissions, analytics and sync records are kept;
-- published routes, places and operator listings are never touched here.
-- Applied to the project as "personal_data_retention_cleanup" (+ later versions) and
-- finally "retention_cleanup_feedback_only" (the function below).
create extension if not exists pg_cron;

create or replace function public.purge_expired_personal_data()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  n_feedback int;
begin
  delete from public.feedback_submissions
   where (status <> 'pending' and coalesce(reviewed_at, created_at) < now() - interval '90 days')
      or (status = 'pending' and created_at < now() - interval '180 days');
  get diagnostics n_feedback = row_count;
  return jsonb_build_object('feedback_deleted', n_feedback);
end;
$$;

revoke all on function public.purge_expired_personal_data() from public, anon, authenticated;

-- Runs every day at 21:30 UTC (3:00 AM India time).
select cron.schedule('purge-expired-personal-data', '30 21 * * *', $cron$select public.purge_expired_personal_data();$cron$);
