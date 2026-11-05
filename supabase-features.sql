-- New features of 6 November 2026 (version 2.128). Run this ONCE in Supabase: SQL Editor -> New query -> paste -> Run.
-- Run it AFTER the other scripts (setup, shared, friends, chat, push); it only adds things and is safe to run again.
--   1. Reactions in chat (an emoji on a message), locked like the messages themselves.
--   2. Your sizes (clothing and shoes) for your friends.
--   3. Members of a gift list, and handing a gift list to a new owner when its maker leaves.

-- ---------- 1. Reactions ----------
-- One reaction per person per message: ra by the sender (a), rb by the receiver (b). Each is locked with the chat's
-- key on the phone (only the two of them can read it); '' = none.
alter table wl_messages add column if not exists ra text not null default '';
alter table wl_messages add column if not exists rb text not null default '';

create or replace function wl_chat_react(p_id text, p_token text, p_msg bigint, p_react text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_react, '')) > 400 then raise exception 'too long'; end if;
  update wl_messages set
    ra = case when a = p_id then coalesce(p_react, '') else ra end,
    rb = case when b = p_id then coalesce(p_react, '') else rb end
  where id = p_msg and (a = p_id or b = p_id);
end $$;

-- The reactions in your chat with p_other: [{id, mine, theirs}] for messages that have one.
create or replace function wl_chat_reacts(p_id text, p_token text, p_other text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', m.id,
      'mine', case when m.a = p_id then m.ra else m.rb end,
      'theirs', case when m.a = p_id then m.rb else m.ra end) order by m.id), '[]'::jsonb)
    into v
    from wl_messages m
    where ((m.a = p_id and m.b = p_other) or (m.a = p_other and m.b = p_id)) and (m.ra <> '' or m.rb <> '');
  return v;
end $$;

-- ---------- 2. Sizes ----------
alter table wl_profiles add column if not exists sizes text not null default '';

-- Your sizes as a short JSON text {"c": clothing, "s": shoes}, or '' for none.
create or replace function wl_pf_sizes(p_id text, p_token text, p_sizes text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if length(coalesce(p_sizes, '')) > 200 then raise exception 'too long'; end if;
  update wl_profiles set sizes = coalesce(p_sizes, '') where id = p_id;
end $$;

-- Your friends' sizes: {profile id: sizes}, only friends who accepted and have sizes.
create or replace function wl_fr_sizes(p_id text, p_token text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  perform wl_pf_check(p_id, p_token);
  select coalesce(jsonb_object_agg(p.id, p.sizes), '{}'::jsonb) into v
    from wl_friends f join wl_profiles p on p.id = case when f.a = p_id then f.b else f.a end
    where f.accepted and (f.a = p_id or f.b = p_id) and p.sizes <> '';
  return v;
end $$;

-- ---------- 3. Gift list members and a new owner ----------
create table if not exists wl_sh_members (
  list_code text not null references wl_lists(code) on delete cascade,
  profile text not null references wl_profiles(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (list_code, profile)
);
alter table wl_sh_members enable row level security;
-- The member the maker handed the list to, and the owner token waiting for them.
alter table wl_lists add column if not exists owner_next text not null default '';
alter table wl_lists add column if not exists owner_next_token text not null default '';

-- You (an app user with a profile) are on the gift list with edit key p_key.
create or replace function wl_sh_member(p_id text, p_token text, p_key text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  perform wl_pf_check(p_id, p_token);
  v_code := wl_sh_code(p_key);
  insert into wl_sh_members (list_code, profile) values (v_code, p_id) on conflict do nothing;
end $$;

create or replace function wl_sh_member_leave(p_id text, p_token text, p_key text)
returns void language plpgsql security definer set search_path = public as $$
declare v_code text;
begin
  perform wl_pf_check(p_id, p_token);
  v_code := wl_sh_code(p_key);
  delete from wl_sh_members where list_code = v_code and profile = p_id;
end $$;

-- The members with their names, for the maker only (their owner token).
create or replace function wl_sh_members(p_code text, p_owner text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v jsonb;
begin
  if coalesce(p_owner, '') = '' or not exists (select 1 from wl_lists where code = p_code and owner_token = p_owner) then
    raise exception 'not allowed';
  end if;
  select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name) order by m.joined_at), '[]'::jsonb) into v
    from wl_sh_members m join wl_profiles p on p.id = m.profile where m.list_code = p_code;
  return v;
end $$;

-- The maker hands the list to member p_profile: the list gets a new owner token at once (the maker's stops working),
-- kept for that member until their phone takes it (wl_sh_owner_claim).
create or replace function wl_sh_handover(p_code text, p_owner text, p_profile text)
returns void language plpgsql security definer set search_path = public as $$
declare v_new text;
begin
  if coalesce(p_owner, '') = '' or not exists (select 1 from wl_lists where code = p_code and owner_token = p_owner) then
    raise exception 'not allowed';
  end if;
  if not exists (select 1 from wl_sh_members where list_code = p_code and profile = p_profile) then
    raise exception 'not a member';
  end if;
  v_new := md5(random()::text || clock_timestamp()::text || p_code) || md5(clock_timestamp()::text || random()::text);
  update wl_lists set owner_token = v_new, owner_next = p_profile, owner_next_token = v_new where code = p_code;
end $$;

-- The new owner's phone takes the owner token (once), or gets null.
create or replace function wl_sh_owner_claim(p_id text, p_token text, p_key text)
returns text language plpgsql security definer set search_path = public as $$
declare v_code text; v_t text;
begin
  perform wl_pf_check(p_id, p_token);
  v_code := wl_sh_code(p_key);
  select owner_next_token into v_t from wl_lists where code = v_code and owner_next = p_id and owner_next_token <> '';
  if v_t is null then return null; end if;
  update wl_lists set owner_next = '', owner_next_token = '' where code = v_code;
  return v_t;
end $$;

grant execute on function
  wl_chat_react(text, text, bigint, text),
  wl_chat_reacts(text, text, text),
  wl_pf_sizes(text, text, text),
  wl_fr_sizes(text, text),
  wl_sh_member(text, text, text),
  wl_sh_member_leave(text, text, text),
  wl_sh_members(text, text),
  wl_sh_handover(text, text, text),
  wl_sh_owner_claim(text, text, text)
to anon;
