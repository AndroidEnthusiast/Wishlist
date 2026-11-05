-- Checks supabase-features.sql (6 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where supabase-setup.sql, supabase-shared.sql, supabase-friends.sql, supabase-chat.sql and
-- supabase-features.sql were run, and a role "anon". The last line prints ALL FEATURE CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cleo') as code_c \gset
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
-- 2. sizes
select wl_pf_sizes('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','{"c":"M","s":"43"}');
select pg_temp.ok(wl_fr_sizes('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_')->>'AAAAAAAAAAAAAAAAAAAA' = '{"c":"M","s":"43"}', 'a friend sees my sizes');
select pg_temp.ok(wl_fr_sizes('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_') = '{}'::jsonb, 'a stranger sees nothing');
do $$ begin perform wl_pf_sizes('AAAAAAAAAAAAAAAAAAAA','wrong_wrong_wrong_wr','{}'); raise exception 'FAIL wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- 1. reactions
select (wl_chat_send('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA','body','spk','rpk')->>'id')::bigint as msg \gset
select wl_chat_react('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', :msg, 'lockedHeart');
select pg_temp.ok(wl_chat_reacts('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB')->0->>'mine' = 'lockedHeart', 'my reaction');
select pg_temp.ok(wl_chat_reacts('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA')->0->>'theirs' = 'lockedHeart', 'they see it as theirs');
select wl_chat_react('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_', :msg, 'evil');
select pg_temp.ok(wl_chat_reacts('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','AAAAAAAAAAAAAAAAAAAA')->0->>'mine' = '', 'a stranger can''t react');
select wl_chat_react('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', :msg, '');
select pg_temp.ok(jsonb_array_length(wl_chat_reacts('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB')) = 0, 'reaction taken off');
-- 3. members and a new owner
select wl_sh_create('GIFTMEM1', 'editkey_editkey_editkey_', 'maker_maker_maker_ma', 'Mum');
select wl_sh_member('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','editkey_editkey_editkey_');
select wl_sh_member('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','editkey_editkey_editkey_');
select wl_sh_member('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','editkey_editkey_editkey_');
select pg_temp.ok(jsonb_array_length(wl_sh_members('GIFTMEM1','maker_maker_maker_ma')) = 2, 'two members, once each');
do $$ begin perform wl_sh_members('GIFTMEM1','wrong'); raise exception 'FAIL members without owner token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_sh_member('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','wrong_key_wrong_key_wro'); raise exception 'FAIL member with wrong key';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_sh_handover('GIFTMEM1','maker_maker_maker_ma','CCCCCCCCCCCCCCCCCCCC'); raise exception 'FAIL handover to a non-member';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select wl_sh_member_leave('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','editkey_editkey_editkey_');
select pg_temp.ok(jsonb_array_length(wl_sh_members('GIFTMEM1','maker_maker_maker_ma')) = 1, 'a member left');
select wl_sh_handover('GIFTMEM1','maker_maker_maker_ma','BBBBBBBBBBBBBBBBBBBB');
do $$ begin perform wl_sh_members('GIFTMEM1','maker_maker_maker_ma'); raise exception 'FAIL old owner token still works';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(wl_sh_owner_claim('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','editkey_editkey_editkey_') is null, 'another member can''t claim');
select wl_sh_owner_claim('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','editkey_editkey_editkey_') as newtok \gset
select pg_temp.ok(length(:'newtok') = 64, 'the new owner gets a token');
select pg_temp.ok(wl_sh_owner_claim('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','editkey_editkey_editkey_') is null, 'only once');
select pg_temp.ok(jsonb_array_length(wl_sh_members('GIFTMEM1', :'newtok')) = 1, 'the new owner token works');
select wl_sh_reset_key('GIFTMEM1', :'newtok', 'newkey_newkey_newkey_ne');
select pg_temp.ok(wl_sh_info('newkey_newkey_newkey_ne')->>'name' = 'Mum', 'the new owner can renew the link');
reset role;
\echo ALL FEATURE CHECKS OK
