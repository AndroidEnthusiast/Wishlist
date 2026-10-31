# Wishlist: standing notes for Claude Code

Read this first, then `docs/DECISIONS.md` (what the owner has decided and why: follow it, and add to it whenever the owner decides something new). The long history and the open to-do list are in `docs/HANDOFF.md`; every version's technical notes are in `README.md`.

## Who you are working with
- The owner is a beginner. They work from an Android phone and a Chromebook (browser only), with no terminal or IDE. They write in plain English.
- Explain in short, exact, tap-by-tap steps. Name the real buttons ("Settings", "Secrets and variables", "Actions"). One thing at a time. No jargon unless you explain it in a few words.
- Be honest about testing: always say what you ran and what you could NOT check (see "Cannot be checked here" below).
- No iOS app is planned. iPhone and computer users edit shared lists in the browser editor (`web/edit.html`).

## What this is
An Android wishlist app (Kotlin, Jetpack Compose, Material 3, package `com.wishlist.app`) plus web pages on GitHub Pages plus a free Supabase backend.
- Repo `AndroidEnthusiast/Wishlist`; site `https://androidenthusiast.github.io/Wishlist/`; Supabase URL and the public publishable key are in `app/src/main/java/com/wishlist/app/Config.kt` and at the top of `web/index.html` and `web/edit.js` (they are meant to be public).
- Lists are link-only (never public). Friends open `?c=CODE` and can reserve gifts. The owner and invited editors never see who reserved what.
- Shared lists: an invite link `?j=KEY` lets people edit together and reserve gifts, in the app or in the browser (`web/edit.html`). It is the only link of a shared list (since 2.35).

