-- Bustime campaign poster storage setup and browser-admin upload policy.
-- Bucket is public for image display in the Android app. Browser uploads are limited
-- to supported image extensions; the admin UI enforces the 8 MiB limit and MIME types.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ads-posters', 'ads-posters', true, 8388608, array['image/png','image/jpeg','image/webp'])
on conflict (id) do update
set public = true, file_size_limit = 8388608,
    allowed_mime_types = array['image/png','image/jpeg','image/webp'];

drop policy if exists "Public can view ad posters" on storage.objects;
create policy "Public can view ad posters"
on storage.objects for select using (bucket_id = 'ads-posters');

drop policy if exists "Signed-in users can upload ad posters" on storage.objects;
drop policy if exists "Signed-in users can update ad posters" on storage.objects;
drop policy if exists "Browser admins can upload ad posters" on storage.objects;
create policy "Browser admins can upload ad posters"
on storage.objects for insert to anon, authenticated
with check (
  bucket_id = 'ads-posters'
  and lower(storage.extension(name)) in ('png','jpg','jpeg','webp')
);

drop policy if exists "Browser admins can update ad posters" on storage.objects;
create policy "Browser admins can update ad posters"
on storage.objects for update to anon, authenticated
using (bucket_id = 'ads-posters')
with check (
  bucket_id = 'ads-posters'
  and lower(storage.extension(name)) in ('png','jpg','jpeg','webp')
);