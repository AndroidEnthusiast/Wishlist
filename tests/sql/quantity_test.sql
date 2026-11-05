-- Checks reservations with a number (2.128, supabase-setup.sql) on a local PostgreSQL. See tests/README.md.
-- Needs a database where supabase-setup.sql was run, and a role "anon". The last line prints ALL QUANTITY CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
set role anon;
select wl_create_list('QTY00001', 'owner_owner_owner_ow', 'Q');
select wl_sync_list('QTY00001', 'owner_owner_owner_ow', 'Q', '[]',
  '[{"id":1,"title":"one"},{"id":2,"title":"three","qty":3},{"id":3,"title":"bad","qty":"lots"}]');
create or replace function pg_temp.res(d text) returns jsonb language sql as $$ select wl_get_reservations('QTY00001', d) $$;
create or replace function pg_temp.row(d text, i int) returns jsonb language sql as $$
  select e from jsonb_array_elements(pg_temp.res(d)) e where (e->>'id')::int = i $$;
-- a wish wanted once: as before
select pg_temp.ok(wl_reserve_item('QTY00001', 1, 'devA'), 'reserve a single wish');
select pg_temp.ok(not wl_reserve_item('QTY00001', 1, 'devB'), 'a second giver can''t reserve it');
select pg_temp.ok(pg_temp.row('devA', 1)->>'mine' = 'true' and pg_temp.row('devB', 1)->>'mine' = 'false', 'mine as before');
select pg_temp.ok(jsonb_array_length(pg_temp.res('devA')) = 1, 'one line per wish');
-- a wish wanted 3 times
select pg_temp.ok(wl_reserve_count('QTY00001', 2, 'devA', 2), 'reserve 2 of 3');
select pg_temp.ok((pg_temp.row('devB', 2)->>'n')::int = 2 and pg_temp.row('devB', 2)->>'full' = 'false' and (pg_temp.row('devB', 2)->>'my')::int = 0, '2 of 3 reserved, not full');
select pg_temp.ok(not wl_reserve_count('QTY00001', 2, 'devB', 2), 'only 1 left: 2 refused');
select pg_temp.ok(wl_reserve_count('QTY00001', 2, 'devB', 1), 'the last one');
select pg_temp.ok(pg_temp.row('devC', 2)->>'full' = 'true' and (pg_temp.row('devC', 2)->>'n')::int = 3, 'now full');
select pg_temp.ok(jsonb_array_length(pg_temp.res('devA')) = 2, 'still one line per wish');
select pg_temp.ok(wl_reserve_count('QTY00001', 2, 'devA', 1), 'change my number down');
select pg_temp.ok((pg_temp.row('devA', 2)->>'my')::int = 1 and (pg_temp.row('devA', 2)->>'n')::int = 2, 'my number changed');
-- an older app reserves all that is still free
select pg_temp.ok(wl_reserve_item('QTY00001', 2, 'devOld'), 'old app reserves the rest');
select pg_temp.ok((pg_temp.row('devOld', 2)->>'my')::int = 1 and pg_temp.row('devOld', 2)->>'full' = 'true', 'the rest = 1');
select pg_temp.ok(not wl_reserve_item('QTY00001', 2, 'devOld2'), 'nothing left for another old app');
select wl_unreserve_item('QTY00001', 2, 'devOld');
select pg_temp.ok(pg_temp.row('devOld', 2)->>'mine' = 'false' and pg_temp.row('devOld', 2)->>'full' = 'false', 'old app undo frees its part');
select pg_temp.ok(wl_reserve_count('QTY00001', 2, 'devB', 0) and pg_temp.row('devB', 2)->>'mine' = 'false', '0 takes mine away');
-- a broken qty counts as 1
select pg_temp.ok(wl_reserve_count('QTY00001', 3, 'devA', 1) and not wl_reserve_count('QTY00001', 3, 'devB', 1), 'bad qty = 1');
select pg_temp.ok(not wl_reserve_count('QTY00001', 99, 'devA', 1), 'unknown wish refused');
select pg_temp.ok(not wl_reserve_count('QTY00001', 2, '', 1), 'no device refused');
do $$ begin perform wl_qty('QTY00001', 2); raise exception 'FAIL inner function callable';
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;
reset role;
\echo ALL QUANTITY CHECKS OK
