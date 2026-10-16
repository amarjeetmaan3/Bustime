-- MDM password verifier. The raw password is never stored.
create table if not exists public.mdm_secret (
  id integer primary key check (id = 1),
  password_salt text not null,
  password_hash text not null,
  iterations integer not null default 310000 check (iterations >= 100000 and iterations <= 1000000),
  updated_at timestamptz not null default now()
);

alter table public.mdm_secret enable row level security;
revoke all on public.mdm_secret from anon, authenticated;
grant all on public.mdm_secret to service_role;
