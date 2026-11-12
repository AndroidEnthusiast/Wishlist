# Roadmap: from "it works" to a real app

Written 8 November 2026 at the owner's request: *"I want the foundations to work for me and my partner but at the same
time allow to scale up in the future."* This is the plan every session follows until it is done. **A second review on 10
November 2026 re-checked Stages 0–3 and added Stage R, more Stage 4 steps, Stage 6 (Google Play) and a checklist for
every new feature: see "Second review" further down, and do Stage R before Stage 4.** Work through it **in
order, one stage at a time**; tick off steps here (`[x]`) in the same commit that does them, and note anything learned.

## The goal, in one line per person
- **You and your partner today:** nothing gets lost, every tap does what you expect, Back always goes where you think,
  the app is fast with hundreds of wishes and doesn't drain the battery.
- **Thousands of users later:** the server stays inside the free plan as long as possible, one person's mistake or abuse
  can't hurt others, and new features can be added without breaking old ones.
- **Every future session:** the code is easy to understand and checked automatically, so changes stop causing surprises.

## Honest state on 8 November 2026 (version 2.152)

### What is solid (keep it)
- **Server security:** 173 Supabase functions, every table locked (row level security, no open policies), every
  function checks a key or token, private wishes never leave the phone, chat end-to-end encrypted.
- **Web pages and database scripts are tested:** about 700 automated checks (`tests/`), run before every push.
- **Decisions are written down** (`docs/DECISIONS.md`, `docs/STYLE.md`, `README.md`), so the app doesn't drift.
- **Careful saving of collab wishlists:** one wish at a time, only the changed fields, undone with a message on failure.

### What is "made to work" (the reasons for this roadmap)
| # | Problem | What a user notices | When it hurts |
|---|---------|--------------------|---------------|
| P1 | **One giant switchboard for screens.** `MainActivity.kt` is 2,480 lines; about 69 on/off switches decide which screen shows; Back has to know all of them. | Back sometimes goes to the wrong place; screens overlap; bugs come back after fixes. | Already now; worse with every screen. |
| P2 | **All app data in one global object (`Repo`) and the phone's simple settings store**, lists as big text blobs (`MyLists`, `SafeStore` exists because one bad save once wiped a list). No separation between screens and data. | Slower start and saving with many wishes; a rare crash during a save can damage a list. | Hundreds of wishes, many lists. |
| P3 | **Your own list goes online in full on every change** (`Sync.pushData` → `wl_sync_list` deletes and rewrites every row). | Saving gets slower as lists grow; two of your phones would overwrite each other. | Big lists; using two phones. |
| P4 | **Polling instead of being told:** open lists ask every 20 s; a background job runs ~10 checks every 30 min (`BinWorker`). | Battery use; news arrives late or in piles. | Many users: the free plan's request and egress limits. |
| P5 | **Profile, group and exchange photos stored as text inside the database.** | Nothing yet. | The 500 MB free database fills up. |
| P6 | **No automated tests for the app's own logic**; GitHub only checks that it compiles, and only after the push. | Build errors and wrong assumptions reach the owner first. | Every update. |
| P7 | **Identity is the phone:** lists belong to the phone that made them (owner tokens, device ids). A lost or reset phone without an Export file loses ownership. | Your partner gets a new phone: their lists can't be managed any more. | Every phone change. |
| P8 | **No safety nets on the server:** the free plan has no backups, pauses a project after a week without use, and nothing limits how often someone may call a function (the public key is meant to be public). | One bad script or one abuser could damage or fill the database; a quiet week pauses the app. | Any time; more likely with more users. |
| P9 | **The product reader guesses** (`Fetch.kt`, `web/parse.js`), tested only on hand-written example pages. | Wrong or missing prices (the Zalando case). | Every new shop layout. |
| P10 | **No crash reports:** a crash on someone's phone is only known if they tell us. | Silent problems. | More users. |

## Rules for executing this roadmap
1. **Foundations first, features frozen** until Stage 3 is done (owner may still ask for small fixes). New features
   built on the old foundations would have to be rebuilt.
2. **Nothing the user sees changes** in Stages 1–3 unless written here. Each stage ends with a short "test on your
   phone" list for the owner, and the next stage starts only after the owner says it works.
