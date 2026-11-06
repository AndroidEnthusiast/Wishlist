-- Checks supabase-exchange.sql on a local PostgreSQL (not the real Supabase). See tests/README.md.
-- Needs a database where supabase-setup, -shared, -friends, -chat, -push (with a pretend net.http_post, see README),
-- -groups, -features and -exchange were run, and a role "anon". The last line prints ALL EXCHANGE CHECKS OK.
\set ON_ERROR_STOP 1
\set QUIET 1
create or replace function pg_temp.ok(c boolean, what text) returns void language plpgsql as $$
begin if c is not true then raise exception 'FAIL %', what; end if; end $$;
create or replace function pg_temp.fails(q text, what text) returns void language plpgsql as $$
begin execute q; raise exception 'FAIL % (was accepted)', what;
exception when others then if sqlerrm like 'FAIL%' then raise; end if; end $$;

-- ---------- 1. The solver against a brute-force search (as the database owner: wl_gx_solve isn't for anon) ----------

-- Does any valid draw exist? Every permutation of [ppl] without fixed points and banned pairs, by brute force.
create or replace function pg_temp.exists_draw(ppl text[], bans jsonb) returns boolean language plpgsql as $$
declare n int := array_length(ppl, 1); r boolean;
begin
  with recursive b(g, r) as (select e->>0, e->>1 from jsonb_array_elements(bans) e),
  p(depth, used, okk) as (
    select 0, array[]::int[], true
    union all
    select p.depth + 1, p.used || j, true from p, generate_series(1, n) j
    where p.depth < n and not j = any(p.used) and j <> p.depth + 1
      and not exists (select 1 from b where b.g = ppl[p.depth + 1] and b.r = ppl[j])
  )
  select exists (select 1 from p where depth = n) into r;
  return r;
end $$;

-- A result is a valid draw: a permutation, nobody draws themselves, no banned pair.
create or replace function pg_temp.valid(ppl text[], bans jsonb, res jsonb) returns boolean language plpgsql as $$
declare k text; seen text[] := '{}';
begin
  if not (res->>'ok')::boolean then return false; end if;
  if (select count(*) from jsonb_object_keys(res->'pairs')) <> array_length(ppl, 1) then return false; end if;
  for k in select jsonb_object_keys(res->'pairs') loop
    if not k = any(ppl) or not (res->'pairs'->>k) = any(ppl) then return false; end if;
    if res->'pairs'->>k = k then return false; end if;
    if (res->'pairs'->>k) = any(seen) then return false; end if;
    seen := seen || (res->'pairs'->>k);
    if exists (select 1 from jsonb_array_elements(bans) e where e->>0 = k and e->>1 = res->'pairs'->>k) then return false; end if;
  end loop;
  return true;
end $$;

do $$
declare ppl text[]; bans jsonb; n int; i int; j int; t int; res jsonb; ex boolean; okc int := 0; failc int := 0; dens float;
begin
  for t in 1..1500 loop
    n := 2 + (t % 6);                         -- 2 to 7 people
    ppl := (select array_agg('P' || s) from generate_series(1, n) s);
    dens := (t % 7) / 10.0;                   -- 0% to 60% of pairs banned
    bans := '[]'::jsonb;
    for i in 1..n loop for j in 1..n loop
      if i <> j and random() < dens then bans := bans || jsonb_build_array(jsonb_build_array(ppl[i], ppl[j])); end if;
    end loop; end loop;
    res := wl_gx_solve(ppl, bans, null);
    ex := pg_temp.exists_draw(ppl, bans);
    if ex then
      if not pg_temp.valid(ppl, bans, res) then raise exception 'FAIL solver missed or broke a draw: % %', ppl, bans; end if;
      okc := okc + 1;
    else
      if (res->>'ok')::boolean then raise exception 'FAIL solver claimed a draw that cannot exist'; end if;
      -- Hall: the stuck people have fewer others left than they are.
      if jsonb_array_length(res->'stuck') <= (res->>'left')::int then raise exception 'FAIL stuck set is not a real obstacle'; end if;
      failc := failc + 1;
    end if;
  end loop;
  perform pg_temp.ok(okc > 500 and failc > 100, format('random cases cover both outcomes (%s ok, %s impossible)', okc, failc));
end $$;

-- Every draw is different (random), and nobody ever draws themselves, in 300 draws of 4 people.
do $$
declare res jsonb; seen text[] := '{}'; s text; t int;
begin
  for t in 1..300 loop
    res := wl_gx_solve(array['A','B','C','D'], '[]', null);
    perform pg_temp.ok(pg_temp.valid(array['A','B','C','D'], '[]', res), 'plain draw valid');
    s := (res->'pairs'->>'A') || (res->'pairs'->>'B') || (res->'pairs'->>'C') || (res->'pairs'->>'D');
    if not s = any(seen) then seen := seen || s; end if;
  end loop;
  -- 4 people have 9 valid draws; 300 random draws should find (nearly) all of them.
  perform pg_temp.ok(array_length(seen, 1) >= 8, format('draws are random (%s different)', array_length(seen, 1)));
end $$;

-- A big exchange with many rules is drawn at once.
do $$
declare ppl text[]; bans jsonb := '[]'; i int; res jsonb; t0 timestamptz := clock_timestamp();
begin
  ppl := (select array_agg('P' || s) from generate_series(1, 60) s);
  for i in 1..60 loop
    bans := bans || jsonb_build_array(jsonb_build_array(ppl[i], ppl[1 + (i % 60)]), jsonb_build_array(ppl[1 + (i % 60)], ppl[i]));
  end loop;
  res := wl_gx_solve(ppl, bans, null);
  perform pg_temp.ok(pg_temp.valid(ppl, bans, res), '60 people with 120 rules');
  perform pg_temp.ok(clock_timestamp() - t0 < interval '5 seconds', 'big draw is quick');
end $$;

-- Repair keeps everything it can: A->L->R and A may draw R: only A changes.
do $$
declare res jsonb;
begin
  res := wl_gx_solve(array['A','B','C','R'], '[]', '{"B":"C","C":"A","R":"B"}');
  perform pg_temp.ok(res->'pairs'->>'A' = 'R' and res->'pairs'->>'B' = 'C' and res->'pairs'->>'C' = 'A' and res->'pairs'->>'R' = 'B', 'repair: only the one who lost their giftee changes');
  -- A may not draw R: the shortest change is found (2 people), and it is still valid.
  res := wl_gx_solve(array['A','B','C','R'], '[["A","R"]]', '{"B":"C","C":"A","R":"B"}');
  perform pg_temp.ok(pg_temp.valid(array['A','B','C','R'], '[["A","R"]]', res), 'repair with a rule is valid');
  perform pg_temp.ok((select count(*) from (values ('B','C'),('C','A'),('R','B')) v(g, r) where res->'pairs'->>g <> r) = 1, 'repair with a rule changes one more person, no more');
end $$;

set role anon;
select pg_temp.fails($q$select wl_gx_solve(array['a','b'], '[]', null)$q$, 'anon can''t call the solver');
select pg_temp.fails($q$select * from wl_gx$q$, 'anon can''t read exchanges');
select pg_temp.fails($q$select * from wl_gx_members$q$, 'anon can''t read the draw');
select pg_temp.fails($q$select * from wl_gx_msgs$q$, 'anon can''t read chats');
select pg_temp.fails($q$select wl_gx_json('x', 'y')$q$, 'anon can''t call the inner json');

-- ---------- 2. People ----------
-- Anna (organiser) is friends with Ben, Cas, Dee and Eve. Web is a browser member. Zed is nobody's friend.
select wl_pf_save('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','Anna') as code_a \gset
select wl_pf_save('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','Ben') as code_b \gset
select wl_pf_save('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','Cas') as code_c \gset
select wl_pf_save('DDDDDDDDDDDDDDDDDDDD','tokD_tokD_tokD_tokD_','Dee') as code_d \gset
select wl_pf_save('EEEEEEEEEEEEEEEEEEEE','tokE_tokE_tokE_tokE_','Eve') as code_e \gset
select wl_pf_save('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','Zed') as code_z \gset
select wl_fr_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_', :'code_a');
select wl_fr_add('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_', :'code_a');
select wl_fr_add('DDDDDDDDDDDDDDDDDDDD','tokD_tokD_tokD_tokD_', :'code_a');
select wl_fr_add('EEEEEEEEEEEEEEEEEEEE','tokE_tokE_tokE_tokE_', :'code_a');
select wl_fr_answer('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_', x, true)
  from unnest(array['BBBBBBBBBBBBBBBBBBBB','CCCCCCCCCCCCCCCCCCCC','DDDDDDDDDDDDDDDDDDDD','EEEEEEEEEEEEEEEEEEEE']) x;
select wl_create_list('LISTA001','ownerA_ownerA_ownerA','Anna list');
select wl_create_list('LISTB001','ownerB_ownerB_ownerB','Ben list');
select wl_create_list('LISTC001','ownerC_ownerC_ownerC','Cas list');
select wl_create_list('LISTD001','ownerD_ownerD_ownerD','Dee list');
select wl_create_list('LISTE001','ownerE_ownerE_ownerE','Eve list');
select wl_create_list('LISTW001','ownerW_ownerW_ownerW','Web list');
select wl_sync_list('LISTB001','ownerB_ownerB_ownerB','Ben list','[]','[{"id":1,"title":"Scarf","price":"20"}]');
select wl_set_link('LISTB001','ownerB_ownerB_ownerB', false);

-- ---------- 3. Making, inviting, joining ----------
\set A '''AAAAAAAAAAAAAAAAAAAA'',''tokA_tokA_tokA_tokA_'''
\set B '''BBBBBBBBBBBBBBBBBBBB'',''tokB_tokB_tokB_tokB_'''
\set C '''CCCCCCCCCCCCCCCCCCCC'',''tokC_tokC_tokC_tokC_'''
\set D '''DDDDDDDDDDDDDDDDDDDD'',''tokD_tokD_tokD_tokD_'''
\set E '''EEEEEEEEEEEEEEEEEEEE'',''tokE_tokE_tokE_tokE_'''
\set Z '''ZZZZZZZZZZZZZZZZZZZZ'',''tokZ_tokZ_tokZ_tokZ_'''
\set X '''GX00000000000000001'''

select pg_temp.fails($q$select wl_gx_create('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','LINKX000000000000001','Xmas','LISTB001','ownerA_ownerA_ownerA',25,'EUR',null,'xmas','')$q$, 'creating with someone else''s list');
select pg_temp.fails($q$select wl_gx_create('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','LINKX000000000000001','  ','LISTA001','ownerA_ownerA_ownerA',25,'EUR',null,'xmas','')$q$, 'creating without a name');
select wl_gx_create(:A, :X, 'linkx000000000000001', 'Family Xmas', 'LISTA001', 'ownerA_ownerA_ownerA', 25, 'EUR', null, 'weird', 'Ho ho ho') as g \gset
select pg_temp.ok((:'g'::jsonb)->>'organiser' = 'AAAAAAAAAAAAAAAAAAAA', 'the maker is the organiser');
select pg_temp.ok((:'g'::jsonb)->>'theme' = 'xmas', 'unknown theme becomes xmas');
select pg_temp.ok(((:'g'::jsonb)->>'budget')::numeric = 25 and (:'g'::jsonb)->>'currency' = 'EUR', 'budget and currency');
select pg_temp.ok(wl_gx_info('LINKX000000000000001')->>'organiser' = 'Anna', 'info by link');
select pg_temp.ok(wl_gx_info('nope') is null, 'unknown link');

select pg_temp.fails($q$select wl_gx_invite('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','ZZZZZZZZZZZZZZZZZZZZ')$q$, 'inviting a stranger');
select wl_gx_invite(:A, :X, x) from unnest(array['BBBBBBBBBBBBBBBBBBBB','CCCCCCCCCCCCCCCCCCCC','DDDDDDDDDDDDDDDDDDDD','EEEEEEEEEEEEEEEEEEEE']) x;
select pg_temp.ok(jsonb_array_length(wl_gx_state(:B)->'invites') = 1, 'Ben sees the invite');
select pg_temp.ok(wl_gx_get(:B, :X) is null, 'an invite alone doesn''t open the exchange');
select pg_temp.fails($q$select wl_gx_invite('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001','AAAAAAAAAAAAAAAAAAAA')$q$, 'only the organiser invites');

select wl_gx_decline(:E, :X);
select pg_temp.ok(wl_gx_state(:A)->'news'->0->>'kind' = 'declined' and wl_gx_state(:A)->'news'->0->>'who' = 'Eve', 'organiser told about No thanks');

select pg_temp.fails($q$select wl_gx_join('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','','GX00000000000000001','LISTA001','ownerB_ownerB_ownerB')$q$, 'joining with someone else''s list');
select pg_temp.ok(wl_gx_join(:B, '', :X, 'LISTB001', 'ownerB_ownerB_ownerB') is not null, 'Ben joins by invite');
select pg_temp.ok(wl_gx_join(:C, 'LINKX000000000000001', '', 'LISTC001', 'ownerC_ownerC_ownerC') is not null, 'Cas joins by link');
select pg_temp.ok(wl_gx_join(:Z, '', :X, 'LISTA001', 'ownerA_ownerA_ownerA') is null, 'Zed has no invite');
select pg_temp.fails($q$select wl_gx_get('BBBBBBBBBBBBBBBBBBBB','tokB_wrong_wrong_wro','GX00000000000000001')$q$, 'wrong token');

-- ---------- 4. Rules ----------
select pg_temp.fails($q$select wl_gx_excl_add('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001','AAAAAAAAAAAAAAAAAAAA','CCCCCCCCCCCCCCCCCCCC',false)$q$, 'only the organiser sets rules');
select pg_temp.ok((wl_gx_excl_add(:A, :X, 'AAAAAAAAAAAAAAAAAAAA', 'BBBBBBBBBBBBBBBBBBBB', false)->>'ok')::boolean, 'one-way rule Anna -/-> Ben');
-- Anna, Ben, Cas: with Anna -/-> Ben the only draw is A->C, C->B, B->A. Both ways B<->A would kill it.
select wl_gx_excl_add(:A, :X, 'BBBBBBBBBBBBBBBBBBBB', 'AAAAAAAAAAAAAAAAAAAA', false) as r \gset
select pg_temp.ok(not ((:'r'::jsonb)->>'ok')::boolean, 'impossible rule refused');
select pg_temp.ok((:'r'::jsonb)->'stuck' is not null and jsonb_array_length((:'r'::jsonb)->'stuck') > (((:'r'::jsonb)->>'left')::int), 'refusal names who is stuck');
select pg_temp.ok(jsonb_array_length(wl_gx_get(:A, :X)->'excl') = 1, 'refused rule not saved');
select pg_temp.ok(jsonb_array_length(wl_gx_get(:B, :X)->'excl') = 0, 'members never see rules');
-- With Dee in, the both-ways rule works.
select wl_gx_join(:D, '', :X, 'LISTD001', 'ownerD_ownerD_ownerD') is not null;
select pg_temp.ok((wl_gx_excl_add(:A, :X, 'AAAAAAAAAAAAAAAAAAAA', 'BBBBBBBBBBBBBBBBBBBB', true)->>'ok')::boolean, 'both-ways rule with 4 people');
select pg_temp.ok(jsonb_array_length(wl_gx_get(:A, :X)->'excl') = 2, 'both ways = two rules');
select pg_temp.ok(wl_gx_get(:A, :X)->'blocked' = 'null', 'not blocked');
-- Dee leaves before the draw: with 3 people A<->B can't hold; the organiser sees it at once.
select wl_gx_leave(:D, :X, '');
select pg_temp.ok(wl_gx_get(:A, :X)->'blocked'->'stuck' is not null, 'organiser sees the rules no longer work');
select wl_gx_draw(:A, :X, true) as r \gset
select pg_temp.ok(not ((:'r'::jsonb)->>'ok')::boolean and (:'r'::jsonb)->'stuck' is not null, 'draw refused while blocked (nothing drawn)');
select pg_temp.ok(not (wl_gx_get(:A, :X)->>'drawn')::boolean, 'still not drawn');
select wl_gx_excl_del(:A, :X, 'AAAAAAAAAAAAAAAAAAAA', 'BBBBBBBBBBBBBBBBBBBB', true);
select wl_gx_excl_add(:A, :X, 'AAAAAAAAAAAAAAAAAAAA', 'BBBBBBBBBBBBBBBBBBBB', false);
select wl_gx_invite(:A, :X, 'DDDDDDDDDDDDDDDDDDDD');

-- ---------- 5. The draw ----------
select pg_temp.fails($q$select wl_gx_draw('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001',true)$q$, 'only the organiser draws');
select wl_gx_draw(:A, :X, false) as r \gset
select pg_temp.ok((:'r'::jsonb)->'pending' = '["Dee"]', 'asks first: Dee hasn''t joined');
select pg_temp.ok((wl_gx_draw(:A, :X, true)->>'ok')::boolean, 'draw without Dee');
select pg_temp.ok((wl_gx_get(:A, :X)->>'drawn')::boolean, 'drawn');
reset role;
select pg_temp.ok((select count(*) from wl_gx_members where gx_id = :X and gives_to is not null) = 3, 'everyone has someone');
select pg_temp.ok(not exists (select 1 from wl_gx_members where gx_id = :X and gives_to = profile), 'nobody has themselves');
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = :X) = 3, 'everyone is drawn once');
select pg_temp.ok(not exists (select 1 from wl_gx_members m join wl_gx_excl e on e.gx_id = m.gx_id and e.giver = m.profile and e.receiver = m.gives_to), 'rules kept');
select gives_to as a_gives from wl_gx_members where gx_id = :X and profile = 'AAAAAAAAAAAAAAAAAAAA' \gset
select gives_to as b_gives from wl_gx_members where gx_id = :X and profile = 'BBBBBBBBBBBBBBBBBBBB' \gset
select gives_to as c_gives from wl_gx_members where gx_id = :X and profile = 'CCCCCCCCCCCCCCCCCCCC' \gset
set role anon;
select pg_temp.ok(wl_gx_state(:B)->'news'->0->>'kind' = 'drawn', 'members told: names drawn');
select pg_temp.fails($q$select wl_gx_excl_add('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','CCCCCCCCCCCCCCCCCCCC','BBBBBBBBBBBBBBBBBBBB',false)$q$, 'rules lock at the draw');
-- Nobody sees pairs: not in anyone's json before they open, never somebody else's.
select pg_temp.ok(wl_gx_get(:A, :X)::text not like '%gives_to%' and wl_gx_get(:A, :X)->'me'->'giftee' = 'null', 'organiser sees no pairs');
select pg_temp.ok((select bool_and(m->>'gave_to' is null) from jsonb_array_elements(wl_gx_get(:A, :X)->'members') m), 'no gave_to before reveal');
select pg_temp.ok((select bool_and((m->>'seen')::boolean = false) from jsonb_array_elements(wl_gx_get(:A, :X)->'members') m), 'draw check: nobody opened yet');
select pg_temp.ok((select bool_and(m->'seen' = 'null') from jsonb_array_elements(wl_gx_get(:B, :X)->'members') m), 'members don''t get the draw check');
select pg_temp.ok(wl_gx_list(:B, :X) is null, 'no giftee list before opening');
select wl_gx_open(:B, :X) as o \gset
select pg_temp.ok((:'o'::jsonb)->'me'->'giftee'->>'id' = :'b_gives', 'Ben opens his name');
select pg_temp.ok((select (m->>'seen')::boolean from jsonb_array_elements(wl_gx_get(:A, :X)->'members') m where m->>'id' = 'BBBBBBBBBBBBBBBBBBBB'), 'draw check shows Ben opened');
select pg_temp.ok(wl_gx_list(:B, :X)->>'code' = (select case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then 'LISTA001' else 'LISTC001' end), 'Ben gets his giftee''s list');
select pg_temp.ok(wl_gx_get(:B, :X)->'me'->>'has_santa' = 'true', 'Ben has a Santa');

