-- Wishlist groups (version 2.108). Run this in Supabase: SQL Editor -> New query -> paste everything -> Run.
-- Run it AFTER supabase-setup.sql, supabase-friends.sql and supabase-chat.sql (you already did those).
-- Safe to run again later; it won't delete anything.
--
-- How it works:
--  * A group is a few people who each bring ONE of their own wishlists. Everyone in the group sees every brought list
--    and can reserve gifts on the others' lists (never on their own; the owner never sees reservations).
--  * Members are profiles (the phone's id and secret token, see supabase-friends.sql). People without the app get a
--    "browser profile" (web = true) made by the group page; their list is an ordinary wl_lists list.
--  * The organiser invites friends (or everyone may, with "Members can invite too") and has the group link: anyone with
--    the link can join. A new link stops the old one working.
--  * News that a phone must be told once: someone said "No thanks" (to the organiser who invited), or you were
--    removed from a group (to you).
--  * Also here: "Hide my lists from …" is gone (2.108). Every hiding is switched off and can't be switched on again.

-- ---------- Hide my lists: gone ----------
update wl_friends set a_hides = false, b_hides = false where a_hides or b_hides;

-- Older apps may still call this: it only ever shows your lists again.
create or replace function wl_fr_hide(p_id text, p_token text, p_other text, p_hide boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  update wl_friends set a_hides = false where a = p_id and b = p_other;
  update wl_friends set b_hides = false where b = p_id and a = p_other;
end $$;

-- ---------- tables ----------

-- A profile made by the group web page for someone without the app, with the one wishlist they made there.
alter table wl_profiles add column if not exists web boolean not null default false;
alter table wl_profiles add column if not exists web_list text not null default '';

create table if not exists wl_groups (
  id text primary key,
  link text not null unique,              -- the secret in the group link (?g=...)
  name text not null default '',
  photo text not null default '',         -- a small JPEG as base64 text, or empty
  photo_v bigint not null default 0,
  organiser text not null references wl_profiles(id) on delete cascade,
  invite_all boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists wl_group_members (
  group_id text not null references wl_groups(id) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  -- The list this member brought. Empty when that list was deleted: they pick another one.
  list_code text references wl_lists(code) on delete set null,
  joined_at timestamptz not null default clock_timestamp(),
  primary key (group_id, profile)
);
create index if not exists wl_group_members_profile on wl_group_members (profile);
create index if not exists wl_group_members_list on wl_group_members (list_code);

create table if not exists wl_group_invites (
  group_id text not null references wl_groups(id) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  by_profile text not null references wl_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (group_id, profile)
);
create index if not exists wl_group_invites_profile on wl_group_invites (profile);

create table if not exists wl_group_news (
  id bigserial primary key,
  profile text not null references wl_profiles(id) on delete cascade,
  kind text not null,                     -- 'declined' or 'removed'
  group_id text not null default '',
  group_name text not null default '',
  who text not null default '',           -- the name of the person it is about ('declined')
  created_at timestamptz not null default now()
);
create index if not exists wl_group_news_profile on wl_group_news (profile);

alter table wl_groups enable row level security;
alter table wl_group_members enable row level security;
alter table wl_group_invites enable row level security;
alter table wl_group_news enable row level security;

-- ---------- helpers (not callable by the app) ----------

create or replace function wl_gr_is_member(p_group text, p_profile text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from wl_group_members where group_id = p_group and profile = p_profile)
$$;

create or replace function wl_gr_check_member(p_id text, p_token text, p_group text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_gr_is_member(p_group, p_id) then raise exception 'not a member'; end if;
end $$;

create or replace function wl_gr_check_organiser(p_id text, p_token text, p_group text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not exists (select 1 from wl_groups where id = p_group and organiser = p_id) then
    raise exception 'not the organiser';
  end if;
end $$;

-- The list must really be yours: its owner token has to match.
create or replace function wl_gr_check_list(p_list text, p_list_token text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if not exists (select 1 from wl_lists where code = p_list and owner_token = p_list_token) then
    raise exception 'not your list';
  end if;
end $$;

-- What a phone shows of a brought list: its name, number of wishes and newest picture (bin and private not counted).
create or replace function wl_gr_list_card(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select case when l.code is null then null else jsonb_build_object(
    'code', l.code, 'name', l.name,
    'count', (select count(*) from wl_items i
              where i.list_code = l.code and i.id <> 0
                and coalesce((i.data->>'bin')::bigint, 0) = 0
                and coalesce((i.data->>'priv')::boolean, false) = false),
    'image', coalesce((select i.data->>'image' from wl_items i
              where i.list_code = l.code and i.id <> 0 and coalesce(i.data->>'image', '') <> ''
                and coalesce((i.data->>'bin')::bigint, 0) = 0
                and coalesce((i.data->>'priv')::boolean, false) = false
              order by i.id desc limit 1), '')
  ) end
  from (select 1) x left join wl_lists l on l.code = p_code
$$;

-- One group as its members see it.
create or replace function wl_gr_json(p_group text, p_me text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', g.id, 'link', g.link, 'name', g.name, 'pv', g.photo_v, 'organiser', g.organiser,
    'invite_all', g.invite_all,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', p.id, 'name', p.name, 'pv', p.photo_v, 'web', p.web,
        -- The friend code, so "Add friend" works (app members only).
        'code', case when p.web then '' else p.code end,
        'friend', exists (select 1 from wl_friends f where f.accepted
                  and ((f.a = p_me and f.b = p.id) or (f.a = p.id and f.b = p_me))),
        'list', wl_gr_list_card(m.list_code)
      ) order by m.joined_at)
      from wl_group_members m join wl_profiles p on p.id = m.profile
      where m.group_id = g.id), '[]'::jsonb),
    -- Friends with an invite that is still open (so they aren't offered again).
    'invited', coalesce((select jsonb_agg(i.profile) from wl_group_invites i where i.group_id = g.id), '[]'::jsonb)
  ) from wl_groups g where g.id = p_group
$$;

-- ---------- for the app and the group web page ----------

-- A browser profile for someone without the app (the group web page), or its new name. Returns nothing.
create or replace function wl_gr_web_profile(p_id text, p_token text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_save(p_id, p_token, p_name);
  update wl_profiles set web = true where id = p_id and token = p_token and not exists (
    -- A profile that already has friends is an app profile: it stays one.
    select 1 from wl_friends f where f.a = p_id or f.b = p_id);
end $$;

-- The wishlist a browser member made (its code and owner token), so the web page can edit it on any device.
create or replace function wl_gr_web_list(p_id text, p_token text, p_list text, p_list_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  perform wl_gr_check_list(p_list, p_list_token);
  update wl_profiles set web_list = p_list where id = p_id and web;
end $$;

-- What a browser member's "My link" opens: their name, their list (code and owner token) and their groups.
create or replace function wl_gr_web_me(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  update wl_profiles set seen_at = now() where id = p_id;
  select jsonb_build_object(
    'name', p.name, 'web', p.web,
    'list', p.web_list,
    'list_token', coalesce((select l.owner_token from wl_lists l where l.code = p.web_list and p.web), ''),
    'groups', coalesce((select jsonb_agg(jsonb_build_object('id', g.id, 'link', g.link, 'name', g.name) order by m.joined_at)
      from wl_group_members m join wl_groups g on g.id = m.group_id where m.profile = p.id), '[]'::jsonb)
  ) into v from wl_profiles p where p.id = p_id;
  return v;
end $$;

-- A group link's preview, before joining (app join screen and web page). Null when the link doesn't work (any more).
create or replace function wl_gr_info(p_link text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', g.id, 'name', g.name, 'photo', g.photo, 'pv', g.photo_v,
    'organiser', (select p.name from wl_profiles p where p.id = g.organiser),
    'count', (select count(*) from wl_group_members m where m.group_id = g.id)
  ) from wl_groups g where g.link = upper(p_link)
$$;

-- A new group: you are its organiser and bring your list [p_list]. Returns the group.
create or replace function wl_gr_create(
  p_id text, p_token text, p_group text, p_link text, p_name text, p_list text, p_list_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  perform wl_gr_check_list(p_list, p_list_token);
  if length(coalesce(p_group, '')) < 16 or length(coalesce(p_link, '')) < 16 then raise exception 'bad key'; end if;
  if btrim(coalesce(p_name, '')) = '' then raise exception 'name needed'; end if;
  insert into wl_groups (id, link, name, organiser) values (p_group, upper(p_link), left(btrim(p_name), 60), p_id);
  insert into wl_group_members (group_id, profile, list_code) values (p_group, p_id, p_list);
  return wl_gr_json(p_group, p_id);
end $$;

-- Join with the group link (p_link), or with an invite (p_group, when you have one). You bring your list [p_list].
-- Already a member: your list is swapped. Returns the group, or null when the link doesn't work (any more).
create or replace function wl_gr_join(
  p_id text, p_token text, p_link text, p_group text, p_list text, p_list_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare g text;
begin
  perform wl_pf_check(p_id, p_token);
  perform wl_gr_check_list(p_list, p_list_token);
  if coalesce(p_link, '') <> '' then
    select id into g from wl_groups where link = upper(p_link);
  else
    select i.group_id into g from wl_group_invites i where i.group_id = p_group and i.profile = p_id;
    if g is null and wl_gr_is_member(p_group, p_id) then g := p_group; end if;
  end if;
  if g is null then return null; end if;
  insert into wl_group_members (group_id, profile, list_code) values (g, p_id, p_list)
    on conflict (group_id, profile) do update set list_code = excluded.list_code;
  delete from wl_group_invites where group_id = g and profile = p_id;
  return wl_gr_json(g, p_id);
end $$;

-- "Change my wishlist": the list you bring to this group.
create or replace function wl_gr_set_list(p_id text, p_token text, p_group text, p_list text, p_list_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_member(p_id, p_token, p_group);
  perform wl_gr_check_list(p_list, p_list_token);
  update wl_group_members set list_code = p_list where group_id = p_group and profile = p_id;
end $$;

-- Everything a phone needs: your groups, invites waiting for you, and news to tell (removed / no thanks).
create or replace function wl_gr_state(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return jsonb_build_object(
    'groups', coalesce((select jsonb_agg(wl_gr_json(m.group_id, p_id) order by m.joined_at)
      from wl_group_members m where m.profile = p_id), '[]'::jsonb),
    'invites', coalesce((select jsonb_agg(jsonb_build_object(
        'group', g.id, 'name', g.name, 'link', g.link, 'by', b.id, 'by_name', b.name, 'pv', g.photo_v,
        'count', (select count(*) from wl_group_members m where m.group_id = g.id)
      ) order by i.created_at)
      from wl_group_invites i join wl_groups g on g.id = i.group_id join wl_profiles b on b.id = i.by_profile
      where i.profile = p_id and not wl_gr_is_member(g.id, p_id)), '[]'::jsonb),
    'news', coalesce((select jsonb_agg(jsonb_build_object(
        'id', n.id, 'kind', n.kind, 'group', n.group_id, 'name', n.group_name, 'who', n.who) order by n.id)
      from wl_group_news n where n.profile = p_id), '[]'::jsonb)
  );
end $$;

-- News up to [p_upto] has been told on this phone.
create or replace function wl_gr_news_seen(p_id text, p_token text, p_upto bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_group_news where profile = p_id and id <= p_upto;
end $$;

-- A brought list of someone in your group, as a friend's list (no reservations). Null when not allowed.
create or replace function wl_gr_list(p_id text, p_token text, p_group text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_member(p_id, p_token, p_group);
  if not exists (select 1 from wl_group_members where group_id = p_group and list_code = p_code) then
    return null;
  end if;
  return wl_list_json(p_code);
end $$;

-- The photo of someone you share a group with (or a friend). '' when there is none.
create or replace function wl_gr_face(p_id text, p_token text, p_other text)
returns text language plpgsql security definer set search_path = public as $$
declare v text;
begin
  perform wl_pf_check(p_id, p_token);
  select p.photo into v from wl_profiles p where p.id = p_other and (
    exists (select 1 from wl_group_members a join wl_group_members b on a.group_id = b.group_id
            where a.profile = p_id and b.profile = p_other)
    or exists (select 1 from wl_friends f where (f.a = p_id and f.b = p_other) or (f.a = p_other and f.b = p_id)));
  return coalesce(v, '');
end $$;

-- The group's photo, for its members.
create or replace function wl_gr_photo(p_id text, p_token text, p_group text)
returns text language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_member(p_id, p_token, p_group);
  return (select photo from wl_groups where id = p_group);
end $$;

-- Invite a friend (the organiser, or any member with "Members can invite too").
create or replace function wl_gr_invite(p_id text, p_token text, p_group text, p_friend text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_member(p_id, p_token, p_group);
  if not exists (select 1 from wl_groups where id = p_group and (organiser = p_id or invite_all)) then
    raise exception 'not allowed';
  end if;
  if not exists (select 1 from wl_friends f where f.accepted
                 and ((f.a = p_id and f.b = p_friend) or (f.a = p_friend and f.b = p_id))) then
    raise exception 'not a friend';
  end if;
  if wl_gr_is_member(p_group, p_friend) then return; end if;
  insert into wl_group_invites (group_id, profile, by_profile) values (p_group, p_friend, p_id)
    on conflict (group_id, profile) do update set by_profile = excluded.by_profile, created_at = now();
end $$;

-- "No thanks": the invite goes, and whoever invited you (and the organiser) is told.
create or replace function wl_gr_decline(p_id text, p_token text, p_group text)
returns void language plpgsql security definer set search_path = public as $$
declare i wl_group_invites;
begin
  perform wl_pf_check(p_id, p_token);
  select * into i from wl_group_invites where group_id = p_group and profile = p_id;
  if i.group_id is null then return; end if;
  delete from wl_group_invites where group_id = p_group and profile = p_id;
  -- Whoever invited, and the organiser too when someone else invited (owner: the organiser is told).
  insert into wl_group_news (profile, kind, group_id, group_name, who)
    select x.p, 'declined', g.id, g.name, (select name from wl_profiles where id = p_id)
    from wl_groups g, (select i.by_profile as p union select g2.organiser from wl_groups g2 where g2.id = p_group) x
    where g.id = p_group;
end $$;

-- Leave a group. The organiser must name the next organiser first ([p_next], a member); alone, they can only delete it.
create or replace function wl_gr_leave(p_id text, p_token text, p_group text, p_next text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_member(p_id, p_token, p_group);
  if exists (select 1 from wl_groups where id = p_group and organiser = p_id) then
    if coalesce(p_next, '') = '' or p_next = p_id or not wl_gr_is_member(p_group, p_next) then
      raise exception 'pick the next organiser';
    end if;
    update wl_groups set organiser = p_next where id = p_group;
  end if;
  delete from wl_group_members where group_id = p_group and profile = p_id;
end $$;

-- The organiser removes a member. They are told ("You are removed from …").
create or replace function wl_gr_remove(p_id text, p_token text, p_group text, p_member text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_organiser(p_id, p_token, p_group);
  if p_member = p_id then raise exception 'not yourself'; end if;
  if not wl_gr_is_member(p_group, p_member) then return; end if;
  delete from wl_group_members where group_id = p_group and profile = p_member;
  insert into wl_group_news (profile, kind, group_id, group_name)
    select p_member, 'removed', g.id, g.name from wl_groups g where g.id = p_group;
end $$;

-- The organiser's settings: the name and "Members can invite too".
create or replace function wl_gr_settings(p_id text, p_token text, p_group text, p_name text, p_invite_all boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_organiser(p_id, p_token, p_group);
  if btrim(coalesce(p_name, '')) = '' then raise exception 'name needed'; end if;
  update wl_groups set name = left(btrim(p_name), 60), invite_all = coalesce(p_invite_all, false) where id = p_group;
end $$;

-- The organiser sets the group's photo (a small JPEG as base64), or '' to remove it.
create or replace function wl_gr_set_photo(p_id text, p_token text, p_group text, p_photo text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_organiser(p_id, p_token, p_group);
  if length(coalesce(p_photo, '')) > 300000 then raise exception 'photo too big'; end if;
  update wl_groups set photo = coalesce(p_photo, ''),
    photo_v = case when coalesce(p_photo, '') = '' then 0 else (extract(epoch from clock_timestamp()) * 1000)::bigint end
    where id = p_group;
end $$;

-- A new group link: the old one stops working. Invites already sent still work.
create or replace function wl_gr_new_link(p_id text, p_token text, p_group text, p_link text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_organiser(p_id, p_token, p_group);
  if length(coalesce(p_link, '')) < 16 then raise exception 'bad key'; end if;
  update wl_groups set link = upper(p_link) where id = p_group;
end $$;

-- The organiser deletes the group for everyone. Nobody's wishlist is deleted.
create or replace function wl_gr_delete(p_id text, p_token text, p_group text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_gr_check_organiser(p_id, p_token, p_group);
  delete from wl_groups where id = p_group;
end $$;

-- A browser member installed the app: the app's profile takes over their list (the app keeps its code and owner
-- token), their places in groups (also as organiser), invites, news and the reservations made in that browser.
-- The browser profile is deleted afterwards. Returns the list code (or '').
create or replace function wl_gr_takeover(
  p_web text, p_web_token text, p_web_device text, p_id text, p_token text, p_device text)
returns text language plpgsql security definer set search_path = public as $$
declare w wl_profiles;
begin
  perform wl_pf_check(p_web, p_web_token);
  perform wl_pf_check(p_id, p_token);
  if p_web = p_id then raise exception 'same profile'; end if;
  select * into w from wl_profiles where id = p_web;
  if not w.web then raise exception 'not a browser profile'; end if;
  -- Groups the app is already in keep the app's list there.
  insert into wl_group_members (group_id, profile, list_code, joined_at)
    select m.group_id, p_id, m.list_code, m.joined_at from wl_group_members m where m.profile = p_web
    on conflict (group_id, profile) do nothing;
  update wl_groups set organiser = p_id where organiser = p_web;
  insert into wl_group_invites (group_id, profile, by_profile)
    select i.group_id, p_id, i.by_profile from wl_group_invites i
    where i.profile = p_web and not wl_gr_is_member(i.group_id, p_id)
    on conflict (group_id, profile) do nothing;
  update wl_group_news set profile = p_id where profile = p_web;
  update wl_group_invites set by_profile = p_id where by_profile = p_web;
  if coalesce(p_web_device, '') <> '' and coalesce(p_device, '') <> '' and p_web_device <> p_device then
    update wl_reservations r set device = p_device where r.device = p_web_device;
  end if;
  delete from wl_profiles where id = p_web;
  return w.web_list;
end $$;

revoke execute on function
  wl_gr_is_member(text, text), wl_gr_check_member(text, text, text), wl_gr_check_organiser(text, text, text),
  wl_gr_check_list(text, text), wl_gr_list_card(text), wl_gr_json(text, text)
from public, anon;

grant execute on function
  wl_fr_hide(text, text, text, boolean),
  wl_gr_web_profile(text, text, text),
  wl_gr_web_list(text, text, text, text),
  wl_gr_web_me(text, text),
  wl_gr_info(text),
  wl_gr_create(text, text, text, text, text, text, text),
  wl_gr_join(text, text, text, text, text, text),
  wl_gr_set_list(text, text, text, text, text),
  wl_gr_state(text, text),
  wl_gr_news_seen(text, text, bigint),
  wl_gr_list(text, text, text, text),
  wl_gr_face(text, text, text),
  wl_gr_photo(text, text, text),
  wl_gr_invite(text, text, text, text),
  wl_gr_decline(text, text, text),
  wl_gr_leave(text, text, text, text),
  wl_gr_remove(text, text, text, text),
  wl_gr_settings(text, text, text, text, boolean),
  wl_gr_set_photo(text, text, text, text),
  wl_gr_new_link(text, text, text, text),
  wl_gr_delete(text, text, text),
  wl_gr_takeover(text, text, text, text, text, text)
to anon;
