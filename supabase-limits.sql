-- Limits against abuse (ROADMAP S4.2; owner, 11 November 2026). Run AFTER all the other supabase-*.sql scripts.
-- Safe to run again. It only adds: nothing that exists is changed, so older apps keep working.
--
-- The publishable key is public on purpose, so anyone could call our functions thousands of times a minute: fill the
-- database with lists, send floods of messages or friend requests. Every limit here is far above what a real person
-- does (the owner's choice: "generous"), and someone who hits one gets the error text 'rate_limited', which the app
-- and the web pages show as "Please try again in a moment".
--
-- How: triggers count what is really ADDED to a table (an AFTER INSERT trigger only sees new rows; saving a list again,
-- which updates rows, never counts). The counts live in wl_limits per actor, action and time window; rows older than a
-- day are removed. The actor is who the row belongs to (a list, a profile, a phone's id). Only for things made by
-- someone the server doesn't know yet (a new list, a new profile, a new group or exchange) the actor is the caller's
-- internet address, SCRAMBLED with a salt that changes every day (owner's choice, 11 November 2026): the real address
-- is never stored, and after a day the scrambled one can't even be matched to itself any more. Without an address
-- (the SQL editor, scheduled jobs) nothing is limited.
--
-- The limits (per actor):
--   new wishes on a list ........ 100 an hour (1000 in a list's first hour: a big list going online for the first time)
--   chat messages ............... 30 a minute per sender (Messages and the gift exchange's anonymous chats each)
--   reactions ................... 60 a minute per person
--   reservations ................ 60 an hour per phone or browser
--   "still being bought?" asks .. 20 an hour per phone or browser
--   friend requests ............. 30 an hour per profile
--   invitations ................. 50 an hour per group, exchange or collab wishlist
--   new lists ................... 20 an hour per internet address
--   new groups and exchanges .... 10 an hour per internet address (each)
--   new profiles ................ 5 an hour per internet address

-- When a list was made (for the first hour's larger room). Existing lists get "now" once: harmless.
alter table wl_lists add column if not exists created_at timestamptz not null default now();

create table if not exists wl_limits (
  actor text not null,
  action text not null,
  win timestamptz not null,     -- the start of the window (the minute or the hour)
  n int not null default 0,
  primary key (actor, action, win)
);
alter table wl_limits enable row level security;

-- The day's salt for scrambling internet addresses: random, new every day, deleted after two days.
create table if not exists wl_limit_salt (
  day date primary key,
  salt text not null
);
alter table wl_limit_salt enable row level security;

-- The caller's internet address, scrambled with today's salt; '' when the request didn't come through the API.
create or replace function wl_limit_ip()
returns text language plpgsql volatile security definer set search_path = public as $$
declare v_h json; v_ip text; v_salt text;
begin
  begin
    v_h := nullif(current_setting('request.headers', true), '')::json;
  exception when others then
    return '';
  end;
  v_ip := btrim(split_part(coalesce(v_h ->> 'x-forwarded-for', v_h ->> 'cf-connecting-ip', v_h ->> 'x-real-ip', ''), ',', 1));
  if v_ip = '' then return ''; end if;
  select salt into v_salt from wl_limit_salt where day = current_date;
  if v_salt is null then
    insert into wl_limit_salt (day, salt) values (current_date, md5(random()::text || clock_timestamp()::text || random()::text))
      on conflict (day) do nothing;
    select salt into v_salt from wl_limit_salt where day = current_date;
  end if;
  return 'ip:' || encode(sha256(convert_to(v_salt || v_ip, 'UTF8')), 'hex');
end $$;

-- Counts one more [p_action] for [p_actor] in the current window of [p_per] ('minute' or 'hour') and refuses when that
-- passes [p_max]. An empty actor is not counted.
create or replace function wl_limit_hit(p_actor text, p_action text, p_max int, p_per text)
returns void language plpgsql volatile security definer set search_path = public as $$
declare v_n int;
begin
  if coalesce(p_actor, '') = '' then return; end if;
  insert into wl_limits (actor, action, win, n) values (p_actor, p_action, date_trunc(p_per, now()), 1)
    on conflict (actor, action, win) do update set n = wl_limits.n + 1
    returning n into v_n;
  -- Now and then, the old counts and salts go (nothing older than a day is kept).
  if random() < 0.01 then
    delete from wl_limits where win < now() - interval '1 day';
    delete from wl_limit_salt where day < current_date - 1;
  end if;
  if v_n > p_max then
    raise exception 'rate_limited' using hint = 'Please try again in a moment';
  end if;
end $$;

-- The triggers. Each counts the new row for its actor.
create or replace function wl_limit_items() returns trigger
language plpgsql security definer set search_path = public as $$
declare v_young boolean;
begin
  select created_at > now() - interval '1 hour' into v_young from wl_lists where code = new.list_code;
  perform wl_limit_hit('list:' || new.list_code, 'wish', case when coalesce(v_young, false) then 1000 else 100 end, 'hour');
  return null;
end $$;

create or replace function wl_limit_messages() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('pf:' || new.a, 'message', 30, 'minute');
  return null;
end $$;

create or replace function wl_limit_gx_msgs() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('gx:' || new.gx_id || ':' || case when new.from_santa then new.santa else new.giftee end, 'gx_message', 30, 'minute');
  return null;
end $$;

create or replace function wl_limit_reacts() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('who:' || new.who, 'reaction', 60, 'minute');
  return null;
end $$;

create or replace function wl_limit_reservations() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('dev:' || new.device, 'reservation', 60, 'hour');
  return null;
end $$;

create or replace function wl_limit_asks() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('dev:' || new.asker, 'ask', 20, 'hour');
  return null;
end $$;

create or replace function wl_limit_friends() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit('pf:' || new.a, 'friend_request', 30, 'hour');
  return null;
end $$;

create or replace function wl_limit_invites() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit(tg_table_name || ':' || coalesce(to_jsonb(new) ->> 'group_id', to_jsonb(new) ->> 'gx_id', to_jsonb(new) ->> 'list_code'), 'invite', 50, 'hour');
  return null;
end $$;

create or replace function wl_limit_new_by_ip() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  perform wl_limit_hit(wl_limit_ip(), tg_table_name,
    case tg_table_name when 'wl_lists' then 20 when 'wl_profiles' then 5 else 10 end, 'hour');
  return null;
end $$;

drop trigger if exists wl_limit_items on wl_items;
create trigger wl_limit_items after insert on wl_items for each row execute function wl_limit_items();
drop trigger if exists wl_limit_messages on wl_messages;
create trigger wl_limit_messages after insert on wl_messages for each row execute function wl_limit_messages();
drop trigger if exists wl_limit_gx_msgs on wl_gx_msgs;
create trigger wl_limit_gx_msgs after insert on wl_gx_msgs for each row execute function wl_limit_gx_msgs();
drop trigger if exists wl_limit_reacts on wl_reacts;
create trigger wl_limit_reacts after insert on wl_reacts for each row execute function wl_limit_reacts();
drop trigger if exists wl_limit_reservations on wl_reservations;
create trigger wl_limit_reservations after insert on wl_reservations for each row execute function wl_limit_reservations();
drop trigger if exists wl_limit_asks on wl_asks;
create trigger wl_limit_asks after insert on wl_asks for each row execute function wl_limit_asks();
drop trigger if exists wl_limit_friends on wl_friends;
create trigger wl_limit_friends after insert on wl_friends for each row execute function wl_limit_friends();
drop trigger if exists wl_limit_invites on wl_group_invites;
create trigger wl_limit_invites after insert on wl_group_invites for each row execute function wl_limit_invites();
drop trigger if exists wl_limit_invites on wl_gx_invites;
create trigger wl_limit_invites after insert on wl_gx_invites for each row execute function wl_limit_invites();
drop trigger if exists wl_limit_invites on wl_sh_invites;
create trigger wl_limit_invites after insert on wl_sh_invites for each row execute function wl_limit_invites();
drop trigger if exists wl_limit_new on wl_lists;
create trigger wl_limit_new after insert on wl_lists for each row execute function wl_limit_new_by_ip();
drop trigger if exists wl_limit_new on wl_profiles;
create trigger wl_limit_new after insert on wl_profiles for each row execute function wl_limit_new_by_ip();
drop trigger if exists wl_limit_new on wl_groups;
create trigger wl_limit_new after insert on wl_groups for each row execute function wl_limit_new_by_ip();
drop trigger if exists wl_limit_new on wl_gx;
create trigger wl_limit_new after insert on wl_gx for each row execute function wl_limit_new_by_ip();

-- Nobody calls these directly: only the triggers above use them.
revoke execute on function wl_limit_ip(), wl_limit_hit(text, text, int, text),
  wl_limit_items(), wl_limit_messages(), wl_limit_gx_msgs(), wl_limit_reacts(), wl_limit_reservations(),
  wl_limit_asks(), wl_limit_friends(), wl_limit_invites(), wl_limit_new_by_ip()
from public, anon;

-- The shop-page helper (supabase/functions/fetch-page, ROADMAP S4.2; owner, 12 November 2026): it loads a shop's page
-- for whoever holds a collab wishlist's edit key or a group link. This checks the key and counts: at most 60 pages an
-- hour per key, then 'rate_limited' ("Please try again in a moment"). Only a fingerprint of the key is counted.
create or replace function wl_fetch_ok(p_key text)
returns boolean language plpgsql volatile security definer set search_path = public as $$
begin
  if coalesce(p_key, '') !~ '^[A-Za-z0-9]{16,64}$' then return false; end if;
  if not exists (select 1 from wl_lists where edit_key = p_key)
     and not exists (select 1 from wl_groups where link = upper(p_key)) then
    return false;
  end if;
  perform wl_limit_hit('fetch:' || md5(p_key), 'fetch', 60, 'hour');
  return true;
end $$;
grant execute on function wl_fetch_ok(text) to anon;
