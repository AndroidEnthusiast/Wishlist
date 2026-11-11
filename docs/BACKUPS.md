# Database backups and keeping Supabase awake

Owner's decisions D3 and D4 (8 November 2026, `docs/DECISIONS.md`). Both are GitHub jobs that only run on a schedule.

## Weekly backup (the private repository `AndroidEnthusiast/Wishlist-backups`, since 11 November 2026)
- Every Sunday night (and whenever you press **Actions > Weekly database backup > Run workflow** there), GitHub copies
  the `public` part of the Supabase database (every list, wish, reservation, friend, group, exchange and encrypted
  chat) and every picture in Supabase Storage's public buckets (wish photos, covers).
- It then **restores the copy into a throw-away database** and checks that every table is there with its rows (a
  difference of a row or two, from a save during the copy, is only a warning).
- Database and pictures are **locked together with your backup password** (AES-256) in one file and kept **60 days**:
  that repository > **Actions** > **Weekly database backup** > a run > **Artifacts**. The repository is private, so
  only you can download them. First run, 11 November 2026: 29 tables, 90 pictures, 360 KB.
- Not in the copy: Supabase's own parts (accounts, settings, scheduled jobs); the `supabase-*.sql` scripts set those
  up again.
- The job fails (and GitHub e-mails you) when a secret is missing, the database can't be reached, the copy is empty,
  more than a few pictures can't be copied, the restore misses rows, or the password doesn't open the file again.
- Until 11 November 2026 the job lived in the public Wishlist repository (`backup.yml`, database only, 90 days).

### The two secrets (once, in Wishlist-backups)
**Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
1. `SUPABASE_DB_URL`: in Supabase, open the project, tap **Connect** (top of the page), choose **Session pooler**
   (not "Direct connection": GitHub can't reach that one), copy the line that starts with `postgresql://`, and replace
   `[YOUR-PASSWORD]` with the database password (forgotten? **Project Settings** > **Database** > **Reset database
   password**; the app and web pages don't use it, so resetting is safe; then update this secret).
2. `BACKUP_PASSWORD`: a long password (at least 16 characters) kept in your password manager. If it is lost, the
   copies can't be opened; if it changes, older copies still need the old one.

## Restoring (the way back)
Only after an accident, and done by Claude in a session, never by hand on a phone:
1. Download the newest copy (the `.tar.gpg` file inside the artifact zip; before 11 November 2026 a `.dump.gpg` in the
   Wishlist repository) and give Claude the backup password in the session's secure form (never in the chat).
2. Unlock: `gpg -d wishlist-YYYY-MM-DD.tar.gpg > bundle.tar && tar -xf bundle.tar` (gives `wishlist.dump`, the
   `pictures/` folder per bucket and list, and `counts.txt`, the rows per table at the time of the copy).
3. Look before touching anything: `pg_restore --list wishlist.dump`. To bring back one table only:
   `pg_restore --data-only --table=wl_items -d "$SUPABASE_DB_URL" wishlist.dump` (into an empty table).
4. Whole database lost: run the `supabase-*.sql` scripts in their usual order in a fresh project first, then
   `pg_restore --data-only --disable-triggers -d "$SUPABASE_DB_URL" wishlist.dump`.
5. Pictures: upload `pictures/<bucket>/<path>` back to the same bucket and path (Supabase > Storage), or ask Claude
   for a small script.
Test a restore into a scratch database before restoring into the real one (the weekly job does that already, every
time, for the database part).

## Keeping Supabase awake (`.github/workflows/keep-awake.yml`)
A free Supabase project pauses after about a week without use. Every 2 days this job asks one tiny question
(reservations of a list that doesn't exist: nothing is read or changed). If Supabase doesn't answer, the job fails and
GitHub e-mails you: open supabase.com, and if the project says **Paused**, press **Restore project**.
Note: GitHub switches off scheduled jobs in a repository with no commits for 60 days; it e-mails a warning first.
