-- Checks supabase-vault.sql (the recovery key's locked copy, ROADMAP S4.5). Needs every other script (as
-- tests/sql/run.sh does), then supabase-vault.sql. Run as the superuser. The last line prints ALL VAULT CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.refused(sql text) returns boolean language plpgsql as $$
begin execute sql; return false; exception when others then return true; end $$;
\set id '''aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'''
\set proof '''bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'''
\set wrong '''cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc'''

set role anon;
select wl_vault_put(:id, :proof, 'bG9ja2VkIGNvcHkgb25l');
select pg_temp.ok(wl_vault_get(:id, :proof) = 'bG9ja2VkIGNvcHkgb25l', 'the owner reads the copy back');
select pg_temp.ok(wl_vault_get(:id, :wrong) is null, 'a wrong proof reads nothing');
select pg_temp.ok(wl_vault_get('dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', :proof) is null, 'an unknown id reads nothing');
select pg_temp.ok(pg_temp.refused($$select wl_vault_put('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'b3ZlcndyaXR0ZW4gYnkgYSBndWVzcw==')$$), 'someone else can''t overwrite it');
select pg_temp.ok(wl_vault_get(:id, :proof) = 'bG9ja2VkIGNvcHkgb25l', 'still the owner''s copy');
select wl_vault_put(:id, :proof, 'bG9ja2VkIGNvcHkgdHdv');
select pg_temp.ok(wl_vault_get(:id, :proof) = 'bG9ja2VkIGNvcHkgdHdv', 'the owner updates it');
select pg_temp.ok(pg_temp.refused($$select wl_vault_put('short', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'bG9ja2VkIGNvcHkgdHdv')$$), 'a malformed id is refused');
select pg_temp.ok(pg_temp.refused($$select wl_vault_put('eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', repeat('x', 300001))$$), 'a copy over 300 kB is refused');
reset role;
select pg_temp.ok((select proof from wl_vaults) <> 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'the server keeps only a fingerprint of the proof');
set role anon;
do $$ begin perform 1 from wl_vaults; raise exception 'FAIL anon can read wl_vaults'; exception when insufficient_privilege then null; end $$;
select wl_vault_delete(:id, :proof);
select pg_temp.ok(wl_vault_get(:id, :proof) is null, 'deleted with its proof');
reset role;
-- The clean-up removes copies not saved for 13 months.
insert into wl_vaults (id, proof, data, updated_at) values ('ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 'x', 'old', now() - interval '14 months');
select wl_cleanup();
select pg_temp.ok(not exists (select 1 from wl_vaults where id like 'ffff%'), 'an old copy goes in the clean-up');

select 'ALL VAULT CHECKS OK';
