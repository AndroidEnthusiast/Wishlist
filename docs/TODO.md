# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **Try the rebuild on the phone (owner, 19 November 2026: nothing of 2.233–2.255 tried yet; the owner tests after the 8 screens).** Everything should look
  and work exactly as before; say what doesn't. In order of risk:
  1. Notifications and links open the right place (a price alert, a message, a friend's list link, a group or gift
     exchange invitation); Back from a tab or a list goes Home (2.244).
  2. Add a wish: paste a shop link (photo, name, price fill in), take or choose a photo, Save to another list, edit a
     price; Save without a name turns the name red (2.246). In My shops: + Add wish on a product page (2.241).
  3. A chat: open it (it starts at the newest message), send text, a wish, a photo; reply, react, delete (2.239).
  4. A group and a gift exchange: open, invite, settings, leave dialog (Cancel) (2.237, 2.238). A gift list
     invitation in dark mode: its heading readable (2.252). A shared list's Leave and Delete are red (2.253).
  5. The photo editor: does the photo show straight away (in the test pictures it only showed after a tool) (2.240).
  6. Home, a list (drag to reorder, select, Reserve on a friend's list; a 2× wish: "How many?" then it shows in Gifts
     I reserved, 2.248), Friends and Profile (2.233–2.236), and the
     look of buttons, dialogs, fields and cards in every view (2.242, 2.243, 2.245, 2.247).
- **The rebuild (owner, 18–19 November 2026; `docs/decisions/roadmap.md`):** Home done (2.233), guards in (2.234), Friends/Profile (2.235), the open list (2.236), gift exchange (2.237), wishlist groups (2.238), Messages (2.239), the photo editor (2.240), My shops (2.241), the building blocks (Styled, 2.242; Themed, 2.243), the app's frame (AppScreen, 2.244), the wish cards (2.245), the wish form (2.246), the styles (Design, 2.247), a friend's list (2.248), App settings (2.249), covers (2.250), the price page (2.251), gift invitations (2.252), shared wishlist dialogs (2.253), reactions (2.254), Overview (2.255): **every screen is done.** Left:
  the files left on `ArchitectureTest.ALLOWED` (6): Shared, Exchange, Alerts, Groups, Import, Fetch: the data
  rebuild (below; Data.kt left the list in 2.256, Friends in 2.257).
- **Try 2.256 on the phone (the data rebuild, nothing should look or work differently):** add, edit and delete a wish
  and tap Undo; a wish on two lists (edit one, both change); move a wish; empty the bin; "I have it" and Received
  gifts; add, rename and remove a category; change theme, view and sort, close the app fully and open it again (all
  kept); delete a list and restore its wish from the bin.
- **Try 2.257 on the phone (Friends rebuilt inside, nothing should differ):** open Friends (your code, friends, a
  request), approve or decline one, add a friend by code or link, change your photo and About me, turn "Visible to
  friends" on and off for a list (also once in flight mode: it should switch back with a message), close the app fully
  and open it again.
- **The data rebuild (owner, 19 November 2026; `docs/decisions/roadmap.md`, last section): Data.kt done in 2.256,
  Friends in 2.257.** Next, one per version, each with a before/after check first (the pretend server
  `Cloud.pretendServer` is ready): Shared, then Groups,
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
- **Try Import (2.226) on the phone:** paste a GoWish/Giftful/Amazon link, share one from their app, paste text from
  notes; the check screen, duplicates, the new list, the fill-in of photos and prices, "Open the page" when nothing is
  found. Until step 2, those sites are read by the general reader: results may be incomplete.
- **Try 2.214 on the phone:** "Love it" labels (Rows on the picture, two per row smaller), the tip on a list with 3
  wishes, hold-and-move to reorder (also on a gift list), Move or copy…, the two-people Friends icon, Gen Z settings on
  cards, opening a chat and going back. Dragging can't be felt here: say how it feels.
- **A wish's own page** (owner, 16 November 2026: "yes, plan it"; `docs/decisions/screens.md`, last section): interview
  on the details first (what goes on it, own vs friends vs gift lists, the web pages), then build in its own session.
- **Next features, chosen 16 November 2026** (`docs/decisions/ideas.md`, "Solo use"): Sold out / back in stock, then
  Screenshot to wish. Interview on risks before building each.
- **Choose the next design style** (owner, 14 November 2026), when you want one: it is made one at a time, to a
  professional standard, following `docs/DESIGN-STYLES.md` (your references and three words for the feeling first;
  a style brief and a specification with mock-ups for you to approve before anything is built).
- **Try Quiet (2.207) on the phone**, light and dark: lists in every view, a wish's sheet, Add a wish, Home, chat,
  App settings. Does it feel calm, precise and premium? Send a screenshot of anything that feels off. Then: polish
  Gen Z chaos the same way, with your partner (`docs/DESIGN-STYLES.md`).
- **Try 2.206 on the phone:** Quiet and Gen Z chaos, light and dark, look as before; someone who had another style
  now has Quiet with their colour and light/dark kept.
- **Gen Z chaos, coloured text** (found 13 November 2026, left as it was because chaos stays unchanged): with the
  Orange, Green or Pink accent in light mode, coloured text (unread chats, "Replying to") is only 2.8–3.8 : 1. Ask
  the owner whether to make it ink in chaos.
- **Try 2.195 and 2.196 on the phone:** screens move like Signal's (opening a list, a chat, a friend's page, Profile;
  tabs; Back; with the keyboard open). Wishes: a long press selects on every list (also collab), the ⋮ opens the new
  sheet. Anything odd, too fast or slow, hard to read?
- **Try 2.195–2.198 on the phone:** "I have it" (keep or Received gifts), All your wishes (⋮, long press, "From which
  list?"), editing a wish that is on two lists changes both.
- **Continue the "logical experience" review** (owner, 12 November 2026: "help me discover those"): more places where
  similar things behave differently. Ask the owner which area next.

## Waiting on the owner (things only the owner can do)
- **Crashlytics:** check that version 2.194 shows up (it said "Latest release 2.182" on 12 November); if not, crash
  reports may have stopped after 2.191. Close the two old 2.154 issues.
- **Test 2.193 and 2.194 on the phone:** Messages and Friends open where you left them; Back from a friend's page
  opened from a chat; turning the phone on a friend's list; a message notifies unless that chat is on screen.
- **R7, the two-phone test** (syncing, restoring with the recovery key, notifications, block and report).
  `docs/ROADMAP.md`, Stage R.
- **R8, account safety:** two-step sign-in for GitHub, Supabase and Google. `docs/ROADMAP.md`, Stage R.
- **S2.5:** confirm both phones run a new version, then the old storage code can be removed. `docs/ROADMAP.md`.
- **R4, real shop pages:** whenever a price reads wrong, tap "Price is wrong?" and send the file (ongoing).

## Later (owner said "leave it for now")
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