-- ---------- 6. Anonymous chat ----------
select wl_push_set(:A, 'pushA'), wl_push_set(:C, 'pushC');
select wl_gx_send(:B, :X, 'giftee', 'Which size?') as m1 \gset
select pg_temp.ok(:m1 > 0, 'Ben writes to his giftee');
-- The giftee reads it in "Your Secret Santa"; the answer goes back; no profile id of the Santa anywhere.
select pg_temp.ok(
  (case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then wl_gx_chat(:A, :X, 'santa', 0) else wl_gx_chat(:C, :X, 'santa', 0) end)->0->>'body' = 'Which size?',
  'giftee reads the Santa''s question');
select pg_temp.ok(
  (case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then wl_gx_chat(:A, :X, 'santa', 0) else wl_gx_chat(:C, :X, 'santa', 0) end)::text not like '%BBBBBBBB%',
  'the Santa stays anonymous');
select pg_temp.ok(
  (case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then wl_gx_get(:A, :X) else wl_gx_get(:C, :X) end)->'me'->>'unread_santa' = '1', 'unread count');
select case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then wl_gx_send(:A, :X, 'santa', 'M please') else wl_gx_send(:C, :X, 'santa', 'M please') end;
select pg_temp.ok(wl_gx_chat(:B, :X, 'giftee', 0)->1->>'body' = 'M please' and (wl_gx_chat(:B, :X, 'giftee', 0)->1->>'mine')::boolean = false, 'Ben gets the answer');
select pg_temp.ok((wl_gx_chat(:B, :X, 'giftee', 0)->0->>'mine')::boolean, 'Ben''s own message is his');
-- The third person can't read it.
select pg_temp.ok(
  (case :'b_gives' when 'AAAAAAAAAAAAAAAAAAAA' then wl_gx_chat(:C, :X, 'santa', 0) else wl_gx_chat(:A, :X, 'santa', 0) end)::text not like '%Which size%',
  'someone else can''t read it');
