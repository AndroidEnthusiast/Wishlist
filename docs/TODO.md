# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **Try Y2K (2.203) on the phone**: Holographic, Liquid chrome, Bubbly, light and dark; Glass/Glossy/Sticker cards;
  Calm/Normal/Wild. Deep themes so far: Doodle, Y2K. Still to rebuild one by one: Retro vintage, Pop futurism, Grunge,
  Flat, Bauhaus, Cottage-core, Japandi, Risograph, Synthwave, Anthropomorphic, Swiss, British Punk, Quiet, Gen Z chaos
  (with the partner).
- **Try Doodle (2.202) on the phone**: does scrolling feel smooth now (also in the other styles)? Its three
  sub-themes (Notebook, Sticky notes, Board), the options Decoration and Cards, light and dark. Then pick which style
  to rebuild next to the same standard (one or two per update).
- **Try 2.199's and 2.200's styles on the phone** (Y2K, Retro vintage, Pop futurism, Grunge, Flat, Bauhaus, Doodle,
  Cottage-core, Japandi, Risograph, Synthwave, Anthropomorphic, Swiss, British Punk; Gen Z chaos and Quiet), light and
  dark, each colour set (the gift box peeking over cards, the crooked Punk cards too): do the fonts arrive, do fields, menus, tick boxes, the bottom panels and the drawings look
  right on every screen, is anything hard to read? Not seen here: real fonts, real photos, real phone sizes.
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
- The screenshot "all" (All your wishes) shows Home: its tap never reaches the page in the picture-taker (since before
  12 November). `AllWishesTest` checks the page itself; fix the picture when screens are next compared.
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
