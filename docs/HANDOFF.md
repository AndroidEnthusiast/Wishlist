# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **Newest (13 November 2026), read first.** 2.199 and 2.200: twelve new styles (Y2K, Retro vintage, Pop futurism,
  Grunge, Flat, Bauhaus; Doodle, Cottage-core, Japandi, Risograph, Synthwave, Anthropomorphic) and every part themed;
  2.201: Swiss, British Punk, 3 colour sets per style, wild cards and a detail of its own for every style. Ask how each feels on the phone, light and dark (fonts, fields, menus, the drawings);
  fix what looks wrong in every style. Gen Z chaos is saved in `themes/gen-z-chaos/`: ask before changing it.
- **START HERE (12 November 2026, late night, after 2.198).** The review's part 2 is built (2.197, 2.198). Ask how
  2.195–2.198 feel on the phone, then continue the "logical experience" review (owner: "help me discover those")
  or what the owner wants next: show all of `docs/TODO.md` when asked. Known limit: Undo of "Moved to Received gifts"
  brings the wish back to its original list only (its copies on other lists don't come back).
- **Everything open** (the complete to-do list, owner): `docs/TODO.md`. Owner wants a new feature built first.

## Last sessions

### 13 November 2026: the themes (2.199–2.201)
Gen Z chaos saved as it was in `themes/gen-z-chaos/` (`tests/themes/check.sh`). Cyberpunk, Neobrutalism, Neumorphism
removed (→ Quiet); Y2K, Retro vintage, Pop futurism, Grunge, Flat, Bauhaus added (magier.com 5, 6, 9, 13, 18, 20).
Every part themed (`Themed.kt`); `LookTest` checks contrast everywhere; screenshots of every style were looked at
(fonts can't download there). 2.200: six more from kittl.com, a gift box with a face. `docs/decisions/look.md`.

### 12 November 2026, night: clean-up, notes, motion, the wish review (2.192–2.198)
Screenshot tests fixed. Clean-up: `LocalGo` (2.192), `Places` and no `*Nav` objects (2.193); a message notifies unless
that chat is on screen (2.194). Crash review: 3 old crashes on 2.154, none since. Notes tidy-up: slim `CLAUDE.md`,
`docs/MAP.md`, `docs/TODO.md`, archives, a stop hook. 2.195 screens move (`Motion.kt`); 2.196–2.198 wishes act alike.

### 12 November 2026, day: Stage 4 finished and Android 16 (2.188–2.191)
Restoring on a new phone with the recovery key and signing the old one out (2.188); privacy page, delete my profile
and data, block and report (2.189); Android's own backup off and the shop-page helper limited to 60 pages an hour per
key (2.190); building blocks up to date: Android 16 target, Kotlin 2.1, Compose 2025.06, Room 2.7 (2.191).
