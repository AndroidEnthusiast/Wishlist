-- Server script versions (owner, 18 November 2026): each script stamps its name and version; the table is locked and
-- only the read function is open (names and numbers only). Run after any script(s).
do $$
declare v jsonb;
begin
  v := public.wl_script_versions();
  if not (v ? 'supabase-setup.sql') then raise exception 'setup.sql stamped itself'; end if;
  if (v->>'supabase-setup.sql')::int < 1 then raise exception 'a version number'; end if;
end $$;
set role anon;
do $$
begin
  begin
    perform 1 from public.wl_versions;
    raise exception 'anon must not read the table directly';
  exception when insufficient_privilege then null;
  end;
  if public.wl_script_versions() is null then raise exception 'anon may read the versions'; end if;
end $$;
reset role;
select 'ALL VERSION CHECKS OK';
