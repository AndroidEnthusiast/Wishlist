# Wishlist: handoff and history

Written 30 October 2026 when the project moved from claude.ai chats to Claude Code in the browser. `CLAUDE.md` has the standing rules; `README.md` has one technical bullet per version. This file adds the story, the decisions and the open list, so nothing from the earlier chats is lost.

## What the owner wanted from the start
A simple wishlist app in Material Design with the Google Sans font: save product links (the app reads picture, price, title, description), add by sharing a link to the app or pasting, share the list so friends can reserve gifts, notifications when a price drops, categories, and light / dark / AMOLED themes. The owner builds everything from an Android phone (now also a Chromebook); the code was only ever compiled by GitHub Actions.

## How it grew (version by version, short)
- v1: local app, link parsing, share-to-app, categories, themes, price-drop worker. Amazon needed special handling (blocked pages, price selectors, URL cleanup, fixed signing key so updates install over the old app).
- v2.2 to v2.5: online sharing with Supabase (friends' page `?c=CODE`, reservations the owner never sees), link reading with a hidden in-app browser as fallback (Funda and Etsy handled), views (large, two per row, images only), price alert settings.
- v2.6 to v2.10: side menu, add/edit dialog with auto-fill and own pictures (uploaded as 600 px JPEG to the `wish-photos` bucket), price history on the phone, friends' page with the same views, share sends only the link.
- v2.11 to v2.14: category editing, most wanted star, notes for givers and a note for the whole list, search and sort, sizes (picked from XS to XL, never typed), owner feedback (the star has an ✕, sort button moved next to the category drop-down).
- v2.15 to v2.17: shared lists that several people edit through an invite link `?j=KEY`. Safe concurrent editing (field-level merge in the database). Invite page with "Open in the app" and "Download app" (the APK from the GitHub release `latest`; the page copies the invite to the clipboard so the app can offer to join after an install). One workflow only. Dialogs ignore taps outside.
- v2.18: delete asks first (unless Settings > Instant delete), then 10 s Undo; "Save to" chooser when a link is shared into the app and there is more than one list.
- v2.19: tap on the star plays a pulse and seven tiny sparkles; in the two-per-row view the owner gets a tappable star button on the picture.
- v2.20 (web only, app unchanged at 2.19): the browser editor `web/edit.html` for people without Android. The invite page sends non-Android visitors straight to it. A Supabase edge function `fetch-page` loads shop pages for it. 170 browser checks + others in `tests/`.

- v2.21 to v2.31 (claude.ai chats and the first Claude Code session): App settings vs list settings, price rises shown, Dutch, colour palettes, 14 fonts, friends' page restyled, private signing key.

## Session of 30–31 October 2026 (Claude Code, one long session; details per version in README.md, choices in DECISIONS.md)
- v2.32–2.33: the bin (Menu > Bin, 10 days, everyone on a shared list sees its bin; delete moves to the bin at once, short "Wish moved to the bin" + Undo; alert "Your reserved gift has been moved to the bin" opens the bin at that gift with a glow). Safer loading (`SafeStore`). Shared price checks split between phones (`chk`). "Download app" button in App settings (2.37).
- v2.34: "Images only" view removed. v2.35: a shared list has one link, the invite. Web: invitation page also on iPhone/computers (only "Edit in the browser"), fits on one screen, confetti up to the top, language/theme carried to the editor, `?v=` cache-busting after a browser mixed old and new files.
- v2.36: private wishes (switch, eye icon, never online; "Save to" option; a friend who reserved a gift that disappears is nudged to pick another, `FriendWatch` / friends' page note).
- v2.38–2.39: "Most wanted" → "Love it" (pink heart) → undone → "Top wish" (gold star, bold words).
- v2.40–2.44: full-screen picture viewer, tap words → shop page, wish form reordered (Name, Category drop-down, Top wish, Private wish, then "Other information" folded away), tappable private eye with 5 s Undo, new delete wording, form texts tuned with the owner, price field shows the currency symbol, "Fetch details" button removed, wallpaper colours (Monet) removed.
- Working style settled this session: push straight to `main`; end every message about a finished build with **App available for download** on the last line.

## Next session: theme designs (the owner's next topic)
The owner wants a few really nice-looking, clearly different designs to pick from (not just colours), each with a choice of colour combinations. Start point: an "organic" design that an earlier chat on claude.ai once made and the owner liked. The owner shared it at the end of this session: it is described, with all its colours, fonts and shapes, in `docs/designs/organic/README.md` (with a copy of its source; the original canvas is https://claude.ai/artifact/25P97s8scneAwjrmwUz4hy). It is a mock-up from before some changes (it still says "Most wanted" and has "Images only"), so take only the look from it. Show previews (HTML mock-ups) before building, as DECISIONS asks. Today the app has: light/dark/system/AMOLED (palettes still defined in `Theme.kt` but not offered), 14 fonts (`Fonts.kt`); the web pages only have light/dark. Material You wallpaper colours were removed in 2.44 and the whole Colours section (the six palettes) in 2.50: App settings > Theme only has System / Light / Dark / AMOLED black, and the app is always indigo. Don't bring the old colour choices back; colour choices come back only as part of the new designs.

## Decisions and why
- Lists are link-only, never listed publicly. Reservations are hidden from the owner and from editors so gifts stay a surprise.
- Shared lists are online-first: no internet means the change is undone with a message (simple and safe). Same field changed by two people at the same moment: the later save wins.
- Only the creating phone can reset the invite link or delete the list; the browser editor therefore has no such buttons.
- One GitHub workflow for build, release and web pages. No second workflow (an earlier idea caused confusion).
- The Supabase URL and publishable key are public on purpose.
- The browser editor reads shop pages via the helper function, because browsers may not load other sites. The page itself parses the HTML (`web/parse.js`).

## State on 30 October 2026 (start of the Claude Code work; see the session above for what followed)
- Owner deployed the edge function `fetch-page` in the Supabase dashboard (editor, function named `fetch-page`) and switched OFF "Verify JWT with legacy secret" in its Settings, saved. NOT yet confirmed working with a real shop link.
- The owner was about to upload the zip with the browser editor to GitHub (and had said they might not have done so yet). Check that `web/edit.html` exists on `main` and that the Actions run is green before anything else.
- From now on work happens here in Claude Code; the zip upload routine is retired.

## Open items, most important first
1. Done in v2.33: the data-loss risk in `Repo.load` (a list that couldn't be read was taken as empty and then saved over). Now `SafeStore.readList` keeps the unreadable text and falls back to the last good copy. The bin was built in v2.32. Nothing yet rescues an `_unreadable` copy automatically; a later version could offer that.
1a. Bin follow-ups: the browser editor has no bin screen yet (its deletes do go to the bin); the invite page's wish count (`wl_sh_info`) still counts wishes in the bin; phones with an app older than 2.32 still show wishes that are in a shared list's bin until they update.
2. Confirm the helper function works with a real shop link; confirm browser picture upload works against the real Supabase (see `CLAUDE.md`, "Cannot be checked here"); try the editor on a real iPhone.
3. Amazon prices: patched several times, never confirmed on the owner's phone; sometimes blocked.
4. Ideas not built (price-drop checks for shared lists exist since 2.2x and share the work between phones since 2.33): several own lists, group gifts (split the price), backup and restore, a Dutch version (the price-word parsing already knows Dutch words), owner-only actions in the browser.
5. Cosmetic: the browser editor and the invite page were checked only in Chromium (phone, tablet and desktop sizes, light and dark).

## How the tests relate to the history
Everything under `tests/` was written in the browser-editor session and runs without any real service. Earlier Kotlin harnesses (concurrent editing, delete/Undo, sorting, categories, invite parsing) used a local Kotlin compiler with hand-written stand-ins for Android and Compose; they were not kept. The Compose screens have only ever been compiled by GitHub.
