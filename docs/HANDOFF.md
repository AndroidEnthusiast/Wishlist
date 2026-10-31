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

## Decisions and why
- Lists are link-only, never listed publicly. Reservations are hidden from the owner and from editors so gifts stay a surprise.
- Shared lists are online-first: no internet means the change is undone with a message (simple and safe). Same field changed by two people at the same moment: the later save wins.
- Only the creating phone can reset the invite link or delete the list; the browser editor therefore has no such buttons.
- One GitHub workflow for build, release and web pages. No second workflow (an earlier idea caused confusion).
- The Supabase URL and publishable key are public on purpose.
- The browser editor reads shop pages via the helper function, because browsers may not load other sites. The page itself parses the HTML (`web/parse.js`).

## State on 30 October 2026
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
