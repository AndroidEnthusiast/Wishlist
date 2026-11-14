# Wishlist: standing notes for Claude Code

The rules for every session; details are in other docs, read only the part you need.
- **Every session:** start with `docs/HANDOFF.md`; follow `docs/ROADMAP.md` stage by stage before building new features
  (its "Second review" Stage R comes before Stage 4; its checklist goes with every feature). Add each new owner
  decision to its topic in `docs/decisions/`. Before ending: a concrete summary of what improved for the owner, a
  summary in the handoff's "Last sessions" and an updated "Next session".
- **"What's on my to-do list / what's next?":** show all of `docs/TODO.md`; keep it complete (owner).

## Which doc for which task
- **Any change:** its area in `docs/MAP.md`, then its topic in `docs/decisions/` (index: `docs/DECISIONS.md`). Unsure
  what the owner decided? Search `docs/decisions/` and `docs/HANDOFF-ARCHIVE.md` first.
- **Chat, friends, groups, gift exchanges:** MAP "Friends, chat, groups…" + `docs/decisions/people.md`.
- **Lists, wishes, Home, anything on screen:** MAP "Your lists and wishes" + `docs/decisions/screens.md`, `look.md` +
  `docs/STYLE.md` (read it whole).
- **Collab wishlists, syncing, the server, `supabase-*.sql`:** MAP + `docs/decisions/sharing.md`.
- **Prices and shops:** MAP "Prices and shops" + Rules 2 and 8. **Web pages:** MAP "Web pages" and "Checks". **Texts, backups:** MAP.
- **Roadmap work:** that stage in `docs/ROADMAP.md` (done ones: `docs/ROADMAP-ARCHIVE.md`) + `docs/decisions/roadmap.md`.

