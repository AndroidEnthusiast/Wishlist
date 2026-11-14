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

## Next: the design plan (step 3)
Tokens (colours light and dark, type scale, corners, lines, depth, spacing, motion), every component of the inventory
in `docs/DESIGN-STYLES.md` with its states, every screen, personalisation, and mock-ups of the key screens in light
and dark for the owner to approve before anything is built.
