-- Checks supabase-groups.sql on a local PostgreSQL (not the real Supabase). See tests/README.md.
-- Needs a database where supabase-setup.sql, supabase-shared.sql, supabase-friends.sql, supabase-chat.sql and
-- supabase-groups.sql were run, and a role "anon". The last line prints ALL GROUP CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.fails(q text, what text) returns void language plpgsql as $$
begin execute q; raise exception 'FAIL % (was accepted)', what;
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
set role anon;

-- Anna (organiser), Ben (her friend), Cas (Ben's friend, not Anna's), and Web (a browser member).
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cas') as code_c \gset
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select wl_fr_add('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_', :'code_b');
select wl_fr_answer('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','CCCCCCCCCCCCCCCCCCCC',true);

-- Hide my lists is gone: an old app's hide does nothing.
select wl_fr_hide('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select pg_temp.ok((wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends'->0->>'hidden')::boolean = false, 'hiding can no longer be switched on');

select wl_create_list('LISTA001','ownerA_ownerA_ownerA','Anna list');
select wl_sync_list('LISTA001','ownerA_ownerA_ownerA','Anna list','[]','[{"id":1,"title":"Book","image":"a.jpg"},{"id":2,"title":"Pen","image":"b.jpg","bin":5},{"id":3,"title":"Mug"}]');
select wl_set_link('LISTA001','ownerA_ownerA_ownerA', false);
select wl_create_list('LISTB001','ownerB_ownerB_ownerB','Ben list');
select wl_create_list('LISTB002','ownerB_ownerB_ownerB','Ben other');
select wl_create_list('LISTC001','ownerC_ownerC_ownerC','Cas list');

-- Creating
select pg_temp.fails($q$select wl_gr_create('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','LINK00000000000000001','Fam','LISTB001','ownerA_ownerA_ownerA')$q$, 'creating with someone else''s list');
select pg_temp.fails($q$select wl_gr_create('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','LINK00000000000000001','  ','LISTA001','ownerA_ownerA_ownerA')$q$, 'creating without a name');
select wl_gr_create('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','link00000000000000001','Family','LISTA001','ownerA_ownerA_ownerA') as g \gset
select pg_temp.ok((:'g'::jsonb)->>'organiser' = 'AAAAAAAAAAAAAAAAAAAA', 'the maker is the organiser');
select pg_temp.ok((:'g'::jsonb)->'members'->0->'list'->>'count' = '2', 'bin not counted');
select pg_temp.ok((:'g'::jsonb)->'members'->0->'list'->>'image' = 'a.jpg', 'newest picture not in the bin');
select pg_temp.ok((:'g'::jsonb)->>'link' = 'LINK00000000000000001', 'link is kept in capitals');
select pg_temp.ok(wl_gr_info('link00000000000000001')->>'count' = '1', 'info by link');
select pg_temp.ok(wl_gr_info('LINK00000000000000001')->>'organiser' = 'Anna', 'info names the organiser');

