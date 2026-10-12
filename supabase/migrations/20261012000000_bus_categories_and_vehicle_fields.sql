alter table public.vehicle_submissions
  add column if not exists vehicle_model_number text,
  add column if not exists vehicle_number text;

create table if not exists public.bus_categories (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

insert into public.bus_categories(name) values
  ('Ordinary'), ('Luxury'), ('Sleeper'), ('Sleeper Plus'),
  ('Seater Plus'), ('Seater'), ('Sleeper + Seater'), ('Custom')
on conflict (name) do nothing;

alter table public.bus_categories enable row level security;

drop policy if exists "Public can read active bus categories" on public.bus_categories;
create policy "Public can read active bus categories" on public.bus_categories
  for select to anon, authenticated using (active = true);

drop policy if exists "Authenticated admins manage bus categories" on public.bus_categories;
create policy "Authenticated admins manage bus categories" on public.bus_categories
  for all to authenticated using (true) with check (true);