## How it is built and published
- ONE workflow, `.github/workflows/build.yml`, runs on every push: job `build` (Gradle assembleRelease, signed, then replaces the GitHub release `latest` with `Wishlist.apk`) and job `pages` (publishes the `web/` folder; Settings > Pages > Source must be "GitHub Actions"). The invite page links to that APK.
- The project files live directly in the repository (they used to be inside `wishlist-android.zip`). The workflow still unzips that zip if one exists, so never recreate it or ask the owner to upload one: it would overwrite newer work.
- Change flow (the owner's choice): commit and push straight to `main`. No pull requests and no test builds unless the owner asks. Each push to `main` updates the app download and the web pages. Tell the owner what you changed and when the build is ready (poll `https://api.github.com/repos/AndroidEnthusiast/Wishlist/actions/runs?per_page=1`).
- The app is a release build signed with a private key that is NOT in the project: the workflow decodes the GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and `KEY_ALIAS` while building (`signingConfigs.release` in `app/build.gradle.kts`). Never put a key or its password in the project, and never change the key: an update only installs over the app when it is signed with the same key. (Until version 2.30 the app was signed with a public debug key; that key is no longer used.)
- Supabase scripts (`supabase-*.sql`) and the edge function are run by the owner by hand in the Supabase dashboard. If you change one, say so clearly and give the exact steps. The SQL files are safe to run again.
- Bump `versionName`/`versionCode` in `app/build.gradle.kts` when app code changes, and add one bullet per version to `README.md` in the same style.

## Where things are
- `app/src/main/java/com/wishlist/app/`: `MainActivity.kt` (screens), `Data.kt` (`Wish`, `Repo`), `ListSettings.kt` (list settings and App settings screens), `I18n.kt` + `NlStrings.kt` (language), `Fonts.kt` (font choice), `Shared.kt` + `SharedUi.kt` (shared lists, online saving), `SharedPrices.kt` (price checks for shared lists), `ShareCard.kt` (share picture), `Cloud.kt` (my own list online), `Fetch.kt` + `Browser.kt` (reading product pages), `MostWanted.kt` (star and its effect), `Delete.kt` + `DeleteUi.kt` + `Trash.kt` (delete with Undo), `Bin.kt` + `BinUi.kt` (the bin, 10 days, and the "reserved gift moved to the bin" alert), `Sorting.kt` + `Browse.kt`, `Categories.kt`, `Sizes.kt`, `History.kt` + `PriceHistory.kt` + `ChartMath.kt`, `PriceWorker.kt` + `Alerts.kt`, `Settings.kt`, `Theme.kt`.
- `web/index.html`: the friends' page and the invite page (styled with `edit.css` plus `wishlist.css`, same look as the editor and the app). `web/edit.html`, `edit.js`, `edit.css`: the browser editor. `web/parse.js`: reads a product page. `web/i18n.js` + `web/nl.js`: language and light/dark switch. `web/join/` and `web/list/`: link pages with their own preview pictures for invites and (older) friends' links of shared lists.
- `supabase/functions/fetch-page/index.ts`: the helper that loads a shop page for the browser editor (the browser may not load other sites itself).
- `tests/`: automated checks for everything under `web/` and the helper (see below).

## Rules that must stay true
1. A wish is a JSON object with: id, url, title, desc, image, price, old, cur, cat, got, and only when set: star, note, size, by, bin, binBy, chk (`bin` = in the bin since, see `Bin.kt`, and every list and page must hide wishes with `bin`; `chk` = when a phone last checked the shop price of a shared wish, see `SharedPrices.kt`). A NEW FIELD must be added in all of: `Wish`, `wishToJson`, `wishFromJson`, `Shared.changedFields`, `Shared.applyFields` (app) and `normWish`, `wishJson`, `FIELDS` in `web/edit.js`; also show it on `web/index.html` if friends should see it.
2. `Fetch.kt` and `web/parse.js` do the same job with the same logic (prices, currencies, Amazon, Funda). Change both together and extend `tests/parse/parse_test.js`.
3. Shared lists save one wish at a time and only the fields that changed (`wl_sh_put_item(p_key, p_item, p_fields)`), so two people never overwrite each other. Same field at the same moment: the later save wins. The browser editor (`edit.js`) follows the same rules as `Shared.kt`: change shows at once, saves in the background, a failed save is undone with a message, a refresh answer older than the last local change is ignored, a delete moves the wish to the bin at once (only `bin` and `binBy` are written) and Undo (about 4 s) takes it out again, a picture is deleted online only by the person who replaced it.
4. Reservations on **My wishlist** are fetched only by the friends' page and friends' devices; the owner's app never fetches its own list's reservations (surprise). A **shared wishlist** is only for the givers (the person the gifts are for is not on it), so everyone on it sees and makes reservations (app: `Shared.reserved` / `Shared.reserve`, editor: `loadRes` / `reserve`, both with the existing `wl_get_reservations` / `wl_reserve_item` and the device id). Shared lists have no "most wanted", no "got it" and no price history; they do get price-drop checks and alerts on every phone on the list (`SharedPrices.kt`).
5. Only the phone that created a list can make a new invite link or delete the list.
6. Text on screen: UK English, short and kind, and always also in Dutch. App: write every visible text as `tr("English text")` (placeholders `%s`, `%d`, or `%1$s` when the order differs) and add its Dutch line to `NlStrings.kt`; the language is `Lang` in `I18n.kt` (App settings > Language). Web: `T("English text", values…)` with `{0}`, `{1}`, and the Dutch line in `web/nl.js`; fixed texts in the HTML are translated by their English wording. `tests/i18n/test.js` fails when a Dutch line is missing. Wishes themselves are never translated.

## Testing
- Run before every push that touches `web/` or the helper: `cd tests && npm install && rm -rf node_modules/playwright node_modules/playwright-core && bash run-all.sh` (in Claude Code on the web, use the Playwright and Chromium already installed; elsewhere use `npx playwright install chromium` instead of the `rm`). All suites must say passed. Add or update checks together with changes. Details in `tests/README.md`.
- The Android code is only compiled by GitHub. If you can run Gradle with an Android SDK in your sandbox, try `gradle assembleDebug`; if not, check bracket balance and syntax carefully, say so, and check the build result yourself after pushing (a red cross means: ask them to open the failed job and paste the error lines).
- Earlier sessions also ran local Kotlin harnesses (real `Shared.kt`, delete/undo, sorting, categories) with stand-ins for Android; they are not in this repo.

## Cannot be checked here (say so, don't claim it works)
- Anything on the real Supabase, the real shops (Amazon often blocks server requests), a real iPhone/Safari, or how the Compose screens look and feel on a phone.
- The browser editor's picture upload sends the headers `x-edit-key` and `cache-control` to Supabase storage from another website. If a real browser refuses, the wish is saved without the picture and says so; check Supabase storage CORS.
- The helper function must be deployed and have its JWT check switched off (function Settings, "Verify JWT with legacy secret" off, then Save). Without it, link auto-fill in the browser editor shows "Couldn't read that page".
