# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **Plan the four ideas chosen on 26 November 2026** (`docs/decisions/ideas.md`, last section): price drop for
  givers (a setting, any reserved gift), reservations run out (server script), Shopping list by shop (own wishes,
  App settings switch off by default), Historic low. Interview on details and risks first.
- **Try 2.282 on the phone:** the Elho pot's price corrects itself to €8.09 at the next price check (quietly, no
  alert); keep sending "Price is wrong?" files for any price that's off (`docs/decisions/prices.md`).
- **Owner: run `supabase-features.sql` (version 2) in Supabase** (the daily server check failed on 22 and 23 November;
  GitHub issue #3 "Server scripts to run").
- **Try 2.280 on the phone:** Search in shops starts at Amazon, then at the shop you last tapped in the row; Gifts I
  reserved and Received gifts have the big title that shrinks; App settings, Profile and Import look as before.
- **From the review of 22 November 2026, not chosen yet** (`docs/decisions/look.md`, last section): "Open in Chrome"
  for every shop (now only in a search; a tapped bol.com tile that refuses is a dead end); the app's offline message
  when tapping a shop tile (now the browser's own error page); and ask whether "How many will you buy?" and the Leave
  forms with "who takes over" should become sheets like the other pickers.
- **Try 2.279 on the phone:** Move or copy from a wish's ⋮ (then Move to…, ← back, + New wishlist), Add to my wishlist
  on a friend's wish, Send a wish in a chat, Invite friends (group, gift exchange, co-op list), Received gifts > Mark
  as received. Do they all look like the ⋮ menus? Are any titles still grey in light mode?
- **Try 2.277 on the phone (Search the shops, `docs/decisions/shop-search.md`):** My shops > type "lego castle" >
  Search: bol.com's results open with a row of shops under the search field; tap each (Amazon, Coolblue, Wehkamp,
  Zalando, de Bijenkorf, Google): does each show its own results, not an error page? If bol.com refuses, tap "Open
  in Chrome". Change the words and search again; "+ Add wish" on a product; once in flight mode (the offline
  message and Try again).
- **The notification list (owner, 20 November 2026):** the owner got every notification listed in the chat; ask which
  to change, drop or add, then build it (`docs/decisions/notifications.md`).
- **Screenshot to wish, open:** a link shared together with several pictures is not used (owner skipped the question).
- **The rebuild (owner, 18–19 November 2026; `docs/decisions/roadmap.md`):** Home done (2.233), guards in (2.234), Friends/Profile (2.235), the open list (2.236), gift exchange (2.237), wishlist groups (2.238), Messages (2.239), the photo editor (2.240), My shops (2.241), the building blocks (Styled, 2.242; Themed, 2.243), the app's frame (AppScreen, 2.244), the wish cards (2.245), the wish form (2.246), the styles (Design, 2.247), a friend's list (2.248), App settings (2.249), covers (2.250), the price page (2.251), gift invitations (2.252), shared wishlist dialogs (2.253), reactions (2.254), Overview (2.255): **every screen is done.** Left:
  no file left on `ArchitectureTest.ALLOWED` (the data rebuild, below, emptied it in 2.256–2.263).
- **Found while checking Import (19 November 2026), next (owner: right after the Fetch split, as its own version):** a price written in kronor or złoty
  ("1.299,00 kr" on a .se shop, "20 zł" on a .pl shop) gets the phone's own currency; an Amazon link written as
  …/Some-Name/dp/B0…/ref=…?th=1 keeps its "ref" part (duplicates of the same product may not be spotted). Both are in the
  shared price reading (`Fetch.kt` + `web/parse.js`, Rule 2): fix together, with cases in `tests/parse`.
