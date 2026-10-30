# Tests for the web pages and the helper function

None of these touch the real Supabase or real shops. They use a pretend Supabase (`e2e/fake.js`), a real headless Chromium (Playwright), jsdom and real Deno.

Run everything (from this folder):

    npm install
    npx playwright install chromium
    bash run-all.sh

The last line must say `ALL SUITES OK`. Each suite also exits with an error code when something fails.

| Folder | What it checks | Checks |
|---|---|---|
| `parse/parse_test.js` | `web/parse.js` reads product pages (prices, currencies, Amazon, JSON-LD, Funda, blocked pages); mirrors `Fetch.kt` | 54 |
| `i18n/test.js` | Every text in the app (`tr`) and the web pages (`T`, fixed HTML texts) has a Dutch version with the same placeholders | 5 |
| `function/test.ts` | `supabase/functions/fetch-page/index.ts` in real Deno: private addresses, redirects, size and time limits, wrong keys | 100 |
| `index-page/test.js` | `web/index.html` in jsdom: friends' page, invite page (Android vs. other devices), clipboard copy | 140 |
| `e2e/t1.js` to `t6.js` | The browser editor `web/edit.html` in Chromium: viewing, the app-style top bar, side menu, category drop-down and sort menu, the three views, adding by link or by hand, editing one field, reservations (Reserve/Undo, someone faster, offline), delete/Undo, categories, list name and note, share dialog and the share picture with the list name, failed saves, sorting, search, offline copy, pictures, two people at once, old refresh answers, iPhone tip, invite redirects, the link-preview pages join/ and list/ and the friends' page of a shared list | 37 + 32 + 29 + 38 + 34 + 42 |

Notes
- `e2e/lib.js` serves the real `web/` folder and starts the pretend Supabase; `boot({...})` options: `items`, `about`, `name`, `key`, `ua`, `dark`, `reduced`, `clock`, `goto`, `viewport`, or `fake` to share one pretend server between two browser windows.
- A suite that fails prints `FAIL <name>`. Fix the code, or the check if the behaviour was changed on purpose, and say which.
- If you change a field of a wish, the helper's rules, `parse.js`, the invite page or the editor, update the matching checks in the same change.
