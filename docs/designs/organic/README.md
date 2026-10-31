# The "organic" design (the owner liked it)

Made in an earlier claude.ai chat as a design canvas: https://claude.ai/artifact/25P97s8scneAwjrmwUz4hy
(three phone screens, 390 x 844: light, dark, AMOLED black). `Main.dc.html` here is a copy of its main screen, for
reference only: it is not used by the app or the web pages. It still shows things that changed since ("Most wanted",
the "Images only" view): use today's features and wording, only take the look from it.

## The look
- **Mood**: warm, natural, soft. Off-white paper background, sage green as the main colour, earthy tints.
- **Fonts**: Fraunces (a soft serif, `SOFT 100`, weight 600) for the list name and the prices; Nunito (rounded sans,
  400–800) for everything else. Small uppercase label above the title ("7 WISHES · 2 CHEAPER", 12 px, letter-spaced).
- **Shapes**: "pebble" corners that differ per corner, e.g. cards `30px 30px 30px 12px` and the next card
  `30px 12px 30px 30px`, pictures inside them `24px 24px 24px 8px`; badges `14px 14px 14px 4px`; the Add wish button
  `22px 30px 26px 30px`. Two soft blob shapes behind the header (irregular `border-radius` like
  `58% 42% 63% 37% / 46% 58% 42% 54%`).
- **Cards**: surface colour with a 1 px line, 10 px padding, the picture area on a soft tint (sage / sand / lilac
  in turn), price in Fraunces 24 px (green when cheaper, old price struck through).
- **Controls**: pill-shaped category button and sort button with a 1 px line; the view switch is a pill group with
  the chosen view filled in the main colour; icons are thin outlined strokes (1.8–2 px).
- **Add wish**: a pebble-shaped extended button in the main colour with a soft shadow.
- **Navigation**: a Material 3 bottom bar (My wishlist, Shared, Friends, Settings) instead of the side menu, the
  chosen one with a pill highlight. (Would be a big change to the app's navigation: ask the owner.)

## Colours
| | Light | Dark | AMOLED |
|---|---|---|---|
| background | #F3EEE4 | #191A15 | #000000 |
| surface (cards) | #FFFCF6 | #23251E | #0C0D0A |
| line | #E3DBCC | #34372C | #26281F |
| text | #2A2B24 | #ECE7DB | #ECE7DB |
| muted text | #66675B | #B2AD9E | #ABA697 |
| main colour (sage) | #4F6B4A | #A9C59E | #A9C59E |
| on main colour | #FFFFFF | #1A2A17 | #1A2A17 |
| container | #D9E5CF | #38492F | #2C3A25 |
| on container | #1F3320 | #D9E8CC | #D9E8CC |
| blob A / blob B | #E2E8D3 / #EEDFC9 | #22291D / #2A251C | #0F140C / #14110C |
| bottom bar | #FBF7EF | #1E201A | #000000 |
| price drop / rise | #2F7A4A / #B4432F | #4FA36C / #E07A63 | same as dark |
| top wish gold | #E9B949 (text #3B2A00) | same | same |
| picture tints | #E5ECDB, #F2E5D3, #E9E2EF | #2D3526, #352E24, #2F2B35 | #161B12, #1C1812, #19161D |
