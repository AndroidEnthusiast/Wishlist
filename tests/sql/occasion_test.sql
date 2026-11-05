-- Checks the occasion of gift lists in supabase-shared.sql (5 November 2026) on a local PostgreSQL. See tests/README.md.
-- Needs a database where supabase-setup.sql and supabase-shared.sql were run, and a role "anon".
-- The last line prints ALL OCCASION CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
-- The about item as an editor's app receives it (wl_sh_get).
create or replace function pg_temp.item0(p_key text) returns jsonb language sql as $$
  select e from jsonb_array_elements(wl_sh_get(p_key)->'items') e where (e->>'id')::int = 0 $$;
set role anon;
select wl_sh_create('GIFT0001', 'editkey_editkey_editkey_', 'owner_owner_owner_ow', 'For Sam');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_') is null, 'a new list has no about item');
select wl_sh_set_occasion('editkey_editkey_editkey_', '{"k":"birthday","d":"07-14","x":"junk"}');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->'occ' = '{"k":"birthday","d":"07-14"}'::jsonb, 'occasion saved, unknown fields dropped');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->>'about' = '', 'empty note with an occasion');
select wl_sh_set_about('editkey_editkey_editkey_', 'Size M');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->'occ'->>'k' = 'birthday', 'changing the note keeps the occasion');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->>'about' = 'Size M', 'the note is saved');
select wl_sh_set_meta('editkey_editkey_editkey_', 'For Sam', '[]', 'Size L');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->'occ'->>'k' = 'birthday', 'older apps (set_meta) keep the occasion');
select wl_sh_set_occasion('editkey_editkey_editkey_', '{"k":"own","n":"A very very long name that goes on and on and on and on","d":"2027-03-01"}');
select pg_temp.ok(length(pg_temp.item0('editkey_editkey_editkey_')->'occ'->>'n') = 40, 'own name cut to 40');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->>'about' = 'Size L', 'changing the occasion keeps the note');
select wl_sh_set_about('editkey_editkey_editkey_', '');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_') is not null, 'empty note: the item stays for the occasion');
select wl_sh_set_occasion('editkey_editkey_editkey_', null);
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_') is null, 'no note and no occasion: the item goes');
select wl_sh_set_about('editkey_editkey_editkey_', 'Hi');
select wl_sh_set_occasion('editkey_editkey_editkey_', '{"k":"christmas"}');
select wl_sh_set_occasion('editkey_editkey_editkey_', 'null');
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->'occ' is null and pg_temp.item0('editkey_editkey_editkey_')->>'about' = 'Hi', 'json null removes only the occasion');
select pg_temp.ok((wl_sh_get('editkey_editkey_editkey_')->'items') @> '[{"id":0,"about":"Hi"}]', 'wl_sh_get returns the about item');
do $$ begin perform wl_sh_set_occasion('wrong_key_wrong_key_wro', '{"k":"birthday"}'); raise exception 'FAIL wrong key accepted';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
do $$ begin perform wl_sh_write_about('GIFT0001', 'hacked', null); raise exception 'FAIL internal function callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
select pg_temp.ok(pg_temp.item0('editkey_editkey_editkey_')->>'about' = 'Hi', 'nothing changed by the refused calls');
reset role;
\echo ALL OCCASION CHECKS OK
