-- Gift exchange (Secret Santa), version 2.129. Run this in Supabase: SQL Editor -> New query -> paste everything -> Run.
-- Run it AFTER supabase-setup.sql, supabase-friends.sql, supabase-chat.sql, supabase-push.sql and supabase-groups.sql
-- (you already did those). Safe to run again later; it won't delete anything.
--
-- How it works (decided with the owner, docs/DECISIONS.md "Side menu, Gifts I reserved and Gift exchange"):
--  * An exchange is a few people who each draw one other person to buy a gift for. Everyone joins with one of their
--    wishlists; only the person who drew you can open it (and reserve on it, like any friend).
--  * The organiser invites friends, and anyone with the exchange link can join (people without the app join in the
--    browser, web/exchange.html, with a browser profile from supabase-groups.sql).
--  * Exclusions ("Anna doesn't draw Ben"): only the organiser sets and sees them, and only before the draw. Every rule is
--    checked the moment it is set: a rule that would make a draw impossible is refused with who would be stuck.
--  * The draw happens here, on the server, all at once (wl_gx_solve: a complete search, so when a valid draw exists it
--    is always found; nobody ever draws themselves). Nobody can read the pairs, not even the organiser, until the
--    organiser presses Reveal after the exchange date.
--  * After the draw: someone who leaves is repaired quietly (whoever had them gets their giftee, or the smallest part
--    possible is redrawn); a late joiner is slotted in next to someone who hasn't opened their name yet.
--  * Anonymous chat: you and the person you drew, and you and your Secret Santa. Only those two can read it (checked
--    here with each person's secret token); the Santa's name is never sent. Not end-to-end encrypted (owner agreed).

-- ---------- tables ----------

create table if not exists wl_gx (
  id text primary key,
  link text not null unique,              -- the secret in the exchange link (?x=...)
  name text not null default '',
  organiser text not null references wl_profiles(id) on delete cascade,
  budget numeric(10, 2),                  -- null: no budget
  currency text not null default '',      -- "EUR", "GBP", ... (the organiser's App settings currency)
  day date,                               -- the exchange date, or null
  theme text not null default 'xmas',     -- xmas, sint, bday, office, plain
  message text not null default '',       -- the organiser's welcome message
  photo text not null default '',         -- a small JPEG as base64 text, or empty (then the theme's drawn cover)
  photo_v bigint not null default 0,
  drawn_at timestamptz,                   -- null: not drawn yet
  revealed boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists wl_gx_members (
  gx_id text not null references wl_gx(id) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  list_code text references wl_lists(code) on delete set null,   -- the wishlist they joined with
  joined_at timestamptz not null default clock_timestamp(),
  gives_to text,                          -- whom they drew (null before the draw, or while waiting to be slotted in)
  seen boolean not null default false,    -- they opened the name they drew
  primary key (gx_id, profile)
);
create index if not exists wl_gx_members_profile on wl_gx_members (profile);

create table if not exists wl_gx_invites (
  gx_id text not null references wl_gx(id) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  by_profile text not null references wl_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (gx_id, profile)
);
create index if not exists wl_gx_invites_profile on wl_gx_invites (profile);

-- One row per one-way rule: giver may not draw receiver. "Both ways" is two rows.
create table if not exists wl_gx_excl (
  gx_id text not null,
  giver text not null,
  receiver text not null,
  primary key (gx_id, giver, receiver),
  foreign key (gx_id, giver) references wl_gx_members(gx_id, profile) on delete cascade,
  foreign key (gx_id, receiver) references wl_gx_members(gx_id, profile) on delete cascade
);

-- News a phone (or browser) must be told once.
create table if not exists wl_gx_news (
  id bigserial primary key,
  profile text not null references wl_profiles(id) on delete cascade,
  kind text not null,      -- drawn, changed, removed, cancelled, settings, organiser, declined, waiting, rules
  gx_id text not null default '',
  gx_name text not null default '',
  who text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists wl_gx_news_profile on wl_gx_news (profile);

-- The anonymous chats. A chat belongs to one pair (santa -> giftee); when a draw is repaired the pair changes and the
-- old chat is no longer shown to anyone.
create table if not exists wl_gx_msgs (
  id bigserial primary key,
  gx_id text not null references wl_gx(id) on delete cascade,
  santa text not null,
  giftee text not null,
  from_santa boolean not null,
  body text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz,
  pushed boolean not null default false
);
create index if not exists wl_gx_msgs_pair on wl_gx_msgs (gx_id, santa, giftee);

alter table wl_gx enable row level security;
alter table wl_gx_members enable row level security;
alter table wl_gx_invites enable row level security;
alter table wl_gx_excl enable row level security;
alter table wl_gx_news enable row level security;
alter table wl_gx_msgs enable row level security;

-- ---------- the draw ----------

-- Finds a draw for [p_people]: everyone gives to exactly one other person and receives from exactly one, nobody draws
-- themselves, and no pair in [p_ban] ([[giver, receiver], ...]) is used. [p_start] ({giver: receiver}) is kept as far
-- as possible: only the people who must change, change (used to repair a draw).
-- This is a complete search (augmenting paths, Kuhn/Berge): if any valid draw exists, it is found, in one go, without
-- ever starting over. Candidates are tried in a random order, so every draw is different.
-- Returns {"ok": true, "pairs": {giver: receiver}} or, when no valid draw exists at all,
-- {"ok": false, "stuck": [people], "left": n}: these people would only have n others left to draw between them.
create or replace function wl_gx_solve(p_people text[], p_ban jsonb, p_start jsonb)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
  n int := coalesce(array_length(p_people, 1), 0);
  adj boolean[];
  ml int[];      -- giver i -> receiver index (0: none)
  mr int[];      -- receiver j -> giver index (0: none)
  ord int[];
  q int[];
  par int[];
  vis boolean[];
  head int; g int; j int; u int; v int; prev int; found int;
  i int; k int; e jsonb; a int; b int;
  pairs jsonb := '{}'::jsonb;
begin
  if n < 2 then
    return jsonb_build_object('ok', false, 'stuck', to_jsonb(coalesce(p_people, '{}'::text[])), 'left', 0);
  end if;
  adj := array_fill(true, array[n * n]);
  for i in 1..n loop adj[(i - 1) * n + i] := false; end loop;
  for e in select x from jsonb_array_elements(coalesce(p_ban, '[]'::jsonb)) x loop
    a := array_position(p_people, e->>0);
    b := array_position(p_people, e->>1);
    if a is not null and b is not null then adj[(a - 1) * n + b] := false; end if;
  end loop;
  ml := array_fill(0, array[n]);
  mr := array_fill(0, array[n]);
  -- Keep the pairs we started with (when they are still allowed).
  if p_start is not null then
    for i in 1..n loop
      b := array_position(p_people, p_start->>p_people[i]);
      if b is not null and adj[(i - 1) * n + b] and mr[b] = 0 then ml[i] := b; mr[b] := i; end if;
    end loop;
  end if;
  -- Every giver without a receiver gets one through the shortest augmenting path (breadth first), so as few existing
  -- pairs as possible change.
  for u in select x from unnest((select array_agg(s order by random()) from generate_series(1, n) s)) x loop
    if ml[u] <> 0 then continue; end if;
    q := array[u];
    par := array_fill(0, array[n]);
    vis := array_fill(false, array[n]);
    head := 1;
    found := 0;
    while head <= array_length(q, 1) and found = 0 loop
      g := q[head];
      head := head + 1;
      ord := (select array_agg(s order by random()) from generate_series(1, n) s);
      foreach j in array ord loop
        if adj[(g - 1) * n + j] and not vis[j] then
          vis[j] := true;
          par[j] := g;
          if mr[j] = 0 then found := j; exit; end if;
          q := q || mr[j];
        end if;
      end loop;
    end loop;
    if found = 0 then
      -- Hall's rule: the givers in q can only reach the receivers we visited, one too few.
      k := 0;
      for j in 1..n loop if vis[j] then k := k + 1; end if; end loop;
      return jsonb_build_object('ok', false, 'left', k,
        'stuck', (select jsonb_agg(p_people[x]) from unnest(q) x));
    end if;
    v := found;
    loop
      g := par[v];
      prev := ml[g];
      ml[g] := v;
      mr[v] := g;
      exit when g = u;
      v := prev;
    end loop;
  end loop;
  for i in 1..n loop
    pairs := pairs || jsonb_build_object(p_people[i], p_people[ml[i]]);
  end loop;
  return jsonb_build_object('ok', true, 'pairs', pairs);
end $$;

-- The exchange's rules as [[giver, receiver], ...].
create or replace function wl_gx_bans(p_gx text)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_array(giver, receiver)), '[]'::jsonb) from wl_gx_excl where gx_id = p_gx
$$;

-- The members who take part in the draw: before the draw everyone; after it, those who have a giftee.
create or replace function wl_gx_people(p_gx text)
returns text[] language sql stable security definer set search_path = public as $$
  select coalesce(array_agg(m.profile order by m.joined_at), '{}'::text[]) from wl_gx_members m join wl_gx x on x.id = m.gx_id
  where m.gx_id = p_gx and (x.drawn_at is null or m.gives_to is not null)
$$;

-- "Anna, Ben and Cas would only have 2 people left to draw." from a solve that failed.
create or replace function wl_gx_stuck_names(p_res jsonb)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('ok', false, 'left', coalesce((p_res->>'left')::int, 0),
    'stuck', coalesce((select jsonb_agg(coalesce(nullif(p.name, ''), '?') order by p.name)
                       from jsonb_array_elements_text(p_res->'stuck') s join wl_profiles p on p.id = s), '[]'::jsonb))
$$;

-- ---------- helpers (not callable by the app) ----------

create or replace function wl_gx_is_member(p_gx text, p_profile text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from wl_gx_members where gx_id = p_gx and profile = p_profile)
$$;

create or replace function wl_gx_check_member(p_id text, p_token text, p_gx text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_gx_is_member(p_gx, p_id) then raise exception 'not a member'; end if;
end $$;

create or replace function wl_gx_check_org(p_id text, p_token text, p_gx text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not exists (select 1 from wl_gx where id = p_gx and organiser = p_id) then raise exception 'not the organiser'; end if;
end $$;

create or replace function wl_gx_news_add(p_profile text, p_kind text, p_gx text, p_who text)
returns void language sql security definer set search_path = public as $$
  insert into wl_gx_news (profile, kind, gx_id, gx_name, who)
    select p_profile, p_kind, x.id, x.name, coalesce(p_who, '') from wl_gx x where x.id = p_gx
$$;

-- Who drew [p_profile] (their Secret Santa), or null.
create or replace function wl_gx_santa_of(p_gx text, p_profile text)
returns text language sql stable security definer set search_path = public as $$
  select profile from wl_gx_members where gx_id = p_gx and gives_to = p_profile limit 1
$$;

-- Unread messages for [p_me] in one of their two chats.
create or replace function wl_gx_unread(p_gx text, p_me text, p_side text)
returns int language sql stable security definer set search_path = public as $$
  select count(*)::int from wl_gx_msgs m
  where m.gx_id = p_gx and m.read_at is null and (
    (p_side = 'giftee' and m.santa = p_me and not m.from_santa
       and m.giftee = (select gives_to from wl_gx_members where gx_id = p_gx and profile = p_me))
    or (p_side = 'santa' and m.giftee = p_me and m.from_santa and m.santa = wl_gx_santa_of(p_gx, p_me)))
$$;

-- One exchange as member [p_me] sees it.
create or replace function wl_gx_json(p_gx text, p_me text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare x wl_gx; me wl_gx_members; org boolean; v jsonb; blocked jsonb := null; ppl text[]; r jsonb;
begin
  select * into x from wl_gx where id = p_gx;
  if x.id is null then return null; end if;
  select * into me from wl_gx_members where gx_id = p_gx and profile = p_me;
  org := x.organiser = p_me;
  -- Before the draw, the organiser learns at once when the rules no longer allow a draw (someone left).
  if org and x.drawn_at is null then
    ppl := wl_gx_people(p_gx);
    if coalesce(array_length(ppl, 1), 0) >= 3 then
      r := wl_gx_solve(ppl, wl_gx_bans(p_gx), null);
      if not (r->>'ok')::boolean then blocked := wl_gx_stuck_names(r); end if;
    end if;
  end if;
  v := jsonb_build_object(
    'id', x.id, 'link', x.link, 'name', x.name, 'organiser', x.organiser,
    'organiser_name', (select name from wl_profiles where id = x.organiser),
    'budget', x.budget, 'currency', x.currency, 'day', x.day, 'theme', x.theme, 'message', x.message,
    'pv', x.photo_v, 'drawn', x.drawn_at is not null, 'revealed', x.revealed,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', p.id, 'name', p.name, 'pv', p.photo_v, 'web', p.web,
        'code', case when p.web then '' else p.code end,
        'friend', exists (select 1 from wl_friends f where f.accepted
                  and ((f.a = p_me and f.b = p.id) or (f.a = p.id and f.b = p_me))),
        -- The draw check (organiser only): has this person opened their name? No pairs.
        'seen', case when org then m.seen else null end,
        'waiting', x.drawn_at is not null and m.gives_to is null,
        -- After the reveal everyone sees who had whom.
        'gave_to', case when x.revealed then m.gives_to else null end
      ) order by m.joined_at)
      from wl_gx_members m join wl_profiles p on p.id = m.profile
      where m.gx_id = x.id), '[]'::jsonb),
    'invited', coalesce((select jsonb_agg(jsonb_build_object('id', i.profile, 'name', p.name) order by i.created_at)
      from wl_gx_invites i join wl_profiles p on p.id = i.profile where i.gx_id = x.id), '[]'::jsonb),
    'excl', case when org then wl_gx_bans(p_gx) else '[]'::jsonb end,
    'blocked', blocked,
    'me', jsonb_build_object(
      'list', wl_gr_list_card(me.list_code),
      'seen', coalesce(me.seen, false),
      'waiting', x.drawn_at is not null and me.gives_to is null,
      'has_santa', wl_gx_santa_of(p_gx, p_me) is not null,
      'giftee', case when me.seen and me.gives_to is not null then (
        select jsonb_build_object('id', p.id, 'name', p.name, 'pv', p.photo_v, 'web', p.web, 'list', wl_gr_list_card(g.list_code))
        from wl_gx_members g join wl_profiles p on p.id = g.profile where g.gx_id = p_gx and g.profile = me.gives_to)
        else null end,
      'unread_giftee', case when me.seen then wl_gx_unread(p_gx, p_me, 'giftee') else 0 end,
      'unread_santa', wl_gx_unread(p_gx, p_me, 'santa')
    )
  );
  return v;
end $$;

-- After someone left: the draw is repaired with as few changes as possible. Givers whose giftee changed open their
-- name again; those who had already looked are told. Never fails: when the rules can't all be kept any more, it is
-- repaired without them and the organiser is told.
create or replace function wl_gx_repair(p_gx text)
returns void language plpgsql security definer set search_path = public as $$
declare ppl text[]; start jsonb; r jsonb; k text; old wl_gx_members;
begin
  ppl := wl_gx_people(p_gx);
  if coalesce(array_length(ppl, 1), 0) < 2 then
    -- One person left can't give to anyone: the draw is undone; the organiser can invite more and draw again.
    update wl_gx_members set gives_to = null, seen = false where gx_id = p_gx;
    update wl_gx set drawn_at = null, revealed = false where id = p_gx;
    return;
  end if;
  select coalesce(jsonb_object_agg(profile, gives_to), '{}'::jsonb) into start
    from wl_gx_members where gx_id = p_gx and profile = any(ppl) and gives_to = any(ppl);
  r := wl_gx_solve(ppl, wl_gx_bans(p_gx), start);
  if not (r->>'ok')::boolean then
    r := wl_gx_solve(ppl, '[]'::jsonb, start);
    perform wl_gx_news_add((select organiser from wl_gx where id = p_gx), 'rules', p_gx, '');
  end if;
  for k in select jsonb_object_keys(r->'pairs') loop
    select * into old from wl_gx_members where gx_id = p_gx and profile = k;
    if old.gives_to is distinct from r->'pairs'->>k then
      update wl_gx_members set gives_to = r->'pairs'->>k, seen = false where gx_id = p_gx and profile = k;
      if old.seen then perform wl_gx_news_add(k, 'changed', p_gx, ''); end if;
    end if;
  end loop;
end $$;

-- A member joined after the draw: slotted in next to someone who hasn't opened their name yet (nobody notices).
-- Returns true when done; false when everyone has looked (then the organiser decides, wl_gx_add_late).
create or replace function wl_gx_slot(p_gx text, p_new text, p_any boolean)
returns boolean language plpgsql security definer set search_path = public as $$
declare h wl_gx_members;
begin
  select m.* into h from wl_gx_members m
  where m.gx_id = p_gx and m.profile <> p_new and m.gives_to is not null and m.gives_to <> p_new
    and (p_any or not m.seen)
    and not exists (select 1 from wl_gx_excl e where e.gx_id = p_gx and e.giver = m.profile and e.receiver = p_new)
    and not exists (select 1 from wl_gx_excl e where e.gx_id = p_gx and e.giver = p_new and e.receiver = m.gives_to)
  order by m.seen, random() limit 1;
  if h.profile is null then return false; end if;
  update wl_gx_members set gives_to = h.gives_to, seen = false where gx_id = p_gx and profile = p_new;
  update wl_gx_members set gives_to = p_new, seen = false where gx_id = p_gx and profile = h.profile;
  if h.seen then perform wl_gx_news_add(h.profile, 'changed', p_gx, ''); end if;
  perform wl_gx_news_add(p_new, 'drawn', p_gx, '');
  return true;
end $$;

-- Takes member [p_member] out (leaving or removed) and repairs the draw.
create or replace function wl_gx_take_out(p_gx text, p_member text)
returns void language plpgsql security definer set search_path = public as $$
declare drawn boolean;
begin
  select drawn_at is not null into drawn from wl_gx where id = p_gx;
  delete from wl_gx_members where gx_id = p_gx and profile = p_member;
  if drawn then perform wl_gx_repair(p_gx); end if;
end $$;

-- ---------- for the app and the exchange web page ----------

-- An exchange link's preview, before joining. Null when the link doesn't work (any more).
create or replace function wl_gx_info(p_link text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', x.id, 'name', x.name, 'theme', x.theme, 'budget', x.budget, 'currency', x.currency, 'day', x.day,
    'drawn', x.drawn_at is not null, 'pv', x.photo_v,
    'organiser', (select p.name from wl_profiles p where p.id = x.organiser),
    'count', (select count(*) from wl_gx_members m where m.gx_id = x.id)
  ) from wl_gx x where x.link = upper(p_link)
$$;

create or replace function wl_gx_clean_theme(p text)
returns text language sql immutable as $$
  select case when p in ('xmas', 'sint', 'bday', 'office', 'plain') then p else 'xmas' end
$$;

-- A new exchange: you organise it and join with your list [p_list]. Returns the exchange.
create or replace function wl_gx_create(
  p_id text, p_token text, p_gx text, p_link text, p_name text, p_list text, p_list_token text,
  p_budget numeric, p_currency text, p_day date, p_theme text, p_message text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  perform wl_gr_check_list(p_list, p_list_token);
  if length(coalesce(p_gx, '')) < 16 or length(coalesce(p_link, '')) < 16 then raise exception 'bad key'; end if;
  if btrim(coalesce(p_name, '')) = '' then raise exception 'name needed'; end if;
  if p_budget is not null and (p_budget < 0 or p_budget > 99999) then raise exception 'bad budget'; end if;
  insert into wl_gx (id, link, name, organiser, budget, currency, day, theme, message)
    values (p_gx, upper(p_link), left(btrim(p_name), 60), p_id, nullif(p_budget, 0), left(coalesce(p_currency, ''), 8),
            p_day, wl_gx_clean_theme(p_theme), left(btrim(coalesce(p_message, '')), 300));
  insert into wl_gx_members (gx_id, profile, list_code) values (p_gx, p_id, p_list);
  return wl_gx_json(p_gx, p_id);
end $$;

-- Join with the exchange link (p_link), or with an invite (p_gx). You join with your list [p_list]. Already a member:
-- your list is swapped. After the draw you are slotted in (or wait for the organiser). Returns the exchange, or null
-- when the link or invite doesn't work (any more).
create or replace function wl_gx_join(
  p_id text, p_token text, p_link text, p_gx text, p_list text, p_list_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare g text; was boolean; x wl_gx;
begin
  perform wl_pf_check(p_id, p_token);
  perform wl_gr_check_list(p_list, p_list_token);
  if coalesce(p_link, '') <> '' then
    select id into g from wl_gx where link = upper(p_link);
  else
    select i.gx_id into g from wl_gx_invites i where i.gx_id = p_gx and i.profile = p_id;
    if g is null and wl_gx_is_member(p_gx, p_id) then g := p_gx; end if;
  end if;
  if g is null then return null; end if;
  select * into x from wl_gx where id = g for update;
  was := wl_gx_is_member(g, p_id);
  insert into wl_gx_members (gx_id, profile, list_code) values (g, p_id, p_list)
    on conflict (gx_id, profile) do update set list_code = excluded.list_code;
  delete from wl_gx_invites where gx_id = g and profile = p_id;
  if not was and x.drawn_at is not null then
    if not wl_gx_slot(g, p_id, false) then
      perform wl_gx_news_add(x.organiser, 'waiting', g, (select name from wl_profiles where id = p_id));
    end if;
  end if;
  return wl_gx_json(g, p_id);
end $$;

-- The organiser adds a late joiner who is waiting (everyone had already looked): one person's name changes, and they
-- are told. The organiser isn't told whose.
create or replace function wl_gx_add_late(p_id text, p_token text, p_gx text, p_member text)
returns void language plpgsql security definer set search_path = public as $$
declare x wl_gx;
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  select * into x from wl_gx where id = p_gx for update;
  if x.drawn_at is null then return; end if;
  if not exists (select 1 from wl_gx_members where gx_id = p_gx and profile = p_member and gives_to is null) then return; end if;
  if not wl_gx_slot(p_gx, p_member, true) then raise exception 'cannot slot in'; end if;
end $$;

-- "Change my wishlist".
create or replace function wl_gx_set_list(p_id text, p_token text, p_gx text, p_list text, p_list_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  perform wl_gr_check_list(p_list, p_list_token);
  update wl_gx_members set list_code = p_list where gx_id = p_gx and profile = p_id;
end $$;

-- Everything a phone needs: your exchanges, invites waiting for you, and news to tell.
create or replace function wl_gx_state(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return jsonb_build_object(
    'exchanges', coalesce((select jsonb_agg(wl_gx_json(m.gx_id, p_id) order by m.joined_at)
      from wl_gx_members m where m.profile = p_id), '[]'::jsonb),
    'invites', coalesce((select jsonb_agg(jsonb_build_object(
        'gx', x.id, 'name', x.name, 'link', x.link, 'by', b.id, 'by_name', b.name, 'theme', x.theme,
        'budget', x.budget, 'currency', x.currency, 'day', x.day,
        'count', (select count(*) from wl_gx_members m where m.gx_id = x.id)
      ) order by i.created_at)
      from wl_gx_invites i join wl_gx x on x.id = i.gx_id join wl_profiles b on b.id = i.by_profile
      where i.profile = p_id and not wl_gx_is_member(x.id, p_id)), '[]'::jsonb),
    'news', coalesce((select jsonb_agg(jsonb_build_object(
        'id', n.id, 'kind', n.kind, 'gx', n.gx_id, 'name', n.gx_name, 'who', n.who) order by n.id)
      from wl_gx_news n where n.profile = p_id), '[]'::jsonb)
  );
end $$;

create or replace function wl_gx_news_seen(p_id text, p_token text, p_upto bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_gx_news where profile = p_id and id <= p_upto;
end $$;

-- One exchange (for the web page and the app's refresh of an open exchange). Null when you're not in it (any more).
create or replace function wl_gx_get(p_id text, p_token text, p_gx text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_gx_is_member(p_gx, p_id) then return null; end if;
  return wl_gx_json(p_gx, p_id);
end $$;

-- The organiser invites a friend.
create or replace function wl_gx_invite(p_id text, p_token text, p_gx text, p_friend text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  if not exists (select 1 from wl_friends f where f.accepted
                 and ((f.a = p_id and f.b = p_friend) or (f.a = p_friend and f.b = p_id))) then
    raise exception 'not a friend';
  end if;
  if wl_gx_is_member(p_gx, p_friend) then return; end if;
  insert into wl_gx_invites (gx_id, profile, by_profile) values (p_gx, p_friend, p_id)
    on conflict (gx_id, profile) do update set by_profile = excluded.by_profile, created_at = now();
end $$;

-- "No thanks": the invite goes and the organiser is told.
create or replace function wl_gx_decline(p_id text, p_token text, p_gx text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not exists (select 1 from wl_gx_invites where gx_id = p_gx and profile = p_id) then return; end if;
  delete from wl_gx_invites where gx_id = p_gx and profile = p_id;
  perform wl_gx_news_add((select organiser from wl_gx where id = p_gx), 'declined', p_gx,
                         (select name from wl_profiles where id = p_id));
end $$;

-- The organiser takes an open invite back.
create or replace function wl_gx_uninvite(p_id text, p_token text, p_gx text, p_friend text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  delete from wl_gx_invites where gx_id = p_gx and profile = p_friend;
end $$;

-- Leave an exchange. The organiser hands over first ([p_next], a member); alone, they can only cancel it.
create or replace function wl_gx_leave(p_id text, p_token text, p_gx text, p_next text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  perform 1 from wl_gx where id = p_gx for update;
  if exists (select 1 from wl_gx where id = p_gx and organiser = p_id) then
    if coalesce(p_next, '') = '' or p_next = p_id or not wl_gx_is_member(p_gx, p_next) then
      raise exception 'pick the next organiser';
    end if;
    update wl_gx set organiser = p_next where id = p_gx;
    perform wl_gx_news_add(p_next, 'organiser', p_gx, '');
  end if;
  perform wl_gx_take_out(p_gx, p_id);
end $$;

-- The organiser removes a member (they are told; the draw is repaired).
create or replace function wl_gx_remove(p_id text, p_token text, p_gx text, p_member text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  perform 1 from wl_gx where id = p_gx for update;
  if p_member = p_id then raise exception 'not yourself'; end if;
  if not wl_gx_is_member(p_gx, p_member) then return; end if;
  perform wl_gx_take_out(p_gx, p_member);
  perform wl_gx_news_add(p_member, 'removed', p_gx, '');
end $$;

-- Hand over the organiser role to another member, at any time.
create or replace function wl_gx_handover(p_id text, p_token text, p_gx text, p_next text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  if p_next = p_id or not wl_gx_is_member(p_gx, p_next) then raise exception 'not a member'; end if;
  update wl_gx set organiser = p_next where id = p_gx;
  perform wl_gx_news_add(p_next, 'organiser', p_gx, '');
end $$;

-- The organiser's settings. Everyone else is told when the budget or the date changed.
create or replace function wl_gx_settings(
  p_id text, p_token text, p_gx text, p_name text, p_budget numeric, p_currency text, p_day date, p_theme text,
  p_message text)
returns void language plpgsql security definer set search_path = public as $$
declare x wl_gx;
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  if btrim(coalesce(p_name, '')) = '' then raise exception 'name needed'; end if;
  if p_budget is not null and (p_budget < 0 or p_budget > 99999) then raise exception 'bad budget'; end if;
  select * into x from wl_gx where id = p_gx for update;
  update wl_gx set name = left(btrim(p_name), 60), budget = nullif(p_budget, 0),
    currency = left(coalesce(p_currency, ''), 8), day = p_day, theme = wl_gx_clean_theme(p_theme),
    message = left(btrim(coalesce(p_message, '')), 300)
  where id = p_gx;
  if x.budget is distinct from nullif(p_budget, 0) or x.day is distinct from p_day or x.currency is distinct from left(coalesce(p_currency, ''), 8) then
    perform wl_gx_news_add(m.profile, 'settings', p_gx, '') from wl_gx_members m where m.gx_id = p_gx and m.profile <> p_id;
  end if;
end $$;

create or replace function wl_gx_set_photo(p_id text, p_token text, p_gx text, p_photo text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  if length(coalesce(p_photo, '')) > 300000 then raise exception 'photo too big'; end if;
  update wl_gx set photo = coalesce(p_photo, ''),
    photo_v = case when coalesce(p_photo, '') = '' then 0 else (extract(epoch from clock_timestamp()) * 1000)::bigint end
    where id = p_gx;
end $$;

create or replace function wl_gx_photo(p_id text, p_token text, p_gx text)
returns text language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  return (select photo from wl_gx where id = p_gx);
end $$;

-- The photo of someone you share an exchange with. '' when there is none.
create or replace function wl_gx_face(p_id text, p_token text, p_other text)
returns text language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return coalesce((select p.photo from wl_profiles p where p.id = p_other and exists (
    select 1 from wl_gx_members a join wl_gx_members b on a.gx_id = b.gx_id where a.profile = p_id and b.profile = p_other)), '');
end $$;

-- The organiser cancels the exchange for everyone. Nobody's wishlist is deleted.
create or replace function wl_gx_cancel(p_id text, p_token text, p_gx text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  perform wl_gx_news_add(m.profile, 'cancelled', p_gx, '') from wl_gx_members m where m.gx_id = p_gx and m.profile <> p_id;
  delete from wl_gx where id = p_gx;
end $$;

-- ---------- rules (exclusions) ----------

-- The organiser adds "[p_giver] doesn't draw [p_receiver]" (and the other way round with [p_both]). Only before the
-- draw. A rule that would make a draw impossible is not saved: {"ok": false, "stuck": [names], "left": n}.
create or replace function wl_gx_excl_add(
  p_id text, p_token text, p_gx text, p_giver text, p_receiver text, p_both boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare bans jsonb; r jsonb; ppl text[];
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  perform 1 from wl_gx where id = p_gx for update;
  if exists (select 1 from wl_gx where id = p_gx and drawn_at is not null) then raise exception 'already drawn'; end if;
  if p_giver = p_receiver or not wl_gx_is_member(p_gx, p_giver) or not wl_gx_is_member(p_gx, p_receiver) then
    raise exception 'not members';
  end if;
  bans := wl_gx_bans(p_gx) || jsonb_build_array(jsonb_build_array(p_giver, p_receiver));
  if p_both then bans := bans || jsonb_build_array(jsonb_build_array(p_receiver, p_giver)); end if;
  ppl := wl_gx_people(p_gx);
  r := wl_gx_solve(ppl, bans, null);
  if not (r->>'ok')::boolean then return wl_gx_stuck_names(r); end if;
  insert into wl_gx_excl (gx_id, giver, receiver) values (p_gx, p_giver, p_receiver) on conflict do nothing;
  if p_both then
    insert into wl_gx_excl (gx_id, giver, receiver) values (p_gx, p_receiver, p_giver) on conflict do nothing;
  end if;
  return jsonb_build_object('ok', true);
end $$;

create or replace function wl_gx_excl_del(
  p_id text, p_token text, p_gx text, p_giver text, p_receiver text, p_both boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  if exists (select 1 from wl_gx where id = p_gx and drawn_at is not null) then raise exception 'already drawn'; end if;
  delete from wl_gx_excl where gx_id = p_gx and ((giver = p_giver and receiver = p_receiver)
    or (p_both and giver = p_receiver and receiver = p_giver));
end $$;

-- ---------- the draw, opening your name, the reveal ----------

-- The organiser draws. With invites still open and [p_without] false: nothing happens and the answer names who hasn't
-- joined ({"pending": [names]}), so the app can ask "Draw without them?" (they can still join and be slotted in).
create or replace function wl_gx_draw(p_id text, p_token text, p_gx text, p_without boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare x wl_gx; ppl text[]; r jsonb; k text; pend jsonb;
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  select * into x from wl_gx where id = p_gx for update;
  if x.drawn_at is not null then return jsonb_build_object('ok', true, 'already', true); end if;
  ppl := wl_gx_people(p_gx);
  if coalesce(array_length(ppl, 1), 0) < 3 then return jsonb_build_object('ok', false, 'few', true); end if;
  select jsonb_agg(p.name order by i.created_at) into pend
    from wl_gx_invites i join wl_profiles p on p.id = i.profile where i.gx_id = p_gx;
  if pend is not null and not coalesce(p_without, false) then
    return jsonb_build_object('ok', false, 'pending', pend);
  end if;
  r := wl_gx_solve(ppl, wl_gx_bans(p_gx), null);
  if not (r->>'ok')::boolean then return wl_gx_stuck_names(r); end if;
  for k in select jsonb_object_keys(r->'pairs') loop
    update wl_gx_members set gives_to = r->'pairs'->>k, seen = false where gx_id = p_gx and profile = k;
  end loop;
  update wl_gx set drawn_at = now() where id = p_gx;
  perform wl_gx_news_add(m.profile, 'drawn', p_gx, '') from wl_gx_members m where m.gx_id = p_gx and m.profile <> p_id;
  return jsonb_build_object('ok', true);
end $$;

-- You open the name you drew (the reveal moment). Returns the exchange.
create or replace function wl_gx_open(p_id text, p_token text, p_gx text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  update wl_gx_members set seen = true where gx_id = p_gx and profile = p_id and gives_to is not null;
  return wl_gx_json(p_gx, p_id);
end $$;

-- The wishlist of the person you drew (null before you opened their name). Reservations go through the usual
-- wl_get_reservations / wl_reserve_count with its code, like any friend's list.
create or replace function wl_gx_list(p_id text, p_token text, p_gx text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare c text;
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  select g.list_code into c from wl_gx_members me join wl_gx_members g on g.gx_id = me.gx_id and g.profile = me.gives_to
    where me.gx_id = p_gx and me.profile = p_id and me.seen;
  if c is null then return null; end if;
  return wl_list_json(c) || jsonb_build_object('code', c);
end $$;

-- The organiser shows everyone who had whom, on or after the exchange date (any time when there is no date).
create or replace function wl_gx_reveal(p_id text, p_token text, p_gx text)
returns boolean language plpgsql security definer set search_path = public as $$
begin
  perform wl_gx_check_org(p_id, p_token, p_gx);
  update wl_gx set revealed = true where id = p_gx and drawn_at is not null and (day is null or day <= current_date);
  return found;
end $$;

-- ---------- the anonymous chat ----------

-- The pair of chat [p_side] for you: 'giftee' = you and the person you drew; 'santa' = your Secret Santa and you.
create or replace function wl_gx_pair(p_gx text, p_me text, p_side text, out santa text, out giftee text)
language plpgsql stable security definer set search_path = public as $$
begin
  if p_side = 'giftee' then
    santa := p_me;
    select gives_to into giftee from wl_gx_members where gx_id = p_gx and profile = p_me and seen;
  elsif p_side = 'santa' then
    giftee := p_me;
    santa := wl_gx_santa_of(p_gx, p_me);
  end if;
end $$;

-- The messages of one chat (the newest 300, or those after p_after), oldest first. Never says who the Santa is.
create or replace function wl_gx_chat(p_id text, p_token text, p_gx text, p_side text, p_after bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
declare pr record; v jsonb;
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  select * into pr from wl_gx_pair(p_gx, p_id, p_side);
  if pr.santa is null or pr.giftee is null then return '[]'::jsonb; end if;
  select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'mine', m.from_santa = (p_side = 'giftee'), 'body', m.body,
           'at', m.created_at, 'read', m.read_at is not null) order by m.id), '[]'::jsonb) into v
    from (select * from wl_gx_msgs m where m.gx_id = p_gx and m.santa = pr.santa and m.giftee = pr.giftee
            and m.id > coalesce(p_after, 0) order by m.id desc limit 300) m;
  return v;
end $$;

-- Send a message in chat [p_side]. Returns its number, or 0 when that chat isn't open (yet).
create or replace function wl_gx_send(p_id text, p_token text, p_gx text, p_side text, p_body text)
returns bigint language plpgsql security definer set search_path = public as $$
declare pr record; v bigint;
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  if btrim(coalesce(p_body, '')) = '' or length(p_body) > 1000 then raise exception 'bad message'; end if;
  if (select count(*) from wl_gx_msgs m where m.created_at > now() - interval '1 day'
      and ((m.santa = p_id and m.from_santa) or (m.giftee = p_id and not m.from_santa))) >= 300 then
    raise exception 'too many messages';
  end if;
  select * into pr from wl_gx_pair(p_gx, p_id, p_side);
  if pr.santa is null or pr.giftee is null then return 0; end if;
  insert into wl_gx_msgs (gx_id, santa, giftee, from_santa, body)
    values (p_gx, pr.santa, pr.giftee, p_side = 'giftee', btrim(p_body)) returning id into v;
  return v;
end $$;

-- You have seen the other side's messages up to p_upto in chat [p_side].
create or replace function wl_gx_chat_seen(p_id text, p_token text, p_gx text, p_side text, p_upto bigint)
returns void language plpgsql security definer set search_path = public as $$
declare pr record;
begin
  perform wl_gx_check_member(p_id, p_token, p_gx);
  select * into pr from wl_gx_pair(p_gx, p_id, p_side);
  update wl_gx_msgs set read_at = now()
    where gx_id = p_gx and santa = pr.santa and giftee = pr.giftee and from_santa = (p_side = 'santa')
      and id <= coalesce(p_upto, 0) and read_at is null;
end $$;

-- A new anonymous message wakes the receiver's phone through the "push" function (supabase/functions/push), like
-- normal messages. The body is {"gx": id} (an older push function refuses it, so nothing goes wrong before it is
-- updated). A failing push never stops a message.
create or replace function wl_gx_push_notify()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from wl_push where id = case when new.from_santa then new.giftee else new.santa end) then
    perform net.http_post(
      url := 'https://aspnmvmsvwbezdltykgx.supabase.co/functions/v1/push',
      body := jsonb_build_object('gx', new.id),
      headers := '{"Content-Type": "application/json"}'::jsonb
    );
  end if;
  return new;
exception when others then
  return new;
end $$;

drop trigger if exists wl_gx_msgs_push on wl_gx_msgs;
create trigger wl_gx_msgs_push after insert on wl_gx_msgs
  for each row execute function wl_gx_push_notify();

-- ---------- a browser member moves to the app ----------

-- Before wl_gr_takeover deletes the browser profile: its exchanges, invites, news and chats move to the app's profile
-- (the draw stays exactly as it was). An exchange the app is already in keeps the app's place.
create or replace function wl_gx_takeover(p_web text, p_web_token text, p_id text, p_token text)
returns void language plpgsql security definer set search_path = public as $$
declare x text;
begin
  perform wl_pf_check(p_web, p_web_token);
  perform wl_pf_check(p_id, p_token);
  if p_web = p_id then raise exception 'same profile'; end if;
  if not (select web from wl_profiles where id = p_web) then raise exception 'not a browser profile'; end if;
  for x in select gx_id from wl_gx_members where profile = p_web and not wl_gx_is_member(gx_id, p_id) loop
    insert into wl_gx_members (gx_id, profile, list_code, joined_at, gives_to, seen)
      select gx_id, p_id, list_code, joined_at, null, seen from wl_gx_members where gx_id = x and profile = p_web;
    update wl_gx_members set gives_to = p_id where gx_id = x and gives_to = p_web;
    update wl_gx_members set gives_to = (select gives_to from wl_gx_members where gx_id = x and profile = p_web)
      where gx_id = x and profile = p_id;
    insert into wl_gx_excl (gx_id, giver, receiver)
      select gx_id, case when giver = p_web then p_id else giver end, case when receiver = p_web then p_id else receiver end
      from wl_gx_excl where gx_id = x and (giver = p_web or receiver = p_web) on conflict do nothing;
    update wl_gx_msgs set santa = p_id where gx_id = x and santa = p_web;
    update wl_gx_msgs set giftee = p_id where gx_id = x and giftee = p_web;
    delete from wl_gx_members where gx_id = x and profile = p_web;
  end loop;
  update wl_gx set organiser = p_id where organiser = p_web;
  insert into wl_gx_invites (gx_id, profile, by_profile)
    select i.gx_id, p_id, i.by_profile from wl_gx_invites i where i.profile = p_web and not wl_gx_is_member(i.gx_id, p_id)
    on conflict (gx_id, profile) do nothing;
  update wl_gx_invites set by_profile = p_id where by_profile = p_web;
  update wl_gx_news set profile = p_id where profile = p_web;
end $$;

-- What a browser member's link opens on the exchange page: their name, their list and their exchanges.
create or replace function wl_gx_web_me(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  update wl_profiles set seen_at = now() where id = p_id;
  return (select jsonb_build_object(
    'name', p.name, 'web', p.web, 'list', p.web_list,
    'list_token', coalesce((select l.owner_token from wl_lists l where l.code = p.web_list and p.web), ''),
    'exchanges', coalesce((select jsonb_agg(jsonb_build_object('id', x.id, 'link', x.link, 'name', x.name) order by m.joined_at)
      from wl_gx_members m join wl_gx x on x.id = m.gx_id where m.profile = p.id), '[]'::jsonb)
  ) from wl_profiles p where p.id = p_id);
end $$;

revoke execute on function
  wl_gx_solve(text[], jsonb, jsonb), wl_gx_bans(text), wl_gx_people(text), wl_gx_stuck_names(jsonb),
  wl_gx_is_member(text, text), wl_gx_check_member(text, text, text), wl_gx_check_org(text, text, text),
  wl_gx_news_add(text, text, text, text), wl_gx_santa_of(text, text), wl_gx_unread(text, text, text),
  wl_gx_json(text, text), wl_gx_repair(text), wl_gx_slot(text, text, boolean), wl_gx_take_out(text, text),
  wl_gx_pair(text, text, text), wl_gx_push_notify(), wl_gx_clean_theme(text)
from public, anon;

grant execute on function
  wl_gx_info(text),
  wl_gx_create(text, text, text, text, text, text, text, numeric, text, date, text, text),
  wl_gx_join(text, text, text, text, text, text),
  wl_gx_add_late(text, text, text, text),
  wl_gx_set_list(text, text, text, text, text),
  wl_gx_state(text, text),
  wl_gx_news_seen(text, text, bigint),
  wl_gx_get(text, text, text),
  wl_gx_invite(text, text, text, text),
  wl_gx_decline(text, text, text),
  wl_gx_uninvite(text, text, text, text),
  wl_gx_leave(text, text, text, text),
  wl_gx_remove(text, text, text, text),
  wl_gx_handover(text, text, text, text),
  wl_gx_settings(text, text, text, text, numeric, text, date, text, text),
  wl_gx_set_photo(text, text, text, text),
  wl_gx_photo(text, text, text),
  wl_gx_face(text, text, text),
  wl_gx_cancel(text, text, text),
  wl_gx_excl_add(text, text, text, text, text, boolean),
  wl_gx_excl_del(text, text, text, text, text, boolean),
  wl_gx_draw(text, text, text, boolean),
  wl_gx_open(text, text, text),
  wl_gx_list(text, text, text),
  wl_gx_reveal(text, text, text),
  wl_gx_chat(text, text, text, text, bigint),
  wl_gx_send(text, text, text, text, text),
  wl_gx_chat_seen(text, text, text, text, bigint),
  wl_gx_takeover(text, text, text, text),
  wl_gx_web_me(text, text)
to anon;
