-- New features of 7 November 2026 (version 2.146). Run this ONCE in Supabase: SQL Editor -> New query -> paste -> Run.
-- Run it AFTER the other scripts (setup, shared, friends, chat, push, groups, features, exchange, together); it only
-- adds things and is safe to run again.
--   1. The people on a collab wishlist (the small circles on its block on Home), for everyone holding its edit key.
--   2. Moving a wish to another of your own wishlists: its reservations, reactions and questions move along, and the
--      phones and browsers that reserved it can follow it to its new list.

-- ---------- 1. People on a collab wishlist ----------
-- The app members of the collab wishlist with edit key p_key (wl_sh_members, supabase-features.sql): their profile id,
-- name and photo version, oldest member first. Only for someone holding the edit key (everyone on the list has it).
create or replace function wl_sh_people(p_key text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_code text; v jsonb;
begin
  v_code := wl_sh_code(p_key);
  select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'pv', p.photo_v) order by m.joined_at), '[]'::jsonb)
    into v
    from wl_sh_members m join wl_profiles p on p.id = m.profile where m.list_code = v_code;
  return v;
end $$;

-- The photo of member p_profile of that collab wishlist ('' when there is none or they aren't on it).
create or replace function wl_sh_face(p_key text, p_profile text)
returns text language plpgsql security definer set search_path = public as $$
declare v_code text; v text;
begin
  v_code := wl_sh_code(p_key);
  select p.photo into v from wl_profiles p
    where p.id = p_profile and exists (select 1 from wl_sh_members m where m.list_code = v_code and m.profile = p_profile);
  return coalesce(v, '');
end $$;

-- ---------- 2. Moving a wish between your own wishlists ----------
-- Where a wish went: from list from_code to list to_code. Kept 60 days, so a giver's phone or browser that reserved it
-- finds it on its new list instead of being told it's gone.
create table if not exists wl_moved (
  from_code text not null,
  item_id bigint not null,
  to_code text not null,
  moved_at timestamptz not null default now(),
  primary key (from_code, item_id)
);
alter table wl_moved enable row level security;

-- Wish p_item moves from your list p_from to your list p_to (both owner tokens needed). The wish goes online on p_to
-- at once (as friends saw it on p_from); its reservations, reactions and open questions move with it. The app then
-- saves both lists as usual (the wish leaves p_from). A wish that isn't online on p_from: nothing to move.
create or replace function wl_move_item(p_from text, p_from_token text, p_to text, p_to_token text, p_item bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  if coalesce(p_from_token, '') = '' or coalesce(p_to_token, '') = ''
     or not exists (select 1 from wl_lists where code = p_from and owner_token = p_from_token)
     or not exists (select 1 from wl_lists where code = p_to and owner_token = p_to_token) then
    raise exception 'not allowed';
  end if;
  if p_from = p_to then return; end if;
  if not exists (select 1 from wl_items where list_code = p_from and id = p_item) then return; end if;
  insert into wl_items (list_code, id, data)
    select p_to, id, data from wl_items where list_code = p_from and id = p_item
    on conflict (list_code, id) do nothing;
  -- A device that already reserved the same wish on p_to (a copy) keeps that one.
  delete from wl_reservations r where r.list_code = p_from and r.item_id = p_item
    and exists (select 1 from wl_reservations t where t.list_code = p_to and t.item_id = p_item and t.device = r.device);
  update wl_reservations set list_code = p_to where list_code = p_from and item_id = p_item;
  delete from wl_reacts r where r.list_code = p_from and r.item_id = p_item
    and exists (select 1 from wl_reacts t where t.list_code = p_to and t.item_id = p_item and t.who = r.who);
  update wl_reacts set list_code = p_to where list_code = p_from and item_id = p_item;
  update wl_asks set list_code = p_to where list_code = p_from and item_id = p_item;
  insert into wl_moved (from_code, item_id, to_code) values (p_from, p_item, p_to)
    on conflict (from_code, item_id) do update set to_code = excluded.to_code, moved_at = now();
  -- Moved back: no forwarding from the list it is on again.
  delete from wl_moved where from_code = p_to and item_id = p_item;
  delete from wl_moved where moved_at < now() - interval '60 days';
end $$;

-- The wishes this device reserved that moved away from list p_code: [{item, to}], only where the device's reservation
-- is on the new list and the wish is still there. Nothing else about the new list is given away.
create or replace function wl_moved_mine(p_code text, p_device text)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('item', m.item_id, 'to', m.to_code)), '[]'::jsonb)
  from wl_moved m
  where m.from_code = p_code and length(coalesce(p_device, '')) >= 8
    and exists (select 1 from wl_reservations r where r.list_code = m.to_code and r.item_id = m.item_id and r.device = p_device)
$$;

revoke all on table wl_moved from anon;
grant execute on function
  wl_sh_people(text),
  wl_sh_face(text, text),
  wl_move_item(text, text, text, text, bigint),
  wl_moved_mine(text, text)
to anon;
