-- Start.io ad controls for Bustime.
-- The Android app can read the active ad configuration; only the configured admin can change it.
create table if not exists public.ad_settings (
  id integer primary key check (id = 1),
  config jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

insert into public.ad_settings (id, config)
values (1, jsonb_build_object(
  'my', jsonb_build_object('enabled', false),
  'startio', jsonb_build_object(
    'app_id', '',
    'banner_enabled', false,
    'main_banner_enabled', false,
    'book_bus_banner_enabled', false,
    'native_enabled', false,
    'results_native_enabled', false,
    'fullscreen_enabled', false,
    'bus_every', 4,
    'fullscreen_every', 5
  )
))
on conflict (id) do nothing;

alter table public.ad_settings enable row level security;
revoke all on public.ad_settings from anon, authenticated;
grant select on public.ad_settings to anon, authenticated;
grant insert, update on public.ad_settings to authenticated;

drop policy if exists "Public can read ad settings" on public.ad_settings;
create policy "Public can read ad settings"
  on public.ad_settings for select to anon, authenticated using (true);

drop policy if exists "Bustime admin can insert ad settings" on public.ad_settings;
create policy "Bustime admin can insert ad settings"
  on public.ad_settings for insert to authenticated
  with check (id = 1 and auth.uid() = 'b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid);

drop policy if exists "Bustime admin can update ad settings" on public.ad_settings;
create policy "Bustime admin can update ad settings"
  on public.ad_settings for update to authenticated
  using (auth.uid() = 'b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid)
  with check (id = 1);
