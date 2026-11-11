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
