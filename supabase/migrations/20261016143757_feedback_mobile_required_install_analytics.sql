-- Require a valid mobile number for every feedback submission, including delete requests.
-- Existing data was checked before applying this migration.
ALTER TABLE public.feedback_submissions
  ALTER COLUMN contact SET NOT NULL;

ALTER TABLE public.feedback_submissions
  ADD CONSTRAINT feedback_contact_phone_check
  CHECK (contact ~ '^[0-9]{10,13}$');

-- Speeds up the all-time distinct-installation lookup in the web admin analytics panel.
CREATE INDEX IF NOT EXISTS analytics_events_app_install_session_idx
  ON public.analytics_events (session_id)
  WHERE event_name = 'app_install';
