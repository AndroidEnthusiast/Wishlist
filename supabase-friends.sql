-- Friends setup (version 2.87): profiles, friend codes, friend requests and the lists friends may see.
-- Run this ONCE in Supabase: SQL Editor -> New query -> paste everything -> Run.
-- Run supabase-setup.sql first (you already did). Safe to run again later; it won't delete anything.
--
-- How it works:
--  * Every phone that opens Friends gets a profile: a random id and a secret token (both only on that phone and here),
--    a name, an optional small photo and a short friend code that you can give to people.
--  * Someone enters your code: that is a friend request. You approve it (or decline it). Until then nothing is shared.
--  * You choose which of your wishlists friends can see (the list's settings). They see those lists and reserve gifts;
--    reservations work exactly as on a friends' link, so you never see them.
--  * You can hide your lists from one friend (they aren't told), or unfollow them (you are no longer friends).
--  * A new friend code stops the old one working. Friends you already have stay.

create table if not exists wl_profiles (
  id text primary key,
  token text not null,
  code text not null unique,
  name text not null default '',
  photo text not null default '',      -- a small JPEG as base64 text, or empty
  photo_v bigint not null default 0,   -- changes every time the photo changes, so phones know to load it again
  created_at timestamptz not null default now(),
  seen_at timestamptz not null default now()
);

-- One row per pair. "a" entered the code of "b"; b approves. Hiding works per side.
create table if not exists wl_friends (
  a text not null references wl_profiles(id) on delete cascade,
  b text not null references wl_profiles(id) on delete cascade,
  accepted boolean not null default false,
  a_hides boolean not null default false,
  b_hides boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (a, b)
);
create index if not exists wl_friends_b on wl_friends (b);

-- The lists a profile shows to its friends. A list that is deleted online disappears from here by itself.
create table if not exists wl_profile_lists (
  profile text not null references wl_profiles(id) on delete cascade,
  list_code text not null references wl_lists(code) on delete cascade,
  primary key (profile, list_code)
);

-- Tables are locked. The app can only use the functions below.
alter table wl_profiles enable row level security;
alter table wl_friends enable row level security;
alter table wl_profile_lists enable row level security;

-- Checks the phone's id and token. Raises 'not allowed' when they don't match.
create or replace function wl_pf_check(p_id text, p_token text)
returns void language plpgsql stable security definer set search_path = public as $$
begin
  if not exists (select 1 from wl_profiles where id = p_id and token = p_token) then
    raise exception 'not allowed';
  end if;
end $$;

-- A new random friend code: 6 letters and digits, without look-alikes (no 0/O, 1/I).
create or replace function wl_pf_new_code_text()
returns text language plpgsql volatile security definer set search_path = public as $$
declare
  abc text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  c text;
begin
  loop
    c := '';
    for i in 1..6 loop
      c := c || substr(abc, 1 + floor(random() * length(abc))::int, 1);
    end loop;
    exit when not exists (select 1 from wl_profiles where code = c);
  end loop;
  return c;
end $$;

-- Makes the profile the first time, otherwise saves the name. Returns the friend code.
create or replace function wl_pf_save(p_id text, p_token text, p_name text)
returns text language plpgsql security definer set search_path = public as $$
declare v text;
begin
  if p_id is null or length(p_id) < 16 or p_token is null or length(p_token) < 16 then
    raise exception 'bad key';
  end if;
  select code into v from wl_profiles where id = p_id;
  if v is null then
    v := wl_pf_new_code_text();
    insert into wl_profiles (id, token, code, name) values (p_id, p_token, v, left(coalesce(p_name, ''), 30));
    return v;
  end if;
  perform wl_pf_check(p_id, p_token);
  update wl_profiles set name = left(coalesce(p_name, ''), 30), seen_at = now() where id = p_id;
  return v;
end $$;

-- The profile photo (a small JPEG as base64), or '' to remove it.
create or replace function wl_pf_photo(p_id text, p_token text, p_photo text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_photo, '')) > 200000 then raise exception 'photo too big'; end if;
  update wl_profiles
    set photo = coalesce(p_photo, ''), photo_v = (extract(epoch from clock_timestamp()) * 1000)::bigint
    where id = p_id;
end $$;

-- A new friend code; the old one stops working. Friends you already have stay.
create or replace function wl_pf_new_code(p_id text, p_token text)
returns text language plpgsql security definer set search_path = public as $$
declare v text;
begin
  perform wl_pf_check(p_id, p_token);
  v := wl_pf_new_code_text();
  update wl_profiles set code = v where id = p_id;
  return v;
end $$;

-- Which of your lists friends can see. p_lists: [{"code": "...", "token": "..."}] with each list's owner token,
-- so only lists that are really yours are accepted. Replaces what was there.
create or replace function wl_pf_lists(p_id text, p_token text, p_lists jsonb)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_profile_lists where profile = p_id;
  insert into wl_profile_lists (profile, list_code)
    select p_id, l.code
    from jsonb_array_elements(coalesce(p_lists, '[]'::jsonb)) as t(e)
    join wl_lists l on l.code = t.e->>'code' and l.owner_token = t.e->>'token'
    on conflict do nothing;
end $$;

-- You entered someone's friend code. Returns {"status": ..., "name": ...}:
--   'sent'    a request now waits for their approval
--   'friends' you are friends now (they had already asked you)
--   'already' you are already friends, or your request is still waiting
--   'self'    that is your own code
--   'none'    no one has that code
create or replace function wl_fr_add(p_id text, p_token text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  o wl_profiles;
  r wl_friends;
begin
  perform wl_pf_check(p_id, p_token);
  select * into o from wl_profiles where code = upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  if o.id is null then return jsonb_build_object('status', 'none'); end if;
  if o.id = p_id then return jsonb_build_object('status', 'self'); end if;
  select * into r from wl_friends where (a = p_id and b = o.id) or (a = o.id and b = p_id);
  if r.a is null then
    insert into wl_friends (a, b) values (p_id, o.id);
    return jsonb_build_object('status', 'sent', 'name', o.name);
  end if;
  if not r.accepted and r.a = o.id then
    -- They asked you first: entering their code says yes.
    update wl_friends set accepted = true where a = r.a and b = r.b;
    return jsonb_build_object('status', 'friends', 'name', o.name);
  end if;
  return jsonb_build_object('status', 'already', 'name', o.name);
end $$;

-- Approve (p_yes = true) or decline a request someone sent you.
create or replace function wl_fr_answer(p_id text, p_token text, p_other text, p_yes boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if p_yes then
    update wl_friends set accepted = true where a = p_other and b = p_id;
  else
    delete from wl_friends where a = p_other and b = p_id and not accepted;
  end if;
end $$;

-- Unfollow a friend, or take back a request you sent. They are not told.
create or replace function wl_fr_remove(p_id text, p_token text, p_other text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_friends where (a = p_id and b = p_other) or (a = p_other and b = p_id);
end $$;

-- Hide your lists from one friend (p_hide = true), or show them again. They are not told.
create or replace function wl_fr_hide(p_id text, p_token text, p_other text, p_hide boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  update wl_friends set a_hides = p_hide where a = p_id and b = p_other;
  update wl_friends set b_hides = p_hide where b = p_id and a = p_other;
end $$;

-- Everything the Friends tab shows: your code, your friends (with the lists they let you see), requests waiting for
-- you, and requests you sent that are still waiting. Photos are not included (see wl_fr_photo), only their version.
create or replace function wl_fr_state(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  update wl_profiles set seen_at = now() where id = p_id;
  select jsonb_build_object(
    'code', (select code from wl_profiles where id = p_id),
    'friends', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', p.id, 'name', p.name, 'pv', p.photo_v, 'since', f.created_at,
        'hidden', case when f.a = p_id then f.a_hides else f.b_hides end,
        'lists', case when (case when f.a = p_id then f.b_hides else f.a_hides end) then '[]'::jsonb else coalesce((
          select jsonb_agg(jsonb_build_object(
            'code', l.code, 'name', l.name,
            'count', (select count(*) from wl_items i
                      where i.list_code = l.code and i.id <> 0
                        and coalesce((i.data->>'bin')::bigint, 0) = 0
                        and coalesce((i.data->>'priv')::boolean, false) = false)
          ) order by l.name)
          from wl_profile_lists pl join wl_lists l on l.code = pl.list_code
          where pl.profile = p.id), '[]'::jsonb) end
      ) order by lower(p.name))
      from wl_friends f join wl_profiles p on p.id = case when f.a = p_id then f.b else f.a end
      where f.accepted and (f.a = p_id or f.b = p_id)), '[]'::jsonb),
    'incoming', coalesce((
      select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'pv', p.photo_v) order by f.created_at)
      from wl_friends f join wl_profiles p on p.id = f.a
      where f.b = p_id and not f.accepted), '[]'::jsonb),
    'outgoing', coalesce((
      select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'pv', p.photo_v) order by f.created_at)
      from wl_friends f join wl_profiles p on p.id = f.b
      where f.a = p_id and not f.accepted), '[]'::jsonb)
  ) into v;
  return v;