## Who you are working with
- The owner is a beginner. They work from an Android phone and a Chromebook (browser only), with no terminal or IDE. They write in plain English.
- Explain in short, exact, tap-by-tap steps. Name the real buttons ("Settings", "Secrets and variables", "Actions"). One thing at a time. No jargon unless you explain it in a few words.
- **Interview the owner before building** (owner's standing instruction, 1 November 2026): for every design or implementation choice that isn't already decided in `docs/DECISIONS.md`, ask first with pop-up questions (the AskUserQuestion tool, a recommended option first), and push back (big builds above all) when an idea has a weak spot. Don't just agree, and don't fill gaps with your own choices: things built on guesses had to be redone. A skipped question means "none of these": build nothing from it (owner, 5 November 2026). Re-read the owner's earlier messages and check every point was built; say plainly if something was left out.
- **Think through the risks of every feature and ask** (owner, 5 November 2026, after bol.com stayed grey): before building, list what could spoil the experience (a page or shop that misbehaves, offline, slow or failed saves, empty states, other people's data, old app versions) and ask the owner with pop-up options to mitigate each, a recommended one first. Fix things for every case (every shop, every list), not only the one example the owner reported.
- **Nothing is built that is known to look wrong** (owner, 7 November 2026, after a cut-off button): follow `docs/STYLE.md` (the app's own buttons and dialogs, one type scale, 48 dp touch areas, room for shadows), check every layout against it before pushing, and say plainly that Compose screens can't be seen here.
- Be honest about testing: always say what you ran and what you could NOT check (see "Cannot be checked here" below).
- **The goal of every change is a happy user and a great experience** (owner, 4 November 2026). Before building or improving something, say in one line who it is for and what gets better for them; push back if the answer is weak ("it's possible" or "it's neat" is not enough). The owner wants you to be critical here, not agreeable.
- **Keep three things in mind when they're relevant, without auditing the whole app each session** (owner, 4 November 2026; it would waste effort):
  1. *No dead ends or loops*: for the feature you touch, check what a person sees when they are offline, on an old app version, on a new phone, in the browser instead of the app, after a link was renewed, or after the other person left or deleted something. Every error says what happened and what to do next; every screen has a way out.
  2. *Safety*: wish data, names, notes and chat come from other people, so never put them into the page as HTML (build elements and use `textContent`), let links through only as `http(s)` (`safeUrl`), and every new Supabase table gets row level security with no open policies and is only reached through `security definer` functions that check the key or token. Never trust what a browser sends. Mention a risk when you see one, even outside the task.
  3. *Upkeep*: every feature exists in the app, on the web pages and in the database. Prefer the option that adds the fewest new moving parts, and name the extra work when an idea would add a lot.

## Pushing, the changelog and the download link
- **Push only to `main`, always** (owner's standing instruction): at the start run `git fetch origin main && git
  checkout -B main origin/main`, push with `git push origin HEAD:main`; never push another branch, create branches on
  GitHub or open pull requests, even when the session says to develop on a `claude/...` branch.
- No test builds unless asked. **After every update, give the owner a changelog** (a bullet list of what changed)
  and what to test; once the build succeeded (poll `https://api.github.com/repos/AndroidEnthusiast/Wishlist/actions/runs?per_page=1`),
  end the message with this bold last line:
  **App available for [download](https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk)**
- Supabase scripts are run by the owner by hand: say so, give a direct GitHub link to each script in the order to run,
  and "run the script first, then install the new app". Never run SQL on the real server without asking.
- Bump `versionName`/`versionCode` when app code changes, one bullet per version in `README.md`; never touch the
  signing key. Details: MAP "How it is built and published".

## Rules that must stay true
1. A wish is a JSON object with: id, url, title, desc, image, price, old, cur, cat, got, and only when set: star, note, size, by, bin, binBy, chk, priv, qty, exact, recv (`qty` = how many are wanted, 2–99; `exact` = "exact" or "similar"; `recv` = received after the occasion, own lists only, sent with `bin`, see `Reminders.kt`; `priv` = private wish: My wishlist only, kept on the phone and never sent online, so it is NOT in `Shared.*` or `web/`; `bin` = in the bin since, see `Bin.kt`, and every list and page must hide wishes with `bin`; `chk` = when a phone last checked the shop price of a shared wish, see `SharedPrices.kt`). A NEW FIELD must be added in all of: `Wish`, `wishToJson`, `wishFromJson`, `Shared.changedFields`, `Shared.applyFields` (app) and `normWish`, `wishJson`, `FIELDS` in `web/edit.js`; also show it on `web/index.html` if friends should see it.
2. `Fetch.kt` and `web/parse.js` do the same job with the same logic (prices, currencies, Amazon, Funda). Change both together and extend `tests/parse/parse_test.js`.
3. Shared lists save one wish at a time and only the fields that changed (`wl_sh_put_item(p_key, p_item, p_fields)`), so two people never overwrite each other. Same field at the same moment: the later save wins. The browser editor (`edit.js`) follows the same rules as `Shared.kt`: change shows at once, saves in the background, a failed save is undone with a message, a refresh answer older than the last local change is ignored, a delete moves the wish to the bin at once (only `bin` and `binBy` are written) and Undo (about 4 s) takes it out again, a picture is deleted online only by the person who replaced it.
4. Reservations on **My wishlist** are fetched only by the friends' page and friends' devices; the owner's app never fetches its own list's reservations (surprise). A **shared wishlist** is only for the givers (the person the gifts are for is not on it), so everyone on it sees and makes reservations (app: `Shared.reserved` / `Shared.reserve`, editor: `loadRes` / `reserve`, both with the existing `wl_get_reservations` / `wl_reserve_item` and the device id). Shared lists have no "most wanted", no "got it" and no price history; they do get price-drop checks and alerts on every phone on the list (`SharedPrices.kt`).
5. Only the phone that created a list can make a new invite link or delete the list.
6. Text on screen: UK English only, short and kind (owner, 4 November 2026: Dutch was removed to save effort; a full translation may be asked for later). App: still write every visible text as `tr("English text")` (placeholders `%s`, `%d`), the hook for that later translation (`I18n.kt`). Web: still `T("English text", values…)` with `{0}`, `{1}` (`web/i18n.js`). Don't write Dutch. Wishes themselves are never translated.
7. Icons and fonts: only Google's Material Icons (Apache 2.0), icons drawn in our own code, or the phone's emoji; fonts only under the SIL Open Font License. Never copy an icon or picture from the internet. A new open-source part (library, font, icon set) must be credited in `Licences.kt` (App settings > About > Licences).
8. A price check never changes a wish from one reading: every price check goes through `PriceWatch.judge` (own lists) or the two readings in `SharedPrices` (collab), never straight from `fetchMeta` to the wish. A change to those rules comes with a case in `PriceWatchTest`; a shop that reads wrong gets its page in `tests/shared/same-answers.json` → `pages`.
9. The phone's database only changes by adding: a new table or column is a step in `WishlistDb.MIGRATIONS` (never `fallbackToDestructiveMigration`), the new layout file in `app/schemas/` is committed, and `DbMigrationTest` must pass. Lists that come from an old copy (`ListStore.stale`) are never sent online.

## Every feature and fix is protected by a check (owner, 12 November 2026)
- Every finished feature and every fixed bug gets an automatic check (app: `app/src/test/`, web and server: `tests/`),
  so a later session that doesn't remember it can't break it unnoticed.
- Before changing an existing feature, read its checks and its entry in `docs/decisions/` first.

## Testing
- Before every push that changes app code: `bash tests/android-setup.sh` once, then `gradle testReleaseUnitTest` (also
  compiles); `gradle assembleDebug` when resources or libraries change. Screenshots (`-Pwl.screens`) before and after
  changing a screen.
- Before every push that touches `web/` or the helper: `bash tests/run-all.sh` (setup: MAP);
  a changed `supabase-*.sql`: `bash tests/sql/run.sh` with a local PostgreSQL. All must pass.
- After pushing, check the GitHub build yourself. Details: MAP "Testing, in full".

## What can and can't be checked here
- Can: the real Supabase, read-only. Can't (say so, never claim it works): writing to it, the database, real shops,
  iPhone/Safari, how screens feel on a phone, App Links, real notifications. Full lists: MAP.

## Keeping the notes small (owner, 12 November 2026; checked by the stop hook, `tests/notes/limits.sh`)
- `CLAUDE.md` stays under 150 lines and 12,000 characters: move details into `docs/MAP.md` or another doc, never
  the "Rules that must stay true", the safety rules or the interview rules (they stay here whatever the limit).
- `docs/HANDOFF.md` keeps only "Next session" (the current plan) and "Last sessions" (the last 3, each at most 5
  lines); older summaries go to `docs/HANDOFF-ARCHIVE.md`.
- Any other doc in `docs/` (not the `-ARCHIVE.md` files, which are only searched) stays under about 400 lines and
  40,000 characters: split it or archive its old parts.
- Never delete a rule or a decision: only move it, leave a pointer to where it went, and run
  `python3 tests/notes/moved.py <old commit>` to prove every removed line exists word for word elsewhere.
- "Put back the notes from before the tidy-up": MAP "Undoing the notes tidy-up" (commit `ef02546`).
