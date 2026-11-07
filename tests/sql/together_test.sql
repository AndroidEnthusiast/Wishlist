-- Checks supabase-together.sql (7 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where all the other scripts were run (push with the pretend net.http_post), then
-- supabase-together.sql without its two pg_cron lines (pg_cron isn't there locally). Prints ALL TOGETHER CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
\set A '''AAAAAAAAAAAAAAAAAAAA'''
\set TA '''tokA_tokA_tokA_tokA_'''
\set B '''BBBBBBBBBBBBBBBBBBBB'''
\set TB '''tokB_tokB_tokB_tokB_'''
\set C '''CCCCCCCCCCCCCCCCCCCC'''
\set TC '''tokC_tokC_tokC_tokC_'''
select wl_pf_save(:A, :TA, 'Anna') as code_a \gset
select wl_pf_save(:B, :TB, 'Ben') as code_b \gset
select wl_pf_save(:C, :TC, 'Cleo') as code_c \gset
select wl_fr_add(:B, :TB, :'code_a');
select wl_fr_answer(:A, :TA, :B, true);
select wl_push_set(:B, :TB, 'pushB');
select wl_push_set(:A, :TA, 'pushA');

-- ---------- 0. devices and news ----------
select wl_dev_link(:A, :TA, 'devA_devA');
do $$ begin perform wl_dev_link('AAAAAAAAAAAAAAAAAAAA', 'wrong_wrong_wrong_wr', 'devX_devX'); raise exception 'FAIL dev link wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_dev_link('AAAAAAAAAAAAAAAAAAAA', 'tokA_tokA_tokA_tokA_', 'x'); raise exception 'FAIL short device';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform 1 from wl_news; raise exception 'FAIL news table readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_news_add('AAAAAAAAAAAAAAAAAAAA', null, null, 'x', '{}'); raise exception 'FAIL inner news function callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;

-- ---------- 1. gift list invitations ----------
select wl_sh_create('GIFTINV1', 'editkey_editkey_editkey_', 'maker_maker_maker_ma', 'Mum''s 60th');
select wl_sh_put_item('editkey_editkey_editkey_', '{"id":5,"title":"Scarf","image":"https://x/a.jpg"}'::jsonb);
select wl_sh_member(:B, :TB, 'editkey_editkey_editkey_');
select pg_temp.ok(wl_sh_invite(:B, :TB, 'editkey_editkey_editkey_', :C) = 'not_friend', 'only Friends can be invited');
select pg_temp.ok(wl_sh_invite(:B, :TB, 'editkey_editkey_editkey_', :A) = 'ok', 'invite a Friend');
select pg_temp.ok(wl_sh_invite(:B, :TB, 'editkey_editkey_editkey_', :A) = 'ok', 'inviting again is fine');
do $$ begin perform wl_sh_invite('BBBBBBBBBBBBBBBBBBBB', 'tokB_tokB_tokB_tokB_', 'wrong_key_wrong_key_wro', 'AAAAAAAAAAAAAAAAAAAA'); raise exception 'FAIL invite with a wrong key';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(wl_sh_invited(:B, :TB, 'editkey_editkey_editkey_')->'invited' = '["AAAAAAAAAAAAAAAAAAAA"]'::jsonb, 'shown as invited');
select pg_temp.ok(jsonb_array_length(wl_sh_invites_mine(:A, :TA)) = 1, 'one invitation waiting');
select pg_temp.ok(wl_sh_invites_mine(:A, :TA)->0->>'by' = 'Ben', 'who invited');
select pg_temp.ok(wl_sh_inv_info(:A, :TA, 'GIFTINV1')->>'name' = 'Mum''s 60th', 'invitation shows the list');
select pg_temp.ok((wl_sh_inv_info(:A, :TA, 'GIFTINV1')->>'count')::int = 1, 'and its wishes');
select pg_temp.ok(wl_sh_inv_info(:A, :TA, 'GIFTINV1')->'givers' = '["Ben"]'::jsonb, 'and the givers');
select pg_temp.ok(wl_sh_inv_info(:A, :TA, 'GIFTINV1')::text not like '%editkey%', 'never the edit key before joining');
select pg_temp.ok(wl_sh_inv_info(:C, :TC, 'GIFTINV1') is null, 'someone not invited sees nothing');
select pg_temp.ok(wl_sh_inv_accept(:C, :TC, 'GIFTINV1') is null, 'someone not invited can''t join');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:A, :TA, '')) e where e->>'kind' = 'sh_invite' and e->'data'->>'by' = 'Ben'), 'the invited Friend gets news');
-- the trigger asked the push function for A (a push address) and the news is there
reset role;
select pg_temp.ok((select count(*) from net.calls where body ? 'n') >= 1, 'news is pushed');
set role anon;
select pg_temp.ok(wl_sh_inv_accept(:A, :TA, 'GIFTINV1') = 'editkey_editkey_editkey_', 'join gives the key');
select pg_temp.ok(wl_sh_inv_accept(:A, :TA, 'GIFTINV1') is null, 'only once');
select pg_temp.ok(jsonb_array_length(wl_sh_members('GIFTINV1', 'maker_maker_maker_ma')) = 2, 'now a member');
select pg_temp.ok(wl_sh_invite(:B, :TB, 'editkey_editkey_editkey_', :A) = 'member', 'a member isn''t invited again');
-- No thanks: whoever invited is told quietly (no push)
select wl_sh_create('GIFTINV2', 'editkey2_editkey2_editk', 'maker2_maker2_maker2', 'Dad');
select wl_sh_member(:B, :TB, 'editkey2_editkey2_editk');
select wl_sh_invite(:B, :TB, 'editkey2_editkey2_editk', :A);
reset role;
delete from net.calls;
set role anon;
select wl_sh_inv_decline(:A, :TA, 'GIFTINV2');
select pg_temp.ok(jsonb_array_length(wl_sh_invites_mine(:A, :TA)) = 0, 'no thanks removes it');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:B, :TB, '')) e
  where e->>'kind' = 'sh_declined' and e->'data'->>'who' = 'Anna'), 'the inviter is told');
