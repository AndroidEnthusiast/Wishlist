-- Checks supabase-push.sql on a local PostgreSQL (see tests/README.md). Run after chat_test.sql's setup and
-- supabase-push.sql, with a stand-in for pg_net: a schema "net" with a table net.calls(url text, body jsonb) and a
-- function net.http_post(url text, body jsonb, headers jsonb) that adds a row to it (pg_net only exists on Supabase).
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
delete from net.calls;
set role anon;
select wl_pf_save('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','Pia') as cp \gset
select wl_pf_save('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','Quin') as cq \gset
select wl_pf_save('RRRRRRRRRRRRRRRRRRRR','tokR_tokR_tokR_tokR_','Rex') as cr \gset
select wl_fr_add('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_', :'cq');
select wl_fr_answer('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','PPPPPPPPPPPPPPPPPPPP',true);
-- No push address yet: sending works, nothing is called.
select (wl_chat_send('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ','L1','a','b')->>'id')::bigint as m1 \gset
reset role;
select pg_temp.ok((select count(*) from net.calls) = 0, 'no push without an address');
set role anon;
do $$ begin perform wl_push_set('QQQQQQQQQQQQQQQQQQQQ','wrong_token_wrong___','T'); raise exception 'FAIL push set with a wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_push_set('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','TOKEN-Q1');
select wl_push_set('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','TOKEN-Q2');
select (wl_chat_send('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ','L2','a','b')->>'id')::bigint as m2 \gset
select (wl_chat_send('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ','L3','a','b')->>'id')::bigint as m3 \gset
reset role;
select pg_temp.ok((select count(*) from wl_push where id = 'QQQQQQQQQQQQQQQQQQQQ') = 1
  and (select token from wl_push where id = 'QQQQQQQQQQQQQQQQQQQQ') = 'TOKEN-Q2', 'one address per profile, the newest');
select pg_temp.ok((select count(*) from net.calls) = 2, 'one call per message');
select pg_temp.ok((select (body->>'id')::bigint from net.calls order by (body->>'id')::bigint limit 1) = :m2, 'call carries the message id');
select pg_temp.ok((select bool_and(url = 'https://aspnmvmsvwbezdltykgx.supabase.co/functions/v1/push') from net.calls), 'calls our push function');
set role anon;
-- Fetching doesn't mark as read any more.
select pg_temp.ok(jsonb_array_length(wl_chat_fetch('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','PPPPPPPPPPPPPPPPPPPP',0)) = 3, 'Quin fetches three');
select pg_temp.ok(jsonb_array_length(wl_chat_reads('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ',0)) = 0, 'fetching is not reading');
select pg_temp.ok(jsonb_array_length(wl_chat_fetch('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','PPPPPPPPPPPPPPPPPPPP',:m1)) = 2, 'fetch after an id');
select pg_temp.ok(jsonb_array_length(wl_chat_fetch('RRRRRRRRRRRRRRRRRRRR','tokR_tokR_tokR_tokR_','PPPPPPPPPPPPPPPPPPPP',0)) = 0, 'a stranger fetches nothing');
-- Seen up to the second message: those two are read, the third not yet.
select wl_chat_seen('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','PPPPPPPPPPPPPPPPPPPP', :m2);
select pg_temp.ok(jsonb_array_length(wl_chat_reads('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ',0)) = 2, 'seen up to m2');
select pg_temp.ok((wl_chat_list('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_')->0->>'unread')::int = 1, 'one still unread');
-- Someone else can't mark them read; the sender can't mark their own messages read.
select wl_chat_seen('RRRRRRRRRRRRRRRRRRRR','tokR_tokR_tokR_tokR_','PPPPPPPPPPPPPPPPPPPP', :m3);
select wl_chat_seen('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ', :m3);
select pg_temp.ok(jsonb_array_length(wl_chat_reads('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ',0)) = 2, 'others cannot mark read');
do $$ begin perform wl_chat_seen('QQQQQQQQQQQQQQQQQQQQ','wrong_token_wrong___','PPPPPPPPPPPPPPPPPPPP', 999999); raise exception 'FAIL seen with a wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_chat_seen('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','PPPPPPPPPPPPPPPPPPPP', :m3);
select pg_temp.ok(jsonb_array_length(wl_chat_reads('PPPPPPPPPPPPPPPPPPPP','tokP_tokP_tokP_tokP_','QQQQQQQQQQQQQQQQQQQQ',0)) = 3, 'all seen');
-- Push addresses and the trigger function are not for the public.
do $$ begin perform 1 from wl_push; raise exception 'FAIL push table readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_push_notify(); raise exception 'FAIL notify callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- An empty address removes it.
select wl_push_set('QQQQQQQQQQQQQQQQQQQQ','tokQ_tokQ_tokQ_tokQ_','');
reset role;
select pg_temp.ok((select count(*) from wl_push where id = 'QQQQQQQQQQQQQQQQQQQQ') = 0, 'address removed');
\echo ALL PUSH CHECKS OK
