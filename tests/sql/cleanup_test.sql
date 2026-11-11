-- Checks supabase-cleanup.sql (ROADMAP S4.3, 11 November 2026) on a local PostgreSQL. Needs every other script (as
-- tests/sql/run.sh does), then supabase-cleanup.sql without its pg_cron line. Run as the superuser.
-- The last line prints ALL CLEANUP CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;

set role anon;
select wl_pf_save('old_old_old_old_old1', 'oldtoken_oldtoken_old', 'Old');
select wl_pf_save('new_new_new_new_new1', 'newtoken_newtoken_new', 'New');
select wl_create_list('OLDLIST1', 'oldlisttoken_oldlistt', 'Old list');
select wl_create_list('NEWLIST1', 'newlisttoken_newlistt', 'New list');
select wl_create_list('SAVED001', 'savedtoken_savedtoken', 'Saved by an old app');
select wl_sync_list('OLDLIST1', 'oldlisttoken_oldlistt', 'Old list', '[]', '[{"id":1,"title":"Lamp"}]');
select wl_reserve_item('OLDLIST1', 1, 'friend-device-1');
reset role;
insert into wl_friends (a, b, accepted) values ('old_old_old_old_old1', 'new_new_new_new_new1', true);
-- 11 and a half months without use.
update wl_profiles set opened_at = now() - interval '345 days', seen_at = now() - interval '345 days' where id = 'old_old_old_old_old1';
update wl_lists set opened_at = now() - interval '13 months', updated_at = now() - interval '13 months' where code = 'OLDLIST1';
update wl_lists set opened_at = now() - interval '13 months' where code = 'SAVED001';

-- ---------- the first night: a warning, and the list unused for 13 months goes ----------
select wl_cleanup() as r \gset
select pg_temp.ok((:'r'::jsonb ->> 'warned')::int = 1, 'one profile warned');
select pg_temp.ok(exists (select 1 from wl_news where profile = 'old_old_old_old_old1' and kind = 'cleanup'), 'the warning is news for that profile (pushed like other news)');
select pg_temp.ok(not exists (select 1 from wl_lists where code = 'OLDLIST1'), 'a list unused for 12 months is removed');
select pg_temp.ok(not exists (select 1 from wl_items where list_code = 'OLDLIST1') and not exists (select 1 from wl_reservations where list_code = 'OLDLIST1'), 'its wishes and reservations go with it');
select pg_temp.ok(exists (select 1 from wl_lists where code = 'SAVED001'), 'a list an older app still saves stays');
select pg_temp.ok(exists (select 1 from wl_lists where code = 'NEWLIST1'), 'a list in use stays');
select pg_temp.ok(exists (select 1 from wl_profiles where id = 'old_old_old_old_old1'), 'the warned profile stays for now');

-- ---------- warned only once ----------
select wl_cleanup() as r \gset
select pg_temp.ok((:'r'::jsonb ->> 'warned')::int = 0, 'no second warning');

-- ---------- opening the app: in use again, and the removed list is named so the app puts it back ----------
set role anon;
select wl_opened('old_old_old_old_old1', 'oldtoken_oldtoken_old',
  '[{"c":"OLDLIST1","t":"oldlisttoken_oldlistt"},{"c":"NEWLIST1","t":"newlisttoken_newlistt"},{"c":"NEWLIST1","t":"wrong_wrong_wrong_wr"}]') as gone \gset
select pg_temp.ok(:'gone'::jsonb = '["OLDLIST1"]'::jsonb, 'only the removed list is named (not one with a wrong token)');
reset role;
select pg_temp.ok((select warned_at is null and opened_at > now() - interval '1 minute' from wl_profiles where id = 'old_old_old_old_old1'), 'opening clears the warning');
set role anon;
select wl_create_list('OLDLIST1', 'oldlisttoken_oldlistt', 'Old list');
select pg_temp.ok(wl_opened('', '', '[{"c":"OLDLIST1","t":"oldlisttoken_oldlistt"}]') = '[]'::jsonb, 'back online under the same link, without a profile too');
do $$ begin perform wl_opened('old_old_old_old_old1', 'wrong_wrong_wrong_wr', '[]'); raise exception 'FAIL a wrong profile token'; exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_opened('', '', (select jsonb_agg(jsonb_build_object('c', 'X' || i, 't', 'y')) from generate_series(1, 101) i)); raise exception 'FAIL more than 100 lists'; exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;

-- ---------- a profile unused for 12 months that was warned a month ago goes, with its friendships ----------
update wl_profiles set opened_at = now() - interval '13 months', seen_at = now() - interval '13 months',
  warned_at = now() - interval '30 days' where id = 'old_old_old_old_old1';
update wl_profiles set opened_at = now() - interval '13 months', seen_at = now() - interval '13 months',
  warned_at = now() - interval '3 days' where id = 'new_new_new_new_new1';
select wl_cleanup() as r \gset
select pg_temp.ok(not exists (select 1 from wl_profiles where id = 'old_old_old_old_old1'), 'the profile is removed after its warning month');
select pg_temp.ok(not exists (select 1 from wl_friends where a = 'old_old_old_old_old1'), 'its friendships go with it');
select pg_temp.ok(exists (select 1 from wl_profiles where id = 'new_new_new_new_new1'), 'a profile warned only 3 days ago stays');

-- ---------- old news and view notes ----------
insert into wl_news (profile, kind, created_at) values ('new_new_new_new_new1', 'sh_invite', now() - interval '61 days');
insert into wl_list_view_last (list_code, device, at) values ('NEWLIST1', 'dev1', now() - interval '2 days');
select wl_cleanup() as r \gset
select pg_temp.ok((:'r'::jsonb ->> 'news')::int >= 1 and (:'r'::jsonb ->> 'views')::int = 1, 'old news and view notes are removed');

-- ---------- only the night job runs the clean-up ----------
set role anon;
select pg_temp.ok(not has_function_privilege('wl_cleanup()', 'execute'), 'wl_cleanup is locked');
reset role;

select 'ALL CLEANUP CHECKS OK';