- **The data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`, last section): done.** Data.kt 2.256,
  Friends 2.257, Shared 2.258, Groups 2.259, Exchange 2.260, Alerts 2.261, Import 2.262, Fetch 2.263. Next, one per version, each with a before/after check first
  (pretend servers ready: `Cloud.pretendServer`, `PretendSharedServer` in the checks):
  Exchange, Alerts, Import, Fetch. After that, what a team would still do: checks on a few real phones (P23), and fewer
  copies of app logic in `web/` (e.g. `parse.js` beside `Fetch.kt`). Interview first on each.
- **GitHub's "Publish the web page" step fails now and then (19 November 2026: 2.242, 2.249, 2.255; rerunning didn't
  always help):** "No artifacts named github-pages were found" right after it was uploaded. The APK always published;
  the web pages kept their previous version (no web file had changed). Before the next change to `web/`: look at the
  pages job in `.github/workflows/build.yml` (e.g. the newer upload-pages-artifact), ask the owner first.
- **AliExpress shows its logo instead of the product photo** (owner, 18 November 2026): needs the real page. Owner: open
  that wish's price page, tap "Price is wrong?", share the file; then fix `Fetch.kt` + `web/parse.js` together and add
  the page to `tests/shared/same-answers.json` → `pages`.
- **Import, step 2 (owner, 18 November 2026; `docs/decisions/import.md`):** the owner makes a small PUBLIC test list on
  GoWish, Giftful, Amazon, bol.com, Etsy and Pinterest (3–4 wishes: one with a price, one without a photo, one marked
  favourite) and sends the links. Then: put them in `tests/import/lists.json`, look at each real page through the
  daily check's browser, write a reader per site in `web/import/reader.js` (raise VERSION) and check it on GitHub.
- **A wish's own page** (owner, 16 November 2026: "yes, plan it"; `docs/decisions/screens.md`, last section): interview
  on the details first (what goes on it, own vs friends vs gift lists, the web pages), then build in its own session.
- **Next features, chosen 16 November 2026** (`docs/decisions/ideas.md`, "Solo use"): Sold out / back in stock
  (Screenshot to wish was built first, 2.265, owner's choice). Interview on risks before building each.
- **Choose the next design style** (owner, 14 November 2026), when you want one: it is made one at a time, to a
  professional standard, following `docs/DESIGN-STYLES.md` (your references and three words for the feeling first;
  a style brief and a specification with mock-ups for you to approve before anything is built).
- **Polish Gen Z chaos the way Quiet was polished, with your partner** (`docs/DESIGN-STYLES.md`; kept from the
  "Try Quiet (2.207)" item, removed 22 November 2026 when the owner said every version was tried).
- **Gen Z chaos, coloured text** (found 13 November 2026, left as it was because chaos stays unchanged): with the
  Orange, Green or Pink accent in light mode, coloured text (unread chats, "Replying to") is only 2.8–3.8 : 1. Ask
  the owner whether to make it ink in chaos.
- **Continue the "logical experience" review** (owner, 12 November 2026: "help me discover those"): more places where
  similar things behave differently. Ask the owner which area next.

## Waiting on the owner (things only the owner can do)
- **Crashlytics:** check that version 2.194 shows up (it said "Latest release 2.182" on 12 November); if not, crash
  reports may have stopped after 2.191. Close the two old 2.154 issues.
- **R7, the two-phone test** (syncing, restoring with the recovery key, notifications, block and report).
  `docs/ROADMAP.md`, Stage R.
- **R8, account safety:** two-step sign-in for GitHub, Supabase and Google. `docs/ROADMAP.md`, Stage R.
- **S2.5:** confirm both phones run a new version, then the old storage code can be removed. `docs/ROADMAP.md`.
- **R4, real shop pages:** whenever a price reads wrong, tap "Price is wrong?" and send the file (ongoing).

## Later (owner said "leave it for now")
- **"How many people in the world added this wish"** (owner, 26 November 2026): after Google Play (`ideas.md`).
- **Lists for your children** (owner, 22 November 2026: "build later, remember it"): a parent makes and manages a
  child's wishlist. Interview first. The full idea list of that day (35 ideas): `docs/decisions/ideas.md`, last section.
- **Copy slips found 22 November 2026** (for the copy project): "Invite Friends" vs "Invite friends"; "No Friends yet.
  Add Friends on the Friends tab, or send the invite link."
- **Long press on things that aren't wishes:** lists on Home and friends open a menu (same as their ⋮), a group
  member's Remove is only behind a long press (no ⋮), groups and exchanges on Home have no ⋮ at all.

## Clean-up (owner: later, after the feature)
- **Split the very large files** (gift exchange 1,400 lines, Friends 1,270, groups 1,070, chat 1,020): no change
  on screen, screenshots identical before and after. About one session.
- **Data out of the big shared objects** (`Repo`, `Shared`, `Friends`, `Groups`, …) into proper layers (ViewModels):
  the slimmer route, area by area when a feature lands there; a full rewrite would be 3–5 sessions (owner: remind
  later). `WishFocus` (the wish that glows) is the last screen-state global.
- **Update the roadmap's "Honest state"** (it still describes the state before 12 November evening).

## Owner's next projects (in this order)
1. **The app's final name** (also the package name and maybe an own web address; permanent once on Google Play).
2. **The copy project:** every text rewritten (`docs/COPY.md`).
3. **Google Play** (Stage 6 in `docs/ROADMAP.md`): Play App Signing with the existing key, the store's forms, a
   closed test with 12 testers for 14 days, a test channel, maybe a robot test on real phones.

## Known gaps (small, no date)
- Two prices of one wish recorded within the same second are one row in the database (its key is the wish and the
  second): after a restart only one is left (found 19 November 2026; practically never happens).
- Reports of abuse have no alert yet (owner: find a better way than a failing backup).
- Photos of a group or exchange deleted with its organiser's profile stay as unreachable files.
- Collab wishlist pictures stay in Storage when the list is deleted by "Delete my data".
- A restored copy may miss changes from the old phone's last hour.
- The web pages ask for news every 20 seconds instead of being told (P-list in `docs/ROADMAP.md`).
- No automatic test on a real or pretend phone (P23).

## Ideas for later (not decided)
- Predictive back (the peek while swiping back from the edge; owner: not now, 12 November 2026).
- The welcome screen redesign; "chip in" (several people pay for one gift: needs P24 first, prices stored as whole
  cents and safer wish ids). More: `docs/decisions/ideas.md`.
- **Brainstorm with the owner (15 November 2026, next to plan, nothing built yet):** "chip in" together (owner wants
  it worked out); improve "Gift ideas for others" (today: collab wishlists for someone) and "About me for givers"
  (today: "I like…" and "My sizes" in Profile): the owner asked for suggestions, even if they change what exists.
- Gen Z chaos: the coloured circles behind text on other screens than settings (settings got solid cards in 2.214;
  ask if another screen is hard to read).
- Buttons: one rule for the whole app (main = filled, secondary = tonal, minor = text, one height and shape); today
  at least six kinds. The owner kept the three buttons on wish cards (15 November 2026).
- Shared wishlists: "Hide reservations" for collaborators, on by default (plan step 6, `docs/designs/plan-2026-11-17.md`): left for now (owner, 17 November 2026).
