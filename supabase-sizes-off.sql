-- Sizes removed (owner, 17 November 2026, version 2.219). Run this ONCE in Supabase: SQL Editor -> New query -> paste
-- -> Run. Run it AFTER all the other scripts; it is safe to run again.
-- It deletes every size people filled in (clothing and shoes) and makes sure older app versions can't save new ones:
--   1. Saving sizes keeps nothing; friends' sizes come back empty.
--   2. The sizes saved in each profile are cleared.
--   3. The copy of the sizes sent along with each wishlist (for the friends' web page) is removed, now and on every
--      later save from an older app.

-- ---------- 1. Nothing is saved or given out any more ----------
create or replace function wl_pf_sizes(p_id text, p_token text, p_sizes text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  update wl_profiles set sizes = '' where id = p_id;
end $$;

create or replace function wl_fr_sizes(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return '{}'::jsonb;
end $$;

-- ---------- 2. Clear the saved sizes ----------
update wl_profiles set sizes = '' where sizes <> '';

-- ---------- 3. The sizes inside a list's "about" item ----------
create or replace function wl_items_no_sizes()
returns trigger language plpgsql set search_path = public as $$
begin
  if new.data ? 'sz' then new.data := new.data - 'sz'; end if;
  return new;
end $$;
revoke all on function wl_items_no_sizes() from public;

drop trigger if exists wl_items_no_sizes on wl_items;
create trigger wl_items_no_sizes before insert or update on wl_items
  for each row execute function wl_items_no_sizes();

update wl_items set data = data - 'sz' where data ? 'sz';

-- Version stamp (owner, 18 November 2026; docs/MAP.md "Server script versions"): records on the server which version
-- of this script was run. Raise the number below with every change to this script: the daily check on GitHub
-- (.github/workflows/import-check.yml) compares it with the server and opens an issue "Run supabase-sizes-off.sql" when it is behind.
-- Only script names and numbers: nothing about people. Safe to run again.
create table if not exists public.wl_versions (script text primary key, version int not null, run_at timestamptz not null default now());
alter table public.wl_versions enable row level security;
revoke all on public.wl_versions from anon;
create or replace function public.wl_script_versions() returns jsonb
  language sql stable security definer set search_path = public
  as $$ select coalesce(jsonb_object_agg(script, version), '{}'::jsonb) from public.wl_versions $$;
grant execute on function public.wl_script_versions() to anon;
insert into public.wl_versions (script, version, run_at) values ('supabase-sizes-off.sql', 1, now())
  on conflict (script) do update set version = excluded.version, run_at = now();
