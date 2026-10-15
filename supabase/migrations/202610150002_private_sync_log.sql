-- Keep the sync log private; Android reads it only through the sync Edge Function.
drop policy if exists "Public can read sync state" on public.data_sync_state;
drop policy if exists "Public can read sync changes" on public.data_change_log;
revoke all on public.data_sync_state from anon, authenticated;
revoke all on public.data_change_log from anon, authenticated;