select pg_temp.fails($q$select wl_gx_chat('ZZZZZZZZZZZZZZZZZZZZ','tokZ_tokZ_tokZ_tokZ_','GX00000000000000001','santa',0)$q$, 'strangers can''t read');
select pg_temp.fails($q$select wl_gx_send('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001','giftee','   ')$q$, 'empty message refused');
-- Cas hasn't opened his name: he can't write to his giftee yet, but can write to his Santa.
select pg_temp.ok(wl_gx_send(:C, :X, 'giftee', 'hi') = 0, 'no giftee chat before opening');
select wl_gx_chat_seen(:B, :X, 'giftee', 999999);
reset role;
select pg_temp.ok((select count(*) from net.calls where body ? 'gx') >= 1, 'a new message asks the push function with {"gx": id}');
select pg_temp.ok(not exists (select 1 from net.calls where body ? 'id' and body->>'id' = :'m1' and body ? 'gx'), 'push body has no plain id (old push functions refuse it)');
set role anon;

-- ---------- 7. A late joiner ----------
-- Dee joins after the draw: slotted in next to someone who hasn't opened (Anna or Cas), nobody who looked changes.
select wl_gx_join(:D, '', :X, 'LISTD001', 'ownerD_ownerD_ownerD') is not null;
reset role;
select pg_temp.ok((select count(*) from wl_gx_members where gx_id = :X and gives_to is not null) = 4, 'Dee slotted in');
select pg_temp.ok((select gives_to from wl_gx_members where gx_id = :X and profile = 'BBBBBBBBBBBBBBBBBBBB') = :'b_gives', 'Ben (who looked) keeps his name');
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = :X) = 4 and not exists (select 1 from wl_gx_members where gx_id = :X and gives_to = profile), 'still a valid draw');
set role anon;
-- Everyone looks; then Eve joins by link: she waits for the organiser.
select wl_gx_open(:A, :X) is not null, wl_gx_open(:C, :X) is not null, wl_gx_open(:D, :X) is not null;
select wl_gx_news_seen(:A, 999999);
select wl_gx_join(:E, 'LINKX000000000000001', '', 'LISTE001', 'ownerE_ownerE_ownerE') -> 'me' ->> 'waiting' as w \gset
select pg_temp.ok(:'w' = 'true', 'Eve waits: everyone had looked');
select pg_temp.ok(wl_gx_state(:A)->'news'->0->>'kind' = 'waiting' and wl_gx_state(:A)->'news'->0->>'who' = 'Eve', 'organiser asked about Eve');
select pg_temp.ok((select (m->>'waiting')::boolean from jsonb_array_elements(wl_gx_get(:A, :X)->'members') m where m->>'id' = 'EEEEEEEEEEEEEEEEEEEE'), 'Eve shown as waiting');
reset role;
create temp table before_add as select profile, gives_to from wl_gx_members where gx_id = :X;
grant select on before_add to anon;
set role anon;
select wl_gx_add_late(:A, :X, 'EEEEEEEEEEEEEEEEEEEE');
reset role;
select pg_temp.ok((select count(*) from wl_gx_members m join before_add b using (profile) where m.gx_id = :X and m.gives_to is distinct from b.gives_to) = 2, 'adding Eve changes one person (and Eve)');
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = :X) = 5 and not exists (select 1 from wl_gx_members where gx_id = :X and gives_to = profile), 'valid with 5');
select pg_temp.ok(not exists (select 1 from wl_gx_members m join wl_gx_excl e on e.gx_id = m.gx_id and e.giver = m.profile and e.receiver = m.gives_to), 'rules kept after slotting in');
select profile as changed from wl_gx_members m join before_add b using (profile) where m.gx_id = :X and b.gives_to is not null and m.gives_to is distinct from b.gives_to \gset
set role anon;
select pg_temp.ok(wl_gx_state(:'changed', (select case :'changed' when 'AAAAAAAAAAAAAAAAAAAA' then 'tokA_tokA_tokA_tokA_' when 'BBBBBBBBBBBBBBBBBBBB' then 'tokB_tokB_tokB_tokB_' when 'CCCCCCCCCCCCCCCCCCCC' then 'tokC_tokC_tokC_tokC_' else 'tokD_tokD_tokD_tokD_' end))->>'news' like '%changed%', 'the one whose name changed is told');

