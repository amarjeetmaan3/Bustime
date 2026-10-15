-- Bustime now supports Bus Routes and Book Bus only.
-- These submission tables are exclusive to the retired Taxi and Auto listings.
-- Bus route corrections, bus operator submissions, feedback, and published bus data remain untouched.
drop table if exists public.taxi_submissions;
drop table if exists public.auto_submissions;
