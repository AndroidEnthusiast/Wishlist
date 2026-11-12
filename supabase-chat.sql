-- Messages setup (version 2.98): end-to-end encrypted chat between friends.
-- Run this ONCE in Supabase: SQL Editor -> New query -> paste everything -> Run.
-- Run supabase-friends.sql first (you already did). Safe to run again later; it won't delete anything.
--
-- How it works:
--  * Every phone makes its own key pair. Only the public half is stored here (wl_profiles.pubkey); the private half
--    never leaves the phone. Messages are locked on the phone with a key only the two friends' phones can work out,
--    so this server (and whoever runs it) only ever sees unreadable text.
--  * A message row keeps the public keys used (spk = sender's, rpk = receiver's), so each phone knows which key it
--    needs; a phone with a new key (new phone, reinstall) simply can't read the old messages.
--  * Only friends can send. After unfollowing, the old conversation stays readable, but nothing new can be sent.
--  * Only the sender can delete a message, and it is gone for both. Messages older than a year are deleted.

alter table wl_profiles add column if not exists pubkey text not null default '';

create table if not exists wl_messages (
  id bigserial primary key,
  a text not null references wl_profiles(id) on delete cascade,   -- sender
  b text not null references wl_profiles(id) on delete cascade,   -- receiver
  body text not null,                                             -- the locked message (base64)
  spk text not null default '',
  rpk text not null default '',
  created_at timestamptz not null default now(),
  read_at timestamptz
);
create index if not exists wl_messages_ab on wl_messages (a, b, id);
create index if not exists wl_messages_ba on wl_messages (b, a, id);
create index if not exists wl_messages_time on wl_messages (created_at);
alter table wl_messages enable row level security;

-- Your public key (the phone sends it when it makes its key pair).
create or replace function wl_pf_key(p_id text, p_token text, p_key text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_key, '')) > 400 then raise exception 'bad key'; end if;
  update wl_profiles set pubkey = coalesce(p_key, '') where id = p_id;
end $$;

-- Can these two chat? Friends can; after unfollowing, the history stays readable (p_send = false).
create or replace function wl_chat_ok(p_id text, p_other text, p_send boolean)
returns boolean language sql stable security definer set search_path = public as $$
  select (exists (select 1 from wl_friends f where f.accepted and ((f.a = p_id and f.b = p_other) or (f.a = p_other and f.b = p_id)))
    or (not p_send and exists (select 1 from wl_messages m where (m.a = p_id and m.b = p_other) or (m.a = p_other and m.b = p_id))))
    -- Blocked (supabase-friends.sql): the one who blocked no longer sees the chat, and nobody can send in it.
    and not exists (select 1 from wl_blocks k where (k.who = p_id and k.whom = p_other) or (p_send and k.who = p_other and k.whom = p_id))
$$;

-- The conversations: everyone you have messages with, newest first, with their last message, the number you haven't
-- read, and their public key (to lock new messages for them).
create or replace function wl_chat_list(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select coalesce(jsonb_agg(x order by (x->'last'->>'id')::bigint desc), '[]'::jsonb) into v from (
    select jsonb_build_object(
      'id', p.id, 'name', p.name, 'pv', p.photo_v, 'pubkey', p.pubkey,
      'friend', exists (select 1 from wl_friends f where f.accepted and ((f.a = p_id and f.b = p.id) or (f.a = p.id and f.b = p_id))),
      'unread', (select count(*) from wl_messages m where m.a = p.id and m.b = p_id and m.read_at is null),
      'last', (select jsonb_build_object('id', m.id, 'mine', m.a = p_id, 'body', m.body, 'spk', m.spk, 'rpk', m.rpk,
                                         'at', m.created_at, 'read', m.read_at is not null)
               from wl_messages m where (m.a = p_id and m.b = p.id) or (m.a = p.id and m.b = p_id) order by m.id desc limit 1)
    ) as x
    from wl_profiles p
    where p.id <> p_id and exists (select 1 from wl_messages m where (m.a = p_id and m.b = p.id) or (m.a = p.id and m.b = p_id))
      and not exists (select 1 from wl_blocks k where k.who = p_id and k.whom = p.id)
  ) t;
  return v;
end $$;

-- The messages with someone after message id p_after (0: the newest 300), oldest first. Reading marks theirs as read.
create or replace function wl_chat_get(p_id text, p_token text, p_other text, p_after bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_chat_ok(p_id, p_other, false) then return '[]'::jsonb; end if;
  update wl_messages set read_at = now() where a = p_other and b = p_id and read_at is null;
  select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'mine', m.a = p_id, 'body', m.body, 'spk', m.spk, 'rpk', m.rpk,
                                                'at', m.created_at, 'read', m.read_at is not null) order by m.id), '[]'::jsonb)
    into v
    from (select * from wl_messages m
          where ((m.a = p_id and m.b = p_other) or (m.a = p_other and m.b = p_id)) and m.id > coalesce(p_after, 0)
          order by m.id desc limit 300) m;
  return v;
end $$;

