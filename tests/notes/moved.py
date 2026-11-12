#!/usr/bin/env python3
"""Proves a notes tidy-up lost nothing (owner, 12 November 2026).

    python3 tests/notes/moved.py <commit before the tidy-up> [file ...]

For each notes file (default: CLAUDE.md and docs/HANDOFF.md), every line that was in it at <commit> and is no longer
in it must now be somewhere in the notes (CLAUDE.md, README.md or any docs/**/*.md), word for word:
  - "moved whole": the same line exists in another file;
  - "moved in pieces": a long line was split up (for example the file list, sorted into areas in docs/MAP.md): each of
    its items (split at top-level commas) or sentences exists word for word somewhere.
Headings are structure: a heading found nowhere is listed as renamed, not counted as missing. Blank lines are skipped. Exit 1 when anything is missing.
"""
import glob, os, re, subprocess, sys

def old_text(commit, path):
    return subprocess.run(["git", "show", f"{commit}:{path}"], capture_output=True, text=True, check=True).stdout

def pieces(line):
    body = re.sub(r"^\s*(- |\d+[a-z]?\. )", "", line).strip()
    items, depth, cur = [], 0, ""
    for ch in body:
        depth += ch == "("; depth -= ch == ")"
        cur += ch
        if ch == "," and depth == 0:
            items.append(cur); cur = ""
    items.append(cur)
    out = []
    for it in items:
        it = it.strip().rstrip(",").strip()
        if it.startswith("- `app/src/main/java/com/wishlist/app/`: "):
            it = it[len("- `app/src/main/java/com/wishlist/app/`: "):]
        it = re.sub(r"^`app/src/main/java/com/wishlist/app/`: ", "", it)
        if it:
            out.append(it)
    return out

def main():
    if len(sys.argv) < 2:
        print(__doc__); return 2
    commit = sys.argv[1]
    files = sys.argv[2:] or ["CLAUDE.md", "docs/HANDOFF.md"]
    notes = ["CLAUDE.md", "README.md"] + sorted(glob.glob("docs/**/*.md", recursive=True))
    text = {f: open(f, encoding="utf-8").read() for f in notes if os.path.exists(f)}
    all_lines = {f: set(t.split("\n")) for f, t in text.items()}
    everything = "\n".join(text.values())
    bad = 0
    for f in files:
        now = all_lines.get(f, set())
        removed = [l for l in old_text(commit, f).split("\n") if l.strip() and l not in now]
        whole, split, heads, missing = [], [], [], []
        for l in removed:
            where = [g for g, ls in all_lines.items() if g != f and l in ls]
            if where:
                whole.append((l, where)); continue
            if l.lstrip().startswith("#"):
                heads.append(l); continue
            gone = [p for p in pieces(l) if p not in everything]
            (missing if gone else split).append((l, gone))
        print(f"{f}: {len(removed)} lines removed: {len(whole)} moved whole, {len(split)} moved in pieces, "
              f"{len(heads)} headings renamed, {len(missing)} MISSING")
        dest = {}
        for l, where in whole:
            for g in where[:1]:
                dest[g] = dest.get(g, 0) + 1
        for g, n in sorted(dest.items()):
            print(f"    {n:4d} whole lines now in {g}")
        for l, _ in split:
            print(f"    in pieces: {l[:100]}…")
        for h in heads:
            print(f"    heading renamed (structure, its lines moved): {h}")
        for l, gone in missing:
            bad += 1
            print(f"    MISSING: {l[:140]}")
            for p in gone:
                print(f"        not found: {p[:140]}")
    print("Nothing lost." if not bad else f"{bad} lines are not found anywhere: put them back.")
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
