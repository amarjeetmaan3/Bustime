# Bustime incremental data sync

## What is deployed
- Supabase migration creates `data_sync_state` and `data_change_log`.
- A second migration removes direct anon/authenticated access to those tables.
- Edge Function `bustime-data-sync` serves status and delta pages to the Android app.
- Android checks on foreground/resume, supports the existing manual Refresh action, and schedules a network-constrained WorkManager check no more frequently than the platform's 15-minute periodic minimum.
- GitHub Actions compares changes to `app/src/main/assets/routes.json` and publishes per-entity upserts/deletions.

## Required GitHub Actions secret
In GitHub, open **amarjeetmaan3/Bustime → Settings → Secrets and variables → Actions → New repository secret**.

Create:
- **Name:** `SUPABASE_SERVICE_ROLE_KEY`
- **Value:** the project's Supabase **service_role** secret key from Supabase Project Settings → API Keys / API.

Never put this key in Android code or commit it to the repository. The publishing workflow uses it only in the GitHub Actions runner. The Android app uses the existing publishable key and calls the Edge Function.

## Initial baseline and future publication
The current `routes.json` baseline has already been seeded into Supabase as revision 1 (109 initial entity records). Older installations perform one full snapshot refresh before saving this revision cursor; later syncs download only deltas.

Every later change to `app/src/main/assets/routes.json` triggers **Publish transport data deltas**. The workflow requires the repository secret above. If a run fails because the secret is missing, add it and rerun the workflow from the Actions tab.

## Important behavior
- Each publish creates one revision and records only changed entities.
- Route payloads include their complete ordered stop list, applied transactionally.
- Deletes are recorded as tombstones so devices can remove old rows.
- The Android cursor advances only after all records in that revision apply.
- The first sync uses the current GitHub snapshot to remove stale records from older installations; subsequent syncs fetch only deltas.
- Offline devices keep their existing local database and retry when they next foreground or WorkManager runs.
