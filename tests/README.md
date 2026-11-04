# Tests for the web pages and the helper function

None of these touch the real Supabase or real shops. They use a pretend Supabase (`e2e/fake.js`), a real headless Chromium (Playwright), jsdom and real Deno.

Run everything (from this folder):

    npm install
    npx playwright install chromium
    bash run-all.sh

The last line must say `ALL SUITES OK`. Each suite also exits with an error code when something fails.

| Folder | What it checks | Checks |
|---|---|---|
| `parse/parse_test.js` | `web/parse.js` reads product pages (prices, currencies, Amazon, JSON-LD, Funda, blocked pages); mirrors `Fetch.kt` | 54 |
| `i18n/test.js` | Every text in the app (`tr`) and the web pages (`T`, fixed HTML texts) has a Dutch version with the same placeholders | 5 |
| `function/test.ts` | `supabase/functions/fetch-page/index.ts` in real Deno: private addresses, redirects, size and time limits, wrong keys, a group link as key | 101 |
| `index-page/test.js` | `web/index.html` in jsdom: friends' page, invite page (Android vs. other devices), clipboard copy | 140 |
| `e2e/t1.js` to `t6.js` | The browser editor `web/edit.html` in Chromium: viewing, the app-style top bar, side menu, category drop-down and sort menu, the three views, adding by link or by hand, editing one field, reservations (Reserve/Undo, someone faster, offline), delete/Undo, categories, list name and note, share dialog and the share picture with the list name, failed saves, sorting, search, offline copy, pictures, two people at once, old refresh answers, iPhone tip, invite redirects, the link-preview pages join/ and list/ and the friends' page of a shared list | 37 + 32 + 29 + 38 + 34 + 42 |

`group/test.js` (in `run-all.sh`, but it says SKIPPED unless `WL_PG` is set): the group page `web/group.html` in Chromium against the REAL Supabase scripts in a local PostgreSQL (each rpc call runs as `select fn(...)` with the role anon; storage and the helper are pretended with the same key rules): joining, reserving, the own list (auto-fill, picture upload, top wish, delete/Undo), My link on another device, leaving, joining again, the move to the app, Dutch. 55 checks. Run with for example `WL_PG="-h /path/to/socket -p 5499 -U postgres" bash run-all.sh` (it makes and drops the database `wl_group_test`). `sql/groups_test.sql` checks `supabase-groups.sql` on its own (run after setup, shared, friends and chat): creating, invites, No thanks, joining by invite and link, members only, change my list, removing (told), new link, photo, browser members and the takeover, leaving with a new organiser, deleting. It ends with `ALL GROUP CHECKS OK`. Note: `sql/friends_test.sql` still checks the old hiding, so run it before `supabase-groups.sql`.

Not in `run-all.sh` (it needs PostgreSQL): `sql/friends_test.sql` checks `supabase-friends.sql` (profiles, friend codes, requests, approve/decline, hiding, unfollow, which lists friends may open) on a local PostgreSQL with a role `anon`: run `supabase-setup.sql`, `supabase-shared.sql` and `supabase-friends.sql` in an empty database, then `psql -v ON_ERROR_STOP=1 -f sql/friends_test.sql`. It ends with `ALL FRIENDS CHECKS OK`. 43 checks. `sql/chat_test.sql` (run after also running `supabase-chat.sql`) checks the messages functions: only friends can send, strangers read nothing, read ticks, deleting only your own, history after unfollowing. It ends with `ALL CHAT CHECKS OK`. 15 checks. `sql/chat_photos_test.sql` (run right after `chat_test.sql`, same database) checks photos in messages (2.109): only friends, wrong token refused, not readable before its message is sent, only the sender sends it and only once, both people can fetch it and nobody else, gone with its message, cleanup after 30 days (unsent after a day), at most 50 a day. It ends with `ALL CHAT PHOTO CHECKS OK`. 24 checks.

`csp/test.js` (in `run-all.sh`): every page in `web/` has the Content-Security-Policy before any script, no inline script code or `on…=` handlers, and no referrer. `e2e/t7.js`: the browser editor's Bin (restore only, 30 days, someone else's delete, a failed restore undone).

Notes
- `e2e/lib.js` serves the real `web/` folder and starts the pretend Supabase; `boot({...})` options: `items`, `about`, `name`, `key`, `ua`, `dark`, `reduced`, `clock`, `goto`, `viewport`, or `fake` to share one pretend server between two browser windows.
- A suite that fails prints `FAIL <name>`. Fix the code, or the check if the behaviour was changed on purpose, and say which.
- If you change a field of a wish, the helper's rules, `parse.js`, the invite page or the editor, update the matching checks in the same change.

`function/push_test.ts` (in `run-all.sh`) checks `supabase/functions/push`: the signed Google token request, one push per message, old messages skipped, no text in the push, forgotten addresses, bad input. `sql/push_test.sql` (not in `run-all.sh`, needs PostgreSQL) checks `supabase-push.sql` after the chat setup: fetching doesn't mark read, `wl_chat_seen`, push addresses, the trigger. Locally pg_net doesn't exist: create a schema `net` with a table `net.calls(url text, body jsonb)` and a function `net.http_post(url text, body jsonb, headers jsonb)` that inserts into it, and leave out the `create extension` line. It ends with `ALL PUSH CHECKS OK`. 20 checks.