reset role;
select pg_temp.ok((select count(*) from net.calls) = 0, 'no thanks is quiet (no push)');
set role anon;
-- news is deleted once told
select max((e->>'id')::bigint) as upto from jsonb_array_elements(wl_news_get(:B, :TB, '')) e \gset
select wl_news_done(:B, :TB, '', :upto);
select pg_temp.ok(jsonb_array_length(wl_news_get(:B, :TB, '')) = 0, 'news gone once told');

-- ---------- 2. reactions ----------
select wl_create_list('RXLIST01', 'owner_owner_owner_ow', 'Anna''s list');
select wl_sync_list('RXLIST01', 'owner_owner_owner_ow', 'L', '[]', '[{"id":1,"title":"one"},{"id":2,"title":"two"}]');
select wl_pf_lists(:A, :TA, '[{"code":"RXLIST01","token":"owner_owner_owner_ow"}]');
select pg_temp.ok(not wl_rx_set('RXLIST01', 1, :A, :TA, '', '', '😍'), 'the owner can''t react on their own list');
select pg_temp.ok(wl_rx_set('RXLIST01', 1, :B, :TB, '', '', '😍'), 'a Friend reacts');
select pg_temp.ok(wl_rx_set('RXLIST01', 1, :B, :TB, '', '', '🔥'), 'a new one replaces it');
select pg_temp.ok(wl_rx_set('RXLIST01', 1, '', '', 'webdev_webdev', 'Sam', '🔥'), 'a browser visitor with a name');
select pg_temp.ok(not wl_rx_set('RXLIST01', 1, '', '', 'webdev2_webdev2', '  ', '🔥'), 'a browser visitor needs a name');
select pg_temp.ok(not wl_rx_set('RXLIST01', 1, '', '', 'webdev2_webdev2', 'Jo', '<b>x</b>'), 'no markup as an emoji');
select pg_temp.ok(not wl_rx_set('RXLIST01', 1, '', '', 'webdev2_webdev2', 'Jo', 'hello'), 'no words as an emoji');
select pg_temp.ok(wl_rx_set('RXLIST01', 2, '', '', 'webdev2_webdev2', 'Jo', '👨‍👩‍👧‍👦'), 'a long emoji is fine');
select pg_temp.ok(not wl_rx_set('RXLIST01', 9, :B, :TB, '', '', '🔥'), 'an unknown wish refused');
do $$ begin perform wl_rx_set('RXLIST01', 1, 'BBBBBBBBBBBBBBBBBBBB', 'wrong_wrong_wrong_wr', '', '', '🔥'); raise exception 'FAIL react with a wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(jsonb_array_length(wl_rx_get('RXLIST01', '', '', '')) = 3, 'three reactions');
select pg_temp.ok((select count(*) from jsonb_array_elements(wl_rx_get('RXLIST01', :B, :TB, '')) e where (e->>'m')::boolean) = 1, 'mine marked for the app');
select pg_temp.ok((select e->>'n' from jsonb_array_elements(wl_rx_get('RXLIST01', '', '', 'webdev_webdev')) e where (e->>'m')::boolean) = 'Sam', 'mine marked for the browser');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_rx_get('RXLIST01', '', '', '')) e where e->>'n' = 'Ben' and e->>'e' = '🔥'), 'app names are the profile name');
select pg_temp.ok(wl_rx_get('RXLIST01', '', '', '')::text not like '%BBBBBBBB%' and wl_rx_get('RXLIST01', '', '', '')::text not like '%webdev%', 'no ids or devices given away');
select wl_rx_set('RXLIST01', 1, :B, :TB, '', '', '');
select pg_temp.ok(jsonb_array_length(wl_rx_get('RXLIST01', '', '', '')) = 2, 'tap yours to remove');
do $$ begin perform 1 from wl_reacts; raise exception 'FAIL reactions table readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- a wish that is deleted takes its reactions along
select wl_sync_list('RXLIST01', 'owner_owner_owner_ow', 'L', '[]', '[{"id":1,"title":"one"}]');
select pg_temp.ok(jsonb_array_length(wl_rx_get('RXLIST01', '', '', '')) = 1, 'reactions of a deleted wish go');

