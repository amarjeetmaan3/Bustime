-- Allow only the Bustime owner admin to delete objects from the public ad-poster bucket.
-- Public read and owner-only upload/update policies remain unchanged.
create policy "single owner deletes ad posters"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'ads-posters'
  and (select auth.uid()) = 'b0787029-45dc-4f37-8568-6a09a4fe2ad9'::uuid
);
