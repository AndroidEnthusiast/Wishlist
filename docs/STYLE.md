# Style guide

The owner's rule (7 November 2026): the whole app looks and behaves the same everywhere, follows good UI practice, and
is readable and accessible. Nothing is built that is known to look wrong (a cut-off button, text out of proportion). Every
new screen, dialog and feature follows this guide; when a rule here doesn't fit, ask the owner and add the answer here.

## Use the app's own parts
- Buttons, dialogs, switches, top bars and icons come from `StyledButtons.kt` / `StyledBars.kt` / `AppIcons.kt` (split from `Styled.kt` in 2.242) (`Button`, `OutlinedButton`, `TextButton`,
  `AlertDialog`, `Switch`, `TopAppBar`, `AppIcons`). Never import Material's own `Button`/`AlertDialog` in a screen:
  ours carry the chosen design style (Quiet, Gen Z chaos; a new one only as in `docs/DESIGN-STYLES.md`) and the sizes below. The same for text fields, menus, tick boxes, choice circles, chips, spinners, progress bars, dividers,
  sliders, bottom panels and `Scaffold` (`Themed*.kt`, since 2.199): never import Material's own in a screen (the
  photo editor's dark field is the one exception). Owner, 13 November 2026: "a theme must change absolutely every
  element of the app"; a new part gets its style in every theme, light and dark.
- Cards, mats and surfaces use `lookCard`, `lookMat`, `lookSurface` (`DesignShapes.kt`, split from `Design.kt` in 2.247); a box with its own background uses
  `lookSurface` too, never `clip(RoundedCornerShape(…)).background(…)`. Coloured text and icons use `look.textAccent`
  (in most styles `look.accent` is a fill colour too light for text). Colours come from the theme
  (`MaterialTheme.colorScheme`, `LocalLook`), never fixed colours, except drawn illustrations.

## Text
- One type scale, Material's: `headlineSmall` big screen headings; `titleLarge` screen titles; `titleMedium` dialog
  titles, card and section headings; `titleSmall` names on small cards and list rows; `bodyLarge` main reading text;
  `bodyMedium` dialog text and explanations; `bodySmall` only for secondary lines under something bigger (a date, a
  count); `labelLarge` buttons; `labelMedium` chips and badges.
- Dialog text is `bodyMedium` (not `bodySmall`): the buttons in the dialog must never look bigger than its text.
- Never smaller than `labelSmall` (11 sp). Text must still fit with the phone's font size at 130%: no fixed heights on
  text, let it wrap (`maxLines` + ellipsis only for names in rows and cards).
- Contrast: text on its background at least 4.5 : 1 (3 : 1 for 18 sp and up). Grey secondary text uses
  `onSurfaceVariant`, never a lighter grey.

## Buttons
- Two sizes (`StyledButtons.kt`): **regular** on screens, at least 46 dp tall; **compact** inside a dialog's text, 40 dp tall
  with a smaller shadow and 6 dp room above and below, so a scrolling dialog never cuts the shadow off. The compact
  size is automatic inside `AlertDialog` (`LocalInDialog`); don't set button heights or paddings by hand.
- One main action per screen or dialog (`Button`, filled); a second choice is `OutlinedButton`; the way out is plain
  words. In dialogs: see "Dialogs" (the rules of 21 November 2026; the earlier wording is in `docs/STYLE-ARCHIVE.md`).
- Labels are short verbs in sentence case ("Send invite", "Invite Friends"), never all capitals.
- **Buttons do, words go** (owner, 21 November 2026; `StyleRulesTest`): anything that does something is a button. On
  a screen or card it is **as wide as its words** (`TextButton` there is a small secondary button; `Button` the main
  one), two side by side when they belong together; full width only for a dialog's or form's own bottom buttons.
  **Plain coloured words** (`LinkButton`) only for going somewhere ("All price changes ›", a licence's text, "I
  already have lists"), a way out outside a dialog (Close, Skip, Cancel) and in a top bar ("Select all", "Done").
  **Red** only for removing, leaving, blocking and deleting.

## Touch and spacing
- Quiet's own rules (2.207, `docs/styles/quiet.md`): labels sit above text fields (the field wrappers do it, pass
  `label` as usual); two or three tabs are `SegmentedTabs`; a wish photo goes on `photoSurface` through
  `PhotoStage`; your colour as text is `MaterialTheme.colorScheme.primary` (already darkened to read), filled blocks
  use `look.accent`.
