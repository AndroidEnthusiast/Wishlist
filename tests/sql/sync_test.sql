-- Checks supabase-sync.sql (ROADMAP Stage 3, 8 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where every other supabase-*.sql script was run (pretend net.http_post, without the pg_cron
-- lines; supabase-photos.sql can be left out), a pretend Realtime BEFORE supabase-sync.sql:
--   create schema realtime; create table realtime.sent(topic text, event text, private boolean);
--   create function realtime.send(payload jsonb, event text, topic text, private boolean) returns void
--     language sql as 'insert into realtime.sent(topic, event, private) values (topic, event, private)';
-- then supabase-sync.sql, and a role "anon". Run as the superuser. The last line prints ALL SYNC CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.sent(p_topic text) returns int language sql as
  $$ select count(*)::int from realtime.sent where topic = p_topic $$;
grant select on realtime.sent to anon;
delete from realtime.sent;

-- ---------- 1. wl_mine_put ----------
set role anon;
select wl_create_list('MINE0001', 'owner_owner_owner_ow', 'Mine');
select wl_create_list('OTHER001', 'other_other_other_ot', 'Other');
select wl_sync_list('OTHER001', 'other_other_other_ot', 'Other', '[]',
  '[{"id":5,"title":"Not yours"}]');
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null,
  '[{"id":1,"title":"Lamp","price":20},{"id":2,"title":"Book"},{"id":0,"kind":"about","about":"Hi"}]', null);
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 3, 'three items put');
select pg_temp.ok(wl_get_list('MINE0001')->>'name' = 'Mine', 'name kept when null');
select pg_temp.ok(wl_reserve_item('MINE0001', 2, 'friend-device-1') is not null, 'a friend reserves the book');
-- Change only the lamp: the book and its reservation stay as they are.
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', 'My wishes', '["Home"]', '[{"id":1,"title":"Lamp","price":15}]', '[]');
select pg_temp.ok((select (e->>'price')::numeric from jsonb_array_elements(wl_get_list('MINE0001')->'items') e where e->>'id' = '1') = 15, 'lamp changed');
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 3, 'nothing else removed');
select pg_temp.ok(wl_get_list('MINE0001')->>'name' = 'My wishes' and wl_get_list('MINE0001')->'cats' = '["Home"]', 'name and categories');
select pg_temp.ok(wl_get_reservations('MINE0001', 'friend-device-1') @> '[{"id":2,"mine":true}]', 'reservation of an untouched wish stays');
-- Remove the lamp; a wish of another list can't be removed with your token.
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, null, '[1, 5]');
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 2, 'lamp removed');
select pg_temp.ok(jsonb_array_length(wl_get_list('OTHER001')->'items') = 1, 'another list''s wish 5 untouched');
-- Refused: wrong or empty token, broken items.
do $$ begin perform wl_mine_put('MINE0001', 'wrong_wrong_wrong_wr', null, null, '[{"id":9}]', null); raise exception 'FAIL wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', '', null, null, '[{"id":9}]', null); raise exception 'FAIL empty token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '[{"title":"no id"}]', null); raise exception 'FAIL item without id';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '[{"id":"1; drop"}]', null); raise exception 'FAIL text id';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '["just text"]', null); raise exception 'FAIL item not an object';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '{"id":1}', null); raise exception 'FAIL items not a list';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, null, '["2"]'); raise exception 'FAIL del as text';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, '"Home"', null, null); raise exception 'FAIL cats not a list';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 2, 'refused calls changed nothing');
-- The old full save still works next to it (older apps).
select wl_sync_list('MINE0001', 'owner_owner_owner_ow', 'My wishes', '[]', '[{"id":2,"title":"Book"},{"id":3,"title":"Pen"}]');
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 2, 'wl_sync_list still works');

