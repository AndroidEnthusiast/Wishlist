#!/usr/bin/env bash
# Runs every check for the web pages and the helper function. Run it from the tests/ folder: bash run-all.sh
set -u
cd "$(dirname "$0")"
fail=0
run() { echo "== $1"; shift; "$@" 2>&1 | tail -n 6; [ "${PIPESTATUS[0]}" -eq 0 ] || fail=1; }
DENO=./node_modules/.bin/deno
[ -x "$DENO" ] || DENO=deno
run "parse.js (reads product pages)" node parse/parse_test.js
run "fetch-page helper (real Deno)"  "$DENO" run --allow-read --allow-env=SUPABASE_URL --allow-net=none function/test.ts
run "push helper (real Deno)"  "$DENO" run --allow-read --allow-env --allow-net=none function/push_test.ts
run "web pages only run their own scripts" node csp/test.js
run "shop browser page scripts: cookies and empty layers (Shops.kt)" node shop/consent_test.js
run "index.html (friends page + invite page)" node index-page/test.js
# Needs PostgreSQL (WL_PG, see README.md); without it this one says SKIPPED.
run "group page against the real SQL (web/group.html)" node group/test.js
run "gift exchange page against the real SQL (web/exchange.html)" node exchange/test.js
for t in e2e/t1.js e2e/t2.js e2e/t3.js e2e/t4.js e2e/t5.js e2e/t6.js e2e/t7.js; do run "browser editor $t" node "$t"; done
[ "$fail" -eq 0 ] && echo "ALL SUITES OK" || echo "SOMETHING FAILED"
exit $fail
