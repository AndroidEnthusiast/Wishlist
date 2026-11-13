#!/usr/bin/env bash
# Saved themes (owner, 13 November 2026): the backup of a theme the owner wants to be able to bring back must never
# change or disappear. Each folder in themes/ has snapshot.sha256; every file must be there, unchanged, and still hold
# the theme's code. Run: bash tests/themes/check.sh
cd "$(dirname "$0")/../.." || exit 1
fail=0
for dir in themes/*/; do
  [ -f "$dir/snapshot.sha256" ] || { echo "FAIL: $dir has no snapshot.sha256"; fail=1; continue; }
  [ -f "$dir/README.md" ] || { echo "FAIL: $dir has no README.md (how to put it back)"; fail=1; }
  (cd "$dir" && sha256sum --quiet -c snapshot.sha256) || { echo "FAIL: the saved copy in $dir was changed or lost"; fail=1; }
done
# Gen Z chaos must be in its saved copy (its colours, fonts and tilts).
g=themes/gen-z-chaos/snapshot
for want in 'StyleInfo("chaos", "Gen Z chaos"' '0xFFFF2BD6' 'google("Bungee")' 'fun Look.tilt' '"chaos" -> Text('; do
  grep -rqF "$want" "$g" || { echo "FAIL: $g is missing: $want"; fail=1; }
done
[ "$fail" -eq 0 ] && echo "Saved themes OK" || exit 1