-- ---------- 8. Leaving and removing after the draw ----------
reset role;
delete from before_add;
insert into before_add select profile, gives_to from wl_gx_members where gx_id = :X;
select profile as dee_santa from wl_gx_members where gx_id = :X and gives_to = 'DDDDDDDDDDDDDDDDDDDD' \gset
set role anon;
select wl_gx_leave(:D, :X, '');
reset role;
select pg_temp.ok((select count(*) from wl_gx_members m join before_add b using (profile) where m.gx_id = :X and m.gives_to is distinct from b.gives_to) <= 2, 'Dee leaves: at most two names change');
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = :X) = 4 and (select count(*) from wl_gx_members where gx_id = :X and gives_to is not null) = 4, 'valid after leaving');
select pg_temp.ok(not exists (select 1 from wl_gx_members where gx_id = :X and gives_to = profile), 'nobody has themselves after leaving');
select pg_temp.ok(not exists (select 1 from wl_gx_members m join wl_gx_excl e on e.gx_id = m.gx_id and e.giver = m.profile and e.receiver = m.gives_to), 'rules kept after leaving');
select pg_temp.ok((select not seen from wl_gx_members where gx_id = :X and profile = :'dee_santa'), 'Dee''s Santa opens a new name');
-- The organiser removes Eve: she is told.
set role anon;
select pg_temp.fails($q$select wl_gx_remove('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001','EEEEEEEEEEEEEEEEEEEE')$q$, 'only the organiser removes');
select wl_gx_remove(:A, :X, 'EEEEEEEEEEEEEEEEEEEE');
select pg_temp.ok(wl_gx_state(:E)->>'news' like '%removed%', 'Eve told she was removed');
select pg_temp.ok(wl_gx_get(:E, :X) is null, 'Eve no longer sees it');
reset role;
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = :X) = 3 and not exists (select 1 from wl_gx_members where gx_id = :X and gives_to = profile), 'valid after removing');
set role anon;

