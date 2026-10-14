-- Store the four-digit vehicle year separately from the vehicle name.
alter table public.vehicle_submissions
  add column if not exists year text;

-- Existing vehicle_model_number values remain available for historical submissions.
