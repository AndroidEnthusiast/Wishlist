#!/bin/bash
# The notes' size limits (owner, 12 November 2026; CLAUDE.md "Keeping the notes small").
#   bash tests/notes/limits.sh            check, print what is too big (exit 1 when something is)
#   bash tests/notes/limits.sh --hook     the session's stop hook (.claude/settings.json): exit 2 blocks finishing
#                                          while a limit is passed or the notes are not committed and pushed to main
# An optional folder after the flag checks a copy instead of this repository (used to test the hook itself).
mode=check
[[ "$1" == "--hook" ]] && { mode=hook; shift; cat >/dev/null; }
root="${1:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$root" || exit 0
export LC_ALL=C.UTF-8
problems=()

chars() { wc -m < "$1" | tr -d ' '; }
lines() { wc -l < "$1" | tr -d ' '; }

# 1. CLAUDE.md: under 150 lines and 12,000 characters.
if [[ -f CLAUDE.md ]]; then
  l=$(lines CLAUDE.md); c=$(chars CLAUDE.md)
  (( l > 150 )) && problems+=("CLAUDE.md has $l lines (limit 150): move details into docs/MAP.md or another doc (never the Rules that must stay true, the safety or the interview rules).")
  (( c > 12000 )) && problems+=("CLAUDE.md has $c characters (limit 12,000): move details into docs/MAP.md or another doc (never the Rules that must stay true, the safety or the interview rules).")
fi

# 2. docs/HANDOFF.md: only "Next session" and "Last sessions"; at most 3 sessions of at most 5 lines each.
h=docs/HANDOFF.md
if [[ -f $h ]]; then
  others=$(grep '^## ' "$h" | grep -v -x -e '## Next session' -e '## Last sessions')
  [[ -n "$others" ]] && problems+=("docs/HANDOFF.md has other sections than \"Next session\" and \"Last sessions\" ($(echo "$others" | tr '\n' ' ')): move them to docs/HANDOFF-ARCHIVE.md.")
  n=$(awk '/^## /{in_last=($0=="## Last sessions")} in_last && /^### /{n++} END{print n+0}' "$h")
  (( n > 3 )) && problems+=("docs/HANDOFF.md has $n sessions under \"Last sessions\" (limit 3): move the oldest to the bottom of docs/HANDOFF-ARCHIVE.md, word for word.")
  long=$(awk '/^## /{in_last=($0=="## Last sessions"); next}
    in_last && /^### /{if (name != "" && k > 5) print name" ("k" lines)"; name=$0; k=0; next}
    in_last && name != "" && NF {k++}
    END{if (name != "" && k > 5) print name" ("k" lines)"}' "$h")
  [[ -n "$long" ]] && problems+=("A session summary in docs/HANDOFF.md is longer than 5 lines: $(echo "$long" | tr '\n' ' ')")
fi

# 3. Every other doc in docs/ (not the -ARCHIVE.md files): under about 400 lines and 40,000 characters.
while IFS= read -r f; do
  l=$(lines "$f"); c=$(chars "$f")
  (( l > 400 )) && problems+=("$f has $l lines (limit about 400): split it or archive its old parts (a -ARCHIVE.md file).")
  (( c > 40000 )) && problems+=("$f has $c characters (limit 40,000): split it or archive its old parts (a -ARCHIVE.md file).")
done < <(find docs -name '*.md' ! -name '*-ARCHIVE.md' 2>/dev/null | sort)

# 4. (Stop hook only) the notes are committed and pushed to main.
if [[ $mode == hook && ${#problems[@]} -eq 0 ]] && git rev-parse --git-dir >/dev/null 2>&1; then
  notes=(CLAUDE.md docs .claude tests/notes)
  if [[ -n "$(git status --porcelain -- "${notes[@]}" 2>/dev/null)" ]]; then
    problems+=("The notes (CLAUDE.md, docs/, .claude/, tests/notes/) have changes that are not committed: commit them and push to main (git push origin HEAD:main).")
  elif git rev-parse --verify -q origin/main >/dev/null && [[ -n "$(git log --oneline origin/main..HEAD -- "${notes[@]}" 2>/dev/null)" ]]; then
    problems+=("The notes have commits that are not on GitHub's main yet: push them (git push origin HEAD:main).")
  fi
fi

if (( ${#problems[@]} )); then
  msg="The notes need tidying before this session may finish (owner's rule, CLAUDE.md \"Keeping the notes small\"). Move, never delete; leave a pointer; prove it with python3 tests/notes/moved.py <commit before>; then commit and push to main."
  if [[ $mode == hook ]]; then
    { echo "$msg"; printf -- '- %s\n' "${problems[@]}"; } >&2
    exit 2
  fi
  echo "$msg"; printf -- '- %s\n' "${problems[@]}"
  exit 1
fi
[[ $mode == check ]] && echo "Notes within limits."
exit 0