3. **Small steps, each one shippable.** Each step is its own version pushed to `main` (the owner's rule), the old way
   keeps working until the new way has replaced it completely, and no step leaves the app half-converted overnight.
4. **Data is sacred.** Any step that moves stored data: (a) keeps the old copy untouched until a later version
   proves the new one, (b) is tested with a copy of real data, (c) has a written way back.
5. **Checks come with the change.** Every step adds or updates automated checks; a fix comes with a check that fails
   without it (as with the Zalando prices).
6. **Supabase scripts stay additive** (old apps keep working: run the script first, then install the app).
7. **A concrete summary at the end of every session** (owner, 8 November 2026): which roadmap steps were done (with
   their numbers), what is now better for the user and in the code (measurable where possible: lines removed from
   `MainActivity.kt`, checks added, requests saved), what was tested and what couldn't be, and the next step. The goal
   is a real Android app, not something that feels vibe coded: no shortcuts that leave the foundations worse.

## Decisions to ask the owner at the start of Stage 0 (pop-up questions)
- **D1 Full network access during the rebuild** (to compile and run the app's tests here before pushing). The owner
  turned it off on 5 November because setup was slow; with tests in GitHub (S0.1) it may not be needed. Recommend: on
  for Stages 1–3 sessions only.
- **D2 Crash reports:** Firebase Crashlytics (free, already have Firebase; sends crash details, no wishes or names) or
  none. Recommend: Crashlytics, mentioned in the privacy text.
- **D3 Database backups:** a weekly GitHub job that copies the database (needs the Supabase database password as a
  GitHub secret; stored as a private artifact for 90 days), or Supabase's paid plan, or none. Recommend: weekly job.
- **D4 Keeping the free project awake:** a small scheduled GitHub job that touches Supabase every few days (free) vs.
  accepting pauses. Recommend: the job.
- **D5 Identity (P7):** keep "the phone is you" plus Export; or add **Supabase anonymous accounts** (still no e-mail;
  can later be linked to Google or a passkey to survive a new phone). This decides how Stage 4 is built. Ask with the
  trade-offs; don't build either before the answer.

## Stage 0 — Safety nets (1–2 sessions)
Goal: catch mistakes before they reach the phone, and never lose server data.
- [x] S0.1 GitHub build also runs the app's unit tests (`gradle testReleaseUnitTest`) and fails if one fails; keep
  the screenshot tests out of CI (owner's choice of 5 November).
- [x] S0.2 First real app tests: move pure logic out of screens where it's easy (price parsing, totals, sorting,
  occasion dates, quantity labels, reservation labels) and test it with plain JUnit (no Android needed).
- [ ] S0.3 *(2.178: the shared `pages` list exists, with 14 hand-made pages answered by both readers; real shop
  pages are step R4 of the second review.)* Product reader fixtures: saved copies of real product pages (only the HTML, no pictures) for the 20 starter
  shops in `tests/parse/pages/`, checked by both `Fetch.kt` (JUnit) and `web/parse.js`; the same page must give the
  same answer in both (P9). Pages are saved by a session with network access or uploaded by the owner.
- [x] S0.4 D3 backups and D4 keep-awake, if the owner said yes. *(Both jobs green on 8 November 2026: the first backup
  copied 29 tables (Postgres 17), locked, kept until 6 February 2027.)*
- [x] S0.5 D2 crash reports, if the owner said yes (`Licences.kt` if a new library is added).
Owner tests: nothing changes on the phone. Done when the build runs tests and the backups exist.
**Signed off by the owner on 8 November 2026** ("seems to work like before"; anything noticed later still gets fixed
first). S0.3 stays open by the owner's choice, to be done when a shop reads badly or pages are uploaded. The backup
after the database password reset was green (run 3).
*Notes (session 1, 8 November 2026, 2.153):* D1–D5 answered (DECISIONS "Roadmap decisions D1–D5"). 32 app checks (31 plain
JUnit + 1 Robolectric start check) and 49 shared app/web checks; the first S0.2 moves: `Totals.kt`, `OccasionUi.kt`,
`QuantityUi.kt`. More pure logic (price parsing helpers, reservation counts on lists) moves when Stage 1 touches those
screens. S0.3 still open: this environment can't reach the shops (most answer cloud computers with a bot check), so the
saved pages must be uploaded by the owner, or fetched by a session with those hosts allowed.

## Stage 1 — Screens that manage themselves (3–5 sessions)  (P1)
Goal: one screen at a time, Back always right, `MainActivity.kt` small.
- [ ] S1.1 Add Navigation Compose and ViewModels (androidx `navigation-compose`, `lifecycle-viewmodel-compose`).
  *(2.154: Navigation Compose added, `Nav.kt`. `lifecycle-viewmodel-compose` is added with the first screen whose state
  moves into a ViewModel, not before: an unused library is only weight. ViewModels only pay off once there is a data
  layer to read from, so they come with S2.2 (screens read the repositories through ViewModels).)*
  One route per screen: Home, a list, list settings, Messages, a chat, My shops, Friends, a friend's list, Profile,
  App settings, Bin, Notifications, Gifts I reserved, Received gifts, groups, gift exchanges, price pages.
- [x] S1.2 Move screens over **one at a time**, starting with the simplest (Bin, App settings, Notifications), then
  the tabs, then the list screen last. Each move removes its on/off switches from `MainActivity`.
  *(2.154: Profile, App settings, Bin, Notifications are routes; 4 of the 75 switches gone. 2.156: Received gifts,
  Gifts I reserved, Price changes, a list's settings; 8 gone. 2.157: a list opened from its link or code; 9 gone. 2.158: the tabs
  (Messages, My shops, Friends); 12 gone. 2.159: a wishlist group, Gift exchanges; 14 gone. 2.160: Home and the open
  list; every screen is a route, the screen switches and "main" Back rules are all gone.)*
- [x] S1.3 Notifications and links (`OPEN_*` extras, App Links) open routes instead of setting switches. *(2.162:
  `Deep.kt`, one `App.go`; a check per entry point. In-app requests between screens (GroupNav, ExchangeNav, ChatNav,
  FriendsNav, PricesNav, NotesNav) still use their flags; they move with S1.4 or the ViewModels.)*
- [x] S1.4 Dialogs that belong to one screen move into that screen; app-wide ones (delete/undo, move, share) stay in
  one place. *(2.163: New category → the list screen; Create a list and its three forms → Home. MainActivity.kt split
  into AppScreen, ListScreen, WishCards, WishForm: 142 lines.)*
- [x] S1.5 Delete the switched-off side menu code (owner decided 7 November 2026, if still wanted). *(2.154, owner
  confirmed 8 November 2026.)*
Owner tests per step: the moved screens open, Back goes to the right place, notifications open the right thing.
Done when `MainActivity.kt` only sets up the app (< 300 lines) and every screen is a route.
*Code work done 8 November 2026 (2.154–2.163): every screen is a route (2.160), every way in goes through `Deep.kt`
(2.162), `MainActivity.kt` 142 lines (2.163), 63 app checks. Left for later on purpose: in-app requests between
screens still use small flag objects (GroupNav, ExchangeNav, ChatNav, FriendsNav, PricesNav, NotesNav, HistoryNav);
they go when those screens get ViewModels (S2.2). **Signed off by the owner on 8 November 2026** after the final
test ("all seems to work"; anything noticed later is fixed first).*

## Stage 2 — A real database on the phone (3–4 sessions)  (P2)
Goal: fast, safe local data that can't be half-saved.
*Owner's ground rules (8 November 2026): tested with their real Export first; their phone only (the partner updates
later); the move in its own version; Export no longer carries Firebase files (2.164). The real Export has 241 stored
values (wishes ~3 KB), plus ~100 leftover shop-icon entries from old versions that the move can leave behind.*
- [x] S2.1 Add Room (SQLite). Tables: lists, wishes (one row per wish per list, so copies are just rows), bin,
  price history, categories, friends' lists, notifications. Keep JSON only where it is truly free-form. *(2.165,
  `Db.kt`, empty: version A. Only a list's uploaded-photo names stay a JSON list. Not in tables yet, on purpose:
  settings (theme, view, sort…), profile, shared lists' caches and keys, groups, exchanges: they stay in the
  settings store for now; Stage 2's goal is your lists and wishes.)*
- [ ] S2.2 *(2.167, part C1: `ListStore`, your lists and wishes read from the database, the settings store written
  too as the fallback. 2.168, part C2: the bin, price history, notifications and friends' lists too. 2.169: only what changed is
  written (DbDiff). ViewModels/streams: not as one rebuild (owner chose the slimmer route, DECISIONS); screens already
  update by themselves through Repo's state, and ViewModels come screen by screen when a screen is changed anyway.)*
  A data layer (repositories) between screens and storage; ViewModels read from it as streams, so every
  screen updates by itself when data changes (replaces `mineVersion` and manual refreshes).
- [ ] S2.3 **Moving existing data:** on first start of the new version, copy everything from the settings store into
  Room inside one transaction, check the counts match, keep the old data untouched for at least two versions, and
  write a flag only when it all worked. If anything fails, the app keeps using the old data and reports it (D2).
  Tested first with an Export file of the owner's real data. *(2.166, version B: a rehearsal at every start, the app
  still uses the settings store; the owner's real Export moved exactly (1 list, 7 wishes, 1 bin, 17 price points,
  5 notifications). The flag that switches the app over comes with version C.)*
