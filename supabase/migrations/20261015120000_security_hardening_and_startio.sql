-- Security hardening: enforce rate limits on every public submission table
-- and seed Start.io controls without overwriting existing advertisement settings.
create or replace function public.enforce_public_submission_limits()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_phone_column text;
  v_phone text;
  v_count bigint;
begin
  if tg_table_name not in (
    'route_submissions',
    'correction_submissions',
    'feedback_submissions',
    'bus_operator_submissions'
  ) then
    raise exception 'submission rate limiter is not configured for this table';
  end if;

  v_phone_column := case when tg_table_name = 'feedback_submissions' then 'contact' else 'phone' end;
  v_phone := regexp_replace(coalesce(to_jsonb(new)->>'phone', to_jsonb(new)->>'contact', ''), '[^0-9]', '', 'g');
  if length(v_phone) < 7 then
    v_phone := null;
  end if;

  -- Serialize inserts per table/phone so parallel requests cannot trivially race the per-phone limit.
  if v_phone is not null then
    perform pg_advisory_xact_lock(hashtext('bustime-submit:' || tg_table_name || ':' || v_phone));
    execute format(
      'select count(*) from public.%I where regexp_replace(coalesce(%I, ''''), ''[^0-9]'', '''', ''g'') = $1 and created_at > now() - interval ''24 hours''',
      tg_table_name, v_phone_column
    ) into v_count using v_phone;
    if v_count >= 5 then
      raise exception 'too many submissions from this phone number; try again later';
    end if;
  end if;

  -- Global per-table cap protects the public insert endpoint even if a caller omits a phone number.
  perform pg_advisory_xact_lock(hashtext('bustime-submit-global:' || tg_table_name));
  execute format(
    'select count(*) from public.%I where created_at > now() - interval ''1 hour''',
    tg_table_name
  ) into v_count;
  if v_count >= 200 then
    raise exception 'too many submissions; try again later';
  end if;

  return new;
end;
$$;

revoke all on function public.enforce_public_submission_limits() from public, anon, authenticated;

drop trigger if exists trg_limit_route_submissions on public.route_submissions;
drop trigger if exists trg_submission_rate_limit on public.route_submissions;
create trigger trg_submission_rate_limit
before insert on public.route_submissions
for each row execute function public.enforce_public_submission_limits();

drop trigger if exists trg_submission_rate_limit on public.correction_submissions;
create trigger trg_submission_rate_limit
before insert on public.correction_submissions
for each row execute function public.enforce_public_submission_limits();

drop trigger if exists trg_submission_rate_limit on public.feedback_submissions;
create trigger trg_submission_rate_limit
before insert on public.feedback_submissions
for each row execute function public.enforce_public_submission_limits();

drop trigger if exists trg_submission_rate_limit on public.bus_operator_submissions;
create trigger trg_submission_rate_limit
before insert on public.bus_operator_submissions
for each row execute function public.enforce_public_submission_limits();

-- Preserve all existing ad settings while ensuring the Android Start.io reader has safe defaults.
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
on conflict (id) do update
set config = jsonb_set(
  coalesce(public.ad_settings.config, '{}'::jsonb),
  '{startio}',
  coalesce(public.ad_settings.config->'startio', excluded.config->'startio'),
  true
),
updated_at = now();
