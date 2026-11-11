-- Syncing like a real app (ROADMAP Stage 3, 8 November 2026). Run this in Supabase: SQL Editor -> New query -> paste
-- everything -> Run. Run it AFTER all the other supabase-*.sql scripts (you already did those). Safe to run again; it
-- only adds things, so older apps keep working exactly as before.
--
-- 1. Small saves of your own lists (S3.1): wl_mine_put sends only the wishes that changed (and the ones removed) in one
--    call, instead of wl_sync_list rewriting the whole list every time. wl_sync_list stays for older apps and for the
--    group and exchange pages.
-- 2. Live signals (S3.2): when a list's wishes or someone's messages change, Supabase Realtime tells the phones that
--    are looking at it "this changed" (never the wishes or messages themselves). The phone then fetches through the
--    same checked functions as before. Channel names are fingerprints (sha256), so they don't give away a list code or
--    a profile. A signal that can't be sent never stops a save.
-- 3. One background question (S3.3): wl_pulse answers "what changed for me?" with a short fingerprint per part (your
--    friends, chats, groups, gift exchanges, news, every collab list and every friend's list you reserved on). The
--    phone's half-hourly check then only asks about the parts whose fingerprint changed, instead of about ten calls.

-- ---------- 1. Small saves of your own lists ----------
-- p_items: the wishes that are new or changed (whole wishes, as wl_sync_list takes them; the note item id 0 too).
-- p_del: the ids of wishes that are gone from the list. p_name / p_cats: null when they didn't change.
-- Only the phone with the list's owner token may call it.
create or replace function wl_mine_put(p_code text, p_token text, p_name text, p_cats jsonb, p_items jsonb, p_del jsonb)
returns void language plpgsql security definer set search_path = public as $$
begin
  if coalesce(p_token, '') = '' or not exists (select 1 from wl_lists where code = p_code and owner_token = p_token) then
    raise exception 'not allowed';
  end if;
  if p_items is not null and jsonb_typeof(p_items) <> 'array' then raise exception 'bad items'; end if;
  if p_del is not null and jsonb_typeof(p_del) <> 'array' then raise exception 'bad del'; end if;
  if p_cats is not null and jsonb_typeof(p_cats) <> 'array' then raise exception 'bad cats'; end if;
  if exists (select 1 from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) t(e)
             where jsonb_typeof(t.e) <> 'object' or coalesce(t.e->>'id', '') !~ '^[0-9]{1,18}$') then
    raise exception 'bad items';
  end if;
  if exists (select 1 from jsonb_array_elements(coalesce(p_del, '[]'::jsonb)) t(e)
             where jsonb_typeof(t.e) <> 'number' or (t.e #>> '{}') !~ '^[0-9]{1,18}$') then
    raise exception 'bad del';
  end if;
  update wl_lists set
    name = coalesce(p_name, name),
    cats = coalesce(p_cats, cats),
    updated_at = now()
  where code = p_code;
  delete from wl_items where list_code = p_code
    and id in (select (t.e #>> '{}')::bigint from jsonb_array_elements(coalesce(p_del, '[]'::jsonb)) t(e));
  insert into wl_items (list_code, id, data)
    select p_code, (t.e->>'id')::bigint, t.e from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) t(e)
    on conflict (list_code, id) do update set data = excluded.data;
end $$;

-- ---------- 2. Live signals ----------
-- The channel of a list (friends' phones and collab members listen while it is open) and of a profile's messages.
create or replace function wl_live_topic(p_kind text, p_id text)
returns text language sql immutable set search_path = public as $$
  select 'wl' || p_kind || '-' || encode(sha256(convert_to('wishlist-' || p_kind || ':' || p_id, 'UTF8')), 'hex')
$$;

-- Sends "changed" on a channel. Never fails: a save must not be stopped by a signal (Realtime missing, a hiccup).
create or replace function wl_live_send(p_topic text)
returns void language plpgsql security definer set search_path = public as $$
begin
  begin
    perform realtime.send('{}'::jsonb, 'changed', p_topic, false);
  exception when others then
    null;
  end;
end $$;

-- A list's wishes changed (any way: the owner's phone, a collab member, the browser editor, a move, the bin).
-- One signal per list per statement, also when wl_sync_list rewrites a whole list.
create or replace function wl_live_items_new()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct list_code from n loop perform wl_live_send(wl_live_topic('l', v)); end loop;
  return null;
end $$;
create or replace function wl_live_items_old()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct list_code from o loop perform wl_live_send(wl_live_topic('l', v)); end loop;
  return null;
end $$;
drop trigger if exists wl_live_items_ins on wl_items;
drop trigger if exists wl_live_items_upd on wl_items;
drop trigger if exists wl_live_items_del on wl_items;
create trigger wl_live_items_ins after insert on wl_items referencing new table as n
  for each statement execute function wl_live_items_new();
create trigger wl_live_items_upd after update on wl_items referencing new table as n
  for each statement execute function wl_live_items_new();
create trigger wl_live_items_del after delete on wl_items referencing old table as o
  for each statement execute function wl_live_items_old();

-- The list's name, categories or note changed (collab lists change them on wl_lists).
create or replace function wl_live_lists()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct n.code from n join o on o.code = n.code
           where n.name is distinct from o.name or n.cats is distinct from o.cats loop
    perform wl_live_send(wl_live_topic('l', v));
  end loop;
  return null;
end $$;
drop trigger if exists wl_live_lists_upd on wl_lists;
create trigger wl_live_lists_upd after update on wl_lists referencing new table as n old table as o
  for each statement execute function wl_live_lists();

-- Reservations changed: everyone on a collab list sees them (the owner of an own list never listens to these).
create or replace function wl_live_res_new()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct list_code from n loop perform wl_live_send(wl_live_topic('l', v)); end loop;
  return null;
end $$;
create or replace function wl_live_res_old()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct list_code from o loop perform wl_live_send(wl_live_topic('l', v)); end loop;
  return null;
end $$;
drop trigger if exists wl_live_res_ins on wl_reservations;
drop trigger if exists wl_live_res_upd on wl_reservations;
drop trigger if exists wl_live_res_del on wl_reservations;
create trigger wl_live_res_ins after insert on wl_reservations referencing new table as n
  for each statement execute function wl_live_res_new();
create trigger wl_live_res_upd after update on wl_reservations referencing new table as n
  for each statement execute function wl_live_res_new();
create trigger wl_live_res_del after delete on wl_reservations referencing old table as o
  for each statement execute function wl_live_res_old();

-- Messages: a new one, read ticks, a reaction or a delete tells both people. The channel is made from the profile's
-- secret token, so nobody can listen to someone else's.
create or replace function wl_live_msgs_new()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct p.token from wl_profiles p where p.id in (select a from n union select b from n) loop
    perform wl_live_send(wl_live_topic('c', v));
  end loop;
  return null;
end $$;
create or replace function wl_live_msgs_old()
returns trigger language plpgsql security definer set search_path = public as $$
declare v text;
begin
  for v in select distinct p.token from wl_profiles p where p.id in (select a from o union select b from o) loop
    perform wl_live_send(wl_live_topic('c', v));
  end loop;
  return null;
end $$;
drop trigger if exists wl_live_msgs_ins on wl_messages;
drop trigger if exists wl_live_msgs_upd on wl_messages;
drop trigger if exists wl_live_msgs_del on wl_messages;
create trigger wl_live_msgs_ins after insert on wl_messages referencing new table as n
  for each statement execute function wl_live_msgs_new();
create trigger wl_live_msgs_upd after update on wl_messages referencing new table as n
  for each statement execute function wl_live_msgs_new();
create trigger wl_live_msgs_del after delete on wl_messages referencing old table as o
  for each statement execute function wl_live_msgs_old();

-- ---------- 3. One background question ----------
-- A list's fingerprint for the half-hourly check: its name, wishes and categories, and only the reservations of
-- p_device (when p_all is false), so the owner of a list can never use this to notice that someone reserved something.
-- 'gone' when there is no such list, 'off' when its link is switched off (the friends' link then shows nothing either).
create or replace function wl_pulse_list(p_code text, p_device text, p_all boolean)
returns text language plpgsql stable security definer set search_path = public as $$
declare l record;
begin
  select name, cats, link_off into l from wl_lists where code = p_code;
  if not found then return 'gone'; end if;
  if l.link_off and not p_all then return 'off'; end if;
  return md5(coalesce(l.name, '') || '|' || l.cats::text || '|' ||
    coalesce((select string_agg(i.id::text || ':' || md5(i.data::text), ',' order by i.id)
              from wl_items i where i.list_code = p_code), '') || '|' ||
    coalesce((select string_agg(r.item_id::text || ':' || r.device || ':' || r.n, ',' order by r.item_id, r.device)
              from wl_reservations r where r.list_code = p_code and (p_all or r.device = p_device)), '') || '|' ||
    coalesce((select string_agg(m.item_id::text || ':' || m.to_code, ',' order by m.item_id)
              from wl_moved m where m.from_code = p_code
                and exists (select 1 from wl_reservations r where r.list_code = m.to_code and r.item_id = m.item_id
                            and r.device = p_device)), ''));
end $$;

-- p_id / p_token: your profile (both null on a phone without one: then only the lists are answered).
-- p_keys: the edit keys of your collab lists; p_codes: the friends' lists this phone reserved on. At most 200 each.
-- Answers {"fr", "chat", "gr", "gx", "news": fingerprints, "lists": {code: fp}, "shared": {key: {"fp", "claim"}}}.
-- A part that can't be worked out is left out; the phone then simply asks about it as before.
create or replace function wl_pulse(p_id text, p_token text, p_device text, p_keys jsonb, p_codes jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb := '{}'::jsonb; v_lists jsonb := '{}'::jsonb; v_shared jsonb := '{}'::jsonb; k text; v_code text; v_me boolean;
begin
  if length(coalesce(p_device, '')) < 8 then raise exception 'not allowed'; end if;
  if (p_keys is not null and (jsonb_typeof(p_keys) <> 'array' or jsonb_array_length(p_keys) > 200))
     or (p_codes is not null and (jsonb_typeof(p_codes) <> 'array' or jsonb_array_length(p_codes) > 200)) then
    raise exception 'too many';
  end if;
  v_me := coalesce(p_id, '') <> '' and exists (select 1 from wl_profiles where id = p_id and token = p_token);
  if coalesce(p_id, '') <> '' and not v_me then raise exception 'not allowed'; end if;
  if v_me then
    begin v := v || jsonb_build_object('fr', md5(wl_fr_state(p_id, p_token)::text)); exception when others then null; end;
    begin v := v || jsonb_build_object('chat', md5(wl_chat_list(p_id, p_token)::text)); exception when others then null; end;
    begin v := v || jsonb_build_object('gr', md5(wl_gr_state(p_id, p_token)::text)); exception when others then null; end;
    begin v := v || jsonb_build_object('gx', md5(wl_gx_state(p_id, p_token)::text)); exception when others then null; end;
    begin v := v || jsonb_build_object('news', md5(wl_news_get(p_id, p_token, p_device)::text)); exception when others then null; end;
  end if;
  for k in select distinct t.e #>> '{}' from jsonb_array_elements(coalesce(p_codes, '[]'::jsonb)) t(e)
           where jsonb_typeof(t.e) = 'string' loop
    v_lists := v_lists || jsonb_build_object(k, wl_pulse_list(k, p_device, false));
  end loop;
  for k in select distinct t.e #>> '{}' from jsonb_array_elements(coalesce(p_keys, '[]'::jsonb)) t(e)
           where jsonb_typeof(t.e) = 'string' and length(t.e #>> '{}') >= 16 loop
    select code into v_code from wl_lists where edit_key = k;
    if v_code is null then
      v_shared := v_shared || jsonb_build_object(k, jsonb_build_object('fp', 'gone', 'claim', false));
    else
      v_shared := v_shared || jsonb_build_object(k, jsonb_build_object(
        'fp', wl_pulse_list(v_code, p_device, true),
        'claim', v_me and exists (select 1 from wl_lists where code = v_code and owner_next = p_id and owner_next_token <> '')));
    end if;
  end loop;
  return v || jsonb_build_object('lists', v_lists, 'shared', v_shared);
end $$;

-- 4. Your own list read back with its owner token (ROADMAP R1, 2.180), also when its link is switched off ("My
--    friends", "Only me"): the app's "Get my lists back" when the phone's own copy is damaged. Nothing for a wrong or
--    empty token or an unknown list. Reservations are not in it (the owner never sees them).
create or replace function wl_mine_get(p_code text, p_token text)
returns jsonb language sql stable security definer set search_path = public as $$
  select wl_list_json(l.code) from wl_lists l
  where l.code = p_code and l.owner_token = p_token and coalesce(p_token, '') <> ''
$$;

revoke execute on function
  wl_live_topic(text, text),
  wl_live_send(text),
  wl_live_items_new(), wl_live_items_old(), wl_live_lists(),
  wl_live_res_new(), wl_live_res_old(), wl_live_msgs_new(), wl_live_msgs_old(),
  wl_pulse_list(text, text, boolean)
from public, anon;
grant execute on function
  wl_mine_put(text, text, text, jsonb, jsonb, jsonb),
  wl_pulse(text, text, text, jsonb, jsonb),
  wl_mine_get(text, text)
to anon;
