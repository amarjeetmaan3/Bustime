-- Multilingual Terms/Disclaimer, driver warning popup, and scrolling warning ticker.
-- Run once in Supabase Dashboard > SQL Editor. Admin edits content from the Admin panel.
create table if not exists public.app_content_settings (
  id integer primary key check (id = 1),
  settings jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

insert into public.app_content_settings (id, settings)
values (1, jsonb_build_object(
  'terms_enabled', true,
  'terms', jsonb_build_object('en', '', 'hi', '', 'pa', ''),
  'popup_enabled', true,
  'popup', jsonb_build_object('en', '', 'hi', '', 'pa', ''),
  'popup_seconds', 5,
  'ticker_enabled', true,
  'ticker', jsonb_build_object('en', '', 'hi', '', 'pa', '')
))
on conflict (id) do nothing;

alter table public.app_content_settings enable row level security;
revoke all on public.app_content_settings from anon, authenticated;
grant select on public.app_content_settings to anon, authenticated;
grant select, insert, update on public.app_content_settings to authenticated;

drop policy if exists "public can read app content settings" on public.app_content_settings;
create policy "public can read app content settings"
  on public.app_content_settings for select to anon, authenticated using (true);

drop policy if exists "authenticated admin can insert app content settings" on public.app_content_settings;
create policy "authenticated admin can insert app content settings"
  on public.app_content_settings for insert to authenticated with check (id = 1 and auth.uid() = 'b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid);

drop policy if exists "authenticated admin can update app content settings" on public.app_content_settings;
create policy "authenticated admin can update app content settings"
  on public.app_content_settings for update to authenticated using (auth.uid() = 'b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid) with check (id = 1);

create or replace function public.touch_app_content_settings_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
drop trigger if exists trg_app_content_settings_updated_at on public.app_content_settings;
create trigger trg_app_content_settings_updated_at
before update on public.app_content_settings
for each row execute function public.touch_app_content_settings_updated_at();
