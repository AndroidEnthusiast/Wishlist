-- Checks supabase-friends.sql on a local PostgreSQL (not the real Supabase). See tests/README.md.
-- Needs a database where supabase-setup.sql, supabase-shared.sql and supabase-friends.sql were run, and a role "anon".
-- Every check raises an error when it fails; the last line prints ALL FRIENDS CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select pg_temp.ok(length(:'code_a') = 6, 'a friend code has 6 characters');
select pg_temp.ok(wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna R') = :'code_a', 'saving the name keeps the code');
do $$ begin perform wl_pf_save('AAAAAAAAAAAAAAAAAAAA','wrong_wrong_wrong_wr','X'); raise exception 'FAIL wrong token accepted';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_create_list('LISTA001','ownerA_ownerA_ownerA','Anna birthday');
select wl_sync_list('LISTA001','ownerA_ownerA_ownerA','Anna birthday','[]','[{"id":1,"title":"Book"},{"id":2,"title":"Old","bin":1730000000000},{"id":0,"title":"about"}]');
select wl_create_list('LISTA002','ownerA_ownerA_ownerA','Not visible');
select wl_pf_lists('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','[{"code":"LISTA001","token":"ownerA_ownerA_ownerA"},{"code":"LISTA002","token":"badtoken"}]');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', lower(substr(:'code_a',1,3)||'-'||substr(:'code_a',4)))->>'status' = 'sent', 'code with dash and small letters sends a request');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a')->>'status' = 'already', 'second time: already');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_b')->>'status' = 'self', 'own code');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', 'ZZZZZZ')->>'status' = 'none', 'unknown code');
select pg_temp.ok(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'incoming'->0->>'name' = 'Ben', 'the code owner sees the request');
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'outgoing'->0->>'name' = 'Anna R', 'the sender sees it waiting');
select pg_temp.ok(wl_fr_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','LISTA001') is null, 'no list before approval');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'friends'->0->'lists' = '[{"code":"LISTA001","name":"Anna birthday","count":1}]'::jsonb, 'friend sees the visible list only, bin and note not counted');
select pg_temp.ok(wl_fr_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','LISTA001')->>'name' = 'Anna birthday', 'friend can open the list');
select pg_temp.ok(wl_fr_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','LISTA002') is null, 'a list not visible to friends stays closed');
select wl_fr_hide('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'friends'->0->'lists' = '[]'::jsonb, 'hidden: no lists');
select pg_temp.ok((wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends'->0->>'hidden')::boolean, 'the one hiding sees it');
select pg_temp.ok(wl_fr_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','LISTA001') is null, 'hidden: list closed');
select pg_temp.ok(wl_fr_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB','LISTA001') is null, 'cannot open your own list through a friend');
select wl_fr_hide('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',false);
select wl_pf_photo('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','abc');
select pg_temp.ok(wl_fr_photo('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA') = 'abc', 'friend gets the photo');
select pg_temp.ok((wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'friends'->0->>'pv')::bigint > 0, 'photo version changes');
select pg_temp.ok(wl_pf_new_code('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_') <> :'code_a', 'new code');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a')->>'status' = 'none', 'the old code stops working');
select pg_temp.ok(jsonb_array_length(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends') = 1, 'friends stay after a new code');
select wl_delete_list('LISTA001','ownerA_ownerA_ownerA');
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'friends'->0->'lists' = '[]'::jsonb, 'a deleted list disappears');
select wl_fr_remove('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA');
select pg_temp.ok(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends' = '[]'::jsonb, 'unfollow works for both');
select pg_temp.ok(wl_fr_photo('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA') = '', 'no photo after unfollow');
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cas') as code_c \gset
select wl_pf_new_code('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_') as code_a2 \gset
select pg_temp.ok(wl_fr_add('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_', :'code_a2')->>'status' = 'sent', 'C asks A');
select pg_temp.ok(wl_fr_add('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', :'code_c')->>'status' = 'friends', 'A entering C''s code approves');
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a2');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',false);
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'outgoing' = '[]'::jsonb, 'declined request is gone');
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a2');
select wl_fr_answer('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA',true);
select pg_temp.ok(wl_fr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'friends' = '[]'::jsonb, 'the sender cannot approve their own request');
do $$ begin perform 1 from wl_profiles; raise exception 'FAIL tables readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- 2.95: birthday and "I like…", a list's link switched off
select wl_pf_about('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','07-14','Coffee and Lego');
select pg_temp.ok(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends'->0->>'birthday' = '07-14', 'friend sees the birthday');
select pg_temp.ok(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends'->0->>'bio' = 'Coffee and Lego', 'friend sees I like');
do $$ begin perform wl_pf_about('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','13-40',''); raise exception 'FAIL bad birthday accepted';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_create_list('LISTC001','ownerC_ownerC_ownerC','Cas list');
select wl_pf_lists('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','[{"code":"LISTC001","token":"ownerC_ownerC_ownerC"}]');
select wl_set_link('LISTC001','ownerC_ownerC_ownerC',false);
select pg_temp.ok(wl_get_list('LISTC001') is null, 'link off: the link does not open the list');
select pg_temp.ok(wl_fr_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','CCCCCCCCCCCCCCCCCCCC','LISTC001')->>'name' = 'Cas list', 'link off: friends still see it');
do $$ begin perform wl_set_link('LISTC001','wrong',true); raise exception 'FAIL wrong token switched the link';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_set_link('LISTC001','ownerC_ownerC_ownerC',true);
select pg_temp.ok(wl_get_list('LISTC001')->>'name' = 'Cas list', 'link on again');
do $$ begin perform wl_list_json('LISTC001'); raise exception 'FAIL internal function callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
\echo ALL FRIENDS CHECKS OK
