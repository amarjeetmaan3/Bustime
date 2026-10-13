-- Bustime Admin Review Workflow migration. Run once in Supabase SQL Editor.
-- Keep user-submitted columns intact; admin edits are stored separately in reviewed_data.
alter table public.route_submissions add column if not exists reviewed_data jsonb, add column if not exists reviewed_at timestamptz, add column if not exists approved_at timestamptz;
alter table public.correction_submissions add column if not exists reviewed_data jsonb, add column if not exists reviewed_at timestamptz, add column if not exists approved_at timestamptz;
alter table public.vehicle_submissions add column if not exists reviewed_data jsonb, add column if not exists reviewed_at timestamptz, add column if not exists approved_at timestamptz;
alter table public.feedback_submissions add column if not exists reviewed_data jsonb, add column if not exists reviewed_at timestamptz, add column if not exists approved_at timestamptz;
alter table public.bus_operator_submissions add column if not exists reviewed_data jsonb, add column if not exists reviewed_at timestamptz, add column if not exists approved_at timestamptz;