-- ---------- 9. Settings, handing over, reveal ----------
select wl_gx_news_seen(:B, 999999);
select wl_gx_settings(:A, :X, 'Family Xmas', 30, 'EUR', (current_date + 10), 'sint', 'Bring it wrapped');
select pg_temp.ok(wl_gx_state(:B)->'news'->0->>'kind' = 'settings', 'members told when budget/date change');
select pg_temp.ok(not wl_gx_reveal(:A, :X), 'no reveal before the exchange date');
select wl_gx_settings(:A, :X, 'Family Xmas', 30, 'EUR', (current_date - 1), 'sint', 'Bring it wrapped');
select pg_temp.fails($q$select wl_gx_reveal('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000001')$q$, 'only the organiser reveals');
select pg_temp.ok(wl_gx_reveal(:A, :X), 'reveal after the date');
select pg_temp.ok((select bool_and(m->>'gave_to' is not null) from jsonb_array_elements(wl_gx_get(:B, :X)->'members') m), 'everyone sees who had whom');
select pg_temp.fails($q$select wl_gx_handover('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','ZZZZZZZZZZZZZZZZZZZZ')$q$, 'hand over only to a member');
select wl_gr_web_profile('VVVVVVVVVVVVVVVVVVVV', 'tokV_tokV_tokV_tokV_', 'Vic');
select wl_create_list('LISTV001','ownerV_ownerV_ownerV','Vic list');
select wl_gx_join('VVVVVVVVVVVVVVVVVVVV', 'tokV_tokV_tokV_tokV_', 'LINKX000000000000001', '', 'LISTV001', 'ownerV_ownerV_ownerV') is not null;
select pg_temp.fails($q$select wl_gx_handover('AAAAAAAAAAAAAAAAAAAA','tokA_tokA_tokA_tokA_','GX00000000000000001','VVVVVVVVVVVVVVVVVVVV')$q$, 'a browser member can''t organise');
select wl_gx_leave('VVVVVVVVVVVVVVVVVVVV', 'tokV_tokV_tokV_tokV_', :X, '');
select wl_gx_handover(:A, :X, 'CCCCCCCCCCCCCCCCCCCC');
select pg_temp.ok(wl_gx_get(:A, :X)->>'organiser' = 'CCCCCCCCCCCCCCCCCCCC', 'handed over');
select pg_temp.ok(wl_gx_state(:C)->>'news' like '%organiser%', 'new organiser told');
select pg_temp.fails($q$select wl_gx_leave('CCCCCCCCCCCCCCCCCCCC','tokC_tokC_tokC_tokC_','GX00000000000000001','')$q$, 'organiser must hand over before leaving');

