# Database backups and keeping Supabase awake

Owner's decisions D3 and D4 (8 November 2026, `docs/DECISIONS.md`). Both are GitHub jobs that only run on a schedule.

## Weekly backup (`.github/workflows/backup.yml`)
- Every Sunday night (and whenever you press **Run workflow**), GitHub copies the `public` part of the Supabase
  database: every list, wish, reservation, friend, group, exchange and (encrypted) chat.
- The copy is **locked with your backup password** (AES-256) before it is saved, because anyone with a GitHub account
  can download the job files of a public repository. Without the password the copy can't be opened, by anyone.
- Each copy is kept **90 days**: GitHub > **Actions** > **Weekly database backup** > a run > **Artifacts**.
- Not in the copy: pictures uploaded to Supabase Storage (only their addresses) and Supabase's own parts; the
  `supabase-*.sql` scripts set those up again.
- The job checks itself: it fails (and GitHub e-mails you) when a secret is missing, the database can't be reached, the
  copy looks empty or the password doesn't open it again.

### The two secrets (once)
Both go in GitHub: the repository > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
1. `SUPABASE_DB_URL`: in Supabase, open the project, tap **Connect** (top of the page), choose **Session pooler**
   (not "Direct connection": GitHub can't reach that one), copy the line that starts with `postgresql://`, and replace
   `[YOUR-PASSWORD]` with the database password (forgotten? **Project Settings** > **Database** > **Reset database
   password**; the app and web pages don't use it, so resetting is safe).
2. `BACKUP_PASSWORD`: a long password you make up (at least 16 characters). **Save it in your password manager first.**
   If it is lost, the copies can't be opened; if it changes, older copies still need the old one.

## Restoring (the way back)
Only after an accident, and done by Claude in a session, never by hand on a phone:
1. Download the newest copy (the `.dump.gpg` file inside the artifact zip) and give Claude the backup password in the
   session's secure form (never in the chat).
2. Unlock: `gpg -d wishlist-YYYY-MM-DD.dump.gpg > wishlist.dump`.
3. Look before touching anything: `pg_restore --list wishlist.dump`. To bring back one table only:
   `pg_restore --data-only --table=wl_items -d "$SUPABASE_DB_URL" wishlist.dump` (into an empty table).
4. Whole database lost: run the `supabase-*.sql` scripts in their usual order in a fresh project first, then
   `pg_restore --data-only --disable-triggers -d "$SUPABASE_DB_URL" wishlist.dump`.
Test a restore into a scratch database before restoring into the real one.

## Keeping Supabase awake (`.github/workflows/keep-awake.yml`)
A free Supabase project pauses after about a week without use. Every 2 days this job asks one tiny question
(reservations of a list that doesn't exist: nothing is read or changed). If Supabase doesn't answer, the job fails and
GitHub e-mails you: open supabase.com, and if the project says **Paused**, press **Restore project**.
Note: GitHub switches off scheduled jobs in a repository with no commits for 60 days; it e-mails a warning first.
