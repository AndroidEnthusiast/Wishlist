# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **2.219 (17 November 2026), read first.** Ask whether every dialog's buttons now read well (Share, Move, Profile),
  and how "About me" under the name looks. Then build the agreed collab merge (`docs/decisions/sharing.md`, last
  section: one "New wishlist" with "Who is it for? Me / Someone else", the name, the invite warning, the Friends
  picker) unless it was built in the same session (see Last sessions).
- **2.214 (16 November 2026), read first.** The owner's review (`docs/decisions/screens.md`, last section): ask how
  "Love it", the tip, dragging (can't be felt here), Move or copy…, the Friends icon, Gen Z settings cards and the
  chat feel. Then the chosen next features: Sold out / back in stock, then Screenshot to wish
  (`docs/decisions/ideas.md`, "Solo use"); interview on risks first. Ask how the wish form (2.216), friend page (2.217) and Home (2.218) feel. Next big one: a wish's own page (TODO).
- **2.213 (15 November 2026), read first.** "♥ Love this" replaced "Top wish" (app and web). Ask how it reads.
- **2.212 (15 November 2026).** Sheets, price page and wish form rebuilt (`docs/styles/identity.md`, last
  section). Ask how they feel; the remaining drop-down menus elsewhere (e.g. a category's menu) could become sheets too.
- **2.211 (15 November 2026).** The owner's first look at 2.210 is fixed (`docs/styles/identity.md`, last
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

### 17 November 2026: readable dialog buttons, About me, sizes removed (2.219)
Buttons in a dialog's text took the dialog's grey (Share: "Send invite", "Copy"): every button keeps its own colour now.
Owner: no chips under a person, only "About me" (100 letters, was "I like…"); birthday only on a friend's page near
it; sizes deleted (`supabase-sizes-off.sql`). Interviewed on dropping collab wishlists: merge in the menus, with
"Who is it for?" to protect the surprise. Checks: `DialogButtonInkTest`, `aboutMeInsteadOfChips`, `sizes_off_test.sql`.

### 16 November 2026: owner's review and solo interview (2.214)
"♥ Love it" / "Jorai loves it", smaller label (Rows on the picture), one-time heart tip; hold and move to drag
("My order", sort per list), Move or copy…; two-people Friends icon; Gen Z settings on cards; chat opens at its end,
no bounce going back (Moving had a size animation). Interviewed on solo use: sold out alerts and screenshot to wish next.
2.215: Shopify pictures given as http:// (Baracuta) load over https (`secureImage`, app and web).
2.216: wish form trimmed, Quiet field text full ink. 2.217: friend page person first, 2-photo collages, failed pictures, card ✕. 2.218: Home = your profile, + menu, pop-ups.

### 15 November 2026, very late: "Love this" (2.213)
Owner asked whether people say "top wish": they don't. Now "♥ Love this" with a heart in the same gold, everywhere in
the app and on the web pages; smaller label on small cards. Data unchanged. App, web and database checks pass.
