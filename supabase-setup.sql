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

-- Anyone with the code can read the list. Reservations are NOT included here.
create or replace function wl_get_list(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'name', l.name,
    'cats', l.cats,
    'items', coalesce(
      (select jsonb_agg(i.data order by i.id desc) from wl_items i where i.list_code = l.code),
      '[]'::jsonb)
  ) from wl_lists l where l.code = p_code
$$;

-- Only friends' phones ever call this one. The owner's app never does.
create or replace function wl_get_reservations(p_code text, p_device text)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(
    jsonb_agg(jsonb_build_object('id', r.item_id, 'mine', r.device = p_device)),
    '[]'::jsonb)
  from wl_reservations r where r.list_code = p_code
$$;

create or replace function wl_reserve_item(p_code text, p_item bigint, p_device text)
returns boolean language plpgsql security definer set search_path = public as $$
begin
  insert into wl_reservations (list_code, item_id, device) values (p_code, p_item, p_device);
  return true;
exception when unique_violation or foreign_key_violation then
  return false;
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
  wl_unreserve_item(text, bigint, text)
to anon;
