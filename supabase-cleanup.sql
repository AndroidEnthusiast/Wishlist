-- Clean-up of what nobody uses any more (ROADMAP S4.3; owner, 11 November 2026). Run AFTER all the other
-- supabase-*.sql scripts (after supabase-limits.sql too). Safe to run again; it only adds.
--
-- The free database is 500 MB. Lists, profiles and their friendships, chats and places in groups and exchanges that
-- their owner hasn't used for 12 months are removed online (the owner's choice: "used" means the owner's own app,
-- not friends looking at a list). The copy on the owner's phone is never touched, and when that app is opened again
-- it quietly puts its lists back online under the same links (2.186).
--
-- "Used" (the newest of these):
--   * the app was opened (wl_opened, sent by the app at most once a day while it is on screen, from 2.186);
--   * for a list: it was saved (updated_at), also by older apps that don't send wl_opened, and for a collab wishlist by
--     anyone on it;
--   * for a profile: the app's own checks (seen_at), so a phone with an older app keeps its profile.
-- A month before (after 11 months), a profile gets the news 'cleanup': the app shows "Open Wishlist to keep your lists
-- online". Lists without a profile can't be warned; they come back by themselves when the app is opened (see above).
--
-- Also removed: news older than 60 days, and the "opened within 10 minutes" notes of "Viewed 23 times" after a day.
-- Runs every night (pg_cron, 03:17 UTC); wl_cleanup() returns what it did.

alter table wl_lists add column if not exists opened_at timestamptz not null default now();
alter table wl_profiles add column if not exists opened_at timestamptz not null default now();
alter table wl_profiles add column if not exists warned_at timestamptz;

-- The app is on screen: the profile [p_id] (with its token; '' for none) and the lists [p_lists], given as
-- [{"c": code, "t": owner token}], are in use. Returns the codes of those lists that are no longer online (removed
-- by the clean-up, or never there), so the app puts them back. A list whose token doesn't match is left alone and
-- not returned.
create or replace function wl_opened(p_id text, p_token text, p_lists jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_gone jsonb := '[]'::jsonb; e jsonb; v_code text; v_tok text;
begin
  if coalesce(p_id, '') <> '' then
    perform wl_pf_check(p_id, p_token);
    update wl_profiles set opened_at = now(), warned_at = null where id = p_id;
  end if;
  if jsonb_typeof(coalesce(p_lists, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_lists, '[]'::jsonb)) > 100 then
    raise exception 'bad lists';
  end if;
  for e in select * from jsonb_array_elements(coalesce(p_lists, '[]'::jsonb)) loop
    v_code := e ->> 'c'; v_tok := e ->> 't';
    if coalesce(v_code, '') = '' or coalesce(v_tok, '') = '' then continue; end if;
    update wl_lists set opened_at = now() where code = v_code and owner_token = v_tok;
    if not found and not exists (select 1 from wl_lists where code = v_code) then
      v_gone := v_gone || to_jsonb(v_code);
    end if;
  end loop;
  return v_gone;
end $$;

-- The nightly clean-up. Returns {warned, lists, profiles, news, views}.
create or replace function wl_cleanup()
returns jsonb language plpgsql security definer set search_path = public as $$
declare v_warned int; v_lists int; v_profiles int; v_news int; v_views int; r record;
begin
  -- A month's warning to profiles unused for 11 months (once, until they are used again).
  v_warned := 0;
  for r in select id from wl_profiles
           where greatest(opened_at, seen_at) < now() - interval '11 months' and warned_at is null loop
    perform wl_news_add(r.id, null, null, 'cleanup', '{}'::jsonb);
    update wl_profiles set warned_at = now() where id = r.id;
    v_warned := v_warned + 1;
  end loop;
  -- Lists unused for 12 months (their wishes, reservations, reactions and views go with them).
  delete from wl_lists where greatest(opened_at, updated_at) < now() - interval '12 months';
  get diagnostics v_lists = row_count;
  -- Profiles unused for 12 months that were warned at least 25 days ago (friendships, chats, places in groups and
  -- exchanges go with them; a group or exchange they organised goes too).
  delete from wl_profiles
    where greatest(opened_at, seen_at) < now() - interval '12 months' and warned_at < now() - interval '25 days';
  get diagnostics v_profiles = row_count;
  delete from wl_news where created_at < now() - interval '60 days';
  get diagnostics v_news = row_count;
  delete from wl_list_view_last where at < now() - interval '1 day';
  get diagnostics v_views = row_count;
  -- Recovery copies not saved for 13 months (supabase-vault.sql; only when that script was run).
  if to_regclass('public.wl_vaults') is not null then
    execute 'delete from wl_vaults where updated_at < now() - interval ''13 months''';
  end if;
  return jsonb_build_object('warned', v_warned, 'lists', v_lists, 'profiles', v_profiles, 'news', v_news, 'views', v_views);
end $$;

revoke execute on function wl_cleanup() from public, anon;
grant execute on function wl_opened(text, text, jsonb) to anon;

-- Every night at 03:17 UTC. (pg_cron was switched on by the owner on 7 November 2026.)
select cron.schedule('wl-cleanup', '17 3 * * *', 'select public.wl_cleanup()');
