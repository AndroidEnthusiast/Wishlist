# Roadmap: from "it works" to a real app

Written 8 November 2026 at the owner's request: *"I want the foundations to work for me and my partner but at the same
time allow to scale up in the future."* This is the plan every session follows until it is done. Work through it **in
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
- [ ] S0.3 Product reader fixtures: saved copies of real product pages (only the HTML, no pictures) for the 20 starter
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
  moves into a ViewModel, not before: an unused library is only weight.)*
  One route per screen: Home, a list, list settings, Messages, a chat, My shops, Friends, a friend's list, Profile,
  App settings, Bin, Notifications, Gifts I reserved, Received gifts, groups, gift exchanges, price pages.
- [ ] S1.2 Move screens over **one at a time**, starting with the simplest (Bin, App settings, Notifications), then
  the tabs, then the list screen last. Each move removes its on/off switches from `MainActivity`.
  *(2.154: Profile, App settings, Bin, Notifications are routes; 4 of the 75 switches gone. 2.156: Received gifts,
  Gifts I reserved, Price changes, a list's settings; 8 gone. 2.157: a list opened from its link or code; 9 gone. Next: the
  tabs (Messages, My shops, Friends), groups and gift exchanges, the list screen last.)*
- [ ] S1.3 Notifications and links (`OPEN_*` extras, App Links) open routes instead of setting switches.
- [ ] S1.4 Dialogs that belong to one screen move into that screen; app-wide ones (delete/undo, move, share) stay in
  one place.
- [x] S1.5 Delete the switched-off side menu code (owner decided 7 November 2026, if still wanted). *(2.154, owner
  confirmed 8 November 2026.)*
Owner tests per step: the moved screens open, Back goes to the right place, notifications open the right thing.
Done when `MainActivity.kt` only sets up the app (< 300 lines) and every screen is a route.

## Stage 2 — A real database on the phone (3–4 sessions)  (P2)
Goal: fast, safe local data that can't be half-saved.
- [ ] S2.1 Add Room (SQLite). Tables: lists, wishes (one row per wish per list, so copies are just rows), bin,
  price history, categories, friends' lists, notifications. Keep JSON only where it is truly free-form.
- [ ] S2.2 A data layer (repositories) between screens and storage; ViewModels read from it as streams, so every
  screen updates by itself when data changes (replaces `mineVersion` and manual refreshes).
- [ ] S2.3 **Moving existing data:** on first start of the new version, copy everything from the settings store into
  Room inside one transaction, check the counts match, keep the old data untouched for at least two versions, and
  write a flag only when it all worked. If anything fails, the app keeps using the old data and reports it (D2).
  Tested first with an Export file of the owner's real data.
- [ ] S2.4 Export/Import read and write the new database (old Export files still import).
- [ ] S2.5 Only after the owner confirms on both phones: remove the old storage code (`MyLists` blobs, `SafeStore`).
Owner tests: everything is still there after updating (lists, bin, history, categories, covers), on both phones.

## Stage 3 — Syncing like a real app (2–3 sessions)  (P3, P4)
Goal: small, fast saves; changes arrive when they happen; less battery.
- [ ] S3.1 Own lists save one wish at a time, only changed fields (new `wl_put_mine` with the owner token, like
  `wl_sh_put_item`), with the same undo-on-failure rule as collab wishlists. The full-list sync stays for older apps
  only.
- [ ] S3.2 Supabase Realtime for open lists, collab wishlists and chat instead of 20-second polling (falls back to
  polling when Realtime isn't reachable). Needs RLS-safe channels: broadcast "something changed" only, never data;
  the app then fetches through the existing checked functions.
- [ ] S3.3 One background job that asks the server "what's new for me?" in one call (extend `wl_news_get`) instead of
  ten separate checks; pushes stay for urgent things.
- [ ] S3.4 Measure: requests per user per day before and after (log counts in debug builds), written down here.
Owner tests: two phones editing the same list, chat, collab list updates arrive within seconds, battery over a day.

## Stage 4 — The server, ready to grow (2–3 sessions)  (P5, P7, P8)
- [ ] S4.1 Photos (profile, group, exchange) move to Supabase Storage with small file sizes; the database keeps only
  the address. Old text photos are moved by a script, then cleared.
- [ ] S4.2 Limits against abuse inside the functions: how many lists, wishes, messages, reactions, invites per
  profile/device per hour; friendly errors in the app and web pages.
- [ ] S4.3 Clean-up jobs (pg_cron): lists nobody opened for a long time (owner decides how long), expired news, old
  view counters; and a size report per table.
- [ ] S4.4 Indexes checked against the slowest queries (Supabase's query performance page).
- [ ] S4.5 Identity as decided in D5 (if anonymous accounts: a step-by-step move where every existing owner token and
  device keeps working).
Owner tests: photos still show everywhere (app and web); nothing else changes.

## Stage 5 — Keeping it real (ongoing)
- [ ] Crash and error review at the start of each session (D2), fixes first.
- [ ] New features only with: who it's for, the risks asked, checks added, and the data layer used (no new global state).
- [ ] The product reader: a new shop problem gets a saved page in `tests/parse/pages/` and a fix in both readers.
- [ ] Update this file's "Honest state" when a stage is done.

## Rough size
Stages 0–4 are about 12–17 sessions. The app keeps working throughout; the owner can stop after any stage and still
have a better app than before. Stages 1 and 2 give the biggest gain for the two of you; Stages 3 and 4 are what lets the
app grow.
