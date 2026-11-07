-- Checks supabase-collab.sql (7 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where setup, shared, friends, chat, push (pretend net.http_post), groups, features, together
-- (without its pg_cron lines) and supabase-collab.sql were run, and a role "anon". The last line prints ALL COLLAB CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cleo') as code_c \gset
select wl_pf_photo('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','BENPHOTO');
select wl_pf_photo('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','CLEOPHOTO');

-- 1. people on a collab wishlist
select wl_sh_create('COLLAB01', 'editkey_editkey_editkey_', 'maker_maker_maker_ma', 'Mum');
select wl_sh_member('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','editkey_editkey_editkey_');
select wl_sh_member('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','editkey_editkey_editkey_');
select pg_temp.ok(jsonb_array_length(wl_sh_people('editkey_editkey_editkey_')) = 2, 'two people');
select pg_temp.ok(wl_sh_people('editkey_editkey_editkey_')->0->>'name' = 'Anna', 'oldest member first');
select pg_temp.ok((wl_sh_people('editkey_editkey_editkey_')->1->>'pv')::bigint > 0, 'photo version given');
select pg_temp.ok(wl_sh_people('editkey_editkey_editkey_')->0->>'token' is null, 'no tokens given away');
do $$ begin perform wl_sh_people('wrong_key_wrong_key_wro'); raise exception 'FAIL people with a wrong key';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_sh_people('short'); raise exception 'FAIL people with a short key';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(wl_sh_face('editkey_editkey_editkey_','BBBBBBBBBBBBBBBBBBBB') = 'BENPHOTO', 'a member''s photo');
select pg_temp.ok(wl_sh_face('editkey_editkey_editkey_','CCCCCCCCCCCCCCCCCCCC') = '', 'not a member: no photo');
do $$ begin perform wl_sh_face('wrong_key_wrong_key_wro','BBBBBBBBBBBBBBBBBBBB'); raise exception 'FAIL face with a wrong key';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;

-- 2. moving a wish between your own lists
select wl_create_list('LISTFROM', 'ownerA_ownerA_ownerA', 'Birthday');
select wl_create_list('LISTTOOO', 'ownerA2_ownerA2_owne', 'Christmas');
select wl_create_list('OTHERSLI', 'ownerC_ownerC_ownerC', 'Cleo''s');
select wl_sync_list('LISTFROM', 'ownerA_ownerA_ownerA', 'Birthday', '[]', '[{"id":1,"title":"Lamp"},{"id":2,"title":"Book","qty":2}]');
select wl_sync_list('LISTTOOO', 'ownerA2_ownerA2_owne', 'Christmas', '[]', '[{"id":9,"title":"Socks"}]');
select wl_reserve_item('LISTFROM', 1, 'device_ben_device_ben');
select wl_reserve_count('LISTFROM', 2, 'device_ben_device_ben', 1);
select wl_reserve_count('LISTFROM', 2, 'device_cleo_device_cl', 1);
do $$ begin perform wl_move_item('LISTFROM','wrong','LISTTOOO','ownerA2_ownerA2_owne',1); raise exception 'FAIL move with a wrong from token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_move_item('LISTFROM','ownerA_ownerA_ownerA','OTHERSLI','wrong',1); raise exception 'FAIL move into someone else''s list';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_move_item('LISTFROM','ownerA_ownerA_ownerA','LISTTOOO','',1); raise exception 'FAIL move with an empty token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(wl_moved_mine('LISTFROM','device_ben_device_ben') = '[]'::jsonb, 'nothing moved yet');
select wl_move_item('LISTFROM','ownerA_ownerA_ownerA','LISTTOOO','ownerA2_ownerA2_owne',1);
select wl_move_item('LISTFROM','ownerA_ownerA_ownerA','LISTTOOO','ownerA2_ownerA2_owne',2);
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_get_reservations('LISTTOOO','device_ben_device_ben')) e where (e->>'id')::int = 1 and (e->>'mine')::boolean), 'the reservation moved along');
reset role;
select pg_temp.ok((select count(*) from wl_reservations where list_code = 'LISTTOOO' and item_id = 2) = 2, 'both parts of the 2x wish moved');
select pg_temp.ok((select count(*) from wl_reservations where list_code = 'LISTFROM') = 0, 'nothing left behind');
set role anon;
-- The app then saves both lists: the wish leaves the old list; the reservations stay on the new one.
select wl_sync_list('LISTFROM', 'ownerA_ownerA_ownerA', 'Birthday', '[]', '[]');
select wl_sync_list('LISTTOOO', 'ownerA2_ownerA2_owne', 'Christmas', '[]', '[{"id":1,"title":"Lamp"},{"id":2,"title":"Book","qty":2},{"id":9,"title":"Socks"}]');
reset role;
select pg_temp.ok((select count(*) from wl_reservations where list_code = 'LISTTOOO') = 3, 'reservations kept after both lists are saved');
set role anon;
select pg_temp.ok(jsonb_array_length(wl_moved_mine('LISTFROM','device_ben_device_ben')) = 2, 'Ben''s phone can follow both wishes');
select pg_temp.ok(wl_moved_mine('LISTFROM','device_ben_device_ben')->0->>'to' = 'LISTTOOO', 'to the new list');
select pg_temp.ok(jsonb_array_length(wl_moved_mine('LISTFROM','device_cleo_device_cl')) = 1, 'Cleo only learns about her own reservation');
select pg_temp.ok(wl_moved_mine('LISTFROM','someone_else_device') = '[]'::jsonb, 'someone who reserved nothing learns nothing');
select pg_temp.ok(wl_moved_mine('LISTFROM','') = '[]'::jsonb, 'an empty device learns nothing');
-- A wish that isn't online: nothing happens.
select wl_move_item('LISTFROM','ownerA_ownerA_ownerA','LISTTOOO','ownerA2_ownerA2_owne',77);
-- Moving back.
select wl_move_item('LISTTOOO','ownerA2_ownerA2_owne','LISTFROM','ownerA_ownerA_ownerA',1);
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_get_reservations('LISTFROM','device_ben_device_ben')) e where (e->>'id')::int = 1 and (e->>'mine')::boolean), 'moved back with its reservation');
select pg_temp.ok(not exists (select 1 from jsonb_array_elements(wl_moved_mine('LISTFROM','device_ben_device_ben')) e where (e->>'item')::int = 1), 'no forwarding from the list it is on again');
reset role;
-- The table is locked.
set role anon;
do $$ begin perform count(*) from wl_moved; raise exception 'FAIL anon reads wl_moved';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;
select 'ALL COLLAB CHECKS OK';
