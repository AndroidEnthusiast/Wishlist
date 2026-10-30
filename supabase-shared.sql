-- Shared wishlists setup (two or more people edit one list). Run this ONCE in Supabase:
-- SQL Editor -> New query -> paste -> Run.
-- Run supabase-setup.sql and supabase-photos.sql first (you already did). Safe to run again later; it won't delete anything.
-- If you ran an earlier version of this file: run this one again, completely. It adds the "merge" functions (version 2.16).
--
-- How it works:
--  * A shared list is a normal row in wl_lists, so the friends' page (?c=CODE) and reservations work unchanged.
--  * It also has a secret "edit key". Whoever knows the key can add, change and delete wishes (the invite link contains it).
--  * The creator's phone also keeps the list's owner token. Only that can reset the key or delete the list.
--  * Everyone on a shared list sees its reservations (2.22): the list is only for the givers. They use the normal
--    reservation functions from supabase-setup.sql with the list code; nothing below is needed for that.
--  * Two people editing at the same time don't undo each other: a save only writes the fields the person changed
--    (wl_sh_put_item), and the name, the note and every category change is its own small step, done inside the database.

alter table wl_lists add column if not exists edit_key text;
create unique index if not exists wl_lists_edit_key_uq on wl_lists (edit_key) where edit_key is not null;

-- Finds the list for an edit key. Raises 'not allowed' for a wrong key.
create or replace function wl_sh_code(p_key text)
returns text language plpgsql stable security definer set search_path = public as $$
declare v text;
begin
  if p_key is null or length(p_key) < 16 then raise exception 'not allowed'; end if;
  select code into v from wl_lists where edit_key = p_key;
  if v is null then raise exception 'not allowed'; end if;
  return v;
end $$;

