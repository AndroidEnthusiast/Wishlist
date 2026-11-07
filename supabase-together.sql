-- Doing things together (7 November 2026, version 2.142). Run this in Supabase: SQL Editor -> New query ->
-- paste everything -> Run. Run it AFTER all the other scripts (setup, shared, friends, chat, push, groups, features,
-- exchange). It only adds things and is safe to run again.
-- It needs the extensions pg_net (already on for message notifications) and pg_cron (Database -> Extensions).
--
--   0. News for a phone, sent at once: the database asks the "push" function to wake that phone (like messages).
--   1. Inviting Friends to a gift list: an invitation to accept (Join / No thanks). The list's edit key is only given
--      to the invited person, and only when they join.
--   2. Reactions on wishes: one emoji per person per wish. App users by their profile, browser visitors by their
--      browser (device id) with a first name.
--   3. Stuck reservations: "Is it still being bought?". The person who reserved answers within 2 days; otherwise the
--      gift becomes free. A timer (pg_cron) checks every 10 minutes. The list's owner never sees any of it.

-- ---------- 0. News, pushed at once ----------

-- A phone tells which profile it belongs to, so a reservation (made with the phone's device id) can be answered with
-- a push. Only the phone itself knows its device id.
create table if not exists wl_devices (
  device text primary key,
  profile text not null references wl_profiles(id) on delete cascade,
  updated_at timestamptz not null default now()
);
create index if not exists wl_devices_profile on wl_devices (profile);
alter table wl_devices enable row level security;

create or replace function wl_dev_link(p_id text, p_token text, p_device text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_device, '')) < 8 or length(p_device) > 64 then raise exception 'bad device'; end if;
  insert into wl_devices (device, profile, updated_at) values (p_device, p_id, now())
    on conflict (device) do update set profile = excluded.profile, updated_at = now();
end $$;

-- One piece of news for a profile (an app) or for a browser (device, on one list). Read once, then deleted.
create table if not exists wl_news (
  id bigserial primary key,
  profile text references wl_profiles(id) on delete cascade,
  device text,
  list_code text,
  kind text not null,
  data jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  pushed boolean not null default false
);
create index if not exists wl_news_profile on wl_news (profile, id);
create index if not exists wl_news_device on wl_news (device, list_code);
alter table wl_news enable row level security;

