# Decisions: look and feel (archive)

Older sections of `docs/decisions/look.md`, moved word for word. Only searched.

## Six more themes (owner, 13 November 2026, built in 2.200)
- **Added, from https://www.kittl.com/blogs/design-styles-name-stl/**: Conceptual sketch (doodle art), Farmhouse or
  cottage-core, Japandi, Risograph, Synthwave, Anthropomorphic. Same rules as the six before (own colours, light and
  dark equally good, every part themed, fonts fitting the theme, no moving effects, text on calm surfaces).
- Built right away, before the owner had tried 2.199 (owner's choice; Claude advised waiting): problems found in 2.199
  get fixed in all styles at once.
- **Anthropomorphic's character: a gift box with a face** (the app's gift brought to life: eyes, a smile, little arms,
  the bow on its head), drawn in our own code. It shows on **empty pages and wishes without a photo** and **peeks over
  the top edge of cards** (Home's profile card and the list's header card; not on every wish card, to keep lists calm).
  Not next to the wordmark and not as the spinner (owner skipped those).
- **Lettering effects on big titles only**: Risograph's misaligned colour copy and Synthwave's neon glow go on the
  Wishlist title, screen titles and section headings; wish names, prices, chat and settings stay plain.

## Swiss, British Punk, colour sets and wild cards (owner, 13 November 2026, built in 2.201)
- **Added from https://uiuxshowcase.com/blog/which-graphic-design-styles-every-designer-should-know/**: 3 Swiss Design
  and 5 British Punk. Swiss: white, black and Swiss red (dark: black page, white type, the same red). Punk: **no Union
  Jack** (punk colours: black-and-white photocopy, red, blue, safety-pin yellow, torn paper, ransom-note letters on
  big titles only).
- **Every style gets at least 3 colour sets to pick from** (owner: "so that picking a theme is not so much what you see
  is what you get"); logical sets that fit the style (Claude picks them, named in the Themer). Quiet and Gen Z chaos
  keep their accent colours (5 + any colour).
- **Wild placement** ("like Gen Z chaos but not a one-on-one copy"): British Punk (strongest: cut-outs tilted and
  nudged out of line, taped down), Grunge (more crooked), Doodle (loose sketches), Risograph (layers nudged sideways).
  The owner's answer on skewing was "like Gen Z" (cards turn; no slanted text).
- **Recheck every style** so its buttons, cards and other parts have something only that style has, not just colours
  (owner: "whether the buttons, cards etc could be more themed or designed in a way that is unique to that style").

## Deep themes, one at a time (owner, 13 November 2026, after 2.201)
- Owner: the colour sets "only change a few accent colours"; expected "a theme within a theme"; themes felt like "the
  bare minimum" instead of "proper heavy feature-rich themes with options to personalise"; Doodle "isn't doodly
  enough"; **scrolling lags in Doodle** ("worryingly slow"). Claude agreed: 16 themes got a thin layer each.
- **Plan (owner's choice): Doodle first, then the others one or two per update** in the order the owner picks, each
  rebuilt to the Doodle standard once the owner is happy with Doodle.
- **A colour set is a theme within the theme**: it changes the page, cards and colours throughout, the shapes of cards
  and buttons, and the drawings and textures (not the fonts).
- **Options inside a theme** (Themer, under the sets): **amount of decoration** (Calm, Normal, Wild: drawings behind
  the screens and how much cards tilt) and **card style** (e.g. Outlined, Filled, Sticker).
- **Doodle**: **everything hand-drawn** (icons, tick boxes, switches, buttons, the bottom bar, dividers as marker
  scribbles) and **sketchbook paper** (lined or graph paper, margins, tape and paper clips). Handwriting on all text
  and doodles around content were not chosen.
- **Speed first**: fix the lag in every style, and a check that keeps it fixed.
- **Y2K is the second deep theme** (owner, 13 November 2026: "pick a theme"; Claude picked Y2K: three really different
  sub-worlds, one of the thinnest, and the opposite of Doodle; Gen Z chaos waits for the partner's input). Sub-themes:
  **Holographic**, **Liquid chrome**, **Bubbly (Frutiger Aero)**. Own parts: **glossy jelly buttons and switch**,
  **star and sparkle icons**, **stickers on cards** (stars, hearts, planets; more at Wild). Card styles: **Glass**
  (see-through frosted panels), **Glossy** (solid with a shine), **Sticker** (white, thick outline, holo shadow).
  Not chosen: chrome lettering on titles, an early-web sub-theme.
- **British Punk is the third deep theme** (owner: "next"; Claude picked Punk: the owner's own "wild" example).
  Sub-themes: **Photocopy zine**, **Gig poster**, **Safety pin & tartan**. Own parts: **cut-out buttons** (paper strips
  taped down, a little crooked) and **stamped chips**; **safety pins, staples and tape** holding cards (per sub-theme,
  more at Wild); **ransom-note screen titles** (not only the Wishlist title). Card styles: **Cut-out**, **Taped flyer**,
  **Badge**. Not chosen: punk icons (the icons stay), a spray-paint sub-theme.
- **Retro vintage is the fourth deep theme** (owner: "next"; 14 November 2026). Sub-themes: **50s diner**, **70s
  groovy**, **Vintage travel poster**. Own parts: **ticket and label buttons** (notched ends; chosen chips like a luggage
  tag), **stamps and badges on cards** (per sub-theme, more at Wild), **retro icons** (thick, rounded sign-painter
  style). Card styles: **Ticket**, **Postcard**, **Enamel sign**. Not chosen: neon-sign titles, an old-cinema sub-theme.

## The new themes (owner, 13 November 2026)
- **Removed: Cyberpunk, Neobrutalism, Neumorphism** ("poorly implemented: poppy in one area, bland in another").
  People who had one move to **Quiet** (their light/dark stays). Gen Z chaos is the benchmark: a theme must change
  absolutely every element of the app (buttons, fonts, text boxes, menus, tick boxes, sheets, message bars, spinners,
  everything) without hurting accessibility, readability or good UI.
- **Added, from https://www.magier.com/blog/graphic-design-styles** (numbers on that page): 5 Y2K Evolution, 6 Retro
  Vintage, 9 Pop Futurism, 13 Grunge, 18 Flat design, 20 Bauhaus. Final list: Quiet (default for new installs, also
  fully themed) + Gen Z chaos + those six.
- **Colours:** each new theme has its own fixed palette; the 5 accents stay for Quiet and Gen Z chaos only (the accent
  row is hidden for the others).
- **Light and dark:** every theme gets both, designed per theme; "make absolutely sure each theme looks as good in
  light mode as it does in dark mode and vice versa".
- **Fonts:** Claude picks, "truly fitting the theme"; headings in the style's character, longer text in a clean font
  that fits; picks listed in the changelog. Offline: a close fallback until the download finishes (no bundled fonts).
- **Decoration:** full (backgrounds, empty pages, spinners, the wordmark), all drawn in our own code, never behind
  text: text always sits on a calm, solid surface. **No moving effects.** Photos sit on a calm frame that fits the theme.
- **Gen Z chaos:** finish it (the parts that were plain Android get chaos styling); nothing that was chaos changes.
  The saved copy in `themes/gen-z-chaos/` stays the backup of the 2.198 version.
- **App only:** the friends' web pages don't follow the themes. **One update** with everything, no previews.
