-- Run once in Supabase SQL Editor for the updated Book Bus + taxi/auto forms.
-- Safe to re-run: columns/tables/policies use IF NOT EXISTS / DROP POLICY IF EXISTS.

ALTER TABLE public.vehicle_submissions ADD COLUMN IF NOT EXISTS fuel text;
ALTER TABLE public.vehicle_submissions ADD COLUMN IF NOT EXISTS union_name text;

ALTER TABLE public.bus_operators ADD COLUMN IF NOT EXISTS seat_type text;
ALTER TABLE public.bus_operators ADD COLUMN IF NOT EXISTS sleeper_count integer;
ALTER TABLE public.bus_operators ADD COLUMN IF NOT EXISTS seater_count integer;
ALTER TABLE public.bus_operators ADD COLUMN IF NOT EXISTS whatsapp text;
ALTER TABLE public.bus_operators ADD COLUMN IF NOT EXISTS location text;

CREATE TABLE IF NOT EXISTS public.bus_operator_submissions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  operator_name text NOT NULL,
  service_name text,
  bus_type text NOT NULL DEFAULT 'Ordinary',
  seat_type text,
  sleeper_count integer,
  seater_count integer,
  from_text text,
  to_text text,
  location text,
  phone text NOT NULL,
  whatsapp text,
  details text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','rejected'))
);

ALTER TABLE public.bus_operator_submissions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.bus_operator_submissions FROM anon, authenticated;
GRANT INSERT ON public.bus_operator_submissions TO anon;
GRANT SELECT, UPDATE, DELETE ON public.bus_operator_submissions TO authenticated;
DROP POLICY IF EXISTS "public submit bus listing" ON public.bus_operator_submissions;
CREATE POLICY "public submit bus listing" ON public.bus_operator_submissions
  FOR INSERT TO anon WITH CHECK (status = 'pending');
DROP POLICY IF EXISTS "admin manage bus listings" ON public.bus_operator_submissions;
CREATE POLICY "admin manage bus listings" ON public.bus_operator_submissions
  FOR ALL TO authenticated USING (true) WITH CHECK (true);
