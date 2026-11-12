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
reset role; delete from wl_vaults; set role anon;
reset role;
-- The clean-up removes copies not saved for 13 months.
insert into wl_vaults (id, proof, data, updated_at) values ('ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 'x', 'old', now() - interval '14 months');
select wl_cleanup();
select pg_temp.ok(not exists (select 1 from wl_vaults where id like 'ffff%'), 'an old copy goes in the clean-up');

-- Restoring (2.188): the new phone gives the profile and the lists new secrets; the old ones stop working.
insert into wl_profiles (id, token, code, name) values ('pf-restore-1', 'oldtoken-profile-0001', 'RSTR01', 'Sam');
insert into wl_lists (code, owner_token, name) values ('rstrlist1', 'oldtoken-list-000001', 'Birthday');
insert into wl_lists (code, owner_token, name) values ('rstrlist2', 'someone-elses-token1', 'Not mine');
set role anon;
select wl_vault_put(:id, :proof, 'bG9ja2VkIGNvcHkgdGhyZWU=');
select pg_temp.ok(not wl_signed_out('["oldtoken-profile-0001"]'), 'not signed out before a restore');
select pg_temp.ok(pg_temp.refused($$select wl_vault_restore('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'pf-restore-1', 'oldtoken-profile-0001', 'newtoken-profile-0001', '[]')$$), 'a wrong proof can''t move a profile');
select pg_temp.ok(pg_temp.refused($$select wl_vault_restore('dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'pf-restore-1', 'oldtoken-profile-0001', 'newtoken-profile-0001', '[]')$$), 'without a copy nothing moves');
select pg_temp.ok(wl_vault_restore(:id, :proof, 'pf-restore-1', 'oldtoken-profile-0001', 'newtoken-profile-0001',
  '[{"c":"rstrlist1","o":"oldtoken-list-000001","n":"newtoken-list-000001"},{"c":"rstrlist2","o":"guessed-token-000001","n":"stolen-token-0000001"}]')
  = '{"lists": 1, "profile": true}'::jsonb, 'the profile and the own list moved, a list with another token did not');
select pg_temp.ok(wl_vault_get(:id, :proof) is null, 'the old copy is gone after the restore');
select pg_temp.ok(pg_temp.refused($$select wl_vault_put('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'bG9ja2VkIGNvcHkgZm91cg==')$$), 'the old phone can''t save a copy under the old key again');
select pg_temp.ok(pg_temp.refused($$select wl_vault_restore('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'pf-restore-1', 'newtoken-profile-0001', 'stolen-profile-00001', '[]')$$), 'the old key can''t restore twice');
select pg_temp.ok(wl_signed_out('["oldtoken-profile-0001"]'), 'the old phone learns it was signed out (profile)');
select pg_temp.ok(wl_signed_out('["x","oldtoken-list-000001"]'), 'the old phone learns it was signed out (list)');
select pg_temp.ok(not wl_signed_out('["newtoken-profile-0001","someone-elses-token1"]'), 'the new secrets and others are not signed out');
select pg_temp.ok(not wl_signed_out('"text"'), 'a wrong question answers false');
reset role;
select pg_temp.ok((select token from wl_profiles where id = 'pf-restore-1') = 'newtoken-profile-0001', 'the profile has the new token');
select pg_temp.ok((select owner_token from wl_lists where code = 'rstrlist1') = 'newtoken-list-000001', 'the list has the new token');
select pg_temp.ok((select owner_token from wl_lists where code = 'rstrlist2') = 'someone-elses-token1', 'someone else''s list is untouched');
select pg_temp.ok(not exists (select 1 from wl_old_secrets where hash like '%oldtoken%'), 'only fingerprints of old secrets are kept');
set role anon;
do $$ begin perform 1 from wl_old_secrets; raise exception 'FAIL anon can read wl_old_secrets'; exception when insufficient_privilege then null; end $$;
reset role;

select 'ALL VAULT CHECKS OK';
