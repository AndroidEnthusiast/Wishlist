# Design styles: how they are made

The owner's bar (14 November 2026, after twelve styles were removed): a design style is implemented "as though a
professional would have done it": **everything styled, a true integration that feels native to the app and not
shoehorned in**, with the best practices that matter for this app, "not a quick one-off implementation", "the highest
standards", and **personalisation features that are well thought out**. One design style at a time. Read this whole
doc before starting one; the decisions behind it are in `docs/decisions/look.md`.

## What went wrong before (so it doesn't happen again)
- **Breadth over depth.** Sixteen styles in two days, each a palette, a shape and a background drawing. A style is a
  whole design system, not a skin.
- **Swapping colours and calling it a style.** Colour sets that only changed the accent; "options" (Calm, Normal,
  Wild) whose steps were barely visible.
- **Decorations bolted on.** Stickers, tape and pins drawn over generic cards instead of components designed in the
  style from the start.
- **No design before code.** No written study of the style, no specification, no review with the owner before
  building; problems were found in screenshots afterwards, one at a time.
- **Code spread thin.** Every style was a branch in a dozen `when` blocks across many files: hard to see what a style
  is, easy to forget parts, costly to keep up.
- **Speed as an afterthought.** Scrolling lagged until it was measured by the owner on the phone.

## The process (owner checkpoints marked ✋)
1. **Choose and brief.** The owner picks the style. Ask (pop-up questions) for references (links, pictures, apps they
   like), three words for the feeling, and which screens matter most to them. ✋
2. **Study the style.** Its history, its principles and its real examples: structure and grid, type, colour, shape,
   depth, texture, illustration, iconography, motion, tone of voice. Write a short **style brief**: the principles in
   one page, and what is *not* this style (the clichés to avoid). Show it to the owner. ✋
3. **Translate it into this app.** A **design specification** (`docs/styles/<style>.md`):
   - **Tokens**: colour roles for light and dark (each designed, not inverted; contrast measured), type scale and
     fonts (why each fits; SIL OFL only; a close fallback), shape scale, line weights, depth model (shadows or none),
     texture and pattern rules, spacing changes if any, motion (short, calm, off with "Remove animations").
   - **Every component** in `docs/STYLE.md`'s inventory below, with a sketch or exact description: how it looks at
     rest, pressed, chosen, disabled, in error, in light and dark.
   - **Every screen** in the inventory below: what changes, what the style adds (its signature moments), what stays
     calm so content can lead.
   - **Personalisation**: which options truly fit this style (not the same three for every style), and for each step
     the exact, visible differences; an option that changes too little is dropped, not shipped.
   - Show the specification to the owner, with mock-ups of the key screens (Home, a list in every view, a wish's
     sheet, a chat, App settings, an empty page) in light and dark. Ask whether they want to see them first; build
     only what was agreed. ✋
4. **Build it as one unit.** One file (or folder) per style that implements the app's style contract (tokens and the
   painters for each component), not branches scattered across screens. Shared parts read the contract. Performance
   rules from the start (below). New screens later get the style for free through the contract.
5. **Verify like a professional.** Screenshots of every screen × light and dark × every option, at 360 dp and at the
   largest font; compared against the specification point by point; `LookTest` (contrast), `PerformanceRulesTest`
   (speed), `PartsScreenshotTest` (every component); TalkBack labels; "Remove animations". Fix everything found, then
   say plainly what can't be checked here (real fonts, real photos, how it scrolls on a phone).
6. **The owner tries it on the phone.** ✋ Iterate until it feels right. Only then the next style.

## Best practices that matter for this app
- **The wishes are the content.** Photos of products in every colour, names of every length, prices and price changes:
  the style frames them and never competes with them. A photo always sits on a calm surface; a name and its price are
  always the easiest things to read on a card.
- **Lists are scanned, not read.** Rhythm, alignment and contrast first; decoration at the edges and in empty space,
  never under text or over a button. Two per row, rows and large cards all get the same care.
- **Other people's content.** Friends' names, photos, notes and chat come in any length, language and emoji; the style
  must survive all of it (long names, no photo, an emoji-only message, a right-to-left name).
- **Meaning stays the same in every style.** A cheaper price is green and a dearer one red; reserved, owned, private,
  in the bin: each keeps its colour or sign so nobody has to relearn the app.
- **Readable and usable for everyone.** Text at least 4.5 : 1 (3 : 1 for large text and for the edges of controls),
  touch areas 48 dp, the phone's font size up to 130 % (and larger without breaking), TalkBack names for everything
  that is only drawn, light and dark both first-class.
- **Fast on an ordinary phone.** Lists of cards with photos scroll at the phone's frame rate: shapes and drawings are
  made once per size (`drawWithCache`), no `Modifier.border` on irregular shapes, no picture per card, textures drawn
  once. Checked by `PerformanceRulesTest`; the owner confirms on the phone.
- **Works without the internet.** Fonts download; until then a close font of the phone shows and nothing jumps.
- **Fits the rest of the app.** Quiet stays the calm default and Gen Z chaos the partner's favourite; a new style
  must not make either worse. Web pages stay as they are (owner, 13 November 2026).

## Inventory: what a design style must cover
- **Components**: top bar (and its line), bottom bar and its + button, Add wish button, buttons (filled, outlined,
  text; screen and dialog sizes), switches, tick boxes, choice circles, chips (category, view, filter), text fields
  (with label, help text, error), menus, dialogs, bottom panels (a wish's sheet, the profile), message bar (Undo),
  spinners and progress bars, dividers, badges (price change, owned, reserved, private), photo frames and the Blur
  stage, wish cards (two per row, rows, large cards), list header (numbers, occasion), Home's blocks (profile card,
  wishlists, group lists, tiles), chat bubbles and the chat field, avatars, empty pages and pictures without a photo,
  icons, the wordmark, the page background.
- **Screens**: Home, Wishlists, All your wishes, Top wishes, a list (every view, selecting, search), a friend's list,
  a collab list, Add / Edit wish, a wish's sheet, Price changes, Received gifts, the Bin, Messages and a chat, Friends
  and a friend's page, Gift exchange, Groups, My shops and the in-app browser, Notifications, Profile, App settings,
  the Themer, Welcome and the recovery key.

## How the owner can help (owner: "help me to help you")
- Send references: links or screenshots of designs, apps or posters that feel right, and ones that feel wrong.
- Name the feeling in a few words, and the screens you use most.
- Review the style brief and the specification before anything is built, and say what's missing.
- On the phone, send screenshots of anything that feels off, with the screen and the setting it was in.

## Definition of done
Everything in the inventory is designed in the style, light and dark; every option makes a visible difference that
the owner agreed to; all checks pass; screenshots of every screen were looked at; the specification is up to date; the
owner has tried it on the phone and is happy.