- Everything you tap has a touch area of at least 48 × 48 dp, even when it looks smaller (chips use
  `minimumInteractiveComponentSize()`). Icons alone get a `contentDescription` (or a click label).
- Spacing comes in steps of 4 dp: 4, 8, 12, 16, 24. Screen edges 16 dp; space between items in a dialog 8 dp; between
  sections 16–24 dp.
- Raised surfaces (hard shadows of chaos, and of any style that has them) need room around them: in a scrolling column, keep at least 8 dp
  between a raised element and the column's edge.

## Chips and badges
- The small chips on a card (size label, reactions, "+ react") are all 28 dp tall (`CHIP_HEIGHT`) and sit on one row,
  vertically centred, 8 dp apart.

## Wishes: tap, ⋮ and long press (owner, 12 November 2026)
- On every wish card, on every list: a tap shows the photo or opens the shop; the ⋮ opens the wish's sheet
  (`WishSheet.kt`, same rows in the same order, only what applies); a long press starts selecting wherever you may
  change wishes. Never put an action only behind a long press.

## Dialogs
- **One rule in every style (owner, 21 November 2026; checked by `DialogRulesTest`):** title, then `bodyMedium` text,
  then the content, then the buttons **stacked and full width**: the main action **filled** (`confirmButton`), a
  second choice **outlined** (`secondButton`), and the way out **plain words at the bottom** (`dismissButton`: only
  Cancel, Close, Not now, Keep editing, Back, Skip, …). A lone button closes the dialog, so it is plain words too.
  **Never two buttons in one slot** (that made Move invisible). A long dialog scrolls in its text slot. Taps outside
  close it unless something would be lost (then `dismissOnClickOutside = false`).
- **Pickers are bottom sheets** (owner, 22 November 2026, after "Move or copy?" looked out of place as a centred
  pop-up; `PickSheet.kt`, `PickSheetRulesTest`): every "choose what to do" and every "pick a list, a wish or a
  person" is a `PickSheet`: the question as its title, what it's about under it, the rows in a `SheetGroup`, no
  Cancel (swipe down or tap outside). A second step stays in the same sheet with ← (Move or copy → Move to…).
  Picking several ends with one filled button (Invite, Done). Centred pop-ups only for "are you sure?", short
  messages and forms with typing (Leave with "who takes over", the group's join form).
- **Choosing between actions** (Move or Copy): **choice rows**, not buttons under a paragraph: an icon in its tile, the
  word, and one line on what it does; tap = choose. One that isn't possible stays, greyed, its line saying why.
- **Picking from a list** (a wishlist, a person): plain rows, tap = pick; "+ New …" last, in your colour.
- **Show what it's about:** a dialog or picker that acts on a wish shows it on top (photo, name, price; several: their
  photos and "3 wishes").
- **Every menu is a bottom sheet** (owner, 21 November 2026; `MenuSheet.kt`, `StyleRulesTest`): every ⋮ (a wish, a
  list, a chat, a group, a gift exchange, a friend), the + menu, a chat message's long press, a photo's "Take a photo /
  Choose a photo". Rows in one group, each with its icon in a tinted tile (`DropdownMenuItem(leadingIcon = …)`), red
  rows (`danger = true`) for leaving, removing and blocking. A tap does it and closes the sheet (switches stay open).
  **No ✕**: swipe it down or tap outside. (The small pop-up menus of 16 November 2026 are gone.)
