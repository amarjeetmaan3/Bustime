-- Bustime: यूज़र से आने वाले रूट रखने की टेबल
-- Supabase Dashboard -> SQL Editor -> New query में पूरा चिपकाकर RUN दबाओ (एक ही बार)।

create table if not exists public.route_submissions (
  id            uuid primary key default gen_random_uuid(),
  created_at    timestamptz not null default now(),
  from_id       text not null check (char_length(from_id) between 1 and 100),
  to_id         text not null check (char_length(to_id)   between 1 and 100),
  start_time    text not null check (start_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  service_name  text check (service_name is null or char_length(service_name) <= 60),
  long_route    boolean not null default false,
  start_date    date,
  stops         jsonb not null default '[]'::jsonb
                check (jsonb_typeof(stops) = 'array' and jsonb_array_length(stops) <= 80),
  phone         text not null check (phone ~ '^[0-9]{10,13}$'),
  status        text not null default 'pending' check (status in ('pending','approved','rejected'))
);

create index if not exists route_submissions_status_idx on public.route_submissions (status, created_at desc);

alter table public.route_submissions add column if not exists long_route boolean not null default false;
alter table public.route_submissions add column if not exists start_date date;

-- सुरक्षा: Row Level Security चालू
alter table public.route_submissions enable row level security;

-- ऐप (anon) को सिर्फ़ INSERT की इजाज़त; पढ़ना, बदलना, मिटाना नहीं
revoke all on public.route_submissions from anon, authenticated;
grant insert on public.route_submissions to anon;
grant select, update, delete on public.route_submissions to authenticated;

drop policy if exists "app can submit"    on public.route_submissions;
drop policy if exists "admin can read"    on public.route_submissions;
drop policy if exists "admin can update"  on public.route_submissions;
drop policy if exists "admin can delete rejected" on public.route_submissions;

create policy "app can submit" on public.route_submissions
  for insert to anon with check (status = 'pending');

-- लॉगिन किया हुआ एडमिन (authenticated) देख और बदल सकता है
create policy "admin can read" on public.route_submissions
  for select to authenticated using (true);

create policy "admin can update" on public.route_submissions
  for update to authenticated using (true) with check (true);

create policy "admin can delete rejected" on public.route_submissions
  for delete to authenticated using (status = 'rejected');

-- स्पैम रोक: एक नंबर से 24 घंटे में 5 से ज़्यादा नहीं, और कुल मिलाकर घंटे में 200 से ज़्यादा नहीं
create or replace function public.limit_route_submissions() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if (select count(*) from public.route_submissions
        where phone = new.phone and created_at > now() - interval '24 hours') >= 5 then
    raise exception 'too many submissions from this number';
  end if;
  if (select count(*) from public.route_submissions
        where created_at > now() - interval '1 hour') >= 200 then
    raise exception 'too many submissions, try later';
  end if;
  return new;
end $$;

drop trigger if exists trg_limit_route_submissions on public.route_submissions;
create trigger trg_limit_route_submissions before insert on public.route_submissions
  for each row execute function public.limit_route_submissions();