-- ---------- 2. Live signals ----------
reset role;
delete from realtime.sent;
set role anon;
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '[{"id":2,"title":"Book 2"},{"id":3,"title":"Pen 2"}]', null);
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) = 1, 'one signal for a save of two wishes');
select pg_temp.ok((select bool_and(event = 'changed' and private = false) from realtime.sent), 'event changed, public channel');
select pg_temp.ok((select bool_and(topic not like '%MINE0001%') from realtime.sent), 'the topic doesn''t give away the code');
select pg_temp.ok(wl_live_topic('l', 'MINE0001') <> wl_live_topic('l', 'OTHER001') and wl_live_topic('l', 'X') <> wl_live_topic('c', 'X'), 'different topics');
select pg_temp.ok(wl_live_topic('l', 'MINE0001') ~ '^wll-[0-9a-f]{64}$', 'topic shape');
-- The same fingerprint as the app computes (Live.kt): sha256 of "wishlist-l:MINE0001".
select pg_temp.ok(wl_live_topic('l', 'MINE0001') = 'wll-' || encode(sha256('wishlist-l:MINE0001'::bytea), 'hex'), 'same as the app');
delete from realtime.sent;
set role anon;
select wl_sync_list('MINE0001', 'owner_owner_owner_ow', 'My wishes', '[]', '[{"id":2,"title":"Book"},{"id":3,"title":"Pen"},{"id":4,"title":"Cup"}]');
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) between 1 and 3, 'a whole-list save: one signal per step, not per wish');
delete from realtime.sent;
set role anon;
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', 'Renamed', null, null, null);
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) = 1, 'a new name is a signal');
delete from realtime.sent;
set role anon;
select wl_reserve_item('MINE0001', 3, 'friend-device-2');
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) = 1, 'a reservation is a signal');
delete from realtime.sent;
set role anon;
select wl_unreserve_item('MINE0001', 3, 'friend-device-2');
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) = 1, 'a reservation undone is a signal');

-- A collab list: a member's change of one wish.
set role anon;
select wl_sh_create('COLLAB09', 'editkey_editkey_editkey9', 'maker_maker_maker_m9', 'For Mum');
reset role;
delete from realtime.sent;
set role anon;
select wl_sh_put_item('editkey_editkey_editkey9', '{"id":7,"title":"Scarf"}');
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'COLLAB09')) = 1, 'a collab change is a signal');
select pg_temp.ok(pg_temp.sent(wl_live_topic('l', 'MINE0001')) = 0, 'other lists hear nothing');

-- Messages: both people's channels, made from their secret tokens.
set role anon;
select wl_pf_save('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','Xena') as cx \gset
select wl_pf_save('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','Yuri') as cy \gset
select wl_fr_add('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :'cy');
select wl_fr_answer('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX',true);
reset role;
delete from realtime.sent;
set role anon;
select wl_chat_send('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY','bG9ja2Vk','','');
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('c', 'tokY_tokY_tokY_tokY_')) = 1, 'the receiver hears a new message');
select pg_temp.ok(pg_temp.sent(wl_live_topic('c', 'tokX_tokX_tokX_tokX_')) = 1, 'the sender''s other screens hear it too');
select pg_temp.ok(pg_temp.sent(wl_live_topic('c', 'YYYYYYYYYYYYYYYYYYYY')) = 0, 'a profile id is not enough to listen');
delete from realtime.sent;
set role anon;
select wl_chat_list('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_') is not null;
select wl_chat_seen('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX', 999999) is null;
reset role;
select pg_temp.ok(pg_temp.sent(wl_live_topic('c', 'tokX_tokX_tokX_tokX_')) = 1, 'read ticks tell the sender');

-- Locked: the helpers can't be called from outside.
set role anon;
do $$ begin perform wl_live_send('anything'); raise exception 'FAIL wl_live_send callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_live_topic('c', 'x'); raise exception 'FAIL wl_live_topic callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;

-- A signal that can't be sent never stops a save.
create or replace function realtime.send(payload jsonb, event text, topic text, private boolean) returns void
  language plpgsql as $$ begin raise exception 'realtime is down'; end $$;
set role anon;
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '[{"id":8,"title":"Saved anyway"}]', null);
select pg_temp.ok(wl_get_list('MINE0001')->'items' @> '[{"id":8}]', 'saved while Realtime is down');
select wl_sync_list('MINE0001', 'owner_owner_owner_ow', 'My wishes', '[]', '[{"id":9,"title":"Also saved"}]');
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 1, 'whole save while Realtime is down');
reset role;
-- Without Realtime at all (a plain PostgreSQL): still saves.
drop function realtime.send(jsonb, text, text, boolean);
set role anon;
select wl_mine_put('MINE0001', 'owner_owner_owner_ow', null, null, '[{"id":10,"title":"No Realtime"}]', null);
select pg_temp.ok(jsonb_array_length(wl_get_list('MINE0001')->'items') = 2, 'saved without Realtime');
reset role;
create function realtime.send(payload jsonb, event text, topic text, private boolean) returns void language sql as
  'insert into realtime.sent(topic, event, private) values (topic, event, private)';

select 'ALL SYNC CHECKS OK';
