#!/usr/bin/env bash
# Runs every database check in tests/sql against the REAL supabase-*.sql scripts in a local PostgreSQL (ROADMAP R5).
# Each check gets its own fresh database with exactly the scripts it needs, in the order the owner runs them.
# Needs WL_PG, the psql connection options of a superuser, for example:
#   WL_PG="-h /tmp/pg -p 5499 -U postgres" bash sql/run.sh
# Without WL_PG it says SKIPPED (like the page suites that need PostgreSQL). See tests/README.md.
set -u
cd "$(dirname "$0")"
ROOT="$(cd ../.. && pwd)"
if [ -z "${WL_PG:-}" ]; then echo "SKIPPED (set WL_PG to run it against PostgreSQL)"; exit 0; fi
# shellcheck disable=SC2206
PG=($WL_PG)
export PGOPTIONS="-c client_min_messages=warning"
DB=wl_sql_test
fail=0
sql() { psql "${PG[@]}" -d "${2:-$DB}" -v ON_ERROR_STOP=1 -q -At -c "$1"; }
sql "select 1" postgres >/dev/null 2>&1 || { echo "FAIL: can't reach PostgreSQL with WL_PG=$WL_PG"; exit 1; }
sql "do \$\$ begin create role anon; exception when duplicate_object then null; end \$\$" postgres >/dev/null

# Supabase's own parts that don't exist locally: pg_net (push) and Realtime (live updates) get stand-ins that only
# write down what was asked; pg_cron's lines are left out.
NET="create schema if not exists net; create table net.calls(url text, body jsonb);
create function net.http_post(url text, body jsonb, headers jsonb) returns bigint language sql as 'insert into net.calls values (url, body) returning 1::bigint';"
REALTIME="create schema realtime; create table realtime.sent(topic text, event text, private boolean);
create function realtime.send(payload jsonb, event text, topic text, private boolean) returns void language sql as 'insert into realtime.sent(topic, event, private) values (topic, event, private)';"

# check "<scripts and stand-ins in order>" test.sql [test2.sql ...]
check() {
  local parts="$1"; shift
  sql "drop database if exists $DB" postgres >/dev/null 2>&1; sql "create database $DB" postgres >/dev/null || { fail=1; return; }
  for p in $parts; do
    case "$p" in
      net) sql "$NET" >/dev/null ;;
      realtime) sql "$REALTIME" >/dev/null ;;
      *) grep -v -E 'create extension|cron\.schedule' "$ROOT/supabase-$p.sql" | psql "${PG[@]}" -d "$DB" -v ON_ERROR_STOP=1 -q -f - >/dev/null ;;
    esac || { echo "FAIL: supabase-$p.sql did not run (for $*)"; fail=1; return; }
  done
  for t in "$@"; do
    out=$(psql "${PG[@]}" -d "$DB" -v ON_ERROR_STOP=1 -q -At -f "$t" 2>&1)
    if [ $? -eq 0 ] && echo "$out" | tail -n 1 | grep -q "^ALL .* OK$"; then echo "ok   $t: $(echo "$out" | tail -n 1)"
    else echo "FAIL $t"; echo "$out" | tail -n 5; fail=1; fi
  done
}

check "setup" quantity_test.sql
check "setup shared" occasion_test.sql
check "setup shared friends" friends_test.sql
check "setup shared friends chat" chat_test.sql chat_photos_test.sql
check "net setup shared friends chat push" push_test.sql
check "setup shared friends chat groups" groups_test.sql
check "setup shared friends chat features" features_test.sql
check "setup shared friends chat features" views_test.sql
check "net setup shared friends chat push groups features exchange" exchange_test.sql
check "net setup shared friends chat push groups features exchange together" together_test.sql
check "net setup shared friends chat push groups features exchange together collab" collab_test.sql
check "net setup shared friends chat push groups features exchange together collab realtime sync" sync_test.sql
check "net setup shared friends chat push groups features exchange together collab realtime sync limits" limits_test.sql
check "net setup shared friends chat push groups features exchange together collab" people_photos_test.sql
check "net setup shared friends chat push groups features exchange together collab realtime sync limits cleanup" cleanup_test.sql
# Every script must also be safe to run a second time (the owner is told so).
sql "drop database if exists $DB" postgres >/dev/null 2>&1; sql "create database $DB" postgres >/dev/null
sql "$NET" >/dev/null; sql "$REALTIME" >/dev/null
twice=ok
for round in 1 2; do
  for p in setup shared friends chat push groups features exchange together collab sync limits cleanup; do
    grep -v -E 'create extension|cron\.schedule' "$ROOT/supabase-$p.sql" | psql "${PG[@]}" -d "$DB" -v ON_ERROR_STOP=1 -q -f - >/dev/null 2>/tmp/wl_sql_err || { echo "FAIL supabase-$p.sql, run $round: $(tail -n 2 /tmp/wl_sql_err)"; twice=bad; fail=1; }
  done
done
[ "$twice" = ok ] && echo "ok   every script runs twice in a row"
sql "drop database if exists $DB" postgres >/dev/null 2>&1
[ "$fail" -eq 0 ] && echo "ALL PASSED (database checks)" || echo "SOMETHING FAILED (database checks)"
exit $fail
