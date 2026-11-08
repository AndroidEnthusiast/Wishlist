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

revoke execute on function
  wl_live_topic(text, text),
  wl_live_send(text),
  wl_live_items_new(), wl_live_items_old(), wl_live_lists(),
  wl_live_res_new(), wl_live_res_old(), wl_live_msgs_new(), wl_live_msgs_old()
from public, anon;
grant execute on function
  wl_mine_put(text, text, text, jsonb, jsonb, jsonb)
to anon;
