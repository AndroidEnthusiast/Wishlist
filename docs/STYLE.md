# Style guide

The owner's rule (7 November 2026): the whole app looks and behaves the same everywhere, follows good UI practice, and
is readable and accessible. Nothing is built that is known to look wrong (a cut-off button, text out of proportion). Every
new screen, dialog and feature follows this guide; when a rule here doesn't fit, ask the owner and add the answer here.

## Use the app's own parts
- Buttons, dialogs, switches, top bars and icons come from `Styled.kt` (`Button`, `OutlinedButton`, `TextButton`,
  `AlertDialog`, `Switch`, `TopAppBar`, `AppIcons`). Never import Material's own `Button`/`AlertDialog` in a screen:
  ours carry the chosen style (Quiet, Gen Z chaos, Y2K, Retro vintage, Pop futurism, Grunge, Flat, Bauhaus) and the
  sizes below. The same for text fields, menus, tick boxes, choice circles, chips, spinners, progress bars, dividers,
  sliders, bottom panels and `Scaffold` (`Themed.kt`, since 2.199): never import Material's own in a screen (the
  photo editor's dark field is the one exception). Owner, 13 November 2026: "a theme must change absolutely every
  element of the app"; a new part gets its style in every theme, light and dark.
- Cards, mats and surfaces use `lookCard`, `lookMat`, `lookSurface` (`Design.kt`); a box with its own background uses
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
- Two sizes (`Styled.kt`): **regular** on screens, at least 46 dp tall; **compact** inside a dialog's text, 40 dp tall
  with a smaller shadow and 6 dp room above and below, so a scrolling dialog never cuts the shadow off. The compact
  size is automatic inside `AlertDialog` (`LocalInDialog`); don't set button heights or paddings by hand.
- One main action per screen or dialog (`Button`); others are `OutlinedButton` or `TextButton`. A dialog's own
  Close / Cancel / Done are `TextButton`s in its `confirmButton` / `dismissButton` slots.
- Labels are short verbs in sentence case ("Send invite", "Invite Friends"), never all capitals.

## Touch and spacing
- Everything you tap has a touch area of at least 48 × 48 dp, even when it looks smaller (chips use
  `minimumInteractiveComponentSize()`). Icons alone get a `contentDescription` (or a click label).
- Spacing comes in steps of 4 dp: 4, 8, 12, 16, 24. Screen edges 16 dp; space between items in a dialog 8 dp; between
  sections 16–24 dp.
- Raised surfaces (hard shadows of chaos, Y2K, Retro, Pop futurism, Grunge, Bauhaus) need room around them: in a scrolling column, keep at least 8 dp
  between a raised element and the column's edge.

## Chips and badges
- The small chips on a card (size label, reactions, "+ react") are all 28 dp tall (`CHIP_HEIGHT`) and sit on one row,
  vertically centred, 8 dp apart.

## Wishes: tap, ⋮ and long press (owner, 12 November 2026)
- On every wish card, on every list: a tap shows the photo or opens the shop; the ⋮ opens the wish's sheet
  (`WishSheet.kt`, same rows in the same order, only what applies); a long press starts selecting wherever you may
  change wishes. Never put an action only behind a long press.

## Dialogs
- Title, then `bodyMedium` text, then the content, then the actions at the bottom right. A long dialog scrolls in its
  text slot. Taps outside close it unless something would be lost (then `dismissOnClickOutside = false`).

## Motion
- Animations are short and calm. When the phone's animations are switched off (Settings > Accessibility > Remove
  animations), nothing moves (see `Snow.kt`); information never depends on an animation.
- Screens move like Signal's (owner, 12 November 2026), always through `Motion.kt`, about 0.25 s: going deeper slides
  in a short way from the right while fading in, Back reverses it, the bottom bar's tabs fade through. A new route
  gets this by itself; a place inside a screen that switches without being a route (an open chat, a friend's page)
  uses `Moving(…)` with its depth. No other screen transitions.

## Before building
- Check every new layout against this guide on the smallest phone width (360 dp), with long names and the largest font,
  in all eight styles, light and dark (`-Pwl.screens -Pwl.style=…` and `PartsScreenshotTest`). Drawings behind
  screens stay faint behind text (`LookTest` checks 4.5 : 1 over them) or stay in the outer 12 dp. The screenshots
  are a close copy, not the phone (fonts don't download there): say so,
  build only what follows these rules, and ask the owner to check the screen on the phone.
