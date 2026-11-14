# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **Newest (14 November 2026, after 2.206), read first.** The owner was disappointed by the design styles of
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

### 14 November 2026: back to Quiet and Gen Z chaos (2.206)
Owner: the design styles "disappointed me"; keep Quiet and Gen Z chaos, remove the rest, and next time one design style
at a time "as though a professional would have done it". 2.206 removes the twelve styles, their colour sets and
options; removed keys become Quiet. New `docs/DESIGN-STYLES.md` (process, best practices for this app, inventory,
definition of done); decision in `docs/decisions/look.md`. Checks and screenshots of Quiet and chaos pass.

### 13–14 November 2026: the themes (2.199–2.205)
Gen Z chaos saved as it was in `themes/gen-z-chaos/` (`tests/themes/check.sh`). Cyberpunk, Neobrutalism, Neumorphism
removed (→ Quiet); Y2K, Retro vintage, Pop futurism, Grunge, Flat, Bauhaus added (magier.com 5, 6, 9, 13, 18, 20).
Every part themed (`Themed.kt`); `LookTest` checks contrast everywhere; screenshots of every style were looked at
(fonts can't download there). 2.200: six more from kittl.com, a gift box with a face. `docs/decisions/look.md`.

### 12 November 2026, night: clean-up, notes, motion, the wish review (2.192–2.198)
Screenshot tests fixed. Clean-up: `LocalGo` (2.192), `Places` and no `*Nav` objects (2.193); a message notifies unless
that chat is on screen (2.194). Crash review: 3 old crashes on 2.154, none since. Notes tidy-up: slim `CLAUDE.md`,
`docs/MAP.md`, `docs/TODO.md`, archives, a stop hook. 2.195 screens move (`Motion.kt`); 2.196–2.198 wishes act alike.
