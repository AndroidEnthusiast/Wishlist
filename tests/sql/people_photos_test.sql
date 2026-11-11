-- Checks photos as files (ROADMAP S4.1, 2.185: supabase-friends.sql, -groups.sql, -exchange.sql, -collab.sql) on a
-- local PostgreSQL. Needs every other script (as tests/sql/run.sh does; supabase-photos.sql isn't run locally: it needs
-- Supabase's Storage). Run as the superuser. The last line prints ALL PEOPLE PHOTO CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.refused(sql text) returns boolean language plpgsql as $$
begin execute sql; return false; exception when others then return true; end $$;
-- Pretends the request came with these headers (as Storage passes them to its rules).
create or replace function pg_temp.as_profile(p_id text, p_tok text) returns void language sql as
  $$ select set_config('request.headers', json_build_object('x-profile-id', p_id, 'x-profile-token', p_tok)::text, false) $$;

set role anon;
select wl_pf_save('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'Anna');
select wl_pf_save('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'Ben');
select wl_pf_save('cleo_cleo_cleo_cleo1', 'cleotoken_cleotoken_1', 'Cleo');
reset role;
-- Anna and Ben are friends; Cleo is not.
insert into wl_friends (a, b, accepted) values ('anna_anna_anna_anna1', 'ben_ben_ben_ben_ben1', true);
set role anon;

-- ---------- an old app's photo (base64) still comes through ----------
select wl_pf_photo('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'QUJD');
select pg_temp.ok(wl_fr_photo('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'anna_anna_anna_anna1') = 'QUJD', 'an embedded photo still reaches a friend');

-- ---------- a photo as a file: its address goes to friends only ----------
select pg_temp.ok(wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1',
  'https://x.supabase.co/storage/v1/object/public/people-photos/p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg') = '', 'nothing replaced the first time');
select pg_temp.ok(wl_fr_photo('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'anna_anna_anna_anna1')
  = 'https://x.supabase.co/storage/v1/object/public/people-photos/p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg', 'a friend gets the address');
select pg_temp.ok(wl_fr_photo('cleo_cleo_cleo_cleo1', 'cleotoken_cleotoken_1', 'anna_anna_anna_anna1') = '', 'a stranger gets nothing');
reset role;
select pg_temp.ok((select photo from wl_profiles where id = 'anna_anna_anna_anna1') = '', 'the embedded copy is gone (space)');
select pg_temp.ok((select photo_v from wl_profiles where id = 'anna_anna_anna_anna1') > 0, 'friends see a new photo version');
set role anon;
select pg_temp.ok(wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1',
  'https://x.supabase.co/storage/v1/object/public/people-photos/p/anna_anna_anna_anna1/bbbbbbbbbbbbbbbbbbbb.jpg')
  = 'https://x.supabase.co/storage/v1/object/public/people-photos/p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg', 'a new photo returns the old address (to delete it)');
select pg_temp.ok(pg_temp.refused($$select wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'https://evil.example/x.jpg')$$), 'another website is refused');
select pg_temp.ok(pg_temp.refused($$select wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'https://x.supabase.co/storage/v1/object/public/people-photos/p/ben_ben_ben_ben_ben1/aaaaaaaaaaaaaaaaaaaa.jpg')$$), 'someone else''s folder is refused');
select pg_temp.ok(pg_temp.refused($$select wl_pf_photo_url('anna_anna_anna_anna1', 'wrongtoken_wrongtoken', '')$$), 'a wrong token is refused');
select pg_temp.ok(wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1', '') like '%bbbbbbbbbbbbbbbbbbbb.jpg', 'removing returns the address to delete');
select pg_temp.ok(wl_fr_photo('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'anna_anna_anna_anna1') = '', 'a removed photo is gone for friends');
-- An old app saving base64 again replaces the address too.
select wl_pf_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'https://x.supabase.co/storage/v1/object/public/people-photos/p/anna_anna_anna_anna1/cccccccccccccccccccc.jpg');
select wl_pf_photo('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'REVG');
select pg_temp.ok(wl_fr_photo('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'anna_anna_anna_anna1') = 'REVG', 'an older app''s new photo wins over the address');

-- ---------- who may put files where (the Storage rules) ----------
select pg_temp.as_profile('anna_anna_anna_anna1', 'annatoken_annatoken_1');
select pg_temp.ok(wl_people_photo_ok('p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg'), 'your own folder');
select pg_temp.ok(not wl_people_photo_ok('p/ben_ben_ben_ben_ben1/aaaaaaaaaaaaaaaaaaaa.jpg'), 'not someone else''s folder');
select pg_temp.ok(not wl_people_photo_ok('p/anna_anna_anna_anna1/short.jpg'), 'not a guessable name');
select pg_temp.ok(not wl_people_photo_ok('p/anna_anna_anna_anna1/sub/aaaaaaaaaaaaaaaaaaaa.jpg'), 'no deeper folders');
select pg_temp.ok(not wl_people_photo_ok('q/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg'), 'no other kinds of folder');
select pg_temp.as_profile('anna_anna_anna_anna1', 'wrongtoken_wrongtoken');
select pg_temp.ok(not wl_people_photo_ok('p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg'), 'not with a wrong token');
select set_config('request.headers', '', false);
select pg_temp.ok(not wl_people_photo_ok('p/anna_anna_anna_anna1/aaaaaaaaaaaaaaaaaaaa.jpg'), 'not without the headers');

-- ---------- a group's photo: its organiser only; members get the address ----------
select wl_create_list('ANNALST1', 'annalisttoken_annalis', 'Anna''s list');
select wl_create_list('BENLIST1', 'benlisttoken_benlistt', 'Ben''s list');
select wl_gr_create('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'group_group_group_g1', 'GROUPLINKGROUPLINK01', 'Family', 'ANNALST1', 'annalisttoken_annalis');
select wl_gr_join('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'GROUPLINKGROUPLINK01', '', 'BENLIST1', 'benlisttoken_benlistt');
select wl_gr_photo_url('anna_anna_anna_anna1', 'annatoken_annatoken_1', 'group_group_group_g1',
  'https://x.supabase.co/storage/v1/object/public/people-photos/g/group_group_group_g1/dddddddddddddddddddd.jpg');
select pg_temp.ok(wl_gr_photo('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'group_group_group_g1') like '%dddddddddddddddddddd.jpg', 'a member gets the group photo''s address');
select pg_temp.ok(wl_gr_info('GROUPLINKGROUPLINK01') ->> 'photo' like 'https://%', 'the link preview too');
select pg_temp.ok(pg_temp.refused($$select wl_gr_photo_url('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'group_group_group_g1', '')$$), 'a member who isn''t the organiser can''t change it');
select pg_temp.as_profile('anna_anna_anna_anna1', 'annatoken_annatoken_1');
select pg_temp.ok(wl_people_photo_ok('g/group_group_group_g1/dddddddddddddddddddd.jpg'), 'the organiser may upload the group photo');
select pg_temp.as_profile('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1');
select pg_temp.ok(not wl_people_photo_ok('g/group_group_group_g1/dddddddddddddddddddd.jpg'), 'a member may not');
select pg_temp.ok(wl_gr_face('ben_ben_ben_ben_ben1', 'bentoken_bentoken_be1', 'anna_anna_anna_anna1') = 'REVG', 'group faces as before');
select set_config('request.headers', '', false);

-- ---------- the setters are for the app; the helpers answer only true or false ----------
reset role;
select pg_temp.ok(has_function_privilege('anon', 'wl_pf_photo_url(text, text, text)', 'execute'), 'the app may set its photo address');
select pg_temp.ok(has_function_privilege('anon', 'wl_gr_photo_url(text, text, text, text)', 'execute'), 'and its group''s');
select pg_temp.ok(has_function_privilege('anon', 'wl_gx_photo_url(text, text, text, text)', 'execute'), 'and its exchange''s');

select 'ALL PEOPLE PHOTO CHECKS OK';