-- ---------- 10. A browser member moves to the app ----------
select wl_gx_create(:A, 'GX00000000000000002', 'LINKX000000000000002', 'Office', 'LISTA001', 'ownerA_ownerA_ownerA', null, '', null, 'office', '') is not null;
select wl_gr_web_profile('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', 'Wim');
select wl_gr_web_list('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', 'LISTW001', 'ownerW_ownerW_ownerW');
select wl_gx_join('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', 'LINKX000000000000002', '', 'LISTW001', 'ownerW_ownerW_ownerW') is not null;
select pg_temp.ok(wl_gx_web_me('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_')->'exchanges'->0->>'name' = 'Office', 'browser member''s exchanges');
select wl_gx_join(:B, 'LINKX000000000000002', '', 'LISTB001', 'ownerB_ownerB_ownerB') is not null;
select wl_gx_excl_add(:A, 'GX00000000000000002', 'WWWWWWWWWWWWWWWWWWWW', 'BBBBBBBBBBBBBBBBBBBB', false);
select (wl_gx_draw(:A, 'GX00000000000000002', true)->>'ok')::boolean;
select wl_gx_open('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', 'GX00000000000000002') is not null;
select wl_gx_send('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', 'GX00000000000000002', 'giftee', 'hello from the browser');
select wl_gx_takeover('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', :E);
select wl_gr_takeover('WWWWWWWWWWWWWWWWWWWW', 'tokW_tokW_tokW_tokW_', '', :E, '');
reset role;
select pg_temp.ok((select count(*) from wl_gx_members where gx_id = 'GX00000000000000002') = 3, 'the app took the browser member''s place');
select pg_temp.ok((select count(distinct gives_to) from wl_gx_members where gx_id = 'GX00000000000000002') = 3 and not exists (select 1 from wl_gx_members where gx_id = 'GX00000000000000002' and gives_to = profile), 'draw intact after the move');
select pg_temp.ok((select seen from wl_gx_members where gx_id = 'GX00000000000000002' and profile = 'EEEEEEEEEEEEEEEEEEEE'), 'opened name kept');
select pg_temp.ok(exists (select 1 from wl_gx_excl where gx_id = 'GX00000000000000002' and giver = 'EEEEEEEEEEEEEEEEEEEE' and receiver = 'BBBBBBBBBBBBBBBBBBBB'), 'rules moved');
set role anon;
select pg_temp.ok(wl_gx_chat(:E, 'GX00000000000000002', 'giftee', 0)->0->>'body' = 'hello from the browser', 'chat moved');

