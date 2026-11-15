# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **2.211 (15 November 2026), read first.** The owner's first look at 2.210 is fixed (`docs/styles/identity.md`, last
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

### 15 November 2026, night: first look at the character (2.211)
Owner: "much better and more like our own app". Fixed: dialog text black on dark, scrolling broken (the collapsing
title took every swipe), fixed titles on the main tabs, no confetti but a pulsing "Reserved by you", "Top wish" on
every view, profile picture without ring. Each fix has a check that fails without it.

### 15 November 2026, evening: the app's own character (2.210)
Owner: it felt like "a standard Android visual experience". Diagnosed (Google icons, Settings-style menus, switch,
dialogs, flat bars, ripple, near-Google font, Android's default colours), interviewed in 5 rounds, built: own icons,
Berry, warm surfaces, Jakarta, squircles, soft press, own switch/dialogs/bars, grouped menus, two celebrations.

### 15 November 2026, later: the owner's review (2.209)
Owner's review: selecting gets a bottom action bar; Messages without tabs, a round chat button; "Viewed" removed (app and
web); Private keeps the ⋮ sheet open; amounts never cut off (`FitText`, the check also found Quiet cut off with big
text); "Create list" label fixed; chaos: numbers row, own Secret Santa card, one message box. Brainstorm still to plan.
