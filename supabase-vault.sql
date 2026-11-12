-- The recovery key's locked copy (ROADMAP S4.5; owner, 11 November 2026). Run AFTER all the other supabase-*.sql
-- scripts. Safe to run again; it only adds.
--
-- Every app has a recovery key (RecoveryKey.kt): 32 letters and digits that only its owner has (in their password
-- manager, notes...). From the key the app works out three things the server never sees together with the key:
--   * the copy's id      (a fingerprint of the key, so the server can find the copy),
--   * a write proof      (a second fingerprint; the server keeps only ITS fingerprint, so even a database leak can't
--                         be used to overwrite or read a copy),
--   * the lock           (an AES-256 key that never leaves the phone).
-- The copy itself is locked on the phone before it is sent: the server stores data it can't read. It holds what a
-- new phone needs (the profile and list keys, private wishes, "Only me" lists, the bin, price history, chat keys).
-- Restoring on a new phone (2.188) reads it with the id and the proof, and unlocks it with the lock.
--
-- At most 300 kB per copy (the free database is 500 MB); copies not saved for 13 months go (supabase-cleanup.sql).

create table if not exists wl_vaults (
  id text primary key,                         -- 64 hex characters
  proof text not null,                         -- sha256 of the write proof, hex
  data text not null default '',               -- the locked copy, base64
  updated_at timestamptz not null default now()
);
alter table wl_vaults enable row level security;

create or replace function wl_vault_ok(p_id text, p_proof text)
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce(p_id, '') ~ '^[0-9a-f]{64}$' and coalesce(p_proof, '') ~ '^[0-9a-f]{64}$'
$$;

-- Saves the locked copy [p_data] under [p_id]. The first save sets the proof; later saves need the same proof.
create or replace function wl_vault_put(p_id text, p_proof text, p_data text)
returns void language plpgsql security definer set search_path = public as $$
declare v_hash text;
begin
  if not wl_vault_ok(p_id, p_proof) then raise exception 'bad key'; end if;
  if length(coalesce(p_data, '')) < 16 or length(p_data) > 300000 then raise exception 'bad size'; end if;
  v_hash := encode(sha256(convert_to(p_proof, 'UTF8')), 'hex');
  insert into wl_vaults (id, proof, data) values (p_id, v_hash, p_data)
    on conflict (id) do nothing;
  if not found then
    update wl_vaults set data = p_data, updated_at = now() where id = p_id and proof = v_hash;
    if not found then raise exception 'not allowed'; end if;
  end if;
end $$;

-- The locked copy, or null (no copy, or a wrong proof: the same answer, so a guess learns nothing).
create or replace function wl_vault_get(p_id text, p_proof text)
returns text language plpgsql stable security definer set search_path = public as $$
begin
  if not wl_vault_ok(p_id, p_proof) then return null; end if;
  return (select data from wl_vaults where id = p_id and proof = encode(sha256(convert_to(p_proof, 'UTF8')), 'hex'));
end $$;

