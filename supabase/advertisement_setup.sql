-- Bustime Advertisement configuration
-- Run once in Supabase SQL Editor. Public app can read config; only signed-in users can change it.
create table if not exists public.ad_settings (
  id integer primary key check (id = 1),
  config jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.ad_settings enable row level security;
drop policy if exists "Advertisement config readable by app" on public.ad_settings;
create policy "Advertisement config readable by app"
  on public.ad_settings for select to anon, authenticated using (true);
drop policy if exists "Signed-in admins can manage advertisement config" on public.ad_settings;
create policy "Signed-in admins can manage advertisement config"
  on public.ad_settings for all to authenticated using (true) with check (true);
insert into public.ad_settings (id, config)
values (1, jsonb_build_object(
  'my', jsonb_build_object('enabled', false, 'category', 'general', 'poster_url', '', 'start_date', '', 'end_date', '', 'close_delay', 3, 'route_id', '', 'check_route_enabled', false, 'call_enabled', false, 'phone', '', 'link_enabled', false, 'url', '', 'cta_label', 'Learn more'),
  'admob', jsonb_build_object('banner_enabled', false, 'native_enabled', false, 'banner_unit_id', '', 'native_unit_id', '', 'placements', jsonb_build_array('bus_search_top'), 'bus_every', 4, 'taxi_every', 4, 'auto_every', 4)
))
on conflict (id) do nothing;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ads-posters', 'ads-posters', true, 8388608, array['image/png','image/jpeg','image/webp'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;
drop policy if exists "Public can view ad posters" on storage.objects;
create policy "Public can view ad posters" on storage.objects for select using (bucket_id = 'ads-posters');
drop policy if exists "Signed-in users can upload ad posters" on storage.objects;
create policy "Signed-in users can upload ad posters" on storage.objects for insert to authenticated with check (bucket_id = 'ads-posters');
drop policy if exists "Signed-in users can update ad posters" on storage.objects;
create policy "Signed-in users can update ad posters" on storage.objects for update to authenticated using (bucket_id = 'ads-posters') with check (bucket_id = 'ads-posters');
