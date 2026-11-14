# Quiet: style brief and design plan

Step 2 of `docs/DESIGN-STYLES.md` (owner, 14 November 2026: "Let's polish both", Quiet first, Gen Z chaos after it
with the partner). The owner's three words: **calm, precise, premium**. Every screen gets the same care (owner: "my
whole app should get the most care, not specific pages"). Weak spots: the owner asked Claude to judge ("You judge and
tell me"). History: Quiet was chosen on 2 November 2026 (references Pinterest / Airbnb, Apple Wallet and Photos,
Revolut / Monzo; `docs/decisions/look.md`, "Premium identity").

## Where Quiet falls short today (Claude's verdict, from the screenshots of 2.206)
1. **Wish cards: a frame inside a frame.** The photo sits on a tinted square with its own shadow, inside a white card
   with its own shadow; the tint runs into the card's edge. Two surfaces glued together, and in dark the tints go muddy.
2. **Cards are tall and mostly empty, and noisy at the bottom.** The name is small and regular, the price only a
   little bolder; under them a row of three icons (⋮, share, bin) on every card. The eye lands on the icons, not the
   wish. The star sits in a heavy dark disc on the photo; when selecting, a second circle joins it.
3. **The type does not lead.** Many sizes close together (10, 11, 12, 13, 14 sp), labels like "Wishes" and "Total
   value" tiny and grey, a list's name on Home 13 sp. Premium apps use fewer sizes with bigger steps.
4. **No colour discipline.** Filled buttons are indigo in one place (Try again), black in another (Messages: Chats,
   Add friends) and lavender elsewhere; the recovery card is lavender; the Secret Santa card is a loud red gradient with
   an emoji and snow dots; the + button glows in colour, the only coloured shadow in the app.
5. **The same numbers twice.** Home's profile card and a list's header both show "5 Wishes · €345.45 Total value";
   "+ Add friends" is squeezed in beside them in tiny accent text.
6. **Rhythm and alignment.** "Create list" (a dashed box) floats in the middle beside the list tile; gaps between
   Home's sections differ; corners differ (12, 16, 22, 28 dp) without a system.
7. **Inverted hierarchy in places.** Friends: the label "Your friend code" is small, the error under it big and grey.
8. **Not yet seen here:** the screenshots of Add wish, a wish's sheet, All your wishes and a chat land on the wrong
   screen. Fixing those screenshots is the first piece of work, so every screen can be judged and checked.

## Style brief: what Quiet is
- **Content first.** The photo, the name and the price lead; everything else steps back. A card shows the wish, not
  its buttons.
- **One accent, used sparingly.** The accent marks the one main action on a screen and what is chosen; text, icons
  and lines are ink and greys. One kind of filled button everywhere.
- **Type makes the hierarchy.** Fraunces (soft serif) for titles, Figtree for everything else; a short scale with
  clear steps; prices in figures of equal width so they line up.
- **Calm surfaces.** White cards on a soft grey page (light); raised dark grey on true black (dark). One family of
  corners. Depth from the page colour and hairlines; a soft shadow only for things that float (bottom bar, panels,
  menus). A photo sits on one calm surface, never a frame in a frame.
- **Precise spacing.** A 4 dp grid, 16 dp screen edges, the same gaps between the same things on every screen;
  everything aligns to a few lines.
- **Premium moments, few and small.** The wordmark, the profile header, a price drop, the + button, the empty pages:
  each done with care rather than decoration.
- **Light and dark both first-class**, each designed (not inverted), contrast measured.

## What is NOT Quiet (the clichés to avoid)
Gradients, coloured or glowing shadows, emoji as decoration, black buttons beside accent buttons, more than one filled
button in one place, dashed boxes, badges everywhere, banners that shout, frames inside frames, tiny grey labels.

## The owner's answers (14 November 2026)
- The brief is approved: go on to the design plan.
- Wish cards keep ⋮, share and bin, made quieter (smaller, lighter, less weight than the wish).
- The Secret Santa card stays festive, fitted to the style (no shouting: festive through colour and a careful
  drawing, not effects).