-- ---------- 3. stuck reservations ----------
select wl_create_list('ASKLIST1', 'owner_owner_owner_ow', 'Zoe''s list');
select wl_sync_list('ASKLIST1', 'owner_owner_owner_ow', 'L', '[]',
  '[{"id":1,"title":"Lamp"},{"id":2,"title":"Cups","qty":2},{"id":3,"title":"Book"}]');
-- A (an app, device linked) reserved the lamp; B (an app) wants it.
select wl_reserve_item('ASKLIST1', 1, 'devA_devA');
select pg_temp.ok(wl_ask('ASKLIST1', 3, 'devB_devB', :B, :TB) = 'free', 'a free wish needs no ask');
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devA_devA', '', '') = 'mine', 'you can''t ask about your own reservation');
reset role; delete from net.calls; set role anon;
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devB_devB', :B, :TB) = 'ok', 'ask');
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devC_devC', '', '') = 'asked', 'someone already asked');
reset role;
select pg_temp.ok((select count(*) from net.calls) = 2, 'two pushes at once: the reserver and the asker');
set role anon;
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:A, :TA, '')) e where e->>'kind' = 'ask' and e->'data'->>'title' = 'Lamp'), 'the reserver gets the question');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:B, :TB, '')) e where e->>'kind' = 'ask_sent'), 'the asker gets a short push');
select pg_temp.ok(jsonb_array_length(wl_ask_mine('devA_devA', '')) = 1, 'the question waits in Gifts I reserved');
select pg_temp.ok(jsonb_array_length(wl_ask_mine('devB_devB', '')) = 0, 'not for the asker');
select (wl_ask_mine('devA_devA', '')->0->>'ask')::bigint as ask1 \gset
select pg_temp.ok(not wl_ask_answer('devB_devB', :ask1, false), 'only the reserver answers');
select pg_temp.ok(wl_ask_answer('devA_devA', :ask1, true), 'Yes, I''m buying it');
select pg_temp.ok(not wl_ask_answer('devA_devA', :ask1, false), 'answered once');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:B, :TB, '')) e where e->>'kind' = 'ask_yes'), 'the asker is told it''s still being bought');
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devC_devC', '', '') = 'recent', 'no new ask for 7 days');
select pg_temp.ok(wl_get_reservations('ASKLIST1', 'devA_devA')->0->>'mine' = 'true', 'still reserved');
reset role; update wl_asks set answered_at = now() - interval '8 days'; set role anon;
-- a week later: a browser visitor asks; A lets go
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devC_devC', '', '') = 'ok', 'after 7 days a new ask');
select (wl_ask_mine('devA_devA', 'ASKLIST1')->0->>'ask')::bigint as ask2 \gset
select pg_temp.ok(wl_ask_answer('devA_devA', :ask2, false), 'Let someone else');
select pg_temp.ok(jsonb_array_length(wl_get_reservations('ASKLIST1', 'devC_devC')) = 0, 'the gift is free at once');
select pg_temp.ok(wl_news_web('devC_devC', 'ASKLIST1')->0->>'kind' = 'ask_free', 'the browser asker sees it is free');
select pg_temp.ok(jsonb_array_length(wl_news_web('devC_devC', 'ASKLIST1')) = 0, 'once');
-- no answer in 2 days: free, both told; a browser reserver sees it on the next visit
select wl_reserve_item('ASKLIST1', 1, 'webR_webR');
select pg_temp.ok(wl_ask('ASKLIST1', 1, 'devB_devB', :B, :TB) = 'ok', 'ask a browser reserver');
select pg_temp.ok(jsonb_array_length(wl_ask_mine('webR_webR', 'ASKLIST1')) = 1, 'the browser reserver can answer on the page');
reset role;
select wl_ask_expire();
select pg_temp.ok((select count(*) from wl_reservations where list_code = 'ASKLIST1' and item_id = 1) = 1, 'not before 2 days');
update wl_asks set created_at = now() - interval '2 days 1 minute' where state = 'open';
select wl_ask_expire();
set role anon;
select pg_temp.ok(jsonb_array_length(wl_get_reservations('ASKLIST1', 'x_x_x_x_x')) = 0, 'free after 2 days (no hold)');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_get(:B, :TB, '')) e where e->>'kind' = 'ask_free'), 'the asker: free, reserve it now?');
select pg_temp.ok(wl_news_web('webR_webR', 'ASKLIST1')->0->>'kind' = 'ask_ended', 'the browser reserver: your reservation ended');
-- a reserver who takes it away themselves while asked: free, the asker told
select wl_reserve_item('ASKLIST1', 1, 'devA_devA');
select wl_ask('ASKLIST1', 1, 'devC_devC', '', '');
select wl_unreserve_item('ASKLIST1', 1, 'devA_devA');
select pg_temp.ok(exists (select 1 from jsonb_array_elements(wl_news_web('devC_devC', 'ASKLIST1')) e where e->>'kind' = 'ask_free'), 'undoing a reservation answers the ask');
select pg_temp.ok(jsonb_array_length(wl_ask_mine('devA_devA', '')) = 0, 'nothing left to answer');
-- 2x: everyone who reserved is asked; one says yes, one lets go -> free part
select wl_reserve_count('ASKLIST1', 2, 'devA_devA', 1);
select pg_temp.ok(wl_ask('ASKLIST1', 2, 'devC_devC', '', '') = 'free', '2x not fully reserved: no ask');
select wl_reserve_count('ASKLIST1', 2, 'webR_webR', 1);
select pg_temp.ok(wl_ask('ASKLIST1', 2, 'devC_devC', '', '') = 'ok', '2x fully reserved: ask');
select pg_temp.ok(jsonb_array_length(wl_ask_mine('devA_devA', 'ASKLIST1')) = 1 and jsonb_array_length(wl_ask_mine('webR_webR', 'ASKLIST1')) = 1, 'both reservers asked');
select (wl_ask_mine('devA_devA', 'ASKLIST1')->0->>'ask')::bigint as ask3 \gset
select wl_ask_answer('devA_devA', :ask3, true);
select pg_temp.ok(jsonb_array_length(wl_news_web('devC_devC', 'ASKLIST1')) = 0, 'nothing told while one hasn''t answered');
select wl_ask_answer('webR_webR', :ask3, false);
select pg_temp.ok((select count(*) from jsonb_array_elements(wl_get_reservations('ASKLIST1', 'devA_devA')) e where e->>'id' = '2' and (e->>'my')::int = 1 and e->>'full' = 'false') = 1, 'the yes part stays, the other is free');
select pg_temp.ok(wl_news_web('devC_devC', 'ASKLIST1')->0->>'kind' = 'ask_free', 'the asker: a part is free');
-- the occasion has passed: no asking
select wl_sh_create('GIFTASK1', 'askkey_askkey_askkey_ask', 'maker3_maker3_maker3', 'Party');
select wl_sh_put_item('askkey_askkey_askkey_ask', '{"id":1,"title":"Cake"}'::jsonb);
select wl_sh_set_occasion('askkey_askkey_askkey_ask', '{"k":"own","n":"Party","d":"2020-01-01"}'::jsonb);
select wl_reserve_item('GIFTASK1', 1, 'devA_devA');
select pg_temp.ok(wl_ask('GIFTASK1', 1, 'devB_devB', :B, :TB) = 'passed', 'not after the occasion');
select wl_sh_set_occasion('askkey_askkey_askkey_ask', '{"k":"christmas"}'::jsonb);
select pg_temp.ok(wl_ask('GIFTASK1', 1, 'devB_devB', :B, :TB) = 'ok', 'a yearly occasion never passes');
select pg_temp.ok((wl_ask_mine('devA_devA', 'GIFTASK1')->0->>'gift')::boolean and wl_ask_mine('devA_devA', 'GIFTASK1')->0->>'list' = 'Party', 'the place of a gift list');
do $$ begin perform wl_ask_expire(); raise exception 'FAIL the timer is callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform 1 from wl_asks; raise exception 'FAIL asks table readable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_ask_free(1, 'devA_devA', true); raise exception 'FAIL inner ask function callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- running the script again keeps everything (checked by running it twice before this file)
\echo ALL TOGETHER CHECKS OK