-- Inviting
select pg_temp.fails($q$select wl_gr_invite('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','CCCCCCCCCCCCCCCCCCCC')$q$, 'inviting someone who isn''t a friend');
select wl_gr_invite('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','BBBBBBBBBBBBBBBBBBBB');
select pg_temp.ok(wl_gr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'invites'->0->>'by_name' = 'Anna', 'Ben sees the invite');
select pg_temp.ok(wl_gr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'groups'->0->'invited' = '["BBBBBBBBBBBBBBBBBBBB"]', 'Anna sees who is invited');
select pg_temp.fails($q$select wl_gr_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','LISTA001')$q$, 'an invite alone doesn''t open lists');

-- No thanks: Anna is told once.
select wl_gr_decline('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001');
select pg_temp.ok(wl_gr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'invites' = '[]', 'invite gone after No thanks');
select (wl_gr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'news'->0) as n \gset
select pg_temp.ok((:'n'::jsonb)->>'kind' = 'declined' and (:'n'::jsonb)->>'who' = 'Ben' and (:'n'::jsonb)->>'name' = 'Family', 'organiser told about No thanks');
select wl_gr_news_seen('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', ((:'n'::jsonb)->>'id')::bigint);
select pg_temp.ok(wl_gr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'news' = '[]', 'news told once');

-- Joining by invite, then by link
select wl_gr_invite('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','BBBBBBBBBBBBBBBBBBBB');
select pg_temp.fails($q$select wl_gr_join('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','','GROUP0000000000000001','LISTA001','ownerB_ownerB_ownerB')$q$, 'joining with someone else''s list');
select pg_temp.ok(wl_gr_join('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','','GROUP0000000000000001','LISTB001','ownerB_ownerB_ownerB')->>'name' = 'Family', 'Ben joins by invite');
select pg_temp.ok(wl_gr_join('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','','GROUP0000000000000001','LISTC001','ownerC_ownerC_ownerC') is null, 'no invite, no link: no joining');
select pg_temp.ok(wl_gr_join('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','link00000000000000001','','LISTC001','ownerC_ownerC_ownerC') is not null, 'Cas joins with the link');
select pg_temp.ok(jsonb_array_length(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'groups'->0->'members') = 3, 'three members');
select pg_temp.ok(wl_gr_list('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GROUP0000000000000001','LISTA001')->>'name' = 'Anna list', 'a member opens a brought list even with its link off');
select pg_temp.ok(wl_gr_list('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GROUP0000000000000001','LISTB002') is null, 'a list that wasn''t brought stays closed');
select pg_temp.ok((select (m->>'friend')::boolean from jsonb_array_elements(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'groups'->0->'members') m where m->>'id' = 'AAAAAAAAAAAAAAAAAAAA') = false, 'Cas and Anna are not friends');
select pg_temp.ok((select m->>'code' from jsonb_array_elements(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'groups'->0->'members') m where m->>'id' = 'AAAAAAAAAAAAAAAAAAAA') = :'code_a', 'Cas gets Anna''s code for Add friend');
select pg_temp.ok(wl_gr_face('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','AAAAAAAAAAAAAAAAAAAA') = '', 'group members may load each other''s photo');

-- Members can't invite unless allowed
select pg_temp.fails($q$select wl_gr_invite('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','CCCCCCCCCCCCCCCCCCCC')$q$, 'member invites without permission');
select pg_temp.fails($q$select wl_gr_settings('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','Mine',true)$q$, 'member changes settings');
select wl_gr_settings('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','Family 2026',true);
select wl_gr_invite('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','AAAAAAAAAAAAAAAAAAAA');
select pg_temp.ok(wl_gr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'invites' = '[]', 'inviting a member does nothing');

-- Change my wishlist
select wl_gr_set_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','LISTB002','ownerB_ownerB_ownerB');
select pg_temp.ok(wl_gr_list('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GROUP0000000000000001','LISTB002')->>'name' = 'Ben other', 'swapped list is open');
select pg_temp.ok(wl_gr_list('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GROUP0000000000000001','LISTB001') is null, 'old list closed');

-- A deleted list leaves an empty place
select wl_delete_list('LISTB002','ownerB_ownerB_ownerB');
select pg_temp.ok((select m->'list' from jsonb_array_elements(wl_gr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'groups'->0->'members') m where m->>'id' = 'BBBBBBBBBBBBBBBBBBBB') = 'null', 'deleted list: no list');
select wl_gr_set_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','LISTB001','ownerB_ownerB_ownerB');

-- Removing: Cas is told
select pg_temp.fails($q$select wl_gr_remove('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','CCCCCCCCCCCCCCCCCCCC')$q$, 'member removes someone');
select wl_gr_remove('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','CCCCCCCCCCCCCCCCCCCC');
select pg_temp.ok(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'groups' = '[]', 'Cas is out');
select pg_temp.ok(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'news'->0->>'kind' = 'removed', 'Cas is told');
select pg_temp.ok(wl_gr_state('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_')->'news'->0->>'name' = 'Family 2026', 'with the group''s name');
select pg_temp.fails($q$select wl_gr_list('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GROUP0000000000000001','LISTA001')$q$, 'removed member can''t open lists');

-- New link
select wl_gr_new_link('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','LINK00000000000000002');
select pg_temp.ok(wl_gr_info('LINK00000000000000001') is null, 'old link stops working');
select pg_temp.ok(wl_gr_info('LINK00000000000000002') is not null, 'new link works');

-- Photo
select pg_temp.fails($q$select wl_gr_set_photo('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','abc')$q$, 'member sets the photo');
select wl_gr_set_photo('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','abc');
select pg_temp.ok(wl_gr_photo('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001') = 'abc', 'member loads the photo');

-- A browser member joins with the link, then moves to the app
select wl_gr_web_profile('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_','Wendy');
select wl_create_list('LISTW001','ownerW_ownerW_ownerW','Wendy list');
select wl_gr_web_list('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_','LISTW001','ownerW_ownerW_ownerW');
select wl_gr_join('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_','LINK00000000000000002','','LISTW001','ownerW_ownerW_ownerW');
select pg_temp.ok(wl_gr_web_me('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_')->>'list_token' = 'ownerW_ownerW_ownerW', 'My link gives the list back');
select pg_temp.ok(wl_gr_web_me('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_')->'groups'->0->>'link' = 'LINK00000000000000002', 'and the group');
select pg_temp.ok((select (m->>'web')::boolean and m->>'code' = '' from jsonb_array_elements(wl_gr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'groups'->0->'members') m where m->>'id' = 'WWWWWWWWWWWWWWWWWWWW'), 'browser member: no Add friend');
select pg_temp.fails($q$select wl_gr_web_me('WWWWWWWWWWWWWWWWWWWW','wrong_wrong_wrong_wr')$q$, 'My link with a wrong token');
select wl_reserve_item('LISTA001', 1, 'web-device-1');
select wl_pf_save('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','Wendy (app)');
select pg_temp.ok(wl_gr_takeover('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_','web-device-1','XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','app-device-1') = 'LISTW001', 'takeover returns the list');
select pg_temp.ok(wl_gr_list('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','GROUP0000000000000001','LISTW001')->>'name' = 'Wendy list', 'the app is in the group with that list');
select pg_temp.ok(wl_get_reservations('LISTA001','app-device-1')->0->>'mine' = 'true', 'reservations move to the app');
select pg_temp.fails($q$select wl_gr_web_me('WWWWWWWWWWWWWWWWWWWW','tokW_tokW_tokW_tokW_')$q$, 'browser profile is gone');

-- Leaving: the organiser must pick the next organiser
select pg_temp.fails($q$select wl_gr_leave('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','')$q$, 'organiser leaves without a successor');
select pg_temp.fails($q$select wl_gr_leave('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','CCCCCCCCCCCCCCCCCCCC')$q$, 'successor must be a member');
select wl_gr_leave('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001','BBBBBBBBBBBBBBBBBBBB');
select pg_temp.ok(wl_gr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'groups'->0->>'organiser' = 'BBBBBBBBBBBBBBBBBBBB', 'Ben is organiser now');
select wl_gr_leave('XXXXXXXXXXXXXXXXXXXX','tokX_tokX_tokX_tokX_','GROUP0000000000000001','');
select pg_temp.fails($q$select wl_gr_leave('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001','')$q$, 'alone: can''t leave, only delete');
select pg_temp.fails($q$select wl_gr_delete('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GROUP0000000000000001')$q$, 'a former organiser can''t delete');
select wl_gr_delete('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GROUP0000000000000001');
select pg_temp.ok(wl_gr_state('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->'groups' = '[]', 'group deleted');
select pg_temp.ok(wl_get_list('LISTB001') is not null, 'deleting a group keeps the lists');

-- Locked parts
select pg_temp.fails($q$select wl_gr_json('GROUP0000000000000001','AAAAAAAAAAAAAAAAAAAA')$q$, 'helpers are not callable');
select pg_temp.fails($q$select * from wl_groups$q$, 'tables are locked');
\echo ALL GROUP CHECKS OK
