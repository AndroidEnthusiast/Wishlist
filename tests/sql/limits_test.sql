-- Checks supabase-limits.sql (ROADMAP S4.2, 11 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where every other script was run (as tests/sql/run.sh does), then supabase-limits.sql, and a role
-- "anon". Run as the superuser. The last line prints ALL LIMIT CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
-- True when [sql], run as anon, is refused with 'rate_limited'.
create or replace function pg_temp.limited(sql text) returns boolean language plpgsql as $$
begin
  execute sql;
  return false;
exception when others then
  return sqlerrm = 'rate_limited';
end $$;
create or replace function pg_temp.items(p_from int, p_to int) returns jsonb language sql as
  $$ select jsonb_agg(jsonb_build_object('id', i, 'title', 'W' || i)) from generate_series(p_from, p_to) i $$;
grant execute on function pg_temp.items(int, int) to anon;

-- ---------- new lists: 20 an hour per internet address, scrambled ----------
set role anon;
select set_config('request.headers', '{"x-forwarded-for": "203.0.113.7, 10.0.0.1"}', false);
do $$ begin for i in 1..20 loop perform wl_create_list('LIM' || lpad(i::text, 5, '0'), 'token_token_token_tok', 'L'); end loop; end $$;
select pg_temp.ok(pg_temp.limited($$select wl_create_list('LIMXX021', 'token_token_token_tok', 'L')$$), 'the 21st new list from one address is refused');
select set_config('request.headers', '{"x-forwarded-for": "198.51.100.9"}', false);
select wl_create_list('LIMOTHER', 'token_token_token_tok', 'L');
select set_config('request.headers', '', false);
do $$ begin for i in 1..30 loop perform wl_create_list('NOH' || lpad(i::text, 5, '0'), 'token_token_token_tok', 'L'); end loop; end $$;
reset role;
select pg_temp.ok(exists (select 1 from wl_lists where code = 'LIMOTHER'), 'another address may still make lists');
select pg_temp.ok((select count(*) from wl_lists where code like 'NOH%') = 30, 'without an address (SQL editor, jobs) nothing is limited');
select pg_temp.ok(not exists (select 1 from wl_limits where actor like '%203.0.113%' or actor like '%198.51%'), 'the real address is never stored');
select pg_temp.ok((select count(*) from wl_limits where action = 'wl_lists') = 2, 'one scrambled address per caller');

-- ---------- new wishes: 100 an hour per list, only really new ones; 1000 in a list's first hour ----------
update wl_lists set created_at = now() - interval '2 hours' where code = 'LIM00001';
set role anon;
select wl_mine_put('LIM00001', 'token_token_token_tok', null, null, pg_temp.items(1, 100), null);
select pg_temp.ok(pg_temp.limited($$select wl_mine_put('LIM00001', 'token_token_token_tok', null, null, pg_temp.items(101, 101), null)$$), 'the 101st new wish in an hour is refused');
reset role;
select pg_temp.ok((select count(*) from wl_items where list_code = 'LIM00001') = 100, 'a refused save changes nothing');
set role anon;
select wl_sync_list('LIM00001', 'token_token_token_tok', 'L', '[]', pg_temp.items(1, 100));
select wl_mine_put('LIM00001', 'token_token_token_tok', null, null, (select jsonb_agg(jsonb_build_object('id', i, 'title', 'Changed')) from generate_series(1, 100) i), null);
reset role;
select pg_temp.ok((select count(*) from wl_items where list_code = 'LIM00001' and data->>'title' = 'Changed') = 100, 'saving or changing the same wishes again never counts');
set role anon;
select wl_sync_list('LIM00002', 'token_token_token_tok', 'L', '[]', pg_temp.items(1, 400));
reset role;
select pg_temp.ok((select count(*) from wl_items where list_code = 'LIM00002') = 400, 'a big list going online in its first hour fits');

-- ---------- reservations: 60 an hour per phone ----------
set role anon;
do $$ begin for i in 1..60 loop perform wl_reserve_item('LIM00002', i, 'device_device_device_1'); end loop; end $$;
select pg_temp.ok(pg_temp.limited($$select wl_reserve_item('LIM00002', 61, 'device_device_device_1')$$), 'the 61st reservation from one phone in an hour is refused');
select wl_reserve_item('LIM00002', 62, 'device_device_device_2');
reset role;

-- ---------- profiles: 5 an hour per address; friend codes: 30 tries an hour ----------
set role anon;
select set_config('request.headers', '{"x-forwarded-for": "192.0.2.44"}', false);
do $$ begin for i in 1..5 loop perform wl_pf_save('profile_profile_' || lpad(i::text, 4, '0'), 'ptoken_ptoken_ptoken', 'P' || i); end loop; end $$;
select pg_temp.ok(pg_temp.limited($$select wl_pf_save('profile_profile_0006', 'ptoken_ptoken_ptoken', 'P6')$$), 'the 6th new profile from one address is refused');
select wl_pf_save('profile_profile_0001', 'ptoken_ptoken_ptoken', 'P1 renamed');
do $$ begin for i in 1..30 loop perform wl_fr_add('profile_profile_0001', 'ptoken_ptoken_ptoken', 'ZZZZZ' || i); end loop; end $$;
select pg_temp.ok(pg_temp.limited($$select wl_fr_add('profile_profile_0001', 'ptoken_ptoken_ptoken', 'ZZZZ99')$$), 'the 31st friend code tried in an hour is refused, also when they were all wrong');
select set_config('request.headers', '', false);
reset role;
select pg_temp.ok((select name from wl_profiles where id = 'profile_profile_0001') = 'P1 renamed', 'an existing profile can still save');

-- ---------- messages: 30 a minute per sender ----------
insert into wl_messages (a, b, body) select 'profile_profile_0001', 'profile_profile_0002', 'x' from generate_series(1, 30);
select pg_temp.ok(pg_temp.limited($$insert into wl_messages (a, b, body) values ('profile_profile_0001', 'profile_profile_0002', 'x')$$), 'the 31st message in a minute is refused');
insert into wl_messages (a, b, body) values ('profile_profile_0002', 'profile_profile_0001', 'the other person can still answer');

-- ---------- the helpers are not for callers ----------
set role anon;
select pg_temp.ok(not pg_temp.limited($$select wl_limit_hit('x', 'y', 1, 'hour')$$) and not has_function_privilege('wl_limit_hit(text, text, int, text)', 'execute'), 'wl_limit_hit is locked');
select pg_temp.ok(not has_function_privilege('wl_limit_ip()', 'execute'), 'wl_limit_ip is locked');
do $$ begin perform 1 from wl_limits; raise exception 'FAIL anon can read wl_limits'; exception when insufficient_privilege then null; end $$;
reset role;

-- Old counts go: a count from two days ago is removed sooner or later (forced here).
insert into wl_limits (actor, action, win, n) values ('old', 'wish', now() - interval '2 days', 5);
delete from wl_limits where win < now() - interval '1 day';
select pg_temp.ok(not exists (select 1 from wl_limits where actor = 'old'), 'old counts can be removed');

-- ---------- the shop-page helper: 60 pages an hour per key ----------
insert into wl_lists (code, owner_token, name, edit_key) values ('FETCHLST', 'fetchowner-token-0001', 'Fetch', 'FETCHKEYFETCHKEY0001');
set role anon;
select pg_temp.ok(not wl_fetch_ok('NOSUCHKEYNOSUCHKEY01'), 'an unknown key may not use the helper');
select pg_temp.ok(not wl_fetch_ok('bad key!'), 'a malformed key is refused');
select pg_temp.ok(wl_fetch_ok('FETCHKEYFETCHKEY0001'), 'a collab wishlist key may') from generate_series(1, 60);
select pg_temp.ok(pg_temp.limited($$select wl_fetch_ok('FETCHKEYFETCHKEY0001')$$), 'the 61st page in an hour waits');
reset role;
select pg_temp.ok(not exists (select 1 from wl_limits where actor like '%FETCHKEY%'), 'only a fingerprint of the key is counted');

select 'ALL LIMIT CHECKS OK';
