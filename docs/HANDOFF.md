# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
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

### 15 November 2026, later: the owner's review (2.209)
Owner's review: selecting gets a bottom action bar; Messages without tabs, a round chat button; "Viewed" removed (app and
web); Private keeps the ⋮ sheet open; amounts never cut off (`FitText`, the check also found Quiet cut off with big
text); "Create list" label fixed; chaos: numbers row, own Secret Santa card, one message box. Brainstorm still to plan.

### 15 November 2026: profile sheet text fixed (2.208)
Owner's screenshot: the profile sheet in Quiet dark showed black text on the dark grey panel (2.207's lifted grey is
not a scheme colour, so the sheet's text fell back to black). Every bottom panel now sets its text colour
(`sheetContent` in `Themed.kt`); check `LookTest.sheetTextIsReadable`; new screenshot `profileSheet`.

### 14 November 2026, later: Quiet polished (2.207)
Owner: polish Quiet, then chaos; challenge big asks. Brief, plan and mock-ups approved (photos on a light surface in
dark too, labels above fields, sheet switches). 2.207 builds it; `LookTest.quietPartsAreReadable` found green, orange
and pink too light as text (fixed). Screenshots fixed (they showed Home), plus App settings and big text; chaos
checked pixel for pixel. Notes rule: challenge the owner on big builds.
