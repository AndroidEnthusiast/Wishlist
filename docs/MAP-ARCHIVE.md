# Map archive: older wording, kept word for word

Moved here from `docs/MAP.md` on 20 November 2026 (its size limit). Only searched when needed.

## The reading order before the tidy-up (kept word for word)
Until 12 November 2026 `CLAUDE.md` began with this line. Its rules still hold and are in `CLAUDE.md` in short ("Every
session"); only the reading changed: read the parts of these docs that matter to the task, not all of them (owner).

Read this first, then **`docs/ROADMAP.md` (the plan to turn the app into a real, scalable app: follow it stage by stage before building new features, and end every session with a concrete summary of what improved; owner, 8 November 2026; its "Second review" of 10 November 2026 adds Stage R, which comes before Stage 4, and a checklist to go through with every new feature)**, then `docs/STYLE.md` (the style guide: every screen, dialog and feature follows it; owner, 7 November 2026), then `docs/DECISIONS.md` (what the owner has decided and why: follow it, and add to it whenever the owner decides something new). The long history, a short summary of every session, the open to-do list and **what the owner wants to do next** ("Next session") are in `docs/HANDOFF.md`: read it at the start of a session, and before a session ends add a short summary of it and update "Next session". Every version's technical notes are in `README.md`.

## Undoing the notes tidy-up
The notes were tidied on 12 November 2026 (owner): `CLAUDE.md` slimmed, details moved here, the handoff archived
(`docs/HANDOFF-ARCHIVE.md`), the decisions split by topic (`docs/decisions/`), the finished roadmap stages archived
(`docs/ROADMAP-ARCHIVE.md`). The last commit before the tidy-up is `ef02546`. If the owner says "put back the notes
from before the tidy-up": `git checkout ef02546 -- CLAUDE.md docs/`, remove the files the tidy-up added
(`docs/MAP.md`, `docs/HANDOFF-ARCHIVE.md`, `docs/ROADMAP-ARCHIVE.md`, `docs/decisions/`), commit and push to `main`.
The stop hook's limits would then block, so also remove the Stop hook from `.claude/settings.json` (or ask the owner).

## Earlier short wording in CLAUDE.md (kept word for word, 12 November 2026)
- Before 14 November 2026 ("(big builds above all)" was added, owner):
- **Interview the owner before building** (owner's standing instruction, 1 November 2026): for every design or implementation choice that isn't already decided in `docs/DECISIONS.md`, ask first with pop-up questions (the AskUserQuestion tool, a recommended option first), and push back when an idea has a weak spot. Don't just agree, and don't fill gaps with your own choices: things built on guesses had to be redone. A skipped question means "none of these": build nothing from it (owner, 5 November 2026). Re-read the owner's earlier messages and check every point was built; say plainly if something was left out.
Replaced by shorter lines when `docs/TODO.md` was added; the full versions are above in this file.
- Before every push that touches `web/` or the helper: `bash tests/run-all.sh` (setup in MAP "Testing, in full");
- Can: the real Supabase, read-only (public key, GET calls to `/rest/v1/rpc/...`, functions with a wrong key).
- Can't (say so, never claim it works): writing to the real Supabase, the database, real shops, iPhone/Safari, how
  screens feel on a phone, App Links, real notifications. Details: MAP.
- **Lists, wishes, Home, anything on screen:** MAP "Your lists and wishes" + `docs/decisions/screens.md` +
- `docs/decisions/screens.md`: Screens, look, wishes and prices (Look and feel; Private wishes; Top wish; Wishes on screen and the wish form; Prices; App review; Premium identity; Owner's list of 3 November 2026; Settings review; Home tiles, Collab wishlists, moving wishes, notifications and web search).