- Personalisation: the accent colour (5 and your own) and light or dark; nothing more.

## The design plan (step 3, 14 November 2026, waiting for the owner's approval)
Mock-ups of every key screen, light and dark: `docs/styles/quiet-mockups.html`, published at
https://claude.ai/artifact/QUmdTreBKMernTMXcRffGh (fonts show there; the app's screenshots can't download them).
Correction to verdict point 5: Home's numbers are all lists together and a list's header only that list; they only
match when there is one list, so both stay (Home gets a third number, Lists).

- **Colours, light**: page #F2F2F7, card #FFFFFF, photo surface #F4F4F7, ink #111114, grey text #6C6C74, line
  #E5E5EA, field line #8E8E93, accent = your colour (Indigo #4C4CD6), soft accent 12 % (tonal buttons, chosen chips,
  icon circles), cheaper #1F8A4C, dearer #C4321F (badges on a 12 % wash of the same colour).
- **Colours, dark**: page #000000, card #1C1C1E, raised #2C2C2E (bottom bar, panels, menus, dialogs), photo surface
  #2A2A2D, ink #F5F5F7, grey text #98989F, line #2C2C2E, field line #7C7C82, accent the dark tone (#8585FF) with dark
  text on it, cheaper #4CD98A, dearer #FF7B6E.
- **Type**: Fraunces SemiBold 28 (main tabs' titles, the wordmark), 20 (screen titles, sections, dialogs, empty
  pages), Figtree: price Bold 17 (equal-width figures), name Medium 16, text 15, detail 13, nothing under 12.
- **Shapes**: cards 20, fields 14, panels and dialogs 28, buttons and chips fully round; one photo surface clipped by
  the card (no frame in a frame).
- **Depth**: cards have no shadow (white on grey; dark grey on black); only floating things (bottom bar, panels,
  menus, dialogs, the message bar) get a soft neutral shadow (dark: a raised grey and a hairline). The + button keeps a
  small neutral shadow, no coloured glow.
- **Spacing**: 16 screen edges, 12 between cards, 28 between sections, 16 inside cards; a 4 dp grid.
- **Buttons**: filled (the one main action per area), tonal (second), text (third); 48 tall (40 in tight rows, still
  48 to tap). No black buttons. Tabs like Chats / Friends and All wishes / Top wishes: a segmented switch.
- **Wish cards**: rows (photo 116 wide on the left), two per row (square photo on top), large cards (4 : 3 photo);
  shop · category in grey, the name, the price with the old price and a badge when it changed; ⋮, share and bin in
  grey at 20 dp (owner: keep all three, quieter); the star a small white disc, filled in the accent for a top wish.
- **Selecting**: chosen cards a 2 dp accent line and a light accent wash with a filled tick; the others an empty
  circle.
- **A wish's sheet**: a header with photo, name and price; rows of 52; Private and I have it as switches; Delete red.
- **Add a wish**: labels above the fields; Exactly this / Similar is fine as chips that show the choice; Save a
  full-width button at the bottom, ✕ to cancel; section titles in Fraunces.
- **Chat**: your messages in the accent, theirs white (dark: card grey), 20 round with a 6 corner at the tail; the
  day as a small pill; a round message field; the send button fills with the accent when there is text.
- **Home**: the recovery-key card calm (lock in a soft circle, tonal button, Later as text); the profile card with
  name in Fraunces, Add friends as a tonal button and three even numbers; list tiles without shadow and Create list as
  a tile of the same size; the Secret Santa card festive red, one solid colour, a drawn gift (no emoji, no snow).
- **App settings**: grouped white blocks with small grey titles, rows of equal height, switches in the accent.
- **Empty pages**: a drawn icon in a soft accent circle, a Fraunces title, one line, one filled button.
- **Message bar**: ink, with its action in the accent.

## Next
The owner approves or changes the plan (step 3 ✋), then it is built as one unit (step 4) and checked (step 5).
