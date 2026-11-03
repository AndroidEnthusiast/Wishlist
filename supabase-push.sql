-- Instant message notifications and honest "read" ticks (version 2.105).
-- Run this ONCE in Supabase: SQL Editor -> New query -> paste everything -> Run.
-- Run supabase-chat.sql first (you already did). Safe to run again later; it won't delete anything.
--
-- How it works:
--  * "Read" (owner): fetching messages no longer marks them as read (wl_chat_fetch). The phone says which messages
--    were really seen, with the app open, the chat open and the newest message on screen (wl_chat_seen).
--  * Push: each phone saves its Firebase push address with its profile (wl_push). When a message is saved, the
--    database asks our Supabase function "push" (supabase/functions/push) to wake the receiver's phone. The push only
--    says "new message": the text stays locked, Firebase/Google never sees it. Each message is pushed at most once.

-- 1. Messages without marking them as read (the newest 300, or those after p_after), oldest first.
create or replace function wl_chat_fetch(p_id text, p_token text, p_other text, p_after bigint)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  if not wl_chat_ok(p_id, p_other, false) then return '[]'::jsonb; end if;
  select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'mine', m.a = p_id, 'body', m.body, 'spk', m.spk, 'rpk', m.rpk,
                                                'at', m.created_at, 'read', m.read_at is not null) order by m.id), '[]'::jsonb)
    into v
    from (select * from wl_messages m
          where ((m.a = p_id and m.b = p_other) or (m.a = p_other and m.b = p_id)) and m.id > coalesce(p_after, 0)
          order by m.id desc limit 300) m;
  return v;
end $$;

-- 2. You have seen their messages up to message p_upto: those get "read".
create or replace function wl_chat_seen(p_id text, p_token text, p_other text, p_upto bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  update wl_messages set read_at = now()
    where a = p_other and b = p_id and id <= coalesce(p_upto, 0) and read_at is null;
end $$;

-- 3. Push addresses: one per profile (the phone it belongs to).
create table if not exists wl_push (
  id text primary key references wl_profiles(id) on delete cascade,
  token text not null,
  updated_at timestamptz not null default now()
);
alter table wl_push enable row level security;

create or replace function wl_push_set(p_id text, p_token text, p_push text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_push, '')) = 0 then
    delete from wl_push where id = p_id;
    return;
  end if;
  if length(p_push) > 4096 then raise exception 'bad push address'; end if;
  insert into wl_push (id, token, updated_at) values (p_id, p_push, now())
    on conflict (id) do update set token = excluded.token, updated_at = now();
end $$;

-- 4. Each message is pushed once: the function sets "pushed" before it sends.
alter table wl_messages add column if not exists pushed boolean not null default false;

-- 5. When a message is saved: ask the "push" function to wake the receiver's phone (when it has a push address).
--    pg_net sends the request after the message is saved; a failing push never stops a message.
create extension if not exists pg_net;

create or replace function wl_push_notify()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from wl_push where id = new.b) then
    perform net.http_post(
      url := 'https://aspnmvmsvwbezdltykgx.supabase.co/functions/v1/push',
      body := jsonb_build_object('id', new.id),
      headers := '{"Content-Type": "application/json"}'::jsonb
    );
  end if;
  return new;
exception when others then
  return new;
end $$;

drop trigger if exists wl_messages_push on wl_messages;
create trigger wl_messages_push after insert on wl_messages
  for each row execute function wl_push_notify();

revoke execute on function wl_push_notify() from public, anon;

grant execute on function
  wl_chat_fetch(text, text, text, bigint),
  wl_chat_seen(text, text, text, bigint),
  wl_push_set(text, text, text)
to anon;
