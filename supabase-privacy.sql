-- Privacy (ROADMAP S4.7 and S4.8; owner, 12 November 2026): block, report, and "Delete my profile and data".
-- Run AFTER all the other supabase-*.sql scripts (supabase-friends.sql and supabase-chat.sql again first: they have
-- the block table and its checks). Safe to run again; it only adds.

-- ---------- Block ----------
-- Blocking someone (a chat's menu or their page): you are no longer friends, they can't send you a friend request, and
-- the chat is hidden for you. They are not told. Entering their friend code again undoes it (wl_fr_add).
create or replace function wl_block(p_id text, p_token text, p_other text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if coalesce(p_other, '') = '' or p_other = p_id or not exists (select 1 from wl_profiles where id = p_other) then
    raise exception 'not found';
  end if;
  insert into wl_blocks (who, whom) values (p_id, p_other) on conflict do nothing;
  delete from wl_friends where (a = p_id and b = p_other) or (a = p_other and b = p_id);
end $$;

create or replace function wl_unblock(p_id text, p_token text, p_other text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  delete from wl_blocks where who = p_id and whom = p_other;
end $$;

-- The people you blocked: [{id, name}], newest first (Profile > Blocked people).
create or replace function wl_blocked_list(p_id text, p_token text)
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  return coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name) order by k.created_at desc)
                   from wl_blocks k join wl_profiles p on p.id = k.whom where k.who = p_id), '[]'::jsonb);
end $$;

-- ---------- Report ----------
-- A report: who reported whom, a reason (spam, hurtful, other), the reporter's note (at least 5 letters or digits) and
-- the last 20 messages of the chat as the reporter's phone unlocked them (the reporter is told they are sent). The owner
-- reads them in Supabase > Table editor > wl_reports. Kept 12 months.
create table if not exists wl_reports (
  id bigserial primary key,
  reporter text not null,
  reported text not null,
  reported_name text not null default '',
  reason text not null,
  note text not null,
  messages jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now()
);
alter table wl_reports enable row level security;

create or replace function wl_report(p_id text, p_token text, p_other text, p_reason text, p_note text, p_messages jsonb)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform wl_pf_check(p_id, p_token);
  if coalesce(p_other, '') = '' or p_other = p_id or not exists (select 1 from wl_profiles where id = p_other) then
    raise exception 'not found';
  end if;
  if coalesce(p_reason, '') not in ('spam', 'hurtful', 'other') then raise exception 'bad reason'; end if;
  if length(regexp_replace(coalesce(p_note, ''), '\s', '', 'g')) < 5 or length(p_note) > 2000 then raise exception 'bad note'; end if;
  if jsonb_typeof(coalesce(p_messages, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_messages, '[]'::jsonb)) > 20
     or length(coalesce(p_messages, '[]'::jsonb)::text) > 40000 then
    raise exception 'bad messages';
  end if;
  -- At most 10 reports an hour from one person (far above honest use).
  if (select count(*) from wl_reports where reporter = p_id and created_at > now() - interval '1 hour') >= 10 then
    raise exception 'rate_limited';
  end if;
  insert into wl_reports (reporter, reported, reported_name, reason, note, messages)
    values (p_id, p_other, coalesce((select name from wl_profiles where id = p_other), ''), p_reason, btrim(p_note),
            coalesce(p_messages, '[]'::jsonb));
  delete from wl_reports where created_at < now() - interval '12 months';
end $$;

