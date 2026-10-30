-- Wishlist photo sharing setup. Run this ONCE in Supabase: SQL Editor -> New query -> paste -> Run.
-- Run supabase-setup.sql first (you already did). Safe to run again later.

-- 1. A public bucket for the pictures. Only small JPEG files, 500 KB at most each.
--    "Public" only means a picture can be viewed if you know its exact address,
--    which contains the random list code. Nobody can list or browse the bucket.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('wish-photos', 'wish-photos', true, 512000, array['image/jpeg'])
on conflict (id) do update
  set public = true, file_size_limit = 512000, allowed_mime_types = array['image/jpeg'];

-- 2. Checks that the caller is the owner of the list in the picture's folder.
--    The app sends the list's secret owner token in a header called x-owner-token.
create or replace function public.wl_owner_ok(p_code text)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from wl_lists
    where code = p_code
      and owner_token = coalesce(
        nullif(current_setting('request.headers', true), '')::json ->> 'x-owner-token', '')
  )
$$;

grant execute on function public.wl_owner_ok(text) to anon;

-- 3. Only the owner of a list can add, look at or delete pictures in that list's folder.
drop policy if exists "wl photos insert" on storage.objects;
create policy "wl photos insert" on storage.objects for insert to anon
  with check (bucket_id = 'wish-photos' and public.wl_owner_ok((storage.foldername(name))[1]));

drop policy if exists "wl photos select" on storage.objects;
create policy "wl photos select" on storage.objects for select to anon
  using (bucket_id = 'wish-photos' and public.wl_owner_ok((storage.foldername(name))[1]));

drop policy if exists "wl photos delete" on storage.objects;
create policy "wl photos delete" on storage.objects for delete to anon
  using (bucket_id = 'wish-photos' and public.wl_owner_ok((storage.foldername(name))[1]));
