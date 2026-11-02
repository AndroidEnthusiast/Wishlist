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

## Session of 31 October 2026: theme designs (v2.51)
- The owner asked for at least four themes, each with at least five colour sets, based on reference pictures: Organic, Neobrutalism (a pastel dashboard with thick navy lines), Claymorphism (a soft lavender "Clay Dashboard") and Material 3 (Android 16 screens). Built straight away (the owner asked to build, not for a mock-up first).
- App settings > Theme: the current design and colour set with a **Themer** button, then light/dark. The Themer screen: a sample wishlist at the top (its view buttons switch between two per row and large cards), then Design (four, each with a small sample card), Colours (six per design) and Light or dark, plus "Back to the original look". Choices apply at once.
- What changes per design: wish card frames (shape, lines, hard or soft shadows, card colours in turn), picture mats, badges, the Add wish button, the category and view buttons, the background behind the list (organic blobs, clay glows, neo dot grid), dialog/menu shapes and the font of list names, headings and prices. Not yet styled per design: the side menu, the wish form's fields and the other screens' own buttons (they do take the colours and shapes).
- Not checked: the Android code was not compiled here (no Android SDK in the sandbox); how it looks on a real phone.

- v2.52, same day, after the owner's feedback: the menu button overlapped the Themer's back arrow (fixed: the Themer has only a back arrow). The owner wanted the classic themes (System/Light/Dark/AMOLED, font) kept exactly as before and the four designs as extra themes with their own colours, and much more of each style (fonts, icons, everything, the whole app). Done with `Styled.kt` (themed buttons, switches, dialogs, top bars, icons, labels) and a fuller `Design.kt` (fonts, a dark colour set per design). Not compiled here (no Android SDK); checked on GitHub.

- v2.53: the owner removed the Font setting and "System default" (the first start picks Light or Dark quietly), put "Standard themes" + the Themer button under Theme, and asked for a bigger Themer preview called "Preview" with a fixed top, and the designs in a sideways row whose colour sets pop up when tapped.

