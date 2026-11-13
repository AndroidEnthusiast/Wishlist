# Gen Z chaos: the saved copy (so it can always come back)

Saved on 13 November 2026 at the owner's request ("my partner absolutely loves the gen z chaos theme; make sure I
can always recover it"). This is the theme **exactly as it was in app version 2.198** (main, commit `6fd62b1`).
Nothing in this folder is part of the app: it is only a backup. The check `tests/themes/check.sh` makes sure these
files never change or disappear (`snapshot.sha256`).

**Owner, to get it back:** start a session and say *"Put back the Gen Z chaos theme from themes/gen-z-chaos"*.

## What Gen Z chaos is
Clashing hot pink `#FF2BD6`, yellow `#FFE600` and lime `#C6FF00`; cream page `#FFF7E8` (light) or near-black
`#0D0D0D` (dark); thick ink lines (3 dp) and hard pink/ink shadows; every card and wish row tilted a little, each its
own way; the bottom bar tilted −0.8°; the "Wishlist" wordmark tilted −3° with a hard pink shadow; polka dots and three
big coloured circles behind the screens; Bungee headings, Bricolage Grotesque text, handwritten Caveat small labels.
Works with light and dark and all five accents. Key: `"chaos"`, name "Gen Z chaos".

## Every place it lives (in `snapshot/`, the whole files as they were)
Search each file for `chaos` (and `"neo", "chaos"`, where it shares Neobrutalism's code).
- `Design.kt`: the `StyleInfo("chaos", …)` entry in `STYLES` (the Themer's choice); the fonts `bungee`, `bricolage`,
  `caveat`; the `"chaos" -> { … }` branch of `lookFor` (all its colours); `typographyFor` (fonts, Caveat as the small
  label font); `titleWeight`, `priceWeight`; `shapesFor` (Material shapes); `cardShape`, `pictureLine`, `badgeShape`,
  `chipShape`, `fabShape`, `buttonShape`; `tilt` (the card tilts); `lookSurface` (3 dp lines, 5 dp hard shadow);
  `lookCard` (rotates cards); `lookChip`, `chipContent`; `lookBackdrop` (polka dots and circles).
- `Styled.kt`: text buttons in ink, the switch, the 3 dp line under the top bar, the price badge colours, the status
  badge, the badge border, the framed picture with a hard shadow.
- `Hub.kt`: `Wordmark` (tilted with a pink shadow).
- `BottomBar.kt`: the bar's shape and its tilt.
- `Overview.kt`: `statTile` (the number tiles on Home).
- `Data.kt`: `Repo.design` may hold `"chaos"` (saved choice). If the theme is gone, `styleFor` falls back to Quiet,
  so nobody gets stuck; when it comes back, phones that had it chosen get it again.
- `Licences.kt`: credits Bungee, Bricolage Grotesque and Caveat (SIL Open Font License; Rule 7): keep or put back.
- The fonts are Google Fonts downloaded by the phone (`google("…")`), so there are no font files to save.

## How to put it back (for Claude)
1. Compare each `snapshot/<File>.kt.txt` with today's `app/src/main/java/com/wishlist/app/<File>.kt`
   (`diff`), and copy back only the chaos parts listed above; the rest of those files will have moved on.
2. If code it relies on was renamed or removed (`Look`, `styleType`, `hardShadow`, `pick`, `mix`, `google`), adapt
   the chaos parts to the new names rather than bringing old code back.
3. Credits in `Licences.kt`, a README bullet, a version bump, `gradle testReleaseUnitTest`, and screenshots
   (`-Pwl.screens`) in light and dark to compare with how it looked; follow `docs/STYLE.md`.
4. The owner's original choices for it: `docs/decisions/look.md` (search "Gen Z chaos"); the preview it was chosen
   from: https://claude.ai/artifact/NiJ5D8P4Xk8JQiJLwBMtKe

Also kept forever in the GitHub history: any version of the app up to 2.198 has it (commit `6fd62b1`).
