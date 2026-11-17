-- Checks supabase-sizes-off.sql (17 November 2026, owner: sizes removed and deleted) on a local PostgreSQL, run after
-- every other script. Sizes saved before are cleared; an older app saving sizes (in the profile or inside a list's
-- "about" item) keeps nothing; the rest of the about item stays. The last line prints ALL SIZES-OFF CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','BBBBBBBBBBBBBBBBBBBB',true);
select wl_pf_sizes('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','{"c":"M","s":"43"}');
select pg_temp.ok(wl_fr_sizes('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_') = '{}'::jsonb, 'friends get no sizes');
do $$ begin perform wl_pf_sizes('AAAAAAAAAAAAAAAAAAAA','wrong_wrong_wrong_wr','{}'); raise exception 'FAIL wrong token';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
-- An older app sends its list with sizes in the about item: they are dropped, the note and the wishes stay.
select wl_create_list('SIZE0001', 'owner_owner_owner_ow', 'Mine');
select wl_sync_list('SIZE0001', 'owner_owner_owner_ow', 'Mine', '[]',
  '[{"id":2,"title":"Book"},{"id":0,"kind":"about","about":"Hello","url":"","title":"","sz":{"c":"M","s":"43"}}]');
reset role;
select pg_temp.ok((select not (data ? 'sz') and data->>'about' = 'Hello' from wl_items where list_code = 'SIZE0001' and id = 0), 'sizes dropped from the list, the note kept');
set role anon;
-- The newer way of saving (one item at a time) is covered too.
select wl_mine_put('SIZE0001', 'owner_owner_owner_ow', null, null,
  '[{"id":0,"kind":"about","about":"Hi","sz":{"c":"S"}}]', null);
reset role;
select pg_temp.ok((select not (data ? 'sz') and data->>'about' = 'Hi' from wl_items where list_code = 'SIZE0001' and id = 0), 'sizes dropped when saved one at a time');
select pg_temp.ok((select data->>'title' from wl_items where list_code = 'SIZE0001' and id = 2) = 'Book', 'wishes untouched');
-- Sizes saved before the script: cleared when it runs again.
alter table wl_items disable trigger wl_items_no_sizes;
update wl_items set data = data || '{"sz":{"c":"L"}}' where list_code = 'SIZE0001' and id = 0;
alter table wl_items enable trigger wl_items_no_sizes;
update wl_profiles set sizes = '{"c":"L"}' where id = 'AAAAAAAAAAAAAAAAAAAA';
\i ../../supabase-sizes-off.sql
select pg_temp.ok((select not (data ? 'sz') from wl_items where list_code = 'SIZE0001' and id = 0), 'old list sizes cleared');
select pg_temp.ok((select sizes from wl_profiles where id = 'AAAAAAAAAAAAAAAAAAAA') = '', 'old profile sizes cleared');
-- The trigger function can't be called on its own.
set role anon;
do $$ begin perform wl_items_no_sizes(); raise exception 'FAIL callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;
select 'ALL SIZES-OFF CHECKS OK';
