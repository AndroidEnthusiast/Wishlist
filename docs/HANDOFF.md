# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **2.265 (20 November 2026), read first: Screenshot to wish** (`docs/decisions/screenshots.md`). Ask how it feels:
  share one screenshot, then several (from the gallery, Instagram, a shop app), the mini cards under the photo, Skip,
  "Stop adding?", the "added" notes, and that every wish now needs a name. Open: a link shared with several pictures
  (owner skipped it; ask if it comes up). Then the price-reading findings below.
- **The data rebuild is done (owner, 19 November 2026; 2.256–2.263; `docs/decisions/roadmap.md`, last section).** Ask
  how 2.256–2.263 and the screen rebuild behave on the phone (TODO "Try …"). **Next (owner's choice): the two price
  reading findings as their own version** (TODO "Found while checking Import"): kr/zł on .se/.pl shops get kronor/złoty,
  Amazon …/Name/dp/…/ref=… trimmed to /dp/…; in `FetchText.kt` and `web/parse.js` together, a case each in
  `tests/shared/same-answers.json`; then `FetchSameTest`'s record changes in exactly those lines (re-record, read the
  difference). Then what a team would still do (TODO): real-phone checks, fewer web copies.
- **The rebuild (owner, 18 November 2026; `docs/decisions/roadmap.md`, last section).** Point 2 done: version stamps in
  every script + `server-check.yml` (owner ran all 17; issue closed). Point 1: **Home done in 2.233** (7 small files,
  `HomeModel`, pixel-identical). Ask whether Home behaves the same after a day or two; then the next screen (ask which:
  Friends / Profile (done in 2.235), the open list (done in 2.236), gift exchange (done in 2.237), wishlist groups (done in 2.238), Messages (done in 2.239), the photo editor (done in 2.240; Data.kt waits for its own plan, owner), My shops (done in 2.241), the building blocks (`Styled.kt`, done in 2.242), `Themed.kt` (done in 2.243), the frame (`AppScreen.kt`, done in 2.244), the wish cards (done in 2.245), the wish form (done in 2.246), the styles (`Design.kt`, done in 2.247), a friend's list (done in 2.248), App settings (done in 2.249), covers (done in 2.250), the price page (done in 2.251), gift invitations (done in 2.252), shared list dialogs (done in 2.253), reactions (done in 2.254), Overview (done in 2.255: every screen is done; next the owner tests, TODO "Try the rebuild", then the data part, TODO "Later, remind the owner"), or `ListScreen.kt`), same method: before/after
  screenshots compared pixel by pixel, a state holder, bug fixes only. **2.234: guards** (`ArchitectureTest`: files
  under 400 lines, 29 frozen in ALLOWED; REBUILT screens only draw; `checkup.yml` quarterly). Each rebuilt screen
  joins REBUILT and leaves ALLOWED.
- **2.231–2.232 (18 November 2026).** Home's numbers on a card; card buttons in one order (Reserve left, ⋮ last). **Next, owner's choice:** fix the review points, 1 and 2
  first (split the big files and move screen state out of the shared objects; a version record of the server scripts).
  Interview on the plan before starting (big build, one screen at a time).
- **2.230 (18 November 2026, late night), read first.** Profile as rows, Home blocks + My wishlists | Shared swipe,
  AliExpress shares open Add a wish (`docs/decisions/settings-profile.md`, last section). Ask how they feel. Open: the
  AliExpress photo shows its logo; ask the owner for the page file ("Price is wrong?" on that wish's price page).
- **2.229 (18 November 2026, night), read first.** Compact Reserve pills, Add to my wishlist, one reaction, Import last
  in the + menu (`docs/decisions/people.md`, last section). Ask how they feel on the phone.
- **2.228 (18 November 2026, evening), read first.** Themes now a neutral matched grey with colour on the elements,
  Black mode, Teal, a real Gen Z block; App settings redone as six rows with pages (decisions: `look.md` and
  `screens.md`, last sections). Ask how both feel on the phone; the 2.227 entry below is superseded by this one.
- **2.227 (18 November 2026, later), read first.** Themes made deep (one hue everywhere, light = pastel) and theme
  blocks minimal (`docs/decisions/look.md`, last section). Ask how they feel on the phone in each theme, light and
  dark (colours on a real screen can't be judged here). Then Import step 2 below.
- **2.226 (18 November 2026), read first.** Import step 1 is built (`docs/decisions/import.md`). Ask how it feels on
  the phone (paste a GoWish/Giftful/Amazon link, share one from their app, text from notes) and whether the owner
  made the six PUBLIC test lists yet (TODO, "Import, step 2"). With the links: fill `tests/import/lists.json`, see the
  real pages through the daily check, write one reader per site in `web/import/reader.js` (raise VERSION). This
  session couldn't open gowish.com (blocked here): GitHub's computers can.
- **2.221–2.225 (17 November 2026, evening), read first.** The approved plan (`docs/designs/plan-2026-11-17.md`,
  steps 1–5) is built: centred chip text, My own / With others, the full-width form sheet (+ menu forms, Wishlist
  settings, list menu), the settings sheet, five themes + Gen Z with a Theme screen. Ask how each feels on the phone
  (fonts, tints and sheets can't be seen here). Step 6 left for now. Known limits: the Theme row opens App settings with
  the Theme screen on top (Back then shows App settings); New group / New gift exchange: only their first step is a
  sheet; a form's typed text is lost when switching My own / With others.
- **2.219 (17 November 2026), read first.** Ask whether every dialog's buttons now read well (Share, Move, Profile),
  and how "About me" under the name looks. Then build the agreed collab merge (`docs/decisions/sharing.md`, last
  section), built in 2.220: ask how "Who is it for?" feels, and whether the name should also reach the other members'
  phones (needs a server script).
- **2.214 (16 November 2026), read first.** The owner's review (`docs/decisions/screens.md`, last section): ask how
  "Love it", the tip, dragging (can't be felt here), Move or copy…, the Friends icon, Gen Z settings cards and the
  chat feel. Then the chosen next features: Sold out / back in stock, then Screenshot to wish
  (`docs/decisions/ideas.md`, "Solo use"); interview on risks first. Ask how the wish form (2.216), friend page (2.217) and Home (2.218) feel. Next big one: a wish's own page (TODO).
- **2.213 (15 November 2026), read first.** "♥ Love this" replaced "Top wish" (app and web). Ask how it reads.
- **2.212 (15 November 2026).** Sheets, price page and wish form rebuilt (`docs/styles/identity.md`, last
  section). Ask how they feel; the remaining drop-down menus elsewhere (e.g. a category's menu) could become sheets too.
- **2.211 (15 November 2026).** The owner's first look at 2.210 is fixed (`docs/styles/identity.md`, last
  section). Ask how 2.211 feels; then the open points below.
- **2.210 (15 November 2026).** The app's own character is built (`docs/styles/identity.md`). Ask how it
  feels on the phone, above all the fonts (can't be seen here), the press feel, the large titles and the dialogs. Open:
  the wordmark and app icon (owner: decide later), the warm voice for the remaining texts, the web pages' look.
  "Put back the look of 2.209" = commit `f5a6fc1`.
- **2.209 (15 November 2026), read first.** Ask how the owner's review fixes feel on the phone. Then the brainstorm the
  owner asked for: plan "chip in" and suggestions to improve "gift ideas for others" and "about me for givers" (they
  partly exist, `docs/TODO.md`), interviewing with pop-up questions. Before every push: screenshots of both styles
  with worst-case data (long names, big amounts, no photo), as promised to the owner.
- **2.208 (15 November 2026):** ask the owner whether the profile sheet now reads well in dark mode, then go on below.
- **Quiet polished (14 November 2026, 2.207), read first.** Built to the approved plan (`docs/styles/quiet.md`).
  Ask the owner how it feels on the phone (step 6 of `docs/DESIGN-STYLES.md`) and fix what they send. Then Gen Z
  chaos, the same way, with the partner's input (it is pixel for pixel unchanged so far).
- **Before that (14 November 2026, after 2.206).** The owner was disappointed by the design styles of
  2.199–2.205: 2.206 keeps only **Quiet** and **Gen Z chaos** (others → Quiet; code in git history, `057fe42`). The
  next design style is made **one at a time, to a professional standard**: follow `docs/DESIGN-STYLES.md` step by step,
  with its owner checkpoints (brief, style brief, specification and mock-ups before any code). Don't start without the
  owner's choice of style. Gen Z chaos is saved in `themes/gen-z-chaos/`: ask before changing it.
- **START HERE (12 November 2026, late night, after 2.198).** The review's part 2 is built (2.197, 2.198). Ask how
  2.195–2.198 feel on the phone, then continue the "logical experience" review (owner: "help me discover those")
  or what the owner wants next: show all of `docs/TODO.md` when asked. Known limit: Undo of "Moved to Received gifts"
  brings the wish back to its original list only (its copies on other lists don't come back).
- **Everything open** (the complete to-do list, owner): `docs/TODO.md`. Owner wants a new feature built first.

## Last sessions

### 20 November 2026: Screenshot to wish (2.265)
Owner interviewed (`docs/decisions/screenshots.md`): share 1–10 pictures → pick screen → photo editor → Add a wish, one
by one with Skip, a note per saved wish, "Stop adding?", an end message; the editor shows live mini cards; every wish
needs a name (app + both web editors). Checks: `ShotsModelTest`, `ShareEntryTest`, web e2e t2/t5. Screenshots looked
at; sharing from a real app (Instagram, gallery) can't be tried here.

### 20 November 2026: one tap on your reaction removes it (2.264)
The owner's first small fix (asked before the price-reading findings): app `ReactionModel` + `web/together.js`; checked
with a pretend server, and `tests/together` against a local PostgreSQL (all web suites pass but the two Deno ones,
Deno isn't installed here). Owner tests 2.233–2.264 over the next days.

### 19 November 2026, late night: the data rebuild, Fetch (2.263): done
Four files, every name kept (owner); `FetchSameTest` recorded first; moved line for line; ALLOWED is empty. 2.262's
GitHub build failed on `DataSameTest` (two prices in one second = one DB row, speed-dependent): fixed, re-recorded with
the old Data.kt in a separate copy. Next: the two price-reading findings as their own version (owner).
