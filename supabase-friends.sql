-- Friends setup (version 2.87, extended in 2.95): profiles, friend codes, friend requests, the lists friends may see,
-- and (2.95) birthdays, an "I like…" line and "Who can see this list" (a list's link can be switched off).
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

-- 2.95: an optional birthday ("MM-DD", no year) and an "I like…" line, both visible to friends.
alter table wl_profiles add column if not exists birthday text not null default '';
alter table wl_profiles add column if not exists bio text not null default '';

-- The lists a profile shows to its friends. A list that is deleted online disappears from here by itself.
create table if not exists wl_profile_lists (
  profile text not null references wl_profiles(id) on delete cascade,
  list_code text not null references wl_lists(code) on delete cascade,
  primary key (profile, list_code)
);

-- Block (ROADMAP S4.8; owner, 12 November 2026): [who] blocked [whom]. They are no longer friends, [whom] can't send a
-- friend request (it seems sent, but nothing arrives) and the chat is hidden for [who]. [whom] is not told.
-- Made and undone with wl_block / wl_unblock (supabase-privacy.sql).
create table if not exists wl_blocks (
  who text not null references wl_profiles(id) on delete cascade,
  whom text not null references wl_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (who, whom)
);
create index if not exists wl_blocks_whom on wl_blocks (whom);

-- Tables are locked. The app can only use the functions below.
alter table wl_blocks enable row level security;
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

-- Photos as files (ROADMAP S4.1, 2.185): profile, group and gift exchange photos live in Supabase Storage (bucket
-- "people-photos", set up in supabase-photos.sql) under a random name that changes with every new photo; the database
-- keeps only that address. The functions that hand a photo out give the address when there is one, else the old
-- embedded photo (base64), so photos saved by older apps keep showing. An app older than 2.185 can't show a photo that
-- moved (it shows the first letter instead).
alter table wl_profiles add column if not exists photo_url text not null default '';

-- True for the address of a file in our "people-photos" bucket, in the folder [p_folder] (for example 'p/<profile>').
create or replace function wl_people_url_ok(p_url text, p_folder text)
returns boolean language sql immutable as $$
  select coalesce(p_url, '') ~ ('^https://[a-z0-9.-]+/storage/v1/object/public/people-photos/' || p_folder || '/[A-Za-z0-9_-]{16,64}\.jpg$')
$$;

-- The profile photo (a small JPEG as base64, from apps older than 2.185), or '' to remove it.
create or replace function wl_pf_photo(p_id text, p_token text, p_photo text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_photo, '')) > 200000 then raise exception 'photo too big'; end if;
  update wl_profiles
    set photo = coalesce(p_photo, ''), photo_url = '', photo_v = (extract(epoch from clock_timestamp()) * 1000)::bigint
    where id = p_id;
end $$;

-- The profile photo as a file (2.185): its address in people-photos/p/<your id>/, or '' to remove it. Returns the
-- address it replaces, so the app can delete that file (the old address then stops working).
create or replace function wl_pf_photo_url(p_id text, p_token text, p_url text)
returns text language plpgsql security definer set search_path = public as $$
declare v_old text;
begin
  perform wl_pf_check(p_id, p_token);
  if coalesce(p_url, '') <> '' and not wl_people_url_ok(p_url, 'p/' || p_id) then raise exception 'bad photo address'; end if;
  select photo_url into v_old from wl_profiles where id = p_id;
  update wl_profiles
    set photo_url = coalesce(p_url, ''), photo = '',
        photo_v = case when coalesce(p_url, '') = '' then 0 else (extract(epoch from clock_timestamp()) * 1000)::bigint end
    where id = p_id;
  return coalesce(v_old, '');
end $$;

-- Who may put or delete a file in people-photos (the Storage rules in supabase-photos.sql ask this): the folder is
-- p/<profile> (that profile), g/<group> (its organiser) or x/<gift exchange> (its organiser); the app proves who it is
-- with the headers x-profile-id and x-profile-token.
create or replace function wl_people_photo_ok(p_name text)
returns boolean language plpgsql stable security definer set search_path = public as $$
declare v_h json; v_id text; v_tok text; v_kind text; v_of text;
begin
  begin
    v_h := nullif(current_setting('request.headers', true), '')::json;
  exception when others then
    return false;
  end;
  v_id := coalesce(v_h ->> 'x-profile-id', ''); v_tok := coalesce(v_h ->> 'x-profile-token', '');
  if v_id = '' or v_tok = '' or not exists (select 1 from wl_profiles where id = v_id and token = v_tok) then return false; end if;
  v_kind := split_part(coalesce(p_name, ''), '/', 1); v_of := split_part(coalesce(p_name, ''), '/', 2);
  if split_part(p_name, '/', 4) <> '' or split_part(p_name, '/', 3) !~ '^[A-Za-z0-9_-]{16,64}\.jpg$' then return false; end if;
  return case v_kind
    when 'p' then v_of = v_id
    when 'g' then exists (select 1 from wl_groups where id = v_of and organiser = v_id)
    when 'x' then exists (select 1 from wl_gx where id = v_of and organiser = v_id)
    else false end;
