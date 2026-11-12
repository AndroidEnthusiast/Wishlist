# To-do list: everything that is still open

The one complete list (owner, 12 November 2026: "when I ask something along the lines of what's on my to-do list,
all of the open things should be listed there"). When the owner asks what is open, next or on the list: show **all of
it**, grouped as below, with a recommendation. Add every new open item here the moment it appears (a bug found, a
"later", a check that waits on the owner); remove an item only when it is done (say so in the session summary).
Details of each item are in the doc named after it.

## Now
- **Try 2.195 and 2.196 on the phone:** screens move like Signal's (opening a list, a chat, a friend's page, Profile;
  tabs; Back; with the keyboard open). Wishes: a long press selects on every list (also collab), the ⋮ opens the new
  sheet. Anything odd, too fast or slow, hard to read?
- **2.198 (decided, not built yet):** "All your wishes" and "Top wishes" like any list (⋮ sheet, long press,
  "From which list?" when a wish is on several lists; changes go to every copy of a wish); "I have it" replaces
  Owned and Received
  (`docs/decisions/screens.md`, review part 2).
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
