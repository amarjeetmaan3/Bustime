-- Bustime production fix: allow the authenticated admin role to issue DELETE
-- requests against route_submissions. RLS still restricts which rows can be
-- deleted to the single owner-admin policy; anon remains unable to delete.
-- Safe to run more than once.
GRANT DELETE ON TABLE public.route_submissions TO authenticated;
