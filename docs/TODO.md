# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **The rebuild (owner, 18–19 November 2026; `docs/decisions/roadmap.md`):** Home done (2.233), guards in (2.234). Left:
  the 29 files on `ArchitectureTest.ALLOWED` (biggest: ExchangeUi, Data, GroupsUi, Messages, HeroEditor,
  Shops, Styled, Themed, Shared). One screen per version, bug fixes only; ask the owner which next.
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
