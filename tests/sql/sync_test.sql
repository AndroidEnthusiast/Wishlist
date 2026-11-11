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
select pg_temp.ok(wl_live_topic('l', 'MINE0001') = 'wll-66c508165d9e2f6674dbfa85793a0cfe400eaecd18791709668802c3b79b6ac6', 'same as the app (LiveTest)');
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

-- ---------- 3. wl_pulse ----------
set role anon;
select wl_create_list('FRIEND01', 'fowner_fowner_fowner', 'Friend list');
select wl_mine_put('FRIEND01', 'fowner_fowner_fowner', null, null, '[{"id":1,"title":"Kite"},{"id":2,"title":"Ball"}]', null);
select wl_pulse(null, null, 'my-device-1234', null, '["FRIEND01","NOSUCHLS"]') as p1 \gset
select pg_temp.ok((:'p1'::jsonb)->'lists'->>'NOSUCHLS' = 'gone', 'unknown list: gone');
select pg_temp.ok((:'p1'::jsonb)->>'fr' is null and (:'p1'::jsonb)->>'chat' is null, 'no profile: only lists');
-- Someone else reserving doesn't change it (the owner could otherwise notice a reservation).
select wl_reserve_item('FRIEND01', 1, 'someone-else-5678');
select pg_temp.ok(wl_pulse(null, null, 'my-device-1234', null, '["FRIEND01"]')->'lists'->>'FRIEND01' = (:'p1'::jsonb)->'lists'->>'FRIEND01', 'another device''s reservation: same fingerprint');
select pg_temp.ok(wl_pulse(null, null, 'fowner-device-99', null, '["FRIEND01"]')->'lists'->>'FRIEND01' = (:'p1'::jsonb)->'lists'->>'FRIEND01', 'the owner''s device sees no change either');
select wl_reserve_item('FRIEND01', 2, 'my-device-1234');
select wl_pulse(null, null, 'my-device-1234', null, '["FRIEND01"]') as p2 \gset
select pg_temp.ok((:'p2'::jsonb)->'lists'->>'FRIEND01' <> (:'p1'::jsonb)->'lists'->>'FRIEND01', 'my own reservation changes it');
select wl_mine_put('FRIEND01', 'fowner_fowner_fowner', null, null, null, '[2]');
select pg_temp.ok(wl_pulse(null, null, 'my-device-1234', null, '["FRIEND01"]')->'lists'->>'FRIEND01' <> (:'p2'::jsonb)->'lists'->>'FRIEND01', 'a wish taken off changes it');
reset role;
update wl_lists set link_off = true where code = 'FRIEND01';
set role anon;
select pg_temp.ok(wl_pulse(null, null, 'my-device-1234', null, '["FRIEND01"]')->'lists'->>'FRIEND01' = 'off', 'link switched off: off');
reset role;
update wl_lists set link_off = false where code = 'FRIEND01';
set role anon;
-- Collab lists by edit key: every reservation counts (everyone on it sees them anyway), and a handed-over ownership.
select wl_pulse(null, null, 'my-device-1234', '["editkey_editkey_editkey9","wrong_key_wrong_key_wrong"]', null) as p3 \gset
select pg_temp.ok((:'p3'::jsonb)->'shared'->'wrong_key_wrong_key_wrong'->>'fp' = 'gone', 'wrong key: gone');
select pg_temp.ok(((:'p3'::jsonb)->'shared'->'editkey_editkey_editkey9'->>'claim')::boolean = false, 'nothing to claim');
select wl_reserve_item('COLLAB09', 7, 'another-giver-99');
select pg_temp.ok(wl_pulse(null, null, 'my-device-1234', '["editkey_editkey_editkey9"]', null)->'shared'->'editkey_editkey_editkey9'->>'fp'
  <> (:'p3'::jsonb)->'shared'->'editkey_editkey_editkey9'->>'fp', 'a collab reservation changes it');
reset role;
update wl_lists set owner_next = 'XXXXXXXXXXXXXXXXXXXX', owner_next_token = 'newtoken_newtoken_ne' where code = 'COLLAB09';
set role anon;
select pg_temp.ok((wl_pulse('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', 'my-device-1234', '["editkey_editkey_editkey9"]', null)
  ->'shared'->'editkey_editkey_editkey9'->>'claim')::boolean, 'a list handed to me: claim');
