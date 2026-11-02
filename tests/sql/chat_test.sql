-- Checks supabase-chat.sql on a local PostgreSQL (see tests/README.md). Run after friends_test.sql's setup files and supabase-chat.sql.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_pf_save('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','Xena') as cx \gset
select wl_pf_save('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','Yuri') as cy \gset
select wl_pf_save('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','Zoe') as cz \gset
select wl_pf_key('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','PUBX');
select wl_pf_key('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','PUBY');
do $$ begin perform wl_chat_send('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY','hi','PUBX','PUBY'); raise exception 'FAIL strangers can chat';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_fr_add('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :'cy');
select wl_fr_answer('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX',true);
select pg_temp.ok(wl_chat_key('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY') = 'PUBY', 'friend key');
select pg_temp.ok(wl_chat_key('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','ZZZZZZZZZZZZZZZZZZZZ') = '', 'no key for a stranger');
select (wl_chat_send('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY','LOCKED1','PUBX','PUBY')->>'id')::bigint as m1 \gset
select wl_chat_send('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX','LOCKED2','PUBY','PUBX');
select pg_temp.ok((wl_chat_list('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_')->0->>'unread')::int = 1, 'Yuri has one unread');
select pg_temp.ok((wl_chat_list('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_')->0->'last'->>'body') = 'LOCKED2', 'last message');
select pg_temp.ok(jsonb_array_length(wl_chat_reads('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY',0)) = 0, 'not read yet');
select pg_temp.ok(jsonb_array_length(wl_chat_get('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX',0)) = 2, 'Yuri reads both');
select pg_temp.ok(jsonb_array_length(wl_chat_reads('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY',0)) = 1, 'read tick');
select pg_temp.ok((wl_chat_list('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_')->0->>'unread')::int = 0, 'unread cleared');
select pg_temp.ok(jsonb_array_length(wl_chat_get('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','XXXXXXXXXXXXXXXXXXXX',0)) = 0, 'a stranger reads nothing');
select wl_chat_delete('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_', :m1);
select pg_temp.ok(jsonb_array_length(wl_chat_get('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY',0)) = 2, 'cannot delete their message');
select wl_chat_delete('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_', :m1);
select pg_temp.ok(jsonb_array_length(wl_chat_get('YYYYYYYYYYYYYYYYYYYY','tokY_tokY_tokY_tokY_','XXXXXXXXXXXXXXXXXXXX',0)) = 1, 'own message deleted for both');
select wl_fr_remove('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY');
select pg_temp.ok(jsonb_array_length(wl_chat_get('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY',0)) = 1, 'history after unfollow');
do $$ begin perform wl_chat_send('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','YYYYYYYYYYYYYYYYYYYY','x','',''); raise exception 'FAIL sent after unfollow';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform 1 from wl_messages; raise exception 'FAIL table readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
\echo ALL CHAT CHECKS OK
