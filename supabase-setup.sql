-- Wishlist sharing setup. Run this ONCE in Supabase: SQL Editor -> New query -> paste -> Run.
-- Safe to run again later; it won't delete your data.

create table if not exists wl_lists (
  code text primary key,
  owner_token text not null,
  name text not null default 'My wishlist',
  cats jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists wl_items (
  list_code text not null references wl_lists(code) on delete cascade,
  id bigint not null,
  data jsonb not null,
  primary key (list_code, id)
);

create table if not exists wl_reservations (
  list_code text not null,
  item_id bigint not null,
  device text not null,
  created_at timestamptz not null default now(),
  primary key (list_code, item_id),
  foreign key (list_code, item_id) references wl_items(list_code, id) on delete cascade
);

-- Tables are locked. The app can only use the functions below.
alter table wl_lists enable row level security;
alter table wl_items enable row level security;
alter table wl_reservations enable row level security;

create or replace function wl_create_list(p_code text, p_token text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
begin
  insert into wl_lists (code, owner_token, name) values (p_code, p_token, p_name);
end $$;

create or replace function wl_sync_list(p_code text, p_token text, p_name text, p_cats jsonb, p_items jsonb)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from wl_lists where code = p_code and owner_token = p_token) then
    raise exception 'not allowed';
  end if;
  update wl_lists set name = p_name, cats = p_cats, updated_at = now() where code = p_code;
  delete from wl_items
    where list_code = p_code
    and id not in (select (t.e->>'id')::bigint from jsonb_array_elements(p_items) as t(e));
  insert into wl_items (list_code, id, data)
    select p_code, (t.e->>'id')::bigint, t.e from jsonb_array_elements(p_items) as t(e)
    on conflict (list_code, id) do update set data = excluded.data;
end $$;

create or replace function wl_delete_list(p_code text, p_token text)
returns void language sql security definer set search_path = public as $$
  delete from wl_lists where code = p_code and owner_token = p_token
$$;

-- "Who can see this list" (2.95): with link_off the friends' link doesn't open the list; friends in the app still see
-- it (supabase-friends.sql, wl_fr_list).
alter table wl_lists add column if not exists link_off boolean not null default false;
-- "Owned by …" (2.102): the owner's profile name, shown under the list name to friends (app and web page).
alter table wl_lists add column if not exists owner_name text not null default '';

-- Anyone with the code can read the list, unless its link is switched off. Reservations are NOT included here.
create or replace function wl_get_list(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'name', l.name,
    'owner', l.owner_name,
    'cats', l.cats,
    'items', coalesce(
      (select jsonb_agg(i.data order by i.id desc) from wl_items i where i.list_code = l.code),
      '[]'::jsonb)
  ) from wl_lists l where l.code = p_code and not l.link_off
$$;

-- Only friends' phones ever call this one. The owner's app never does.
-- Quantity (6 November 2026, version 2.128): a wish can be wanted more than once ("qty" in the wish, 1 to 99), and
-- every giver reserves how many they'll buy. So a wish can now have one reservation per giver, each with a number n.
alter table wl_reservations add column if not exists n int not null default 1;
do $$
begin
  if (select count(*) from information_schema.key_column_usage
      where table_name = 'wl_reservations' and constraint_name = 'wl_reservations_pkey') = 2 then
    alter table wl_reservations drop constraint wl_reservations_pkey;
    alter table wl_reservations add primary key (list_code, item_id, device);
  end if;
end $$;

-- How many of a wish are wanted (1 when not set).
create or replace function wl_qty(p_code text, p_item bigint)
returns int language sql stable security definer set search_path = public as $$
  select greatest(1, least(99, coalesce((
    select case when (data->>'qty') ~ '^[0-9]{1,2}$' then (data->>'qty')::int end
    from wl_items where list_code = p_code and id = p_item), 1)))
$$;
revoke execute on function wl_qty(text, bigint) from public, anon;

-- One line per reserved wish (older apps read only "id" and "mine"): "n" how many are reserved, "my" how many by this
-- device, "full" when all are taken.
create or replace function wl_get_reservations(p_code text, p_device text)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', x.item_id, 'mine', x.mine, 'n', x.n, 'my', x.my, 'full', x.n >= x.q)), '[]'::jsonb)
  from (
    select r.item_id, bool_or(r.device = p_device) as mine, sum(r.n)::int as n,
           coalesce(sum(r.n) filter (where r.device = p_device), 0)::int as my, wl_qty(p_code, r.item_id) as q
    from wl_reservations r where r.list_code = p_code group by r.item_id
  ) x
$$;

-- Reserve (the way older apps do it): everything that is still free. False when nothing is left.
create or replace function wl_reserve_item(p_code text, p_item bigint, p_device text)
returns boolean language plpgsql security definer set search_path = public as $$
declare v_q int; v_taken int;
begin
  perform 1 from wl_items where list_code = p_code and id = p_item for update;
  if not found then return false; end if;
  v_q := wl_qty(p_code, p_item);
  select coalesce(sum(n), 0) into v_taken from wl_reservations where list_code = p_code and item_id = p_item;
  if v_taken >= v_q then return false; end if;
  insert into wl_reservations (list_code, item_id, device, n) values (p_code, p_item, p_device, v_q - v_taken);
  return true;
exception when unique_violation or foreign_key_violation then
  return false;
end $$;

-- Reserve [p_n] of a wish for this device (2.128), replacing its earlier number; 0 takes it away. False when that
-- many aren't free any more.
create or replace function wl_reserve_count(p_code text, p_item bigint, p_device text, p_n int)
returns boolean language plpgsql security definer set search_path = public as $$
declare v_q int; v_others int;
begin
  if coalesce(p_device, '') = '' then return false; end if;
  perform 1 from wl_items where list_code = p_code and id = p_item for update;
  if not found then return false; end if;
  if coalesce(p_n, 0) <= 0 then
    delete from wl_reservations where list_code = p_code and item_id = p_item and device = p_device;
    return true;
  end if;
  v_q := wl_qty(p_code, p_item);
  select coalesce(sum(n), 0) into v_others from wl_reservations
    where list_code = p_code and item_id = p_item and device <> p_device;
  if v_others + p_n > v_q then return false; end if;
  insert into wl_reservations (list_code, item_id, device, n) values (p_code, p_item, p_device, p_n)
  on conflict (list_code, item_id, device) do update set n = excluded.n, created_at = now();
  return true;
end $$;

create or replace function wl_unreserve_item(p_code text, p_item bigint, p_device text)
returns void language sql security definer set search_path = public as $$
  delete from wl_reservations where list_code = p_code and item_id = p_item and device = p_device
$$;

grant execute on function
  wl_create_list(text, text, text),
  wl_sync_list(text, text, text, jsonb, jsonb),
  wl_delete_list(text, text),
  wl_get_list(text),
  wl_get_reservations(text, text),
  wl_reserve_item(text, bigint, text),
  wl_reserve_count(text, bigint, text, int),
  wl_unreserve_item(text, bigint, text)
to anon;
