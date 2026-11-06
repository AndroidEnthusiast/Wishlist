-- Checks "Viewed 23 times" in supabase-features.sql (run after setup, shared, friends, chat and features). Ends with ALL VIEW CHECKS OK.
\set ON_ERROR_STOP 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_create_list('VLIST001','ownerV_ownerV_ownerV','V');
select pg_temp.ok(wl_view_count('VLIST001','ownerV_ownerV_ownerV') = 0, 'starts at 0');
select wl_view('VLIST001','dev1'); select wl_view('VLIST001','dev1'); select wl_view('VLIST001','dev2');
select pg_temp.ok(wl_view_count('VLIST001','ownerV_ownerV_ownerV') = 2, 'reopen within 10 minutes counts once');
select pg_temp.ok(wl_view_count('VLIST001','wrong') = 0, 'only the owner reads it');
select wl_view('NOPE0000','dev1');
select wl_view('VLIST001','');
reset role;
update wl_list_view_last set at = now() - interval '11 minutes';
set role anon;
select wl_view('VLIST001','dev1');
select pg_temp.ok(wl_view_count('VLIST001','ownerV_ownerV_ownerV') = 3, 'after 10 minutes it counts again');
select pg_temp.ok(not has_table_privilege('anon', 'wl_list_views', 'select'), 'anon can''t read the counts table');
\echo ALL VIEW CHECKS OK