end $$;

-- The photo of a friend, or of someone in a request with you. '' when there is none or you're not connected.
create or replace function wl_fr_photo(p_id text, p_token text, p_other text)
returns text language plpgsql security definer set search_path = public as $$
declare v text;
begin
  perform wl_pf_check(p_id, p_token);
  select p.photo into v from wl_profiles p
    where p.id = p_other
      and exists (select 1 from wl_friends f where (f.a = p_id and f.b = p_other) or (f.a = p_other and f.b = p_id));
  return coalesce(v, '');
end $$;

-- One list of a friend, as the friends' page sees it (no reservations). Null unless that friend lets you see it.
create or replace function wl_fr_list(p_id text, p_token text, p_other text, p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not exists (
    select 1 from wl_friends f
    where f.accepted
      and ((f.a = p_id and f.b = p_other and not f.b_hides) or (f.a = p_other and f.b = p_id and not f.a_hides))
  ) or not exists (select 1 from wl_profile_lists where profile = p_other and list_code = p_code) then
    return null;
  end if;
  return wl_get_list(p_code);
end $$;

revoke execute on function wl_pf_check(text, text), wl_pf_new_code_text() from public, anon;

grant execute on function
  wl_pf_save(text, text, text),
  wl_pf_photo(text, text, text),
  wl_pf_new_code(text, text),
  wl_pf_lists(text, text, jsonb),
  wl_fr_add(text, text, text),
  wl_fr_answer(text, text, text, boolean),
  wl_fr_remove(text, text, text),
  wl_fr_hide(text, text, text, boolean),
  wl_fr_state(text, text),
  wl_fr_photo(text, text, text),
  wl_fr_list(text, text, text, text)
to anon;