select pg_temp.ok(not (wl_pulse('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_', 'my-device-1234', '["editkey_editkey_editkey9"]', null)
  ->'shared'->'editkey_editkey_editkey9'->>'claim')::boolean, 'handed to someone else: no claim');
-- Your profile's parts.
select wl_pulse('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', 'my-device-1234', null, null) as p4 \gset
select pg_temp.ok((:'p4'::jsonb) ?& array['fr','chat','gr','gx','news'], 'all five parts');
select wl_chat_send('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX','bmV3','','');
select wl_pulse('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', 'my-device-1234', null, null) as p5 \gset
select pg_temp.ok((:'p5'::jsonb)->>'chat' <> (:'p4'::jsonb)->>'chat', 'a new message changes chat');
select pg_temp.ok((:'p5'::jsonb)->>'fr' = (:'p4'::jsonb)->>'fr' and (:'p5'::jsonb)->>'gr' = (:'p4'::jsonb)->>'gr', 'friends and groups unchanged');
select pg_temp.ok(wl_pulse('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', 'my-device-1234', null, null)->>'chat' = (:'p5'::jsonb)->>'chat', 'asking again: the same');
select wl_pf_save('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','Zoe') as cz \gset
select wl_fr_add('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_', :'cx');
select pg_temp.ok(wl_pulse('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', 'my-device-1234', null, null)->>'fr' <> (:'p5'::jsonb)->>'fr', 'a friend request changes friends');
-- Refused.
do $$ begin perform wl_pulse('XXXXXXXXXXXXXXXXXXXX','wrong', 'my-device-1234', null, null); raise exception 'FAIL pulse with a wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_pulse(null, null, 'short', null, null); raise exception 'FAIL pulse with a short device';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_pulse(null, null, 'my-device-1234', null, (select jsonb_agg(i::text) from generate_series(1, 201) i)); raise exception 'FAIL 201 codes';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_pulse_list('FRIEND01', 'x', true); raise exception 'FAIL wl_pulse_list callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;

-- ---------- 4. wl_mine_get (2.180): your own list back with its owner token, also with the link off ----------
set role anon;
select wl_create_list('RESCUE01', 'rescue_rescue_rescue', 'Back');
select wl_mine_put('RESCUE01', 'rescue_rescue_rescue', null, null, '[{"id":1,"title":"Lamp"},{"id":2,"title":"Book"}]', null);
select pg_temp.ok(wl_reserve_item('RESCUE01', 2, 'friend-device-9') is not null, 'a friend reserves on the list to rescue');
select pg_temp.ok(jsonb_array_length(wl_mine_get('RESCUE01', 'rescue_rescue_rescue')->'items') = 2, 'the owner reads the list back');
select pg_temp.ok(wl_mine_get('RESCUE01', 'rescue_rescue_rescue')->>'name' = 'Back', 'with its name');
select pg_temp.ok(wl_mine_get('RESCUE01', 'rescue_rescue_rescue')::text not like '%friend-device-9%', 'no reservations in it');
select pg_temp.ok(wl_mine_get('RESCUE01', 'wrong_wrong_wrong_wr') is null, 'a wrong token reads nothing');
select pg_temp.ok(wl_mine_get('RESCUE01', '') is null, 'an empty token reads nothing');
select pg_temp.ok(wl_mine_get('RESCUE01', null) is null, 'no token reads nothing');
select pg_temp.ok(wl_mine_get('NOPE0000', 'rescue_rescue_rescue') is null, 'an unknown list reads nothing');
reset role;
update wl_lists set link_off = true where code = 'RESCUE01';
set role anon;
select pg_temp.ok(wl_get_list('RESCUE01') is null, 'the link is off for everyone else');
select pg_temp.ok(jsonb_array_length(wl_mine_get('RESCUE01', 'rescue_rescue_rescue')->'items') = 2, 'the owner still reads it with the link off');
reset role;

select 'ALL SYNC CHECKS OK';
