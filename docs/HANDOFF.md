# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **2.281 (22 November 2026).** New + menu lines (`home-and-kinds.md`, last section). The personal link preview
  is dropped (owner). Ask how the new + menu lines read on the phone.
- **Ask first:** which numbers of the idea list (`ideas.md`, last section) the owner wants to plan; explain gladly.
  Children's lists: later. R7 and the Play Store: "neither yet" (owner, 22 November 2026).
- **2.280 (22 November 2026).** The owner's review (`look.md`, last section): search starts at the last shop, two pages
  with the big title, own buttons, unused code gone, shared row parts (`Rows.kt`), GitHub keeps Robolectric's file.
- **2.279 (22 November 2026), read first.** Every picker is a bottom sheet (`look.md`, last section; `PickSheet.kt`).
  Ask how Move or copy (and ← back from "Move to…") and the other pickers feel, and whether the pop-ups that stayed
  (listed there) are right. Light-mode sheet titles were grey before: ask if any text still looks grey.
- **2.278 (22 November 2026).** Thin lines between rows in every grouped block (`look.md`, last section). Ask whether
  Move or copy and the menus read as separate rows now in dark mode. Open: choices as sheets (TODO, owner skipped).
- **2.277 (22 November 2026), read first.** Search the shops (`docs/decisions/shop-search.md`). Ask: does each shop in
  the row show its own results (the search addresses couldn't be opened from here), does bol.com load or need "Open in
  Chrome", is the fading row clear? Owner said every earlier version has been tried: the "Try …" to-do items are gone;
  the owner reports what they notice. Earning money was discussed, nothing decided (same file): ask before building.
- **2.276 (21 November 2026), read first.** One style guide for the whole app (`docs/STYLE.md`, the owner's page
  https://claude.ai/artifact/CfZBnhRcVPydi8YVtjfV2H, `look.md` last section). Ask how menus as sheets, the ✕ rule,
  buttons sized to their words, empty pages and big titles in Gen Z chaos feel. Every new screen follows the guide.
- **2.275 (21 November 2026), read first.** The owner's style guide page: https://claude.ai/artifact/CfZBnhRcVPydi8YVtjfV2H
  (rules in `docs/STYLE.md`). Ask how Move or copy feels (action first, the wish on top) and how dialogs look in both
  styles now (Gen Z chaos changed most). New screens follow the guide; `DialogRulesTest` guards the dialog rule.
- **2.274 (21 November 2026).** Ask whether tapping a price-drop notification or a chat card now shows the whole wish
  in every view. Open: bol.com refuses the app's built-in browser (Chrome works); options given, owner chose none yet.
- **2.273 (21 November 2026), read first.** Ask how Personal / Co-op reads, Home's tabs (they appear with a second
  kind), and taking chosen wishes out (✕, or untick in the picker) (`home-and-kinds.md`). Lesson from the owner:
  before reporting a feature done, also try changing your mind (undo, remove, go back) and say what's missing.
- **2.271–2.272 (21 November 2026).** Ask how New wishlist feels now (one page, Personal / Shared, the card, Privacy)
  and whether form sheets stay open as wanted (X, pull tab, Back asks). Other forms could also report "something
  typed" for Back (group, exchange, settings, profile): ask (`screens.md`, last section).
- **2.270 (21 November 2026), read first.** Did the owner run `supabase-features.sql` (version 2)? Ask how "Chat
  about this wish" feels on a shared wishlist (the picker, Add friend, one person straight to the chat, offline) and
  whether the names in the title font look right (`wish-cards.md`, `look.md`, last sections).
- **2.269 (21 November 2026), read first.** Ask how sharing a link feels now (name and photo first, "Looking for the
  price…", Save waiting with "Getting the price…"), above all on a slow shop shared twice (the second time should be
  faster), and whether the list summary's numbers now match Home (`look.md`, `screens.md`, last sections).
- **2.268 (20 November 2026), read first.** Every new or changed screen needs a screenshot test with
  `compose.guard(...)` (MAP), so the layout inspector covers it. Ask how the cards look now (all views) and whether "I
  have it" feels right. If GitHub's `layout` job fails, read its report (the job's log) before anything else.
- **2.266 (20 November 2026), read first.** Ask how "Chat about this wish" feels (the card above the typing bar, tapping
  a card in the chat), the Home swipe, and the notifications (no more dead ends). The owner has the list of every
  notification (given in the chat): ask which to change or drop, and build that (`docs/decisions/notifications.md`).
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

### 22 November 2026: earning money, Search the shops, lines between rows, pickers as sheets (2.277–2.279)
Owner asked how to earn money without ads, sponsored ideas or accounts: advice written down (affiliate links on what the
user opens, later Plus on the Play Store), nothing decided. Built "Search the shops" after 8 questions: one search
opens bol.com's own results with a row of the six big shops + Google, "Open in Chrome", an offline message. The layout
check caught cut-off letters in small shop icons (fixed). 2.278: rows in dark menus looked like one block (line
almost invisible, some menus none): a thin line after the icon everywhere. 2.279: every picker a bottom sheet. 2.280: the review.

### 21 November 2026: fonts, link reading, chat on co-op lists, New wishlist, Home's tabs (2.269–2.273)
(Earlier wordings of this summary are in HANDOFF-ARCHIVE.md.) 2.269: numbers in the text font; faster link reading.
2.270: big names in the title font; chat about a wish on co-op lists (owner runs `supabase-features.sql` v2). 2.271–2.272:
Privacy in New wishlist, it opens; one page; form sheets close only by X / pull tab. 2.273: "Shared" → Co-op everywhere,
Home's tabs per kind, chosen wishes can be taken out (a flaw Claude missed). Not checkable here: the real phone.

### 20 November 2026: card labels, "I have it", the layout inspector (2.268)
Owner's screenshots (labels overlapping, cut, prices broken) → labels redesigned (`CardLabels.kt`, owner chose the
reservation under the photo), "I have it" straight to Received gifts, and a layout inspector (`LayoutGuard.kt`) on every
screen picture that blocks publishing on GitHub (owner). It found 6 more problems, all fixed; on the old code it flags
exactly the owner's screenshots. Not checkable here: how it feels on a phone.
