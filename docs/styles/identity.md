# The app's own character (owner, 15 November 2026)

Owner: "when I look at some fonts and menus I'm getting the feeling of a standard Android visual experience … the app
should really feel like its own app and not some copy cat". Not only a different look: "it should also come across
as a unique app and feel like it when you use it, whilst holding the highest possible standards and applying the best
practices for our users". Interviewed in 5 rounds (the owner: four questions are not enough for a character).
Build straight away without mock-ups ("don't miss anything"), and keep it easy to go back.

**Safe point:** version **2.209, commit `f5a6fc1`**. "Put back the look of 2.209" = restore the app's look and feel
files from that commit (the features built after it stay).

## Diagnosis (Claude, 15 November 2026): where "standard Android" came from
1. Google's filled Material icons (person, bag, bin, gear, bell, home, ⋮): exactly Android Settings and Gmail.
2. Menus and panels as Google Settings rows: a grey icon, a word, full-width rows.
3. Google's switch, dialog (title, text, text buttons bottom-right), flat top bar (←, title, icon row), ripple.
4. Figtree for most text: a plain geometric sans close to Google Sans and Roboto.
5. Colours: the dark indigo #8585FF sits next to Android's default purple; the dark green #3DDC84 is the Android
   robot's green exactly.
6. No signature detail that repeats.

## The owner's answers
- **Where it hits**: icons, menus and panels, switches / dialogs / top bars, fonts and colours: all of them.
- **Character**: not overtly gift-themed. Apps the owner likes: Airbnb / Pinterest, Revolut / Monzo, Apple's own apps,
  Duolingo / Spotify. **Leading: Apple's calm and precision** (builds on Quiet); borrowed: Airbnb's warm photos and
  surfaces, Revolut's bold prices and totals, Duolingo's small celebrations.
- **Feel**: soft and springy: buttons and cards sink a little when pressed instead of Android's ripple, panels and
  cards settle with a slight bounce.
- **Vibration**: "only for relevant and a select few moments" (not on every switch or tap).
- **Voice**: warm friend: short and kind with a little warmth ("Saved. Sam will see it."), never silly, never long.
- **Signature colour**: **Berry #B0245A**, the app's own colour; it joins the accents as the first one and is the
  default for new people (existing choices stay). The dark green becomes a deeper own green (not Android's robot
  green); the indigo gets a tone further from Android's default purple.
- **Celebrations**: when you **reserve a gift**, and when **a price drops**. Nothing else.
- **Shapes**: smooth squircles (continuous corners), not Android's plain circle-arc corners.
- **Text font**: **Plus Jakarta Sans** (SIL OFL) instead of Figtree; titles stay Fraunces.
- **Icons**: our own drawn icons (about 35), **rounded line**: even strokes with rounded ends and corners; the chosen
  tab or an active state filled.
- **Menus and panels**: grouped cards with hairlines between rows, each icon in a small squircle tile with a soft
  tint, the destructive one red.
- **Own switch, own dialogs, own top bars, own tap feel** (all four). Dialogs: centred, a title in Fraunces, buttons
  full-width stacked with the main one filled, opening with a soft spring. Top bars: a large title that shrinks into
  the bar as you scroll, ← and icons in soft round buttons. Tap: sink and spring back, no ripple.
- **Surfaces**: warm neutrals: light page #F7F5F2 with white cards; dark page #121014 (warm charcoal, not pure black)
  with cards a step lighter.
- **Wordmark and app icon**: decide later.
- **Every style** (owner: "as should be the case with every theme") gets the shared feel: own icons (bolder in
  Gen Z chaos), the tap feel, vibration moments, celebrations and the voice. Gen Z chaos keeps its own look (fonts,
  colours, shapes).

## Best practices kept (Claude, part of "the highest possible standards")
- Contrast measured for every colour in light and dark (`LookTest`); text 4.5 : 1, controls 3 : 1.
- 48 dp touch areas; TalkBack labels unchanged by the new icons; switches and dialogs keep their roles.
- "Remove animations" on the phone turns off springs, celebrations and the collapsing title movement.
- Fonts download on the phone; until then the phone's own font of the same weight shows (no blank text).

## Built (2.210, 15 November 2026)
- Colours and surfaces: `Design.kt` (`QuietColours`, Berry first in `ACCENTS`, `startPalette` in `Data.kt`); font: Plus
  Jakarta Sans (`jakarta`, credited in `Licences.kt`); squircles: `Squircle.kt` (cards, panels, dialogs, fields,
  Material's shapes in Quiet).
- Icons: `OwnIcons.kt` (Quiet 1.8, chaos 2.3), all through `AppIcons`; filled versions for the chosen tab; the eyes
  of private wishes redrawn; no file uses Google's icon set any more (`OwnIconsTest`).
- Tap feel: `Feel.kt` (`SoftPress` instead of the ripple everywhere, `pressScale` on buttons and icon buttons).
- Own parts: `QuietParts.kt` (switch, dialog: the filled main action only when there is also a cancel; a lone button
  is tonal), large collapsing titles and round top-bar buttons (`TopAppBar`, `IconButton` in `Styled.kt`; one-line
  bars for chats, selecting, Home's wordmark and titles with a second line).
- Menus: `SheetGroup` and the tinted icon tiles in `WishSheet.kt` (a wish's sheet, the profile panel, "I have it",
  "From which list?").
- Celebrations: `Celebrate.kt` (reserving: confetti and the one new vibration; a price drop: the price counts down
  on the price page). Vibration now only: starting to select (long press) and reserving a gift.
- Voice: the seven most-seen texts (empty pages, "Theme applied"); the rest still to do, screen by screen.
- Not done, honestly: no icon tile above dialog titles (dialogs don't say which icon fits); App settings rows have no
  icons (they were already grouped); the web pages are unchanged; wordmark and app icon later (owner). The fonts can't
  be seen in the screenshots here (they download on the phone).
- Checks: `IdentityTest` (squircle geometry, Berry readable and first, warm surfaces, Berry only for new phones,
  sheet groups), `OwnIconsTest`, screenshots incl. a dialog (`ScreenshotTest.dialog`).