- v2.54: App settings tidied for the owner (name saves with the keyboard's tick and shows "Saved"; Themer text; Price alerts as a row of boxes plus a "Notifications" sub-heading; the four buttons removed).

- v2.55: "Saved" feedback with a drawn tick that fades after a second, a fixed-width name field; Standard in the Themer pops up the three standard themes; fixed the jumping design tiles (different heights).

- v2.56: the Themer only previews; an "Apply" button top right makes the choice the app's theme.

- v2.57: wish form wording under Top wish; no flash when opening "Other information".

- v2.58: the owner found Claymorphism too close to Material 3; clay now draws real inflated pillows (inner shadows, soft drop shadow) on everything. The "build failed" e-mails came from pushing to the session branch too (GitHub Pages only deploys from main): push only to main.

- v2.59: colour sets in the Themer no longer jump while swiping (fixed height). Work now happens on a local branch `main`, pushed only to `main`.

- v2.60: Organic no longer shows the number of wishes above the list name.

- v2.61: pull to refresh no longer keeps the circle spinning while every shop is asked (Marktplaats and others are read slowly in a hidden browser); the check continues in the background.

- v2.62: one-time background question (Android Unrestricted) after alerts only arrived on opening the app; Neobrutalism Midnight cards bright with dark text.

- v2.63: shop photos as small rounded cards on a theme-coloured picture area (owner chose this over cropping or a blurred background).

- v2.64: dashboard (start screen) with hero picture, Recently opened, My wishlists and Group wishlists; several wishlists of your own; "Group wishlists" name; no default category.

- v2.65: bottom bar (Home, New list, Add wish, Prices, Friends placeholder), Prices page, hero picture editor, colour sets for the standard themes, dashboard details. Proposed to the owner and waiting for an answer: the wording of the Notifications page (price drops and rises, with a red dot in the menu): options A "€10 cheaper than when you added it. Now €79.", B "11% cheaper since you added it (€89 → €79)", C "Good news: down to €79 (was €89)"; Claude recommended A.
- v2.66: copying wishes from your other lists (in the New wishlist form and list settings), removing an original removes its copies; "Wish added on dd/mm/yy" on every wish.

- v2.67: Notifications page (the owner's own wording: picture, name, "decreased/increased in price", bold %), red dots; "Added on Thursday 01-12-26".

- v2.68: "cover photo" with an editor like Marktplaats's (rotate, crop, light, text with colour and position, new photo).

- v2.69: cover photo editor: move and zoom at once with the outside dimmed, colour ball, free text placement.

- v2.70: cover photo editor with a movable, resizable crop frame over the whole photo; text resizable by its corners.

- v2.71: editor black screen fixed, "No price change yet", readable Neobrutalism text (accent colour only for blocks), "New wishlist" gift icon.

- v2.72: licences page (Material Icons, OFL fonts, Apache libraries with full texts); self-drawn "New wishlist" icon. Checked: every icon in the app is Material (Apache 2.0), self-drawn or emoji; Google Sans is the OFL release (2025). Not checked: where the web pages' pictures (icon.png, og*.png) came from (made in earlier sessions).

- v2.73: the cover crop frame keeps its size; only the photo zooms.

- v2.74: cover editor: the photo stands still, only the frame moves, pinch zooms around the frame; the saved cover is cut out exactly (the owner saw a different part on the dashboard in 2.73).

- v2.75: cover editor rebuilt: fixed frame, move and zoom the photo; root cause of the mismatch found (the photo was drawn in a box limited to the screen size).

- v2.76: "New wishlist" label in full, no "· Private wish" in Save to, private eye bottom left. The owner confirmed the cover editor of 2.75 works.

- v2.77: target prices (price history), View product, both prices on the Prices tab, smooth folding of Other information, picker layout, capital letters in text fields. Ideas offered for Prices but not chosen yet: a summary at the top, a trend line per row, sorting chips, "checked … ago", a warning when a shop can't be read.

- v2.78: version number in the menu, clearer texts in the New wishlist choice, no total value on Recently opened cards.

- v2.79: long press a list on Home (Edit name, Delete wishlist / Leave wishlist); deleting your own list sends its wishes to the bin, restore asks where; "Price changes" on Home.

- v2.80: menu items no longer scroll; target price row under "Tracking since…" (save with the keyboard's tick, ✕ removes), never gold straight away.

- v2.81: target price shows as bold text once saved (frame gone, green tick), ✕ brings the field back, short line below.

- v2.83: plus and minus instead of arrows for price changes, app and web.

- v2.84: one highlighted item in the menu; swipe notifications away (Undo); every bottom message 5 s and swipeable.

- v2.85: small long-press menu on Home lists; delete all notifications (bin icon, Yes/No, Undo).
- v2.86: swiping a notification shows a red block with a bin icon as you slide.

## Session of 1 November 2026: Friends (v2.87)
- Talked Friends and Groups through with the owner (choices in DECISIONS.md, "Friends and Groups"), then built Friends: Profile (photo, name, friend code), friend requests with approval, the Friends tab, a friend's page and lists, "Hide my lists from …", "Unfollow", "Visible to friends" per list, notifications for requests and approvals.
- v2.88 (same day, owner's requests): round profile photo editor (the cover editor with a circle), profile picture top right on Home/Prices/Friends, "Menu" as the menu heading, "Your name" removed from App settings, list name saves with the keyboard tick and "Saved".
- v2.89 (same day): Home reorganised (profile summary with numbers and their pages, no cover card, newest wish as a list's picture), summary card and optional cover photo (with Preview) per list. Feedback that started it: testers didn't understand the Home cover photo.
- v2.90–2.94: copies count once, unstar asks first, summary card on own lists only; Home blocks two per row; tapped wish scrolls and glows (several fixes after the owner's screen recording).
- v2.96–2.97: depth, then a premium identity: three directions previewed (Keepsake, Quiet, Bold), owner chose Quiet with Neobrutalism, Neumorphism, Cyberpunk and Gen Z chaos styles (previews in DECISIONS.md). Fallback to v2.96 (commit 123322e) if the owner doesn't like it.
- v2.95: a full app review, interviewing the owner with pop-up questions (now a standing rule in CLAUDE.md); all answers in DECISIONS.md "App review"; everything built in one update (owner's wish).
- v2.98: Messages (end-to-end encrypted chat with friends, `supabase-chat.sql`, checked with `tests/sql/chat_test.sql`). v2.99: four chat layouts (Chat, Calm, Text, Round) in Messages ⋮ and App settings, always in the theme's colours.
- v2.100: "Added on" removed from wish cards; Blur stage pictures (preview first, owner chose); two per row is the default for new users.
- New `supabase-friends.sql` (the owner runs it by hand); checked on a local PostgreSQL with `tests/sql/friends_test.sql` (32 checks). The Android code could not be compiled here (no Android SDK); checked by hand and by the GitHub build.

## Next session
- **Ask how the Blur stage pictures look on the phone** in all five styles (especially the Neumorphism dent and dark mode) and on the web pages. Not seen here: the blur on Android 11 and older (a stretched tiny copy instead of a real blur) and real shop photos.
- **Check Messages**: did the owner run `supabase-chat.sql`? Without it, Messages says it can't reach the server. Ask how chatting feels between two phones (polling delay, ticks, delete) and how the four chat layouts look; none of that could be seen here.
- **First: ask how the new look (v2.97) feels** in all five styles, light and dark. If the owner dislikes it, revert the look to v2.96 (commit 123322e) as promised. Could not be checked here: how the fonts download (Google Fonts via Play services), neumorphic shadows on older phones, and the chaos tilt on long lists.
- **Check first**: did the owner run the updated `supabase-friends.sql` (birthday, "I like…", "Who can see this list")? Without it: birthday/I like don't save online and "My friends" can't switch the link off. Then ask how 2.95 tests on the phone: welcome (fresh install only), Who can see this list, categories shared by all own lists, birthday reminders, friend's page, Export/Import (try on a second phone), Notifications page.
- Not built from the review: nothing knowingly left out. The navigation cleanup was kept small (one `closeAll()`); a full one-screen-state rewrite is still possible if odd Back behaviour shows up.
- Later ideas: "Wishlist sharing group" (friends in a group see each other's wishlists; the name "Group" is reserved for it); recovery without a file (online backup) not needed now that Export/Import exists; a tap-to-add link for friend codes.
- Other open points: ideas for Price watch not chosen yet (summary at the top, trend line per row, sorting chips, "checked … ago", a warning when a shop can't be read).

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