-- Which of your messages to someone have been read (for the ticks), from message id p_from on.
create or replace function wl_chat_reads(p_id text, p_token text, p_other text, p_from bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return coalesce((select jsonb_agg(m.id) from wl_messages m
                   where m.a = p_id and m.b = p_other and m.id >= coalesce(p_from, 0) and m.read_at is not null), '[]'::jsonb);
end $$;

-- Sends a locked message to a friend. Returns its id and time. Old messages (over a year) are cleaned up here.
create or replace function wl_chat_send(p_id text, p_token text, p_other text, p_body text, p_spk text, p_rpk text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare r wl_messages;
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_chat_ok(p_id, p_other, true) then raise exception 'not friends'; end if;
  if length(coalesce(p_body, '')) = 0 or length(p_body) > 12000 then raise exception 'bad message'; end if;
  delete from wl_messages where created_at < now() - interval '1 year';
  insert into wl_messages (a, b, body, spk, rpk) values (p_id, p_other, p_body, coalesce(p_spk, ''), coalesce(p_rpk, ''))
    returning * into r;
  return jsonb_build_object('id', r.id, 'at', r.created_at);
end $$;

-- Deletes one of your own messages, for both of you.
create or replace function wl_chat_delete(p_id text, p_token text, p_msg bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_messages where id = p_msg and a = p_id;
end $$;

-- A friend's public key (to start a chat before any message exists).
create or replace function wl_chat_key(p_id text, p_token text, p_other text)
returns text language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_chat_ok(p_id, p_other, true) then return ''; end if;
  return coalesce((select pubkey from wl_profiles where id = p_other), '');
end $$;

revoke execute on function wl_chat_ok(text, text, boolean) from public, anon;

grant execute on function
  wl_pf_key(text, text, text),
  wl_chat_list(text, text),
  wl_chat_get(text, text, text, bigint),
  wl_chat_reads(text, text, text, bigint),
  wl_chat_send(text, text, text, text, text, text),
  wl_chat_delete(text, text, bigint),
  wl_chat_key(text, text, text)
to anon;

-- ---------------------------------------------------------------------------------------------------------------------
-- Photos in messages (version 2.109, 4 November 2026). Run this whole file again; it only adds things.
--  * The phone shrinks the photo and locks it with a new random key before sending. Only the locked photo is stored
--    here (wl_chat_files.data); its key travels inside the locked message, so this server can't see the photo.
--  * Only the two people in the conversation can fetch it, and only once its message was sent.
--  * A photo is removed here after 30 days (the phones keep their own copy), or with its message when that is deleted.
--    A photo whose message was never sent is removed after a day. At most 50 photos a day per person.

create table if not exists wl_chat_files (
  id text primary key,                                              -- a random name made here
  a text not null references wl_profiles(id) on delete cascade,     -- sender
  b text not null references wl_profiles(id) on delete cascade,     -- receiver
  msg bigint references wl_messages(id) on delete cascade,          -- its message, once sent
  data text not null,                                               -- the locked photo (base64)
  created_at timestamptz not null default now()
);
create index if not exists wl_chat_files_time on wl_chat_files (created_at);
create index if not exists wl_chat_files_msg on wl_chat_files (msg);
create index if not exists wl_chat_files_a on wl_chat_files (a, created_at);
alter table wl_chat_files enable row level security;

-- Stores a locked photo for a friend; returns its name. The message that shows it is sent with wl_chat_send_photo.
create or replace function wl_chat_file_put(p_id text, p_token text, p_other text, p_data text)
returns text language plpgsql security definer set search_path = public as $$
declare v text;
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_chat_ok(p_id, p_other, true) then raise exception 'not friends'; end if;
  if length(coalesce(p_data, '')) < 100 or length(p_data) > 1000000 then raise exception 'bad photo'; end if;
  delete from wl_chat_files
    where created_at < now() - interval '30 days' or (msg is null and created_at < now() - interval '1 day');
  if (select count(*) from wl_chat_files where a = p_id and created_at > now() - interval '1 day') >= 50 then
    raise exception 'too many photos';
  end if;
  v := replace(gen_random_uuid()::text, '-', '');
  insert into wl_chat_files (id, a, b, data) values (v, p_id, p_other, p_data);
  return v;
end $$;

-- Sends the message for a stored photo (like wl_chat_send) and ties the photo to it, in one go.
create or replace function wl_chat_send_photo(p_id text, p_token text, p_other text, p_body text, p_spk text, p_rpk text, p_file text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare r jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  if not exists (select 1 from wl_chat_files where id = p_file and a = p_id and b = p_other and msg is null) then
    raise exception 'no photo';
  end if;
  r := wl_chat_send(p_id, p_token, p_other, p_body, p_spk, p_rpk);
  update wl_chat_files set msg = (r->>'id')::bigint where id = p_file;
  return r;
end $$;

-- A locked photo of a conversation you are in; null when it is gone (older than 30 days, or its message was deleted).
create or replace function wl_chat_file_get(p_id text, p_token text, p_file text)
returns text language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return (select data from wl_chat_files
          where id = p_file and (a = p_id or b = p_id) and msg is not null and created_at > now() - interval '30 days');
end $$;

grant execute on function
  wl_chat_file_put(text, text, text, text),
  wl_chat_send_photo(text, text, text, text, text, text, text),
  wl_chat_file_get(text, text, text)
to anon;
