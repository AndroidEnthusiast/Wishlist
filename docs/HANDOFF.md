# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **START HERE (12 November 2026, night, after 2.197).** Ask how 2.195–2.197 feel on the phone. Then build **2.198**
  (all decided, `docs/decisions/screens.md`, "review, part 2"); it touches stored wishes, so checks first:
  1. **Copies stay one wish:** `Repo.replaceMine` (and every edit of an own wish) also updates the same id on every
     other own list that has it (`copies` in `Data.kt`), all fields but `category` and `bin`. Check in `RepoStoreTest`.
  2. **"I have it"** replaces "Owned" in the ⋮ sheet (`WishSheet.kt`): ticked → untick at once with Undo; unticked →
     asks "Keep it on the list (marked owned)" or "Move it to Received gifts" (`Repo.receive`, givers get a
     thank-you). "What did you get?" (`Reminders.kt`) asks the same once for all ticked wishes.
  3. **All your wishes / Top wishes** (`Overview.kt` `WishLine`): ⋮ sheet (Edit opens the wish's first list with
     `Repo.openMine` first; Private / I have it / star via every copy), long press selects (Move to…, Delete).
     When a wish is on several lists: "This wish is on 2 of your wishlists: A, B. From which one?" (Delete also
     offers "All lists" = trash the original, its copies follow). The sheet shows "On 2 lists: A, B".
- **Everything open** (the complete to-do list, owner): `docs/TODO.md`. Owner wants a new feature built first.

## Last sessions

### 12 November 2026, night: clean-up, notes tidy-up, screens that move (2.192–2.195)
Screenshot tests fixed. Clean-up: `LocalGo` (2.192), `Places` and no `*Nav` objects (2.193); a message notifies unless
that chat is on screen (2.194). Crash review: 3 old crashes on 2.154, none since. Notes tidy-up: slim `CLAUDE.md`,
`docs/MAP.md`, `docs/TODO.md`, decisions by topic, archives, a stop hook. 2.195: screens move like Signal's (`Motion.kt`).

### 12 November 2026, day: Stage 4 finished and Android 16 (2.188–2.191)
Restoring on a new phone with the recovery key and signing the old one out (2.188); privacy page, delete my profile
and data, block and report (2.189); Android's own backup off and the shop-page helper limited to 60 pages an hour per
key (2.190); building blocks up to date: Android 16 target, Kotlin 2.1, Compose 2025.06, Room 2.7 (2.191).

### 10–11 November 2026: the second review, Stage R and Stage 4 part A (2.178–2.187)
Second review of the roadmap; price checks believe two agreeing readings (2.178). Stage R repairs: "new version is
ready" (2.179) up to "Price is wrong?" (2.182) and screens that come back after Android closed the app (2.183). Stage 4
part A: limits against abuse (2.184), photos as files (2.185), clean-up after 12 months (2.186), the recovery key (2.187).