-- Adds news for whoever holds [p_device]: their profile when the phone told us (an app), else that browser.
create or replace function wl_news_add(p_profile text, p_device text, p_code text, p_kind text, p_data jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_pf text := nullif(p_profile, '');
begin
  if v_pf is null and coalesce(p_device, '') <> '' then
    select profile into v_pf from wl_devices where device = p_device;
  end if;
  insert into wl_news (profile, device, list_code, kind, data)
    values (v_pf, case when v_pf is null then nullif(p_device, '') end, p_code, p_kind, coalesce(p_data, '{}'::jsonb));
end $$;

-- News for an app is pushed at once (quiet kinds only show in the app). The push carries no text.
create or replace function wl_news_push()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.profile is not null and new.kind not in ('sh_declined')
     and exists (select 1 from wl_push where id = new.profile) then
    perform net.http_post(
      url := 'https://aspnmvmsvwbezdltykgx.supabase.co/functions/v1/push',
      body := jsonb_build_object('n', new.id),
      headers := '{"Content-Type": "application/json"}'::jsonb
    );
  end if;
  return new;
exception when others then
  return new;
end $$;
drop trigger if exists wl_news_push on wl_news;
create trigger wl_news_push after insert on wl_news for each row execute function wl_news_push();

-- The app's news (by profile, and by this phone's device for news from before it was linked), oldest first.
create or replace function wl_news_get(p_id text, p_token text, p_device text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select coalesce(jsonb_agg(jsonb_build_object('id', n.id, 'kind', n.kind, 'code', n.list_code, 'data', n.data, 'at', n.created_at)
           order by n.id), '[]'::jsonb) into v
    from wl_news n
    where n.profile = p_id or (coalesce(p_device, '') <> '' and n.device = p_device and n.profile is null);
  return v;
end $$;

-- The phone has told news up to [p_upto]: it is deleted.
create or replace function wl_news_done(p_id text, p_token text, p_device text, p_upto bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_news where id <= coalesce(p_upto, 0)
    and (profile = p_id or (coalesce(p_device, '') <> '' and device = p_device and profile is null));
end $$;

-- ---------- 1. Inviting Friends to a gift list ----------

create table if not exists wl_sh_invites (
  list_code text not null references wl_lists(code) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  by_profile text not null references wl_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (list_code, profile)
);
create index if not exists wl_sh_invites_profile on wl_sh_invites (profile);
alter table wl_sh_invites enable row level security;

create or replace function wl_is_friend(p_a text, p_b text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from wl_friends where accepted and ((a = p_a and b = p_b) or (a = p_b and b = p_a)))
$$;
revoke execute on function wl_is_friend(text, text) from public, anon;

-- Everyone on a gift list (they have its edit key) may invite their Friends. Returns 'ok', 'member' (already on the
-- list) or 'not_friend'.
create or replace function wl_sh_invite(p_id text, p_token text, p_key text, p_friend text)
returns text language plpgsql security definer set search_path = public as $$
declare v_code text; v_name text; v_by text;
begin
  perform wl_pf_check(p_id, p_token);
  v_code := wl_sh_code(p_key);
  if not wl_is_friend(p_id, p_friend) then return 'not_friend'; end if;
  if exists (select 1 from wl_sh_members where list_code = v_code and profile = p_friend) then return 'member'; end if;
  insert into wl_sh_invites (list_code, profile, by_profile) values (v_code, p_friend, p_id)
    on conflict (list_code, profile) do update set by_profile = excluded.by_profile, created_at = now();
  select name into v_name from wl_lists where code = v_code;
  select name into v_by from wl_profiles where id = p_id;
  perform wl_news_add(p_friend, null, v_code, 'sh_invite', jsonb_build_object('name', v_name, 'by', v_by));
  return 'ok';
end $$;

-- Who is on the list or invited already (so the picker shows them as such): profile ids.
create or replace function wl_sh_invited(p_id text, p_token text, p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  perform wl_pf_check(p_id, p_token);
  v_code := wl_sh_code(p_key);
  return jsonb_build_object(
    'members', coalesce((select jsonb_agg(profile) from wl_sh_members where list_code = v_code), '[]'::jsonb),
    'invited', coalesce((select jsonb_agg(profile) from wl_sh_invites where list_code = v_code), '[]'::jsonb));
end $$;

-- What an invitation shows before joining: the list, who invited, the givers on it, the wishes and pictures.
-- Never the edit key. Null when there is no invitation (any more).
create or replace function wl_sh_inv_info(p_id text, p_token text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select jsonb_build_object(
      'code', l.code, 'name', l.name, 'by', b.name, 'by_id', b.id,
      'givers', coalesce((select jsonb_agg(p.name order by m.joined_at) from wl_sh_members m
                          join wl_profiles p on p.id = m.profile where m.list_code = l.code), '[]'::jsonb),
      'count', (select count(*) from wl_items it where it.list_code = l.code and it.id > 0
                  and coalesce(nullif(it.data->>'bin', ''), '0') in ('0', 'false')),
      'images', coalesce((select jsonb_agg(x.img) from (
                  select it.data->>'image' as img from wl_items it where it.list_code = l.code and it.id > 0
                    and coalesce(it.data->>'image', '') like 'http%'
                    and coalesce(nullif(it.data->>'bin', ''), '0') in ('0', 'false')
                  order by it.id desc limit 3) x), '[]'::jsonb),
      'occ', (select it.data->'occ' from wl_items it where it.list_code = l.code and it.id = 0))
    into v
    from wl_sh_invites i join wl_lists l on l.code = i.list_code join wl_profiles b on b.id = i.by_profile
    where i.list_code = p_code and i.profile = p_id and l.edit_key is not null;
  return v;
end $$;

-- Your open invitations (the app's state), newest first.
create or replace function wl_sh_invites_mine(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select coalesce(jsonb_agg(jsonb_build_object('code', l.code, 'name', l.name, 'by', b.name) order by i.created_at desc), '[]'::jsonb)
    into v
    from wl_sh_invites i join wl_lists l on l.code = i.list_code join wl_profiles b on b.id = i.by_profile
    where i.profile = p_id and l.edit_key is not null;
  return v;
end $$;

-- Join: the edit key, only for the invited person (and only now). The invitation goes; you are a member.
create or replace function wl_sh_inv_accept(p_id text, p_token text, p_code text)
returns text language plpgsql security definer set search_path = public as $$
declare v_key text;
begin
  perform wl_pf_check(p_id, p_token);
  select l.edit_key into v_key from wl_sh_invites i join wl_lists l on l.code = i.list_code
    where i.list_code = p_code and i.profile = p_id;
  if v_key is null then return null; end if;
  delete from wl_sh_invites where list_code = p_code and profile = p_id;
  insert into wl_sh_members (list_code, profile) values (p_code, p_id) on conflict do nothing;
  return v_key;
end $$;

-- "No thanks": the invitation goes; whoever invited is told quietly (their Notifications page, no push).
create or replace function wl_sh_inv_decline(p_id text, p_token text, p_code text)
returns void language plpgsql security definer set search_path = public as $$
declare i wl_sh_invites; v_name text; v_who text;
begin
  perform wl_pf_check(p_id, p_token);
  select * into i from wl_sh_invites where list_code = p_code and profile = p_id;
  if not found then return; end if;
  delete from wl_sh_invites where list_code = p_code and profile = p_id;
  select name into v_name from wl_lists where code = p_code;
  select name into v_who from wl_profiles where id = p_id;
  perform wl_news_add(i.by_profile, null, p_code, 'sh_declined', jsonb_build_object('name', v_name, 'who', v_who));
end $$;

-- ---------- 2. Reactions on wishes ----------

-- who: 'p:' + profile id (the app) or 'w:' + device id (a browser, with the first name it gave).
create table if not exists wl_reacts (
  list_code text not null,
  item_id bigint not null,
  who text not null,
  name text not null default '',
  emoji text not null,
  created_at timestamptz not null default now(),
  primary key (list_code, item_id, who),
  foreign key (list_code, item_id) references wl_items(list_code, id) on delete cascade
);
alter table wl_reacts enable row level security;

-- The reactions on a list: [{i: wish, e: emoji, n: name, m: is it yours, at}], oldest first. [p_id]/[p_token]: an app
-- (its profile); else [p_device]: a browser. Names of app users are their profile names now.
create or replace function wl_rx_get(p_code text, p_id text, p_token text, p_device text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_me text := ''; v jsonb;
begin
  if coalesce(p_id, '') <> '' then
    perform wl_pf_check(p_id, p_token);
    v_me := 'p:' || p_id;
  elsif coalesce(p_device, '') <> '' then
    v_me := 'w:' || p_device;
  end if;
  select coalesce(jsonb_agg(jsonb_build_object(
      'i', r.item_id, 'e', r.emoji,
      'n', case when r.who like 'p:%' then coalesce(p.name, '') else r.name end,
      'm', r.who = v_me, 'at', r.created_at) order by r.created_at), '[]'::jsonb)
    into v
    from wl_reacts r left join wl_profiles p on r.who like 'p:%' and p.id = substr(r.who, 3)
    where r.list_code = p_code;
  return v;
end $$;

-- Your reaction on a wish: [p_emoji] replaces your earlier one, '' takes it away. App: [p_id]/[p_token]. Browser:
-- [p_device] and a first name [p_name] (needed). Never on your own list. Returns true when saved.
create or replace function wl_rx_set(p_code text, p_item bigint, p_id text, p_token text, p_device text, p_name text, p_emoji text)
returns boolean language plpgsql security definer set search_path = public as $$
declare v_who text; v_name text := '';
begin
  if coalesce(p_id, '') <> '' then
    perform wl_pf_check(p_id, p_token);
    -- The owner can't react on a list they show to friends or bring to a group.
    if exists (select 1 from wl_profile_lists where profile = p_id and list_code = p_code)
       or exists (select 1 from wl_group_members where profile = p_id and list_code = p_code) then return false; end if;
    v_who := 'p:' || p_id;
  else
    if length(coalesce(p_device, '')) < 8 or length(p_device) > 64 then return false; end if;
    v_name := left(btrim(coalesce(p_name, '')), 30);
    if v_name = '' then return false; end if;
    v_who := 'w:' || p_device;
  end if;
  if coalesce(p_emoji, '') = '' then
    delete from wl_reacts where list_code = p_code and item_id = p_item and who = v_who;
    return true;
  end if;
  -- One emoji (with its skin tone and joiners): short, and no letters, digits or markup.
  if char_length(p_emoji) > 16 or p_emoji ~ '[A-Za-z<>&"''\s]' then return false; end if;
  if p_item <= 0 or not exists (select 1 from wl_items where list_code = p_code and id = p_item) then return false; end if;
  insert into wl_reacts (list_code, item_id, who, name, emoji) values (p_code, p_item, v_who, v_name, p_emoji)
    on conflict (list_code, item_id, who) do update set emoji = excluded.emoji, name = excluded.name, created_at = now();
  return true;
end $$;

-- ---------- 3. Stuck reservations ----------

-- One question "Is it still being bought?" about a wish, asked by [asker] (a device; [asker_profile] for an app).
-- state: 'open', 'yes' (still being bought) or 'free' (someone let go or didn't answer).
create table if not exists wl_asks (
  id bigserial primary key,
  list_code text not null,
  item_id bigint not null,
  asker text not null,
  asker_profile text,
  title text not null default '',
  state text not null default 'open',
  created_at timestamptz not null default now(),
  answered_at timestamptz,
  foreign key (list_code, item_id) references wl_items(list_code, id) on delete cascade
);
create index if not exists wl_asks_item on wl_asks (list_code, item_id);
alter table wl_asks enable row level security;

-- Everyone who had reserved the wish when it was asked. state: 'open', 'yes' or 'gone' (let go, or no answer).
create table if not exists wl_ask_to (
  ask_id bigint not null references wl_asks(id) on delete cascade,
  device text not null,
  state text not null default 'open',
  primary key (ask_id, device)
);
create index if not exists wl_ask_to_device on wl_ask_to (device);
alter table wl_ask_to enable row level security;

-- Where a list is, for the words of a notification: a gift list's name, or the owner's name for a wishlist.
create or replace function wl_ask_place(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('list', l.name, 'owner', l.owner_name, 'gift', l.edit_key is not null)
  from wl_lists l where l.code = p_code
$$;
revoke execute on function wl_ask_place(text) from public, anon;

-- True when the list's own occasion (a date of its own, not a yearly one) has passed.
create or replace function wl_ask_passed(p_code text)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((
    select (data->'occ'->>'k') = 'own' and (data->'occ'->>'d') ~ '^\d{4}-\d{2}-\d{2}$'
           and (data->'occ'->>'d')::date < current_date
    from wl_items where list_code = p_code and id = 0), false)
$$;
revoke execute on function wl_ask_passed(text) from public, anon;

-- The whole ask, finished when nobody is left to answer: 'free' when anyone let go, else 'yes'.
create or replace function wl_ask_settle(p_ask bigint)
returns void language plpgsql security definer set search_path = public as $$
declare a wl_asks; v_place jsonb;
begin
  select * into a from wl_asks where id = p_ask for update;
  if not found or a.state <> 'open' then return; end if;
  if exists (select 1 from wl_ask_to where ask_id = p_ask and state = 'open') then return; end if;
  v_place := wl_ask_place(a.list_code) || jsonb_build_object('item', a.item_id, 'title', a.title, 'ask', a.id);
  if exists (select 1 from wl_ask_to where ask_id = p_ask and state = 'gone') then
    update wl_asks set state = 'free', answered_at = now() where id = p_ask;
  else
    update wl_asks set state = 'yes', answered_at = now() where id = p_ask;
    perform wl_news_add(a.asker_profile, a.asker, a.list_code, 'ask_yes', v_place);
  end if;
end $$;
revoke execute on function wl_ask_settle(bigint) from public, anon;

-- One reserver's part ends: their reservation goes, the asker hears at once that it is free (once per ask), and
-- [p_tell] (no answer in time) tells the reserver that their reservation ended.
create or replace function wl_ask_free(p_ask bigint, p_device text, p_tell boolean)
returns void language plpgsql security definer set search_path = public as $$
declare a wl_asks; v_place jsonb; v_first boolean;
begin
  select * into a from wl_asks where id = p_ask;
  if not found then return; end if;
  v_first := not exists (select 1 from wl_ask_to where ask_id = p_ask and state = 'gone');
  update wl_ask_to set state = 'gone' where ask_id = p_ask and device = p_device and state = 'open';
  if not found then return; end if;
  delete from wl_reservations where list_code = a.list_code and item_id = a.item_id and device = p_device;
  v_place := wl_ask_place(a.list_code) || jsonb_build_object('item', a.item_id, 'title', a.title, 'ask', a.id);
  if v_first then perform wl_news_add(a.asker_profile, a.asker, a.list_code, 'ask_free', v_place); end if;
  if p_tell then perform wl_news_add(null, p_device, a.list_code, 'ask_ended', v_place); end if;
  perform wl_ask_settle(p_ask);
end $$;
revoke execute on function wl_ask_free(bigint, text, boolean) from public, anon;

-- "Ask if it's still being bought" on wish [p_item]. [p_device] is the asker's device; [p_id]/[p_token] their app
-- profile when they have one (for the pushes). Returns:
--   'ok' asked; 'asked' someone already asked; 'recent' it was confirmed in the last 7 days; 'free' nobody else has
--   it reserved (or a 2x wish isn't fully reserved); 'mine' you reserved it yourself; 'passed' the occasion is over.
create or replace function wl_ask(p_code text, p_item bigint, p_device text, p_id text, p_token text)
returns text language plpgsql security definer set search_path = public as $$
declare v_ask bigint; v_title text; v_q int; v_taken int; v_pf text; v_place jsonb; d record;
begin
  if length(coalesce(p_device, '')) < 8 then raise exception 'bad device'; end if;
  if coalesce(p_id, '') <> '' then perform wl_pf_check(p_id, p_token); v_pf := p_id; end if;
  perform 1 from wl_items where list_code = p_code and id = p_item for update;
  if not found then return 'free'; end if;
  if wl_ask_passed(p_code) then return 'passed'; end if;
  if exists (select 1 from wl_reservations where list_code = p_code and item_id = p_item and device = p_device) then
    return 'mine';
  end if;
  v_q := wl_qty(p_code, p_item);
  select coalesce(sum(n), 0) into v_taken from wl_reservations where list_code = p_code and item_id = p_item;
  if v_taken = 0 or v_taken < v_q then return 'free'; end if;
  if exists (select 1 from wl_asks where list_code = p_code and item_id = p_item and state = 'open') then return 'asked'; end if;
  if exists (select 1 from wl_asks where list_code = p_code and item_id = p_item and state = 'yes'
             and answered_at > now() - interval '7 days') then return 'recent'; end if;
  select coalesce(nullif(btrim(data->>'title'), ''), '') into v_title from wl_items where list_code = p_code and id = p_item;
  insert into wl_asks (list_code, item_id, asker, asker_profile, title)
    values (p_code, p_item, p_device, v_pf, left(v_title, 120)) returning id into v_ask;
  v_place := wl_ask_place(p_code) || jsonb_build_object('item', p_item, 'title', left(v_title, 120), 'ask', v_ask,
    'until', now() + interval '2 days');
  for d in select device from wl_reservations where list_code = p_code and item_id = p_item loop
    insert into wl_ask_to (ask_id, device) values (v_ask, d.device);
    perform wl_news_add(null, d.device, p_code, 'ask', v_place);
  end loop;
  -- The asker also gets a short push (owner), besides the confirmation on screen.
  perform wl_news_add(v_pf, p_device, p_code, 'ask_sent', v_place);
  return 'ok';
end $$;

-- The questions waiting for this device (the top of "Gifts I reserved", and the web pages): newest first.
-- [p_code] '' for all lists.
create or replace function wl_ask_mine(p_device text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  if length(coalesce(p_device, '')) < 8 then return '[]'::jsonb; end if;
  select coalesce(jsonb_agg(wl_ask_place(a.list_code) || jsonb_build_object(
      'ask', a.id, 'code', a.list_code, 'item', a.item_id, 'title', a.title,
      'until', a.created_at + interval '2 days') order by a.id desc), '[]'::jsonb)
    into v
    from wl_ask_to t join wl_asks a on a.id = t.ask_id
    where t.device = p_device and t.state = 'open' and a.state = 'open'
      and (coalesce(p_code, '') = '' or a.list_code = p_code);
  return v;
end $$;

-- The reserver answers: [p_yes] "Yes, I'm buying it" (the asker is told, no new ask for 7 days), or "Let someone
-- else" (the gift is free at once). Returns false when there is nothing to answer (any more).
create or replace function wl_ask_answer(p_device text, p_ask bigint, p_yes boolean)
returns boolean language plpgsql security definer set search_path = public as $$
begin
  if length(coalesce(p_device, '')) < 8 then return false; end if;
  perform 1 from wl_ask_to t join wl_asks a on a.id = t.ask_id
    where t.ask_id = p_ask and t.device = p_device and t.state = 'open' and a.state = 'open';
  if not found then return false; end if;
  if p_yes then
    update wl_ask_to set state = 'yes' where ask_id = p_ask and device = p_device;
    perform wl_ask_settle(p_ask);
  else
    perform wl_ask_free(p_ask, p_device, false);
  end if;
  return true;
end $$;

-- A browser's news on one list (its asks' results, an ended reservation): returned once, then deleted.
create or replace function wl_news_web(p_device text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  if length(coalesce(p_device, '')) < 8 then return '[]'::jsonb; end if;
  with gone as (
    delete from wl_news where device = p_device and profile is null and list_code = p_code
      and kind in ('ask_yes', 'ask_free', 'ask_ended')
    returning id, kind, data)
  select coalesce(jsonb_agg(jsonb_build_object('kind', kind, 'data', data) order by id), '[]'::jsonb) into v from gone;
  -- Questions and "you asked" are shown from wl_ask_mine and on screen; their news isn't needed in a browser.
  delete from wl_news where device = p_device and profile is null and list_code = p_code and kind in ('ask', 'ask_sent');
  return v;
end $$;

-- Someone took their own reservation away while asked: that part is free (the asker is told).
create or replace function wl_ask_unreserved()
returns trigger language plpgsql security definer set search_path = public as $$
declare t record;
begin
  for t in select x.ask_id from wl_ask_to x join wl_asks a on a.id = x.ask_id
           where a.list_code = old.list_code and a.item_id = old.item_id and x.device = old.device
             and x.state = 'open' and a.state = 'open' loop
    perform wl_ask_free(t.ask_id, old.device, false);
  end loop;
  return old;
exception when others then
  return old;
end $$;
drop trigger if exists wl_ask_unreserved on wl_reservations;
create trigger wl_ask_unreserved after delete on wl_reservations for each row execute function wl_ask_unreserved();

-- The timer: questions without an answer after 2 days make the gift free (no hold). Old news and asks are cleaned up.
create or replace function wl_ask_expire()
returns void language plpgsql security definer set search_path = public as $$
declare t record;
begin
  for t in select x.ask_id, x.device from wl_ask_to x join wl_asks a on a.id = x.ask_id
           where x.state = 'open' and a.state = 'open' and a.created_at <= now() - interval '2 days' loop
    perform wl_ask_free(t.ask_id, t.device, true);
  end loop;
  delete from wl_asks where created_at < now() - interval '60 days';
  delete from wl_news where created_at < now() - interval '30 days';
end $$;
revoke execute on function wl_ask_expire() from public, anon;

-- Every 10 minutes (pg_cron). Running this script again keeps one job.
create extension if not exists pg_cron;
select cron.schedule('wl-ask-expire', '*/10 * * * *', 'select public.wl_ask_expire()');

revoke execute on function wl_news_add(text, text, text, text, jsonb), wl_news_push(), wl_ask_unreserved() from public, anon;

grant execute on function
  wl_dev_link(text, text, text),
  wl_news_get(text, text, text),
  wl_news_done(text, text, text, bigint),
  wl_sh_invite(text, text, text, text),
  wl_sh_invited(text, text, text),
  wl_sh_inv_info(text, text, text),
  wl_sh_invites_mine(text, text),
  wl_sh_inv_accept(text, text, text),
  wl_sh_inv_decline(text, text, text),
  wl_rx_get(text, text, text, text),
  wl_rx_set(text, bigint, text, text, text, text, text),
  wl_ask(text, bigint, text, text, text),
  wl_ask_mine(text, text),
  wl_ask_answer(text, bigint, boolean),
  wl_news_web(text, text)
to anon;
