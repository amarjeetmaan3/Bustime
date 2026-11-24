-- Automatic retention for personal data collected through the app.
-- Published routes, places and live operator listings are NOT touched here.
-- Applied to the project as "personal_data_retention_cleanup" and
-- "personal_data_retention_cleanup_phone_placeholder" (final function below).
-- Removed phone numbers become 0000000000 because the phone columns have a 10-13 digit CHECK.
create extension if not exists pg_cron;

create or replace function public.purge_expired_personal_data()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  removed_phone constant text := '0000000000';
  scrub_keys text[] := array['phone','whatsapp','contact','mobile','message','notes'];
  n_route int; n_operator int; n_correction int; n_feedback int;
  n_analytics int; n_errors int; n_devices int;
begin
  update public.route_submissions
     set phone = removed_phone, reviewed_data = reviewed_data - scrub_keys
   where (phone <> removed_phone or reviewed_data ?| scrub_keys)
     and ((status <> 'pending' and coalesce(reviewed_at, created_at) < now() - interval '90 days')
       or (status = 'pending' and created_at < now() - interval '180 days'));
  get diagnostics n_route = row_count;

  update public.bus_operator_submissions
     set phone = removed_phone, whatsapp = null, details = null, reviewed_data = reviewed_data - scrub_keys
   where (phone <> removed_phone or whatsapp is not null or details is not null or reviewed_data ?| scrub_keys)
     and ((status <> 'pending' and coalesce(reviewed_at, created_at) < now() - interval '90 days')
       or (status = 'pending' and created_at < now() - interval '180 days'));
  get diagnostics n_operator = row_count;

  update public.correction_submissions
     set phone = null, notes = null, reviewed_data = reviewed_data - scrub_keys
   where (phone is not null or notes is not null or reviewed_data ?| scrub_keys)
     and ((status <> 'pending' and coalesce(reviewed_at, created_at) < now() - interval '90 days')
       or (status = 'pending' and created_at < now() - interval '180 days'));
  get diagnostics n_correction = row_count;

  delete from public.feedback_submissions
   where (status <> 'pending' and coalesce(reviewed_at, created_at) < now() - interval '90 days')
      or (status = 'pending' and created_at < now() - interval '180 days');
  get diagnostics n_feedback = row_count;

  delete from public.analytics_events where created_at < now() - interval '180 days';
  get diagnostics n_analytics = row_count;

  delete from public.sync_error_logs
   where (resolved_at is not null and resolved_at < now() - interval '90 days')
      or last_failed_at < now() - interval '180 days';
  get diagnostics n_errors = row_count;

  delete from public.sync_device_state where last_seen_at < now() - interval '180 days';
  get diagnostics n_devices = row_count;

  return jsonb_build_object(
    'route_submissions_scrubbed', n_route, 'operator_submissions_scrubbed', n_operator,
    'correction_submissions_scrubbed', n_correction, 'feedback_deleted', n_feedback,
    'analytics_deleted', n_analytics, 'sync_errors_deleted', n_errors, 'sync_devices_deleted', n_devices);
end;
$$;

revoke all on function public.purge_expired_personal_data() from public, anon, authenticated;

-- Runs every day at 21:30 UTC (3:00 AM India time).
select cron.schedule('purge-expired-personal-data', '30 21 * * *', $cron$select public.purge_expired_personal_data();$cron$);