end $$;

-- Your birthday ("MM-DD" or '') and "I like…" line (2.95).
create or replace function wl_pf_about(p_id text, p_token text, p_birthday text, p_bio text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if coalesce(p_birthday, '') <> '' and p_birthday !~ '^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$' then
    raise exception 'bad birthday';
  end if;
  update wl_profiles set birthday = coalesce(p_birthday, ''), bio = left(coalesce(p_bio, ''), 120) where id = p_id;
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
  -- Every attempt counts, also a wrong code (ROADMAP S4.2, supabase-limits.sql): friend codes are short, so guessing
  -- them must be slow. Before supabase-limits.sql is run, nothing is counted.
  begin
    perform wl_limit_hit('pf:' || p_id, 'friend_code', 30, 'hour');
  exception when undefined_function then null;
  end;
  select * into o from wl_profiles where code = upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  if o.id is null then return jsonb_build_object('status', 'none'); end if;
  if o.id = p_id then return jsonb_build_object('status', 'self'); end if;
  -- They blocked you: it seems sent, nothing arrives (they are not asked again). You blocked them: entering their
  -- code again undoes your block.
  if exists (select 1 from wl_blocks where who = o.id and whom = p_id) then
    return jsonb_build_object('status', 'sent', 'name', o.name);
  end if;
  delete from wl_blocks where who = p_id and whom = o.id;
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
        'id', p.id, 'name', p.name, 'pv', p.photo_v, 'since', f.created_at, 'birthday', p.birthday, 'bio', p.bio,
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
  select coalesce(nullif(p.photo_url, ''), p.photo) into v from wl_profiles p
    where p.id = p_other
      and exists (select 1 from wl_friends f where (f.a = p_id and f.b = p_other) or (f.a = p_other and f.b = p_id));
  return coalesce(v, '');
end $$;

-- "Who can see this list" (2.95): a list's friends' link can be switched off ("My friends" only). Friends in the app
-- still see it through wl_fr_list. (Also in supabase-setup.sql, so running either file again keeps it.)
alter table wl_lists add column if not exists link_off boolean not null default false;
-- "Owned by …" (2.102): the owner's profile name, shown under the list name to friends (app and web page).
alter table wl_lists add column if not exists owner_name text not null default '';

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

-- The list whatever its link says: only for wl_fr_list below (not callable by the app).
create or replace function wl_list_json(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'name', l.name,
    'owner', l.owner_name,
    'cats', l.cats,
    'items', coalesce(
      (select jsonb_agg(i.data order by i.id desc) from wl_items i where i.list_code = l.code),
      '[]'::jsonb)
  ) from wl_lists l where l.code = p_code
$$;

-- Your profile name on your list, for "Owned by …" (owner token required).
create or replace function wl_set_owner(p_code text, p_token text, p_name text)
returns void language plpgsql security definer set search_path = public as $$
begin
  update wl_lists set owner_name = left(coalesce(p_name, ''), 30) where code = p_code and owner_token = p_token;
end $$;

-- Switches the friends' link of your list on or off (owner token required).
create or replace function wl_set_link(p_code text, p_token text, p_on boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not exists (select 1 from wl_lists where code = p_code and owner_token = p_token) then
    raise exception 'not allowed';
  end if;
  update wl_lists set link_off = not p_on where code = p_code;
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
  return wl_list_json(p_code);
end $$;

revoke execute on function wl_pf_check(text, text), wl_pf_new_code_text(), wl_list_json(text) from public, anon;
-- The Storage rules (supabase-photos.sql) call wl_people_photo_ok as the caller, so anon may run it; it only answers
-- true or false.
grant execute on function wl_people_photo_ok(text), wl_people_url_ok(text, text) to anon;

grant execute on function
  wl_pf_save(text, text, text),
  wl_pf_photo(text, text, text),
  wl_pf_photo_url(text, text, text),
  wl_pf_new_code(text, text),
  wl_pf_about(text, text, text, text),
  wl_pf_lists(text, text, jsonb),
  wl_fr_add(text, text, text),
  wl_fr_answer(text, text, text, boolean),
  wl_fr_remove(text, text, text),
  wl_fr_hide(text, text, text, boolean),
  wl_fr_state(text, text),
  wl_fr_photo(text, text, text),
  wl_fr_list(text, text, text, text),
  wl_get_list(text),
  wl_set_link(text, text, boolean),
  wl_set_owner(text, text, text)
to anon;
