-- Checks supabase-privacy.sql (block, report, delete my profile and data; ROADMAP S4.7, S4.8). Needs every other
-- script first (as tests/sql/run.sh does). Run as the superuser. The last line prints ALL PRIVACY CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.refused(sql text) returns boolean language plpgsql as $$
begin execute sql; return false; exception when others then return true; end $$;
set role anon;
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cleo') as code_c \gset
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select wl_chat_send('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','bG9ja2Vk','','');
select pg_temp.ok(jsonb_array_length(wl_chat_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')) = 1, 'a chat before blocking');

-- Block: Anna blocks Ben.
select wl_block('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB');
select pg_temp.ok(jsonb_array_length(wl_chat_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')) = 0, 'the chat is hidden for the one who blocked');
select pg_temp.ok(jsonb_array_length(wl_chat_get('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',0)) = 0, 'and its messages');
select pg_temp.ok(pg_temp.refused($$select wl_chat_send('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','bG9ja2Vk','','')$$), 'the blocked one can''t send');
select pg_temp.ok(jsonb_array_length(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'friends') = 0, 'no longer friends');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a')->>'status' = 'sent', 'a request from the blocked one seems sent');
select pg_temp.ok(jsonb_array_length(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'incoming') = 0, 'but nothing arrives');
select pg_temp.ok(wl_blocked_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->0->>'name' = 'Ben', 'Anna sees whom she blocked');
select pg_temp.ok(jsonb_array_length(wl_blocked_list('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')) = 0, 'Ben isn''t told');
select pg_temp.ok(pg_temp.refused($$select wl_block('AAAAAAAAAAAAAAAAAAAA','wrong_wrong_wrong_wr','BBBBBBBBBBBBBBBBBBBB')$$), 'nobody blocks for someone else');
select wl_unblock('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB');
select pg_temp.ok(wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a')->>'status' = 'sent', 'after unblocking a request is sent');
select pg_temp.ok(jsonb_array_length(wl_fr_state('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')->'incoming') = 1, 'and arrives');
select wl_block('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB');
select wl_fr_add('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', :'code_b');
select pg_temp.ok(jsonb_array_length(wl_blocked_list('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_')) = 0, 'entering their code again undoes your block');

-- Report.
select pg_temp.ok(pg_temp.refused($$select wl_report('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB','spam',' a b  ','[]')$$), 'a note needs 5 letters, spaces don''t count');
select pg_temp.ok(pg_temp.refused($$select wl_report('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB','rude','Sends spam','[]')$$), 'a reason from the list');
select wl_report('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB','spam','Sends links all day','[{"mine":false,"text":"buy now"}]');
do $$ begin perform 1 from wl_reports; raise exception 'FAIL anon can read reports'; exception when insufficient_privilege then null; end $$;
reset role;
select pg_temp.ok((select reported_name || '|' || reason || '|' || (messages->0->>'text') from wl_reports) = 'Ben|spam|buy now', 'the report is saved with the messages');
set role anon;

-- Delete my profile and data: Cleo.
select wl_fr_add('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','CCCCCCCCCCCCCCCCCCCC',true);
select wl_chat_send('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','AAAAAAAAAAAAAAAAAAAA','bG9ja2Vk','','');
select wl_create_list('ANNALIST','ownerA_ownerA_ownerA','Anna birthday');
select wl_sync_list('ANNALIST','ownerA_ownerA_ownerA','Anna birthday','[]','[{"id":1,"title":"Book"}]');
select wl_reserve_item('ANNALIST', 1, 'cleo-device-1');
select wl_sh_create('COLLAB01','editkey-collab-0001','ownerC_ownerC_ownerC','Mum''s birthday');
select wl_sh_put_item('editkey-collab-0001', '{"id":5,"title":"Scarf","by":"Cleo"}', array['title','by']);
select wl_sh_put_item('editkey-collab-0001', '{"id":6,"title":"Gloves","by":"Anna"}', array['title','by']);
reset role;
insert into wl_groups (id, link, name, organiser) values ('GRP1', 'grplink1', 'Family', 'CCCCCCCCCCCCCCCCCCCC');
insert into wl_group_members (group_id, profile) values ('GRP1', 'CCCCCCCCCCCCCCCCCCCC'), ('GRP1', 'AAAAAAAAAAAAAAAAAAAA');
insert into wl_profiles (id, token, code, name, web) values ('WEBWEBWEBWEBWEBWEB01', 'webtok_webtok_webtok', 'WEB001', 'Web Wendy', true);
insert into wl_gx (id, link, name, organiser) values ('GX1', 'gxlink1', 'Santa', 'CCCCCCCCCCCCCCCCCCCC');
insert into wl_gx_members (gx_id, profile) values ('GX1', 'CCCCCCCCCCCCCCCCCCCC'), ('GX1', 'WEBWEBWEBWEBWEBWEB01');
set role anon;
select pg_temp.ok(pg_temp.refused($$select wl_delete_me('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','["cleo-device-1"]','[]')$$), 'refused while she still organises a group with others');
select wl_gr_leave('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GRP1','AAAAAAAAAAAAAAAAAAAA');
select pg_temp.ok(pg_temp.refused($$select wl_delete_me('CCCCCCCCCCCCCCCCCCCC','wrong_wrong_wrong_wr','["cleo-device-1"]','[]')$$), 'nobody deletes someone else');
select wl_delete_me('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','["cleo-device-1"]','[{"k":"editkey-collab-0001","n":"Cleo"}]');
reset role;
select pg_temp.ok(not exists (select 1 from wl_profiles where id = 'CCCCCCCCCCCCCCCCCCCC'), 'the profile is gone');
select pg_temp.ok(not exists (select 1 from wl_messages where a = 'CCCCCCCCCCCCCCCCCCCC' or b = 'CCCCCCCCCCCCCCCCCCCC'), 'her chats are gone');
select pg_temp.ok(not exists (select 1 from wl_friends where a = 'CCCCCCCCCCCCCCCCCCCC' or b = 'CCCCCCCCCCCCCCCCCCCC'), 'her friendships are gone');
select pg_temp.ok(not exists (select 1 from wl_reservations where device = 'cleo-device-1'), 'her reservations are gone');
select pg_temp.ok((select organiser from wl_groups where id = 'GRP1') = 'AAAAAAAAAAAAAAAAAAAA', 'the group she handed over stays');
select pg_temp.ok(not exists (select 1 from wl_gx where id = 'GX1'), 'an exchange nobody else can organise is cancelled');
select pg_temp.ok(exists (select 1 from wl_gx_news where profile = 'WEBWEBWEBWEBWEBWEB01' and kind = 'cancelled'), 'and its members are told');
select pg_temp.ok((select data from wl_items where list_code = 'COLLAB01' and id = 5) = '{"id":5,"title":"Scarf"}'::jsonb, 'her wish stays without her name');
select pg_temp.ok((select data ->> 'by' from wl_items where list_code = 'COLLAB01' and id = 6) = 'Anna', 'someone else''s name stays');
select pg_temp.ok(exists (select 1 from wl_profiles where id = 'AAAAAAAAAAAAAAAAAAAA'), 'other people stay');

select 'ALL PRIVACY CHECKS OK';