- [x] S2.4 Export/Import read and write the new database (old Export files still import). *(2.170: Export from the
  database in the old file format; Import empties the database and the next start moves the file in.)*
- [ ] S2.5 Only after the owner confirms on both phones: remove the old storage code (`MyLists` blobs, `SafeStore`).
  *(2.171, owner's OK on their phone (the partner updates later): the settings store is no longer written for lists,
  wishes, bin, history, friends' lists and notifications; its last copy stays untouched; the old code stays only as
  the fallback when the database can't be read, and for phones coming from older versions. Removing that code is
  for later, once the partner's phone has moved too.)*
Owner tests: everything is still there after updating (lists, bin, history, categories, covers), on both phones.
**Signed off by the owner on 8 November 2026** (2.171 on their phone: "Yes, all works"). The partner's phone moves
its data on its first start of a new version; removing the old storage code (S2.5's last part) waits for that.

## Stage 3 — Syncing like a real app (2–3 sessions)  (P3, P4)
Goal: small, fast saves; changes arrive when they happen; less battery.
- [x] S3.1 Own lists save one wish at a time, only changed fields (new `wl_put_mine` with the owner token, like
  `wl_sh_put_item`), with the same undo-on-failure rule as collab wishlists. The full-list sync stays for older apps
  only. *(2.173: `wl_mine_put` sends only new/changed wishes and removed ids in one call (`MineSync.kt`, fingerprints
  of what the server has); whole wishes, since only the owner's phone writes them. Owner's choice: **a failed save
  keeps the change** and is retried (next change, start, background check), no undo. The whole list still goes the
  first time, weekly as a repair, and while the script isn't run.)*
- [x] S3.2 Supabase Realtime for open lists, collab wishlists and chat instead of 20-second polling (falls back to
  polling when Realtime isn't reachable). Needs RLS-safe channels: broadcast "something changed" only, never data;
  the app then fetches through the existing checked functions. *(2.174, `Live.kt`: collab wishlists, a friend's
  list, Messages and an open chat; public channels named by SHA-256 fingerprints, triggers in supabase-sync.sql; at
  most one fetch per 2 s per screen; polling as before when not live; nothing in the background. Web pages still
  poll: the owner wants a reminder later.)*
- [x] S3.3 One background job that asks the server "what's new for me?" in one call (extend `wl_news_get`) instead of
  ten separate checks; pushes stay for urgent things. *(2.175, `Pulse.kt` + `wl_pulse`: fingerprints per part; only
  changed parts are asked; the first check of each day asks everything (date-based reminders); friends' lists only
  count this phone's own reservations, so an owner can't notice a reservation. Also: reactions asked once before the
  evening instead of every half hour.)*
- [x] S3.4 Measure: requests per user per day before and after (log counts in debug builds), written down here.
  *(2.172: counted on every phone, App settings > About > Details. The owner chose to test all of Stage 3 at the end,
  so there is no measured "before" day; the estimate below is worked out from the code for the owner's use (1 own
  list online, 1 collab list, 2 friends' lists reserved on, a profile with chats). Replace it with the phone's real
  numbers after a normal day on 2.175 + supabase-sync.sql.)*

  | What | Before (2.171) | After (2.175) |
  |---|---|---|
  | Background check, 48 a day | ~11 calls each (+1 reactions per list until the evening) ≈ 550/day | 1 pulse + only changed parts; first check of the day ~11 ≈ 60–75/day |
  | A collab list open 1 hour | 2 calls / 20 s = 360 | 1 connection + 2 calls per change (and own saves' echo) |
  | A friend's list open 1 hour | 2 calls / 20 s = 360 | 1 connection + 2 calls per change |
  | Messages tab open 10 minutes | 1 / 8 s = 75 | 1 per message that comes or goes |
  | A chat open 10 minutes, 10 messages | ~2.5 / 3 s = 500 | ~25 |
  | Editing one wish on your own list | the whole list (every wish) | one small call with that wish |
  | Starting the app | the whole open list + the owner name | nothing, unless a save was waiting |
  | In the background with a list or chat open | kept polling | nothing |
Owner tests: two phones editing the same list, chat, collab list updates arrive within seconds, battery over a day.
**Signed off by the owner on 8 November 2026** (supabase-sync.sql run, 2.175 installed; checked here read-only: `wl_pulse` and
`wl_mine_put` are installed). The two-phone tests and the real S3.4 numbers were not done yet: anything noticed later is fixed first.

## Stage 4 — The server, ready to grow (2–3 sessions)  (P5, P7, P8)
*Owner's order (11 November 2026): A groundwork (S4.1 photos, S4.2 limits, S4.3 clean-up, S4.4 indexes, S4.9 the free
plan's limits), then B identity with a recovery key (S4.5), then C privacy page + delete my data (S4.6, S4.7), then D
report and block (S4.8). Answers in DECISIONS "Stage 4".*
- [x] S4.1 *(2.185, `PeoplePhotos.kt`: files in the bucket `people-photos` with a new random address per photo, old embedded photos still shown and moved by their owner's phone; the owner chose to do it now although the database was 14 MB and the photos 58 kB. Left for a later version, once older apps are gone: clear the remaining embedded photos with a script.)* Photos (profile, group, exchange) move to Supabase Storage with small file sizes; the database keeps only
  the address. Old text photos are moved by a script, then cleared.
- [x] S4.2 *(2.184, `supabase-limits.sql`: triggers on what is added, the owner's numbers, a scrambled internet address only for new lists/profiles/groups/exchanges, friend-code attempts counted. Not limited yet: picture uploads to Storage (500 KB a file already), and the `fetch-page` helper.)* Limits against abuse inside the functions: how many lists, wishes, messages, reactions, invites per
  profile/device per hour; friendly errors in the app and web pages.
- [x] S4.3 *(2.186, `supabase-cleanup.sql` + `Opened.kt`: the owner's 12 months with a warning a month before; lists come back by themselves. Left: pictures of removed lists stay in Storage, and a size report per table is in the weekly backup since 11 November.)* Clean-up jobs (pg_cron): lists nobody opened for a long time (owner decides how long), expired news, old
  view counters; and a size report per table.
- [x] S4.4 *(11 November 2026: checked with `pg_stat_statements` in the SQL editor (the owner's screenshot): the
  busiest query had used about 19 seconds in total since the statistics began; the top ones are Supabase's own
  dashboard queries (function list, time zones), then the app's calls at a few seconds in total each. No index needed;
  adding them would only slow down saving. Check again with the query below when the app has many more users, or when
  something feels slow:
  `select calls, round(mean_exec_time::numeric, 1) as avg_ms, round(total_exec_time::numeric) as total_ms,
  left(regexp_replace(query, '\s+', ' ', 'g'), 90) as query from extensions.pg_stat_statements order by total_exec_time
  desc limit 15;`)*
  Indexes checked against the slowest queries (Supabase's query performance page).
- [x] S4.5 *(Owner, 11 November 2026: a recovery key instead of linking a Google account (DECISIONS "Stage 4"). 2.187: the key and its locked copy (`RecoveryKey.kt`, `supabase-vault.sql`). 2.188: restoring (`Restore.kt`), a new key afterwards and the old phone signed out. P20 (Android's own backup, `allowBackup`) is still open: decide with the owner.)* Identity as decided in D5 (if anonymous accounts: a step-by-step move where every existing owner token and
  device keeps working).
Owner tests: photos still show everywhere (app and web); nothing else changes.

## Stage 5 — Keeping it real (ongoing)
- [ ] Crash and error review at the start of each session (D2), fixes first.
- [ ] New features only with: who it's for, the risks asked, checks added, and the data layer used (no new global
  state: a screen that is changed gets its state out of global `object`s), and the checklist "Think of this with every
  new feature" gone through.
- [ ] The product reader: a new shop problem gets a saved page in `tests/parse/pages/` and a fix in both readers.
- [ ] Update this file's "Honest state" when a stage is done.

## Second review, 10 November 2026 (2.178) — read this before starting Stage 4
The owner asked a newer model to re-evaluate the stages, find what was missed, and write down everything that should be
thought of **before** it bites ("to avoid hindsight fixing obvious things or safety things"). This section is that
review. **Order from here: Stage R (below) first, then Stage 4, then Stage 6 when the owner wants Google Play.**

### What Stages 0–3 really achieved (claim against the code)
| Stage | Signed off as | What the code shows | What is still owed |
|---|---|---|---|
| 0 Safety nets | Done, S0.3 open | Build runs the app's checks; weekly backup; crash reports. | The product reader was only ever tested on hand-written pages (**R4**). The backup has never been restored once, leaves out the uploaded pictures, and its files can be downloaded by anyone with a GitHub account (locked with `BACKUP_PASSWORD` only) (**R3**). The web and database checks ran only by hand until 2.178 (**R5**). |
| 1 Screens | Done | Every screen is a route; `MainActivity.kt` is small. **But "screens that manage themselves" is half done:** 0 ViewModels, 84 global `object`s, 29 files keep screen state in globals, and the `*Nav` flag objects remain. | State in globals is lost when Android closes the app in the background (one case was found and fixed in 2.162; others are likely) (**R9**). No big rewrite (owner's "slimmer route" stands), but the rule in Stage 5 is now firm. |
| 2 Database | Done | Room is the source of lists, wishes, bin, history, notifications. | The fallback was dangerous: when the database couldn't be read, the app quietly used the **old** copy frozen in 2.171 and could send it online over newer lists. 2.178 stops the sending; the proper fix is a clear screen (**R1**). The database had no way to change its layout: 2.178 adds the first migration and a check that runs every migration on a database built from the saved layouts (`DbMigrationTest`). Settings, profile, keys, collab caches, groups and exchanges are still in one settings file that 32 files write to. |
| 3 Syncing | Done | `MineSync`, `Live`, `Pulse` exist and are checked on a local database. | Never tried with two phones; the S3.4 table is still an estimate (**R7**). The web pages still poll every 20 s. Supabase's free plan allows a limited number of live connections at once (check the current number on Supabase's pricing page before growing; 200 when this was written): above that the app falls back to polling, which is the P4 problem again. |

### New problems found (P11 and on)
| # | Problem | What a user notices | Fix |
|---|---------|--------------------|-----|
| P11 | **A price was believed after one reading**, in any currency, even from a "not found" page; a hand-corrected price was overwritten; a copied wish was read and told twice; shops were always asked for American English. | Wrong prices and false "price drop" notifications. | **Fixed in 2.178** (`PriceWatch.kt`, below). Left: real shop pages (R4). |
| P12 | **The signing key may exist only inside GitHub's secrets**, which can never be read back. | If it is lost, nobody can ever update the app again. | **R0, urgent.** |
| P13 | **Every push to `main` is a release to everyone, and nobody is told there is an update.** No test channel, no "update available", no "this version is too old". | People stay on old versions for ever; a bad push reaches everyone. | **R6.** |
| P14 | **No privacy text and no way to delete your data from the server** (names, photos, birthdays, sizes, friends, chats, reservations). D2 promised "mentioned in the privacy text": there is none. | A legal duty in the EU (GDPR) as soon as strangers use the app; Google Play refuses the app without both. | **S4.6, S4.7** (owner: yes). |
| P15 | **No report or block** for chat and shared content (only Unfriend), and no limits (S4.2). | One abuser can spam; Google Play requires report + block for apps where people share content. | **S4.2, S4.8.** |
| P16 | **One person, three accounts** (GitHub, Supabase, Google/Firebase) hold everything. | A lost or hacked account ends the app. | **R8** (two-step sign-in, recovery codes). |
| P17 | **The Firebase key** *(corrected 11 November 2026: it was not unrestricted; Firebase had limited it to 25 Firebase services when it was made, Gemini not among them, but including Firebase AI Logic and two that send text messages, which cost money when abused)*. It is public by design and can't call Gemini today (checked: "Gemini API … disabled" for this project), but an unrestricted key works for any Google service that is ever switched on in that project. | Nothing now; a surprise bill or abuse later. | **R2.** |
| P18 | **The app's Android package name (`com.wishlist.app`), its web address (`androidenthusiast.github.io/Wishlist`) and its Supabase address are built into every installed app and every link ever shared.** All three are hard or impossible to change later: Google Play fixes a package name for ever (and a generic one may already be taken); a new web address breaks every old link; a new Supabase project breaks every old app. | Nothing now; a painful move later. | Decide all three **before** Play and before many links are out (**Stage 6**, first step). R6 gives a way to tell old apps "the server moved". |
| P19 | **Old libraries**: Android target 34, Compose from mid-2024, Kotlin 1.9. | Nothing now. Google Play only accepts apps that target a recent Android version (it moves up every August). | **Stage 6** (an upgrade step of its own, with the screenshot checks). |
| P20 | **Android's own cloud backup is on without any rules** (`allowBackup`): Android may copy the app's data (list keys, the chat's private key, the device id) to the owner's Google account and put it on a new phone. Never tested. | Either a lucky "new phone" rescue, or two phones that think they are the same device, with chat keys in a Google backup. | Decide together with identity (**S4.5**); don't change it before. |
| P21 | **The chat is end-to-end encrypted, but nobody can check the other person's key.** The server hands out the public keys, so whoever runs the server could swap one. Also one fixed key per person (no forward secrecy). | Nothing. But "nobody can read your messages, not even us" is only true as long as the server is honest. | Say it honestly in the privacy page (S4.6); a "verify this friend" code later if wanted. |
| P22 | **GitHub shows the whole project as "SIL Open Font License"** because the font's licence file sits at the top. The code itself has no licence (so legally nobody may reuse it), which may or may not be what the owner wants for a public repository. | Confusing for anyone who looks. | **R8** (move the file; the owner chooses a licence or "all rights reserved"). |
| P23 | **Nothing is ever tried on a real or pretend phone automatically**; the owner is the only tester of every screen, and two-phone flows are untested. | Layout and flow bugs reach the owner first. | **R7** now; Stage 6 considers Firebase Test Lab (a robot that taps through the app on real phones and returns pictures; free daily allowance). |
| P24 | **Prices are decimal numbers, wish ids are "the time it was made"**. Fine today. Two people adding a wish to a collab list in the same millisecond would get the same id; adding up decimals drifts by cents. | Nothing today. | Before any money feature (**chip in**, important to the owner): amounts in whole cents, and never handle payments ourselves. New kinds of things get random ids. |

### What 2.178 changed (the price check, P11)
`PriceWatch.kt` holds the rules, `PriceWatchTest`/`PriceCheckTest` check them (owner's answers of 10 November 2026):
1. **Twice.** A different price counts only when a second reading agrees (later in the same check, else the next check).
2. **The first look is quiet.** A price the person typed stays; a price the app read itself is corrected without a
   notification. After that only a change at the shop counts.
3. **Same currency only**, and **an error page ("not found") never gives a price** (app and `web/parse.js`).
4. **Shops are asked in the phone's language**, not always American English.
5. **A copied wish is read and told once.**
6. **A wish's price page says when it was last checked**, or since when the shop can't be read.
7. `tests/shared/same-answers.json` got **`pages`**: whole product pages that the app's reader and the web reader must
   answer identically. This is the home for real shop pages (R4, the old S0.3).
Known limit, said to the owner: a typed price follows the shop once the shop's own price changes (the wish then takes
the shop's price). Collab wishlists use rules 1 and 3 only (their prices are shared between phones).

### Owner's answers (10 November 2026)
- **Google Play: yes in the end, the download is fine for now.** So: Stage 6 exists, nothing store-specific is
  built yet, but nothing new may make Play harder.
- **Signing key:** the owner has a 16-character password saved and isn't sure about the key file → **R0**.
- **Privacy page and "Delete my profile and data": yes** → S4.6, S4.7.
- **GitHub runs the web checks before publishing the pages: yes, "as long as Claude can fix the issue"** → done in
  2.178 (`web-checks` job; a failing suite is tried once more).

## Stage R — Repairs before growing (1–2 sessions, before Stage 4)
**State on 11 November 2026: done except three things that wait for the owner** (two-step sign-in, R8, "remind me
later"; the two-phone test, R7, when the partner's phone is free; real shop pages, R4, arrive through "Price is
wrong?"). Stage 4 may start; those three stay on the list.
Small steps; each one protects against something that can't be undone later.
- [x] **R0 The signing key has a second home (urgent).** *(Done 11 November 2026: the owner saved the second copy of the key and its password.)* *(11 November 2026: the owner found `wishlist-release.jks` and two text files, the key as text and its password, on the Chromebook/Drive. Left: a second place for the key file, the password moved into a password manager (not a text file next to the key), and the key's alias written down with it.)* The key is a *file* (it ends in `.jks`, `.keystore` or
  `.p12`), or the very long text that was pasted into the secret `KEYSTORE_BASE64` (thousands of letters and digits,
  often starting with `MII` or `/u3+7Q`); a 16-character password alone is not the key (and may be `BACKUP_PASSWORD`,
  which must be at least 16 characters). If the owner can't find the file or the long text: a one-off GitHub job
  that locks the key with `BACKUP_PASSWORD` and offers it as a download for one day, the owner saves it, the job is
  deleted. Ask before building it. Never print or commit the key.
- [x] **R1 When the database can't be read, say so.** *(2.180, `Rescue.kt`: the stop screen with Get my lists back / Try again / Import a backup file; `wl_mine_get` reads your own list back with its owner token. Known limits, said to the owner: a list made after the old copy can't be found again this way (only a backup file has it); private wishes, the bin and price history come from the old copy. Still open: removing the old storage code once the partner's phone has moved.)* Replace the silent fallback to the old copy with a clear
  screen ("Wishlist couldn't open your lists. Nothing is sent or deleted.") and two ways out: try again, and "Get my
  lists back from the online copy" (the server has every list that was shared). Interview the owner first (wording,
  what happens to private wishes, which never went online). Then remove the old storage code (S2.5's last part) once
  the partner's phone has moved. *(2.178: an old copy is never sent online any more: `ListStore.stale`.)*
- [x] **R2 Restrict the Firebase key** *(11 November 2026, done: limited to FCM Registration API + Firebase Installations API, application restrictions "None" on purpose; a chat push still arrived afterwards. Google showed earlier use of `identitytoolkit` (sign-up), which neither the app nor the web pages ever call: strangers testing the public key; now refused. Metrics Explorer: a few requests on one day around 8 November, all refused (400): scanners testing the key, nothing created. The Firebase project is on the free Spark plan (nothing can be billed) and Firebase Authentication was never set up. Still to look at: Crashlytics the next day. Before: limiting it to FCM Registration API + Firebase Installations API. The 25 it had before, to tick again if notifications or crash reports stop: Cloud Datastore, Cloud Firestore, Cloud Logging, Cloud SQL Admin, Cloud Storage for Firebase, FCM Registration, Firebase AI Logic, Firebase App Check, Firebase App Distribution, Firebase App Hosting, Firebase App Testers, Firebase Hosting, Firebase In-App Messaging, Firebase Installations, Firebase Management, Firebase ML, Firebase Phone Number Verification, Firebase Realtime Database Management, Firebase Remote Config, Firebase Remote Config Realtime, Firebase Rules, Firebase SQL Connect, Identity Toolkit, ML Kit, Token Service. Check after: a chat push arrives; Crashlytics still shows the app the next day.)* (owner, in Google Cloud Console; steps were given on 10 November 2026): only
  the Firebase services the app uses. Then check that a chat message still buzzes the other phone and that
  Crashlytics still shows the app.
- [x] **R3 Backups you can trust:** *(11 November 2026: the job moved to the private repository `Wishlist-backups`, copies the pictures too, restores every copy into a throw-away database and compares the rows; first run green: 29 tables, 90 pictures, 360 KB.)* restore one backup into a throw-away database once and write down that it
  worked (`docs/BACKUPS.md`); copy the uploaded pictures too; move the job to a **private** repository so the files
  aren't downloadable by every GitHub user (ask the owner: it means one more repository and moving two secrets).
- [ ] **R4 Real shop pages (the old S0.3).** *(2.182: the "Price is wrong?" button on a wish's price page saves the shop's page as the phone reads it and opens the share sheet (`PriceReport.kt`; owner chose the button over saving pages on the Chromebook). Open until real pages have arrived and are in `pages`.)* For each starter shop one real product page in
  `tests/shared/same-answers.json` → `pages`. Sessions can't reach most shops, so the owner saves pages on the
  Chromebook (open the product, Ctrl+S, "Web page, HTML only") and uploads them, or shares a link when a price reads
  wrong. Ask whether a "This price is wrong" button in the app would be welcome (it could send the link and what was
  read, nothing personal).
- [x] **R5 GitHub runs the database checks too.** *(2.179: `tests/sql/run.sh`, in `run-all.sh` and the `web-checks` job with a throw-away PostgreSQL; 13 check files and "every script runs twice".)* The `tests/sql/*.sql` checks and the three page suites that need a
  PostgreSQL run only by hand today, while the scripts they check are pasted into the real database by the owner.
- [x] **R6 The server can talk to old apps.** *(2.179, `AppInfo.kt`: built as one small file next to the web pages, `app.json`, instead of a database function: one moving part less, no script for the owner, and still reachable if the database moves. Owner chose the slim bar on Home: shown once a version is a day old, Later hides it a week; below `min` in `web/app.json` a full "Please update" screen. To make a version required: raise `min` in `web/app.json` to that versionCode, never above the newest.)* One tiny function (for example `wl_app_info`): the newest version, the
  oldest version that still works, and an optional notice. The app shows "Update available" (with the download) and,
  below the minimum, a kind "Please update" screen. One moving part that covers updates, a server move and
  maintenance. Interview first (how often to nag, wording).
- [ ] **R7 The two-phone test of Stage 3** (owner, 11 November 2026: "not today"; the list below is done whenever the
  partner's phone is free, on the newest app, after the newest scripts). Phone A is yours, phone B your partner's.
  1. *Collab wishlist, live:* both open the same collab wishlist. A adds a wish: it shows on B within about 5 seconds
     without pulling down. B changes its price: A sees it. A reserves it: B sees "Reserved". B moves it to the bin:
     it leaves A's screen; A's bin shows it; A puts it back.
  2. *Moving (2.181):* A moves one of A's own wishes to that collab wishlist: B sees it arrive. B takes a wish off it
     with Move: A sees it go and finds it in the list's bin. A wish A reserved can't be moved by B (Move greyed out).
  3. *A friend's list:* A and B are Friends. B opens A's list. A adds a wish and changes another's name: B's open
     list follows within seconds. B reserves something: A's own list shows nothing of it (the surprise).
  4. *Chat:* B has the app closed. A sends a message: B's phone buzzes within seconds. B opens the chat: A's ticks
     turn coloured only then. Both keep the chat open and write back and forth: messages arrive at once.
  5. *Offline:* A switches on airplane mode, edits a wish on A's own list, switches airplane mode off and reopens the
     app: within a minute B (or the friends' page in a browser) shows the change.
  6. *Android closing the app (2.183):* A opens a chat, then a game or the camera for a few minutes, then comes back:
     the same chat is open. Same with a collab wishlist's settings.
  7. *Battery and calls:* after a normal day, App settings > About > Details on A: write the numbers of `wl_pulse`,
     `live connect` and the total into S3.4's table instead of the estimate. Settings > Battery > Wishlist: the
     percentage of the day.
  Anything that fails: a screen recording, then it is fixed first.
- [ ] **R8 Account and repository safety (owner, with steps):** *(11 November 2026: Dependabot alerts switched on by the owner (Claude can't read the alerts from a session: GitHub e-mails the owner; when one comes, the owner pastes it into the session). Only two-step sign-in is left, "remind me later". The licence is decided, all rights reserved (`LICENSE`, the font licence moved to `docs/licences-OFL.txt`; jsoup's MIT notice added to the app's Licences page, it was missing). Dependabot alerts: steps given. Two-step sign-in: the owner asked to be reminded later.)* two-step sign-in and saved recovery codes on GitHub,
  Supabase and the Google account; Dependabot **alerts** on (Settings > Advanced Security; alerts only, no pull
  requests); move `FONT-LICENSE-OFL.txt` out of the top folder so GitHub stops labelling the whole project with the
  font's licence, and choose what the code's licence is.
- [x] **R9 Screens survive Android closing the app.** *(2.183: found and fixed a collab wishlist's settings showing another list's settings when Android rebuilt only that screen; an open chat, a friend's page, an open gift exchange and a page of Home now come back (`KeepPlaces.kt`) instead of the tab's first page; checks in `EntryTest` empty the app's memory while the screen goes away. Not kept, on purpose: a half-filled form or dialog. Camera files are only prepared when the camera opens.)* No big rewrite: a check per main screen that closes and
  restores the app (Robolectric can), fixes where it fails, and from now on no new screen state in a global `object`.

## Additions to Stage 4 (same stage, more steps)
- [x] S4.6 *(2.189, `web/privacy.html`; the owner still approves the text)* **Privacy page** on the website (plain words: what is stored where, crash reports, how long, who can see
  it, how to delete, the honest line about chat keys of P21), linked from App settings, the welcome and every web
  page. The owner approves the text.
- [x] S4.7 *(2.189, `DeleteMe.kt`, `supabase-privacy.sql`)* **"Delete my profile and data"** in the app (profile, friendships, photos, chats, group and exchange
  places, reservations, own lists online), with a clear warning and no way to do it to someone else. For browser-only
  people: the page says how to ask. Needed for GDPR and for Google Play (which also wants a web address where
  deletion can be asked for without the app).
- [x] S4.8 *(2.189, `Safety.kt`; reports are read in Table editor, no alert yet)* **Report and block** (needed before Play; think it through with the owner: what a report contains when
  chats are end-to-end encrypted, who reads reports, what "blocked" does on shared lists and groups).
- [ ] S4.2 also covers: the `fetch-page` helper per key (it loads any web page for whoever holds a list key), friend
  codes (6 characters: guessing must be slowed down), and, with S4.5, how many anonymous accounts one device may make.
- [ ] S4.5 also decides P20 (Android's cloud backup) and must be designed knowing that linking a Google account
  later needs the signing key's fingerprint registered with Google (another reason for R0).
- [x] S4.9 *(11 November 2026, below. Check the numbers on supabase.com/pricing before relying on them: they change.)*
  | Free plan limit (as Claude knew it) | Today (11 November 2026) | What reaches it first |
  |---|---|---|
  | Database 500 MB | 14 MB (Supabase's own parts most of it; wishes 152 kB, profiles 280 kB) | Chat messages and wishes, at many thousands of users; the clean-up keeps it down |
  | File storage 1 GB | 336 kB, 90 wish pictures (+ profile photos from 2.185) | Wish pictures you upload yourself (about 50 kB each): ~20,000 |
  | Traffic out 5 GB a month | not measured | Pictures and lists loaded by phones and the web pages; the first limit at a few hundred active people a day |
  | Live connections 200 at once | not measured | Everyone with the app open at the same moment (Stage 3); above it the app falls back to polling |
  | Edge function calls 500,000 a month | small (pushes, shop pages in the browser) | Pushes for chat messages |
  | A week without any use pauses the project | prevented by the keep-awake job | |
  The weekly backup reports the database and storage sizes; traffic out and live connections are on Supabase's
  **Reports** page. S4.9 **The free plan's limits, written down with today's use** (database size, storage, traffic out, live
  connections, function calls; look them up on Supabase's pricing page, they change) and which limit is reached
  first at 100, 1,000 and 10,000 users.

## Stage 6 — Google Play (when the owner says so)
Decide first, build second; several of these can't be changed afterwards.
- [ ] S6.1 **Three permanent names** (P18): the app's name (the owner already planned a naming session), the
  package name (unique on Play, permanent), and the web address for links (a domain of your own, about 10–15 euros a
  year, can still be served by GitHub Pages; old `github.io` links must keep working).
- [ ] S6.2 **Keep today's users**: Play can sign the app with the *existing* key ("Play App Signing" with your own
  key), so people who installed the download can update from Play without reinstalling. Needs R0.
- [ ] S6.3 Android target and libraries brought up to date (P19), in its own version, with the screenshot checks.
- [ ] S6.4 The store's forms: privacy page (S4.6), data safety form, data deletion address (S4.7), content
  rating, report/block (S4.8), what Crashlytics and Firebase collect.
- [ ] S6.5 A test round: a new personal developer account must run a closed test with 12 testers for 14 days
  before it may publish (check the current rule). Plan who they are.
- [ ] S6.6 A test channel from then on (internal test → everyone), instead of "every push goes to everyone";
  Play's in-app update prompt replaces R6's notice for Play installs.
- [ ] S6.7 Consider a robot test on real phones in the build (Firebase Test Lab) and shrinking the app (R8/minify).

## Think of this with every new feature (the checklist that prevents hindsight)
Ask these while designing, and ask the owner where the answer is a choice:
1. **Old versions:** what does an app from three months ago do with this? (Scripts only add; R6 for the rest.)
2. **Offline, slow, failed:** what shows, what is kept, what is retried; never a spinner without an end.
3. **A new or second phone, and the browser:** does it still belong to the person? (Identity, S4.5.)
4. **The other person:** what if they left, deleted it, blocked you, or are on an old version?
5. **Abuse:** what can a stranger with the public key and unlimited tries do? What is the limit, and who notices?
6. **Cost:** how many server calls, how many bytes stored and sent, per user per day; which free-plan limit moves.
7. **Personal data:** is something new stored about a person? Then the privacy page, "Delete my data", Export and
   the backup all need it, in the same update.
8. **Surprise:** can the list's owner learn, in any way (counts, timing, notifications, logs), what was reserved?
9. **Three places:** app, web pages, database. And the two readers (`Fetch.kt`, `web/parse.js`) stay the same.
10. **Stored layouts change only by adding:** a database change is a migration with a check (`DbMigrationTest`); a
    wish field goes through every place in CLAUDE.md rule 1.
11. **Android closing the app, rotating, a big font, TalkBack, dark mode, all five styles.**
12. **Notifications:** is it worth a buzz, can it arrive late, can it arrive twice, does it bundle (`NotifyPace`)?
13. **Money and time:** amounts in cents when they're added up or split; dates in the person's own time zone;
    never trust a phone's clock for "who was first".
14. **Google Play's rules** (Stage 6): would this need a permission, a form, or a policy answer?
15. **A check that fails without the fix**, at the layer where the bug lives; and what could NOT be checked, said out loud.
16. **How will we know it works for people?** (A crash report, a counter in App settings > About, or a question to
    ask the owner after a week.)

## Rough size
*(10 November 2026: Stage R adds 1–2 sessions, the Stage 4 additions 1–2, Stage 6 about 2–3.)*
Stages 0–4 are about 12–17 sessions. The app keeps working throughout; the owner can stop after any stage and still
have a better app than before. Stages 1 and 2 give the biggest gain for the two of you; Stages 3 and 4 are what lets the
app grow.