-- ---------- 11. Two people left, then one ----------
select wl_gx_leave(:B, 'GX00000000000000002', '');
reset role;
select pg_temp.ok((select count(*) from wl_gx_members where gx_id = 'GX00000000000000002' and gives_to is not null) = 2 and not exists (select 1 from wl_gx_members where gx_id = 'GX00000000000000002' and gives_to = profile), 'two left: they draw each other');
set role anon;
select wl_gx_leave(:E, 'GX00000000000000002', '');
select pg_temp.ok(not (wl_gx_get(:A, 'GX00000000000000002')->>'drawn')::boolean, 'one left: the draw is undone, nothing broken');

-- ---------- 12. Few people, cancel ----------
select wl_gx_create(:A, 'GX00000000000000003', 'LINKX000000000000003', 'Tiny', 'LISTA001', 'ownerA_ownerA_ownerA', null, '', null, 'plain', '') is not null;
select pg_temp.ok((wl_gx_draw(:A, 'GX00000000000000003', true)->>'few')::boolean, 'at least 3 people to draw');
select wl_gx_join(:B, 'LINKX000000000000003', '', 'LISTB001', 'ownerB_ownerB_ownerB') is not null;
select pg_temp.fails($q$select wl_gx_cancel('BBBBBBBBBBBBBBBBBBBB','tokB_tokB_tokB_tokB_','GX00000000000000003')$q$, 'only the organiser cancels');
select wl_gx_news_seen(:B, 999999);
select wl_gx_cancel(:A, 'GX00000000000000003');
select pg_temp.ok(wl_gx_state(:B)->'news'->0->>'kind' = 'cancelled' and wl_gx_state(:B)->'news'->0->>'name' = 'Tiny', 'members told it was cancelled');
select pg_temp.ok(wl_gx_info('LINKX000000000000003') is null, 'link stops working');

\echo ALL EXCHANGE CHECKS OK
