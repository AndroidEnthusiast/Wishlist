-- Checks the photos part of supabase-chat.sql (2.109). Run after chat_test.sql in the same database.
-- It ends with ALL CHAT PHOTO CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
-- true when the statement is refused
create or replace function pg_temp.refused(q text) returns boolean language plpgsql as $$
begin execute q; return false; exception when others then return true; end $$;
set role anon;
select wl_pf_save('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','Zoe') as cz \gset
select wl_pf_key('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','PUBZ');
select wl_fr_add('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :'cz');
select wl_fr_answer('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','XXXXXXXXXXXXXXXXXXXX',true);
select repeat('Q', 5000) as photo \gset
select pg_temp.ok(pg_temp.refused($q$select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY',repeat('Q',5000))$q$), 'no photo to someone who is no longer a friend');
select pg_temp.ok(pg_temp.refused($q$select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','wrong','ZZZZZZZZZZZZZZZZZZZZ',repeat('Q',5000))$q$), 'wrong token');
select pg_temp.ok(pg_temp.refused($q$select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ',repeat('Q',1000001))$q$), 'too big');
select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ', :'photo') as f1 \gset
select pg_temp.ok(length(:'f1') = 32, 'random name');
select pg_temp.ok(wl_chat_file_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_', :'f1') is null, 'not readable before its message is sent');
select pg_temp.ok(pg_temp.refused(format($q$select wl_chat_send_photo('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','XXXXXXXXXXXXXXXXXXXX','LOCKEDP','PUBZ','PUBX',%L)$q$, :'f1')), 'only the sender can send their photo');
select pg_temp.ok(pg_temp.refused(format($q$select wl_chat_send_photo('XXXXXXXXXXXXXXXXXXXX','wrong','ZZZZZZZZZZZZZZZZZZZZ','LOCKEDP','PUBX','PUBZ',%L)$q$, :'f1')), 'send photo: wrong token');
select (wl_chat_send_photo('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ','LOCKEDP','PUBX','PUBZ', :'f1')->>'id')::bigint as mp \gset
select pg_temp.ok(pg_temp.refused(format($q$select wl_chat_send_photo('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ','AGAIN','PUBX','PUBZ',%L)$q$, :'f1')), 'a photo is sent only once');
select pg_temp.ok(wl_chat_file_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_', :'f1') = :'photo', 'receiver fetches it');
select pg_temp.ok(wl_chat_file_get('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :'f1') = :'photo', 'sender fetches it');
select pg_temp.ok(wl_chat_file_get('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_', :'f1') is null, 'someone else gets nothing');
select pg_temp.ok(pg_temp.refused(format($q$select wl_chat_file_get('ZZZZZZZZZZZZZZZZZZZZ','wrong',%L)$q$, :'f1')), 'get: wrong token');
select pg_temp.ok(jsonb_array_length(wl_chat_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','XXXXXXXXXXXXXXXXXXXX',0)) = 1, 'the photo message arrived');
select pg_temp.ok(pg_temp.refused('select 1 from wl_chat_files'), 'table not readable');
-- deleting the message removes the photo
select wl_chat_delete('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :mp);
select pg_temp.ok(wl_chat_file_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_', :'f1') is null, 'gone with its message');
reset role;
select pg_temp.ok((select count(*) from wl_chat_files where id = :'f1') = 0, 'removed from the table');
-- older than 30 days: not given out, and cleaned up by the next upload; unsent ones after a day
insert into wl_chat_files (id, a, b, msg, data, created_at)
  select 'old1', 'XXXXXXXXXXXXXXXXXXXX', 'ZZZZZZZZZZZZZZZZZZZZ', (select max(id) from wl_messages), 'x', now() - interval '31 days';
insert into wl_chat_files (id, a, b, data, created_at) values ('unsent1', 'XXXXXXXXXXXXXXXXXXXX', 'ZZZZZZZZZZZZZZZZZZZZ', 'x', now() - interval '2 days');
set role anon;
select pg_temp.ok(wl_chat_file_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','old1') is null, 'older than 30 days: gone');
select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ', :'photo') as f2 \gset
reset role;
select pg_temp.ok((select count(*) from wl_chat_files where id in ('old1', 'unsent1')) = 0, 'old and unsent photos cleaned up');
-- at most 50 a day
insert into wl_chat_files (id, a, b, data) select 'many' || g, 'XXXXXXXXXXXXXXXXXXXX', 'ZZZZZZZZZZZZZZZZZZZZ', 'x' from generate_series(1, 49) g;
set role anon;
select pg_temp.ok(pg_temp.refused($q$select wl_chat_file_put('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ',repeat('Q',5000))$q$), 'at most 50 photos a day');
\echo ALL CHAT PHOTO CHECKS OK
