# Map: where things are and how they are built

The details that used to be in `CLAUDE.md` (moved here on 12 November 2026, notes tidy-up). Read only the part for the
area you work on: `CLAUDE.md` says which. Nothing here overrides `CLAUDE.md`.

## What this is
An Android wishlist app (Kotlin, Jetpack Compose, Material 3, package `com.wishlist.app`) plus web pages on GitHub Pages plus a free Supabase backend.
- Repo `AndroidEnthusiast/Wishlist`; site `https://androidenthusiast.github.io/Wishlist/`; Supabase URL and the public publishable key are in `app/src/main/java/com/wishlist/app/Config.kt` and at the top of `web/index.html` and `web/edit.js` (they are meant to be public).
- Lists are link-only (never public). Friends open `?c=CODE` and can reserve gifts. The owner and invited editors never see who reserved what.
- Shared lists: an invite link `?j=KEY` lets people edit together and reserve gifts, in the app or in the browser (`web/edit.html`). It is the only link of a shared list (since 2.35).
- No iOS app is planned. iPhone and computer users edit shared lists in the browser editor (`web/edit.html`).

## How it is built and published
- ONE workflow, `.github/workflows/build.yml`, runs on every push: job `build` (Gradle `testReleaseUnitTest assembleRelease`: the app's own checks first, a failing one stops the update; signed, then replaces the GitHub release `latest` with `Wishlist.apk`) and job `pages` (publishes the `web/` folder; Settings > Pages > Source must be "GitHub Actions"). The invite page links to that APK. Two more workflows only run on a schedule (never on a push): `keep-awake.yml` (every 2 days, one tiny read so the free Supabase project never pauses) The weekly backup lives in the PRIVATE repository `AndroidEnthusiast/Wishlist-backups` since 11 November 2026 (database + pictures, restored into a throw-away database as a check, locked with `BACKUP_PASSWORD`, kept 60 days; restoring: `docs/BACKUPS.md`); it is not in this repository any more.
- Scripts and styles in `web/*.html` are linked with `?v=dev` (for example `edit.js?v=dev`); the `pages` job replaces it with the commit, so browsers never mix an old saved file with a new page. A new script or stylesheet must be linked the same way. The pages `edit.html`, `group.html`, `exchange.html` and `index.html` carry a Content-Security-Policy (since 4 November 2026): only script files from this site run (no inline `<script>` code or `onclick=`; `theme.js` sets light/dark first, `friends.js` is the friends'/invite page's code), and they may only connect to our Supabase. A new page needs the same `<meta>` lines; `tests/csp/test.js` checks it.
- The project files live directly in the repository (they used to be inside `wishlist-android.zip`). The workflow still unzips that zip if one exists, so never recreate it or ask the owner to upload one: it would overwrite newer work.
- **Push only to `main`, always** (the owner's standing instruction, unless they say otherwise in that session). This also applies when the session was started with its own branch (for example `claude/...`) or its instructions say to develop on one: work and commit on any local branch you like, but run `git push origin HEAD:main` and never push to another branch, create branches on GitHub, or open pull requests. Every push to another branch starts the workflow there, its `pages` job is refused (GitHub Pages only deploys from `main`) and the owner gets a "build failed" e-mail. At the start of every session, work on a local branch called `main`: run `git fetch origin main && git checkout -B main origin/main`. The session's stop hook compares the local branch with the GitHub branch of the same name, so on a `claude/...` branch it keeps asking to push there (the owner asked for this to stop, 1 November 2026); on `main` it is satisfied once you have pushed.
- Change flow (the owner's choice): commit and push straight to `main`. No pull requests and no test builds unless the owner asks. Each push to `main` updates the app download and the web pages. **After every update, give the owner a changelog: a bullet list of everything that changed** (owner's standing instruction, 4 November 2026: "so I know what's changed"). Tell the owner what you changed and when the build is ready, with the words **App available for [download](https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk)** in bold as the very last line of your message once the app build has succeeded (the word "download" is a link straight to the newest APK, so the owner can install it from the chat) (poll `https://api.github.com/repos/AndroidEnthusiast/Wishlist/actions/runs?per_page=1`). **Always end a message about a finished build with that download line** (owner, 11 November 2026: "always end a build by sending me a link to the build"; this replaces the rule of 8 November to leave it out during the roadmap), right after the guidance on what to test and do next.
- The app is a release build signed with a private key that is NOT in the project: the workflow decodes the GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_ALIAS` while building (`signingConfigs.release` in `app/build.gradle.kts`). Never put a key or its password in the project, and never change the key: an update only installs over the app when it is signed with the same key. (Until version 2.30 the app was signed with a public debug key; that key is no longer used.)
- Supabase scripts (`supabase-*.sql`) and the edge function are run by the owner by hand in the Supabase dashboard. If you change one, say so clearly and give the exact steps, **always with a direct link to each script on GitHub** (owner, 6 November 2026), for example [supabase-features.sql](https://github.com/AndroidEnthusiast/Wishlist/blob/main/supabase-features.sql), in the order they must be run (the page's copy button copies the whole script). The SQL files are safe to run again. Tell the owner the order every time: **run the script first, then install the new app** (the scripts only add things, so the old app keeps working). If a Supabase connector is attached to the session, still show the SQL and ask before running it.
- Bump `versionName`/`versionCode` in `app/build.gradle.kts` when app code changes, and add one bullet per version to `README.md` in the same style.

## App code, by area

All in `app/src/main/java/com/wishlist/app/` (each item as it was listed in `CLAUDE.md`).

### Frame, screens and moving between them
- `MainActivity.kt` (only sets up the app and reads Intents, since 2.163)
- `AppScreen.kt` (`App`: routes, bottom bar, app-wide pop-ups)
- `Deep.kt` (every way into the app: notifications, links, shared text → `deepOf` → `App.go`)
- `Places.kt` (since 2.193: where you are inside the tabs, owned by `App` with `rememberSaveable` and read as `LocalPlaces`; `FriendCache`, a friend's loaded lists; `ChatOnScreen`, the chat on screen for the message check, 2.194). Screens open other screens with `LocalGo` (`Deep.kt`, 2.192).
- `Nav.kt` (screens as routes, Navigation Compose: Profile, App settings, Bin, Notifications, Received gifts, Gifts I reserved, Price changes, a list's settings, a list opened from its link, the tabs Messages/My shops/Friends, a wishlist group, Gift exchanges, Home, the open list: every screen since 2.160; ROADMAP Stage 1). The app's checks never reach the real server (`Cloud.noServerInChecks`) and run with animations off
- `KeepPlaces.kt` (an open chat, a friend's page, a gift exchange and Home's page come back after Android closed the app; new screen state goes in the route or `rememberSaveable`, never in a new global object) *(Removed in 2.193: where you are inside a tab now lives in `Places.kt`, owned by the screen; moving between screens goes through `LocalGo` in `Deep.kt`, 2.192.)*

### Your lists and wishes
- `ListScreen.kt` (the open list)
- `WishCards.kt` (wish cards)
- `WishForm.kt` (Add/Edit wish)
- `Data.kt` (`Wish`, `Repo`)
- `Db.kt` + `DbMove.kt` + `ListStore.kt` (the Room database: since 2.171 the only source of your lists, wishes, bin, price history, friends' lists and notifications; the settings store keeps an untouched old copy and is written again only if the database can't be read; ROADMAP Stage 2)
- `ListSettings.kt` (list settings and App settings screens)
- `Hub.kt` (the dashboard, the start screen) + `MyLists.kt` (several wishlists of your own: the open one lives in Repo's fields, the others are stored)
- `ListSharing.kt` (who can see a list, `ListVisibility`)
- `MostWanted.kt` ("Top wish": the star, its gold label and effect)
- `Delete.kt` + `DeleteUi.kt` + `Trash.kt` (delete with Undo)
- `Bin.kt` + `BinUi.kt` (the bin, 30 days, and the "reserved gift moved to the bin" alert)
- `Private.kt` (private wishes, the eye icon)
- `Sorting.kt` + `Browse.kt`
- `Totals.kt` (Total value)
- `Categories.kt` (Edit categories, from the wish form)
- `Sizes.kt`
- `Owned.kt` ("Wish marked as owned")
- `Occasion.kt` + `OccasionUi.kt` (a list's occasion, "Birthday · in 3 weeks"; same rules as `web/occasion.js`)
- `Quantity.kt` + `QuantityUi.kt` ("2×", reservations with a number, the wish form's How many / Exactly this; same rules as `web/reserve.js`)
- `Move.kt` (Move to… and selecting several wishes; reservations move along with `wl_move_item`) + `MoveAcross.kt` (to and from collab wishlists, 2.181: leaving one asks Move or Copy, Move goes through that list's bin, a wish reserved by someone else stays)
- `Rescue.kt` (the stop screen when the lists can't be opened, `ListStore.stale`: Get my lists back with `wl_mine_get`, Try again, Import)

### Prices and shops
- `SharedPrices.kt` (price checks for shared lists)
- `Fetch.kt` + `Browser.kt` (reading product pages)
- `History.kt` + `PriceHistory.kt` + `ChartMath.kt`
- `PriceWorker.kt` + `Alerts.kt` + `Background.kt` (background checks)
- `PriceReport.kt` ("Price is wrong?" on a wish's price page: the shop's page as the phone reads it, saved to a file for the share sheet; a file the owner uploads goes into `tests/shared/same-answers.json` → `pages` with the fix in both readers)
- `PriceWatch.kt` (when a price check believes what it read, 2.178: two readings that agree, a quiet first look, typed prices stay; its notes per wish are the database table `price_watch`)
- `Shops.kt` (Find a gift: the shop row on Home and the in-app shop with Add to wishlist; `CONSENT_JS` refuses cookie questions)

### Collab wishlists (gift lists in the code)
- `Shared.kt` + `SharedUi.kt` (shared lists, online saving)
- `ShareCard.kt` (share picture)
- `Members.kt` (gift list members and a new owner)
- `CollabPeople.kt` (the people circles on a collab wishlist's block; `supabase-collab.sql`)
- `GiftInvites.kt` (inviting Friends to a gift list, and `News`: the server's news for this phone, pushed at once; `supabase-together.sql`)
- `Asks.kt` (stuck reservations: "Is it still being bought?")
- `Reserved.kt` ("Gifts I reserved", side menu)

### Your lists online and syncing
- `Cloud.kt` (my own list online)
- `MineSync.kt` (your own lists online in small saves: fingerprints of what the server has, `wl_mine_put`)
- `Live.kt` (live updates through Supabase Realtime, polling as fallback)
- `Pulse.kt` (the half-hourly check asks `wl_pulse` what changed first); all four ROADMAP Stage 3
- `supabase-sync.sql`. On screen "gift lists" are called **Collab wishlists** (since 2.146); in the code they are still `Shared`/gift list.

### Friends, chat, groups and gift exchanges
- `FriendWatch.kt` ("a gift you reserved is no longer on the list")
- `Chat.kt` + `Messages.kt` (encrypted chat) + `ChatPhotos.kt` (photos in chat, locked with their own key) + `Push.kt` (instant message notifications, Firebase)
- `Groups.kt` + `GroupsUi.kt` (wishlist groups: people who each bring one wishlist; `supabase-groups.sql`)
- `Exchange.kt` + `ExchangeUi.kt` (Gift exchange / Secret Santa: draw on the server, rules, anonymous chats; `supabase-exchange.sql`)
- `Reactions.kt` (reactions on wishes, the emoji picker, `LocalWishDeco`; emoji names in `assets/emoji.txt`)

### Notifications, reminders and background work
- `NotifyPace.kt` (every notification goes through it: bundles, one buzz per 3 minutes, stacked)
- `Reminders.kt` (the givers' 7-day reminder, "What did you get?", Received gifts)

### Look, text and fonts
- `I18n.kt` (`tr`, English only)
- `Fonts.kt` (font choice)
- `Settings.kt`
- `Theme.kt` + `Design.kt` + `Styled.kt` + `Themer.kt` (themes and the themed buttons, dialogs, icons)
- `Y2k.kt` (2.203): Y2K's sub-themes, glossy parts, sparkle icons (`Y2kIcons`), stickers
- `Doodle.kt` (2.202): Doodle's sub-themes, hand-drawn parts and icons (`DoodleIcons`), options (`DECO_LEVELS`, `CARD_STYLES` in `Design.kt`); speed rules: `PerformanceRulesTest`
- `Themed.kt` (2.199): the themed text fields, menus, tick boxes, choice circles, chips, spinners, progress bars, dividers, sliders, bottom panels, message bar and `Scaffold` (the style's drawing behind every screen); `Placeholders.kt` draws each style's emblem and Anthropomorphic's gift box with a face (`giftBuddy`, `lookPeek`); checks `LookTest`, `PartsScreenshotTest`
- `themes/`: frozen copies of themes the owner wants to be able to bring back (Gen Z chaos, 13 November 2026), each with a README on putting it back; checked by `tests/themes/check.sh`

### Server, accounts, safety and app health
- `AppInfo.kt` (the "new version is ready" bar on Home and the "Please update" screen; reads `app.json` from the site, which the `pages` job writes: `min` in `web/app.json` is the oldest versionCode that still works, raise it only when an old app would really break)
- `Crash.kt` + `WishlistApp.kt` (crash reports, Firebase Crashlytics, started first)
- `Calls.kt` (server calls counted per day, App settings > About)
- `PeoplePhotos.kt` (profile, group and exchange photos as files in the Storage bucket `people-photos`, ROADMAP S4.1: a new random address per photo, the server hands out the address or an older embedded photo)
- `RecoveryKey.kt` (the recovery key: Profile, the welcome's step 4 and a Home card; the locked copy of the Export plus chat keys in `wl_vaults`, `supabase-vault.sql`; the key never goes online)
- `Opened.kt` ("the app was opened", once a day, for the clean-up of `supabase-cleanup.sql`; lists removed online after a year come back by themselves)
- `ServerNotice` in `Cloud.kt` ("Please try again in a moment" when `supabase-limits.sql` refuses: limits against abuse, ROADMAP S4.2; run that script after all the others)

## Web pages
- `web/index.html` + `friends.js`: the friends' page and the invite page (styled with `edit.css` plus `wishlist.css`, same look as the editor and the app). `web/edit.html`, `edit.js`, `edit.css`: the browser editor (its Bin can only restore, by the owner's choice). `web/parse.js`: reads a product page. `web/occasion.js`: a list's occasion line (friends' page and editor). `web/reserve.js`: quantity labels and "How many will you buy?" (friends' page and editor). `web/i18n.js`: `T()` (English only) and the light/dark switch. `web/join/` and `web/list/`: link pages with their own preview pictures for invites and (older) friends' links of shared lists. `web/group.html` + `group.js` + `group.css` (+ `web/group/`, its link preview): a wishlist group for people without the app. `web/exchange.html` + `exchange.js` + `exchange.css` (+ `web/exchange/`, its link preview): a gift exchange for people without the app (organising is app-only). `web/together.js` (+ `web/emoji.js`, loaded only when the picker opens): reactions and "Is it still being bought?" on the friends' page, the group page and the editor (styles in `edit.css`).

## Server helpers
- `supabase/functions/fetch-page/index.ts`: the helper that loads a shop page for the browser editor and the group page (the browser may not load other sites itself; it accepts a gift list's edit key or a group link). `supabase/functions/push/index.ts`: wakes a friend's phone for a new message, and (2.129) for an anonymous gift-exchange message (`{gx: id}`) (needs the secret `FCM_SERVICE_ACCOUNT`, Verify JWT off).

## Checks (tests)
- `tests/`: automated checks for everything under `web/` and the helper (see below); `tests/shared/same-answers.json` is answered by both the app and the web pages. `app/src/test/`: the app's own checks (JUnit).

## The notes themselves
- `tests/notes/limits.sh`: the notes' size limits (`CLAUDE.md` "Keeping the notes small"); `.claude/settings.json` runs it as the session's stop hook (`--hook`: exit 2 keeps the session from finishing while a limit is passed or the notes are not pushed to `main`).
- `tests/notes/moved.py <commit>`: proves a tidy-up lost nothing (every removed line exists word for word elsewhere).

## Testing, in full
- GitHub runs `tests/run-all.sh` too (job `web-checks`, owner 10 November 2026), with a throw-away PostgreSQL so the database checks (`tests/sql/run.sh`) and the page suites that need the real scripts run as well, and only publishes the web pages when it passes; if that job is red, fix it before anything else. A changed `supabase-*.sql` script: run `bash tests/sql/run.sh` here with `WL_PG` first (start a PostgreSQL as the user `postgres` in a folder under `/tmp`, see `tests/README.md`).
- Run before every push that touches `web/` or the helper: `cd tests && npm install && rm -rf node_modules/playwright node_modules/playwright-core && bash run-all.sh` (in Claude Code on the web, use the Playwright and Chromium already installed; elsewhere use `npx playwright install chromium` instead of the `rm`). All suites must say passed. Add or update checks together with changes. Details in `tests/README.md`.
- Android (owner, D1, 8 November 2026: on for the rebuild, Stages 1–3): the session environment "Wishlist" allows `dl.google.com`. Run `bash tests/android-setup.sh` (about 1 minute), then `gradle testReleaseUnitTest` before every push that changes app code (it also compiles the app), and `gradle assembleDebug` when resources or libraries change. The GitHub build runs the same checks before making the APK. Screenshot tests only with `-Pwl.screens` when the owner asks. Still check the build result yourself after pushing (a red cross means: open the failed job, or ask the owner to paste the error lines).
- Earlier sessions also ran local Kotlin harnesses (real `Shared.kt`, delete/undo, sorting, categories) with stand-ins for Android; they are not in this repo.

## What can and can't be checked, in full
### Can be checked here (since 8 November 2026)
- The real Supabase, read-only: the environment "Wishlist" allows `aspnmvmsvwbezdltykgx.supabase.co` (owner offered it). Use only the public key, and only GET calls to `/rest/v1/rpc/...` (they run read-only, so a write is refused) or functions with a wrong key. Checked on 8 November 2026: every `supabase-*.sql` script is installed (only trigger functions don't show, by design) and the helper `fetch-page` is deployed with its JWT check off (it answers `bad_request` itself).

### Cannot be checked here (say so, don't claim it works)
- Writing to the real Supabase (never done without asking), the database itself (no password here), the real shops (Amazon often blocks server requests), a real iPhone/Safari, or how the Compose screens look and feel on a phone.
- The browser editor's picture upload sends the headers `x-edit-key` and `cache-control` to Supabase storage from another website. If a real browser refuses, the wish is saved without the picture and says so; check Supabase storage CORS.
- App Links: Android only opens our links in the app after it has checked `https://androidenthusiast.github.io/.well-known/assetlinks.json` (repository AndroidEnthusiast.github.io, a copy is in `docs/app-links/`). This sandbox can't reach github.io, so check it in a browser. The fingerprint is the release key's (read from the published APK); never change the key.
- The helper function must be deployed and have its JWT check switched off (function Settings, "Verify JWT with legacy secret" off, then Save). Without it, link auto-fill in the browser editor shows "Couldn't read that page".

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
Replaced by shorter lines when `docs/TODO.md` was added; the full versions are above in this file.
- Before every push that touches `web/` or the helper: `bash tests/run-all.sh` (setup in MAP "Testing, in full");
- Can: the real Supabase, read-only (public key, GET calls to `/rest/v1/rpc/...`, functions with a wrong key).
- Can't (say so, never claim it works): writing to the real Supabase, the database, real shops, iPhone/Safari, how
  screens feel on a phone, App Links, real notifications. Details: MAP.
- **Lists, wishes, Home, anything on screen:** MAP "Your lists and wishes" + `docs/decisions/screens.md` +
- `docs/decisions/screens.md`: Screens, look, wishes and prices (Look and feel; Private wishes; Top wish; Wishes on screen and the wish form; Prices; App review; Premium identity; Owner's list of 3 November 2026; Settings review; Home tiles, Collab wishlists, moving wishes, notifications and web search).