create or replace function wl_sh_create(p_code text, p_key text, p_token text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_key is null or length(p_key) < 16 or p_token is null or length(p_token) < 16 then
    raise exception 'bad key';
  end if;
  insert into wl_lists (code, owner_token, name, edit_key) values (p_code, p_token, p_name, p_key);
end $$;

-- The whole list for an editor: name, categories and wishes (no reservations). Null if the key is wrong.
create or replace function wl_sh_get(p_key text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'code', l.code,
    'name', l.name,
    'cats', l.cats,
    'items', coalesce(
      (select jsonb_agg(i.data order by i.id desc) from wl_items i where i.list_code = l.code),
      '[]'::jsonb)
  ) from wl_lists l
  where p_key is not null and length(p_key) >= 16 and l.edit_key = p_key
$$;

-- Just enough for the invite page: the name and how many wishes. Null if the key is wrong.
create or replace function wl_sh_info(p_key text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'name', l.name,
    'count', (select count(*) from wl_items i where i.list_code = l.code and i.id > 0)
  ) from wl_lists l
  where p_key is not null and length(p_key) >= 16 and l.edit_key = p_key
$$;

-- Adds or changes ONE wish. Other wishes are not touched, so two people can edit at the same time.
--  * p_fields empty (null): the whole wish is replaced (a new wish).
--  * p_fields given: ONLY those fields are written, on top of what is stored right now. A field that is not in p_item is
--    cleared. So if one person changes the price and another the note, both stay.
--  * If the wish was deleted in the meantime, the whole wish is stored again (an edit is never lost silently).
drop function if exists wl_sh_put_item(text, jsonb);
create or replace function wl_sh_put_item(p_key text, p_item jsonb, p_fields text[] default null)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text; v_id bigint; v_cur jsonb; f text;
begin
  v_code := wl_sh_code(p_key);
  v_id := (p_item->>'id')::bigint;
  if v_id is null or v_id <= 0 then raise exception 'bad id'; end if;
  if length(p_item::text) > 20000 then raise exception 'too big'; end if;
  -- lock this wish while we merge, so two saves at the same moment are applied one after the other
  select data into v_cur from wl_items where list_code = v_code and id = v_id for update;
  if v_cur is null or p_fields is null then
    insert into wl_items (list_code, id, data) values (v_code, v_id, p_item)
    on conflict (list_code, id) do update set data = excluded.data;
  else
    foreach f in array p_fields loop
      if f in ('id', 'kind') then continue; end if;
      if p_item ? f then
        v_cur := jsonb_set(v_cur, array[f], p_item -> f, true);
      else
        v_cur := v_cur - f;
      end if;
    end loop;
    update wl_items set data = v_cur where list_code = v_code and id = v_id;
  end if;
  update wl_lists set updated_at = now() where code = v_code;
end $$;

-- Deletes ONE wish (and any reservation on it).
create or replace function wl_sh_del_item(p_key text, p_id bigint)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  if p_id <= 0 then raise exception 'bad id'; end if;
  delete from wl_items where list_code = v_code and id = p_id;
  update wl_lists set updated_at = now() where code = v_code;
end $$;

-- Name, categories and the note for friends (kept as the special item with id 0, like on a normal list).
create or replace function wl_sh_set_meta(p_key text, p_name text, p_cats jsonb, p_about text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  update wl_lists set name = p_name, cats = p_cats, updated_at = now() where code = v_code;
  if coalesce(p_about, '') = '' then
    delete from wl_items where list_code = v_code and id = 0;
  else
    insert into wl_items (list_code, id, data)
    values (v_code, 0, jsonb_build_object('id', 0, 'kind', 'about', 'about', left(p_about, 300), 'url', '', 'title', ''))
    on conflict (list_code, id) do update set data = excluded.data;
  end if;
end $$;

-- The same, but one small step at a time (version 2.16 uses these; set_meta above stays for older apps).
create or replace function wl_sh_set_name(p_key text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  update wl_lists set name = p_name, updated_at = now() where code = v_code;
end $$;

create or replace function wl_sh_set_about(p_key text, p_about text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  if coalesce(p_about, '') = '' then
    delete from wl_items where list_code = v_code and id = 0;
  else
    insert into wl_items (list_code, id, data)
    values (v_code, 0, jsonb_build_object('id', 0, 'kind', 'about', 'about', left(p_about, 300), 'url', '', 'title', ''))
    on conflict (list_code, id) do update set data = excluded.data;
  end if;
  update wl_lists set updated_at = now() where code = v_code;
end $$;

-- Adds a category at the end (nothing happens if it is there already).
create or replace function wl_sh_cat_add(p_key text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  if coalesce(trim(p_name), '') = '' then raise exception 'bad name'; end if;
  update wl_lists set cats = coalesce(cats, '[]'::jsonb) || to_jsonb(p_name), updated_at = now()
    where code = v_code and not (coalesce(cats, '[]'::jsonb) ? p_name);
end $$;

-- Renames a category and moves every wish in it, also wishes that were added a second ago by someone else.
create or replace function wl_sh_cat_rename(p_key text, p_old text, p_new text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  if coalesce(trim(p_new), '') = '' then raise exception 'bad name'; end if;
  update wl_lists set updated_at = now(), cats = (
      select coalesce(jsonb_agg(d.e order by d.first_pos), '[]'::jsonb)
      from (
        select s.e, min(s.ord) as first_pos
        from (
          select case when t.x = p_old then p_new else t.x end as e, t.ord
          from jsonb_array_elements_text(coalesce(wl_lists.cats, '[]'::jsonb)) with ordinality as t(x, ord)
        ) s
        group by s.e
      ) d)
    where code = v_code;
  update wl_items set data = jsonb_set(data, '{cat}', to_jsonb(p_new))
    where list_code = v_code and id > 0 and data ->> 'cat' = p_old;
end $$;

-- Removes a category. The wishes in it stay, without a category.
create or replace function wl_sh_cat_remove(p_key text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  v_code := wl_sh_code(p_key);
  update wl_lists set updated_at = now(), cats = (
      select coalesce(jsonb_agg(t.x order by t.ord), '[]'::jsonb)
      from jsonb_array_elements_text(coalesce(wl_lists.cats, '[]'::jsonb)) with ordinality as t(x, ord)
      where t.x <> p_name)
    where code = v_code;
  update wl_items set data = jsonb_set(data, '{cat}', '""'::jsonb)
    where list_code = v_code and id > 0 and data ->> 'cat' = p_name;
end $$;

-- Creator only: gives the list a new edit key. The old invite link stops working for everyone.
create or replace function wl_sh_reset_key(p_code text, p_token text, p_new_key text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if p_new_key is null or length(p_new_key) < 16 then raise exception 'bad key'; end if;
  update wl_lists set edit_key = p_new_key
    where code = p_code and owner_token = p_token and edit_key is not null;
  if not found then raise exception 'not allowed'; end if;
end $$;

-- Pictures: editors may add and delete pictures in the folder of a shared list.
-- (Same check as before, plus the edit key sent in a header called x-edit-key.)
create or replace function public.wl_owner_ok(p_code text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from wl_lists
    where code = p_code
      and (
        owner_token = coalesce(nullif(current_setting('request.headers', true), '')::json ->> 'x-owner-token', '')
        or (edit_key is not null
            and edit_key = coalesce(nullif(current_setting('request.headers', true), '')::json ->> 'x-edit-key', ''))
      )
  )
$$;

grant execute on function
  wl_sh_create(text, text, text, text),
  wl_sh_get(text),
  wl_sh_info(text),
  wl_sh_put_item(text, jsonb, text[]),
  wl_sh_del_item(text, bigint),
  wl_sh_set_meta(text, text, jsonb, text),
  wl_sh_set_name(text, text),
  wl_sh_set_about(text, text),
  wl_sh_cat_add(text, text),
  wl_sh_cat_rename(text, text, text),
  wl_sh_cat_remove(text, text),
  wl_sh_reset_key(text, text, text),
  public.wl_owner_ok(text)
to anon;