- **Pick one from a list** (Check prices every…, Currency, Category, Size, Sort, View): the same sheet, the options as
  rows with **a tick on the current one** (`selected = true`); a tap chooses and closes. Choice circles only inside a
  form that is confirmed with a button (Leave: who organises after you; New wishlist's Privacy).
- **Forms** (New wishlist, Edit wish, settings, Import, Screenshot to wish): the full-width form sheet, the main button
  filled at the bottom, **the ✕ top right** (in every style), closed only by the ✕ or the pull tab, Back asks when
  something is typed (`docs/decisions/screens.md`).
- **A card that opens something** ("Add wishes from other wishlists"): an icon tile, a title, a detail line and ›.
- The picture version of these rules: the owner's style guide page (link in `docs/HANDOFF.md`).

## Empty and error states (owner, 21 November 2026; `EmptyState.kt`, `StyleRulesTest`)
- Every empty or error page: a **soft icon** in a circle of your colour, **one kind line** saying what happened, and
  **one button** for the next step (Try again, Add a wish, Back, Go to Friends). Never a line alone. `EmptyState(icon,
  text, action, onAction)`.

## Screen titles (owner, 21 November 2026)
- Every screen you open has the **big title that shrinks into the bar** as you scroll, in every style. A one-line bar
  only where the bar shows a person or a second line (a chat, a friend's list, a list's settings), a main tab (Home's
  wordmark, Messages, Friends, My shops: big one-line titles) and while selecting ("2 selected").

## Motion
- Animations are short and calm. When the phone's animations are switched off (Settings > Accessibility > Remove
  animations), nothing moves (see `Snow.kt`); information never depends on an animation.
- **The app's little real objects** (owner, 24 November 2026; `Padlock.kt`, `Objects.kt`): the golden padlock and
  the "Got it!" stamp are the app's signature (the price tag was taken out again on 25 November 2026). A new moment that deserves a flourish gets one of these
  (or a new object drawn the same way, asked first), never confetti or a generic animation; at most about five.
- **Animations show what you did** (owner, 25 November 2026; `CardMotion.kt`, `docs/decisions/look.md`): one per
  family, the same everywhere: leaving (into the bin, to the Home icon), a copy peels off, arriving (drops in),
  an icon acting out its change (eye, heart, padlock, the old price's line). Checked frame by frame before release.
- Screens move like Signal's (owner, 12 November 2026), always through `Motion.kt`, about 0.25 s: going deeper slides
  in a short way from the right while fading in, Back reverses it, the bottom bar's tabs fade through. A new route
  gets this by itself; a place inside a screen that switches without being a route (an open chat, a friend's page)
  uses `Moving(…)` with its depth. No other screen transitions.

## Before building
- Check every new layout against this guide on the smallest phone width (360 dp), with long names and the largest font,
  in every design style, light and dark (`-Pwl.screens -Pwl.style=…` and `PartsScreenshotTest`). Drawings behind
  screens stay faint behind text (`LookTest` checks 4.5 : 1 over them) or stay in the outer 12 dp. The screenshots
  are a close copy, not the phone (fonts don't download there): say so,
  build only what follows these rules, and ask the owner to check the screen on the phone.

## The app's own parts (2.210, owner 15 November 2026; `docs/styles/identity.md`)
- Icons only through `AppIcons` (our own `OwnIcons.kt`); never Google's icon set (`OwnIconsTest` fails).
- Surfaces in Quiet use `Squircle(…)`, not `RoundedCornerShape`; buttons and chips stay fully round.
- Dialogs: one rule in every style, see "Dialogs" above (21 November 2026; the earlier rule is in `docs/STYLE-ARCHIVE.md`).
- Menus: rows in `SheetGroup`s, icons in their tinted tiles (`SheetRow`); every menu is a sheet (see "Dialogs" above).
- **Rows in a group have a thin line between them** (owner, 22 November 2026, after Move and Copy looked like one
  block in dark mode): starting after the icon tile, in the colour `look.groupLine()` (made from the group's own
  colour, visible in every theme; `LookTest` checks it). `SheetGroup` and every menu (`RowLines`) draw it by themselves.
- Vibration only at a few relevant moments (now: start selecting, reserving). Celebrations only on reserving and on
  a price drop.
- Voice: a warm friend, short and kind, never silly or long ("Nothing here yet. Tap + to add the first thing you’d love.").
