# Roadmap archive: the finished parts

Moved here word for word from `docs/ROADMAP.md` on 12 November 2026 (notes tidy-up, owner): the state of 8 November
and Stages 0–3, all done. Only searched or read when needed; the live roadmap is `docs/ROADMAP.md`.

## Honest state on 8 November 2026 (version 2.152, kept for history)

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