-- ---------- Delete my profile and data ----------
-- What the app does first, with the steps people already know: collab wishlists made here are handed over or deleted
-- (wl_sh_handover / wl_delete_list), groups and gift exchanges get a next organiser when you leave them (wl_gr_leave /
-- wl_gx_leave), photos are removed, your own lists are deleted (wl_delete_list) and the recovery copy too.
-- Then this, which removes everything else in one go:
--   * gift exchanges you organise where nobody else has the app (nobody can organise) are cancelled, everyone told;
--   * your place in every other gift exchange (the draw is repaired) and its anonymous chats;
--   * "Added by <your name>" on wishes you added to collab wishlists (p_names: [{k: the list's edit key, n: your
--     name there}]; the wishes stay);
--   * your reservations, questions and reactions (p_devices: this phone's device ids; also the ones linked to you);
--   * your profile, and with it friends, chats, chat photos, blocks, group places, invitations, news and notification
--     addresses (they all go with the profile).
-- Refused ('hand over first') while you still organise a group, or a gift exchange with another app user in it.
-- p_id may be empty (someone without a profile): then only the device's reservations, questions and reactions go.
create or replace function wl_delete_me(p_id text, p_token text, p_devices jsonb, p_names jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare v_dev text[]; e jsonb; r record;
begin
  if jsonb_typeof(coalesce(p_devices, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_devices, '[]'::jsonb)) > 10
     or jsonb_typeof(coalesce(p_names, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_names, '[]'::jsonb)) > 200 then
    raise exception 'bad request';
  end if;
  select coalesce(array_agg(d), '{}') into v_dev
    from jsonb_array_elements_text(coalesce(p_devices, '[]'::jsonb)) d where length(d) >= 8;
  if coalesce(p_id, '') <> '' then
    perform wl_pf_check(p_id, p_token);
    if exists (select 1 from wl_groups g where g.organiser = p_id
               and exists (select 1 from wl_group_members m where m.group_id = g.id and m.profile <> p_id)) then
      raise exception 'hand over first';
    end if;
    for r in select x.id from wl_gx x where x.organiser = p_id loop
      if exists (select 1 from wl_gx_members m join wl_profiles p on p.id = m.profile
                 where m.gx_id = r.id and m.profile <> p_id and not coalesce(p.web, false)) then
        raise exception 'hand over first';
      end if;
      -- Nobody else can organise it: cancelled, everyone told (as with Cancel).
      perform wl_gx_news_add(m.profile, 'cancelled', r.id, '') from wl_gx_members m where m.gx_id = r.id and m.profile <> p_id;
      delete from wl_gx where id = r.id;
    end loop;
    for r in select m.gx_id from wl_gx_members m where m.profile = p_id loop
      perform wl_gx_take_out(r.gx_id, p_id);
    end loop;
    delete from wl_gx_msgs where santa = p_id or giftee = p_id;
    for e in select * from jsonb_array_elements(coalesce(p_names, '[]'::jsonb)) loop
      if length(coalesce(e ->> 'k', '')) < 8 or btrim(coalesce(e ->> 'n', '')) = '' then continue; end if;
      update wl_items i set data = i.data - 'by'
        from wl_lists l
        where l.edit_key = e ->> 'k' and i.list_code = l.code and i.data ->> 'by' = e ->> 'n';
      update wl_items i set data = i.data - 'binBy'
        from wl_lists l
        where l.edit_key = e ->> 'k' and i.list_code = l.code and i.data ->> 'binBy' = e ->> 'n';
    end loop;
    v_dev := v_dev || coalesce((select array_agg(device) from wl_devices where profile = p_id), '{}');
    delete from wl_reacts where who = 'p:' || p_id;
  end if;
  if cardinality(v_dev) > 0 then
    delete from wl_reservations where device = any(v_dev);
    delete from wl_ask_to where device = any(v_dev);
    delete from wl_asks where asker = any(v_dev);
    delete from wl_news where device = any(v_dev);
    delete from wl_reacts where who = any(array(select 'w:' || d from unnest(v_dev) d));
    delete from wl_list_view_last where device = any(v_dev);
  end if;
  if coalesce(p_id, '') <> '' then
    delete from wl_profiles where id = p_id;
  end if;
end $$;

grant execute on function
  wl_block(text, text, text),
  wl_unblock(text, text, text),
  wl_blocked_list(text, text),
  wl_report(text, text, text, text, text, jsonb),
  wl_delete_me(text, text, jsonb, jsonb)
to anon;