-- Removes a copy (when its key is replaced by a new one).
create or replace function wl_vault_delete(p_id text, p_proof text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if not wl_vault_ok(p_id, p_proof) then raise exception 'bad key'; end if;
  delete from wl_vaults where id = p_id and proof = encode(sha256(convert_to(p_proof, 'UTF8')), 'hex');
end $$;

revoke execute on function wl_vault_ok(text, text) from public, anon;
grant execute on function wl_vault_put(text, text, text), wl_vault_get(text, text), wl_vault_delete(text, text) to anon;

-- ---------- Restoring on a new phone (2.188) ----------
-- The new phone has unlocked the copy with the key. In one go it gives the profile and every list made on the old
-- phone a NEW secret (token), and the copy is deleted: the old phone's secrets stop working at once, so it is signed
-- out (owner, 11 November 2026: "restoring signs out the old phone"). Only someone who can read the copy (who has the
-- key) can do this: the call needs the copy's id and proof. Fingerprints of the old secrets are kept 13 months, so
-- the old phone can ask whether it was signed out (wl_signed_out) and show "Your wishlists are on your new phone".
create table if not exists wl_old_secrets (
  hash text primary key,                       -- sha256 of an old secret, hex
  at timestamptz not null default now()
);
alter table wl_old_secrets enable row level security;

create or replace function wl_hash(p text) returns text language sql immutable set search_path = public as $$
  select encode(sha256(convert_to(coalesce(p, ''), 'UTF8')), 'hex')
$$;

-- p_lists: [{c: code, o: old owner token, n: new owner token}], own lists and collab wishlists made on the old phone.
-- Returns {profile: true when the profile moved, lists: how many lists moved}. Lists or a profile that are no longer
-- online (cleaned up, handed over) are skipped: the new phone puts its own lists back by itself.
create or replace function wl_vault_restore(p_vault text, p_proof text, p_id text, p_old text, p_new text, p_lists jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare e jsonb; v_n int := 0; v_pf boolean := false;
begin
  if not wl_vault_ok(p_vault, p_proof) then raise exception 'bad key'; end if;
  if not exists (select 1 from wl_vaults where id = p_vault and proof = wl_hash(p_proof)) then
    raise exception 'not allowed';
  end if;
  if jsonb_typeof(coalesce(p_lists, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_lists, '[]'::jsonb)) > 200 then
    raise exception 'bad lists';
  end if;
  if coalesce(p_id, '') <> '' and length(coalesce(p_old, '')) >= 16 then
    if length(coalesce(p_new, '')) < 16 or length(p_new) > 64 then raise exception 'bad token'; end if;
    update wl_profiles set token = p_new where id = p_id and token = p_old;
    if found then
      v_pf := true;
      insert into wl_old_secrets (hash) values (wl_hash(p_old)) on conflict do nothing;
      -- The old phone's notification address: the new phone sends its own.
      delete from wl_push where id = p_id;
    end if;
  end if;
  for e in select * from jsonb_array_elements(coalesce(p_lists, '[]'::jsonb)) loop
    if length(coalesce(e ->> 'o', '')) < 16 or length(coalesce(e ->> 'n', '')) < 16 or length(e ->> 'n') > 64 then continue; end if;
    update wl_lists set owner_token = e ->> 'n' where code = e ->> 'c' and owner_token = e ->> 'o';
    if found then
      v_n := v_n + 1;
      insert into wl_old_secrets (hash) values (wl_hash(e ->> 'o')) on conflict do nothing;
    end if;
  end loop;
  -- The old copy is emptied and locked rather than deleted: the old phone, not yet aware it was signed out, could
  -- otherwise save a new copy under the old key. Its saves are refused now ('not allowed'); the clean-up removes the
  -- row after 13 months.
  update wl_vaults set data = '', proof = 'restored', updated_at = now() where id = p_vault;
  delete from wl_old_secrets where at < now() - interval '13 months';
  return jsonb_build_object('profile', v_pf, 'lists', v_n);
end $$;

-- Asked by a phone whose calls are refused: were any of these secrets replaced by a restore on another phone?
create or replace function wl_signed_out(p_tokens jsonb)
returns boolean language plpgsql stable security definer set search_path = public as $$
begin
  if jsonb_typeof(coalesce(p_tokens, '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_tokens, '[]'::jsonb)) > 200 then
    return false;
  end if;
  return exists (
    select 1 from jsonb_array_elements_text(coalesce(p_tokens, '[]'::jsonb)) t
    where length(t) >= 16 and exists (select 1 from wl_old_secrets s where s.hash = wl_hash(t))
  );
end $$;

revoke execute on function wl_hash(text) from public, anon;
grant execute on function wl_vault_restore(text, text, text, text, text, jsonb), wl_signed_out(jsonb) to anon;
