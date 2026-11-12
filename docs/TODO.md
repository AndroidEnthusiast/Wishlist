# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **The owner's new feature** (owner, 12 November 2026: a feature first, before the items below). Interview first.

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
- The welcome screen redesign; "chip in" (several people pay for one gift: needs P24 first, prices stored as whole
  cents and safer wish ids). More: `docs/decisions/ideas.md`.
