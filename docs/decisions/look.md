# Decisions: look and feel, the app's identity and screen movement

Moved here word for word from `docs/decisions/screens.md` on 12 November 2026 (it passed its size limit). Index:
`docs/DECISIONS.md`. Add new decisions about how the app looks or moves here.

## Look and feel
- **Themes apply to the app only.** App settings > Theme: "Standard themes" Light, Dark and AMOLED black (the original look, indigo, Google Sans), then the Themer button for the four designs. No "System default" (removed in 2.53 to save space): the first start quietly picks Light or Dark to match the phone.
- **No font choice** (2.53): the standard themes use Google Sans, each design its own fonts. Choosing your own font is a "nice to have" for later.
- **Themer layout** (2.53): a big preview titled "Preview" whose top (name, category, views) stays put while the wishes scroll; under it the designs side by side (swipe), tapping one uses it and pops up its colour sets; tapping "Standard" pops up Light, Dark and AMOLED black (2.55).
- **The four designs are extra themes, complete styles of their own** (owner, 31 October 2026, after 2.51 only changed cards and colours): Material 3 (Expressive, Android 16), Organic, Neobrutalism and Claymorphism, from the owner's reference pictures. Each has its own colour sets (5 light, 1 dark), fonts, icon style and the look of buttons, switches, dialogs, top bar, side menu, labels and wishes, across the whole app. The standard theme settings don't apply to them. See `Design.kt` and `Styled.kt`.
- **No number of wishes above the list name** (2.60): Organic had a small "7 WISHES" line there; the owner wants the list title the same in every theme.
- **Background price checks** (2.62): price alerts waited until the app was opened (Android battery limits overnight). A one-time question "Allow price checks in the background?" leads to Android's own confirmation, which makes Wishlist Unrestricted (an app cannot do that by itself). App settings shows an Allow line only while it is not allowed.
- **Neobrutalism Midnight has bright cards with dark text** and photos on the dark background (2.62), like the other colour sets.
- **Shop photos as small cards on a coloured area** (2.63, owner chose option C): the picture area of a wish takes a soft colour of the theme and the photo sits on it as a rounded card with a light shadow, in every theme. Chosen over filling the area with the photo (cuts products off) and a blurred copy behind it (stays white for white photos).
- **Copying wishes between your own lists** (2.66): optional in the New wishlist form ("Add wishes from your other lists") and in a list's settings; no extra card on an empty list. Copies, not moves. Removing the original removes its copies too (and whoever reserved a copy is told it's gone, as for any removed gift); removing a copy only removes that copy. Group wishlists are not a source (they hold ideas for someone else); "inspiration for others" may come later, but Friends is the better way to share gift ideas.
- **"Added on Thursday 01-12-26"** in small italics on every wish card and on the Prices page (2.67; "Wish added on 01/11/26" of 2.66 looked awful with slashes and "wish" is implied).
- **Notifications can be swiped away** (left or right) to delete them, with "Notification deleted" and Undo (2.84); a bin icon top right deletes them all after "Delete all notifications?" No / Yes, with Undo (2.85). While you swipe, a red block with a white bin icon grows on the uncovered side (2.86, owner).
- **Notifications page** (2.67, owner's wording): menu item above Bin with a red dot (also on the menu button) while something is new. Each notification: the wish's small picture, its name, "decreased in price" or "increased in price" (nothing more), and the percentage in bold green or red on the right. Drops and rises only as switched on in App settings.
- **Standard themes have colour sets too** (2.65, owner): in the Themer, Standard > Light, Dark or AMOLED black, then that mode's colour sets (the six of `PALETTES`, in tones made for light or dark; AMOLED uses the dark tones on black). App settings > Theme only has the Themer button. Photos in Dark and AMOLED sit on a quiet colour (the full accent colour was too bright).
- **Dashboard details** (2.65): small + and pencil buttons; "Total value" of a list's wishes instead of "Shared with friends"; "Recently opened" smaller than the list sections.
- **The dashboard picture is the "cover photo"** (2.68) and is edited before saving, like on Marktplaats: rotate, crop (zoom and move), lighter/darker slider, a text box (colour, box behind it, position), and the arrow up for a new photo (no bin icon there). Then the tick. Since 2.75 (rebuilt simply, the owner: "this little feature needs to work flawlessly"): the frame stands still in the middle in the cover's shape; you move the photo behind it with one finger at any zoom and pinch to zoom; the photo always fills the frame; on saving, exactly the part in the frame is cut out and shown; what is inside the frame is the cover; no zoom slider; tap the text for corners that resize it. (2.69: the photo moved and zoomed straight away (the part outside the frame dimmed), no instruction text; text is only dragged freely (no Top/Middle/Bottom); a rainbow dot next to the six colours opens a colour ball; "Box" sits right after the colours.)
- **Only icons and fonts we may use** (2.72, owner: "100% sure"): Google's Material Icons (Apache 2.0), icons drawn in our own code, the phone's emoji; fonts under the SIL Open Font License (Google Sans built in, the theme fonts from Google Fonts). Never copy an icon or picture from the internet: draw our own version. The licence texts are in App settings > About > Licences; a new open-source part must be added there.
- **"New wishlist" icon** (2.72): a page with a heart and two lines (drawn by us after an example the owner liked), one colour like the other bar icons. Not a gift (the + in the middle is for adding a wish).
- **Managing lists from Home** (2.79, owner): long press a list for a small menu right at the list (since 2.85, no big dialog): Edit name and Delete wishlist. Deleting your own list moves its wishes to the bin (10 days); restoring such a wish asks which list it should go to, or makes a new one. Deleting a group wishlist is for everyone (only its creator; others can leave it). You always keep at least one list.
- **Home shows "Price changes"** (2.79): cheaper wishes first, then dearer ones; square picture, Old and New price, the percentage.
- **Target prices** (2.77, owner; layout since 2.80): in a wish's price history, under "Tracking since…", a bold "Target price" with an amount field: the keyboard's tick saves it: the frame goes, the amount shows in bold with the green tick (tap it to change it); ✕ removes it and brings the field back; below it "You’ll get a notification when the price drops to your target or lower." (2.82). A target must be below the price now; gold only when the price is at or below it. When reached: a notification and a Notifications entry that open the price history, where the target and the price show in gold and bold with a soft glow; "View product" opens the shop. No "lowest ever" labels (we only know prices since the wish was added). Prices tab rows show both prices ("€89 → €79").
- **Text fields start with a capital letter** (names, list names, notes, wish names) (2.77).
- **Shared lists are live** (owner, 2.76): every change to a list with a friends' link goes online (friends see new wishes); private wishes never do.
- **Depth** (owner, 2.96, chosen in a preview with Subtle / Medium / Strong: Medium): pages a little deeper in colour, cards lighter with a soft shadow (dark: lighter cards with a faint edge), in every theme in its own style. The numbers next to your picture get separate, small and quiet tiles (owner: "separate tiles but more subtle"), also on a friend's page.
- **Readable colours** (2.71): text never uses a colour that is too light for its background (Neobrutalism's yellow etc. only fill blocks; text and small controls use the ink).
- **Bottom bar** (2.65; "New wishlist" with a gift icon since 2.71): Home, New wishlist, Add wish (just a +), Prices (price history of all wishes), Friends (coming later). The side menu stays.
- **No default category** (2.64): a new wish starts with "No category"; people pick one themselves if they want.
- **Several wishlists of your own and a dashboard** (2.64, owner's request): the app starts on a dashboard with a hero picture (pencil: choose or take a photo), "Recently opened" (swipe sideways), "My wishlists" (+) and "Group wishlists" (+). New lists: a name ("What is this wishlist called?" when empty) and notes, nothing more for now. "New wishlist" and "New group wishlist" are also in the side menu.
- **"Shared wishlists" are now called "Group wishlists"** (2.64): "shared" sounded like your own list that you shared. (Proposed by Claude, the owner can still pick another short name.)
- **Claymorphism must look puffy, not like Material 3** (2.58, from the owner's reference): inflated pillows with inner glow and shade and a soft shadow below, pressed-in picture mats, everything (cards, buttons, chips, labels, dialogs) in clay.
- **The Themer has only a back arrow** (no menu button). Choices there only change the preview; "Apply" (top right, shown when the choice differs from the theme in use) changes the app (2.56: the owner wanted a confirming action).
- **Web pages only offer light and dark** (plus following the device), as a bare minimum. No palettes on the web.
- **The friends' page looks the same as the app and the browser editor**, for both "My wishlist" and shared lists.
- **The side menu highlights only the screen that is open** (2.84).
- **List settings vs App settings**: each list has its own settings (opened with the settings button where the refresh button used to be); things that are about the whole app sit in "App settings". Pull down to refresh; the refresh circle is hidden when idle, and shows for at most about 2.5 s (2.61): the price check carries on in the background and a message says when it is done.
- **Wording settled by the owner**: "+ Add wish"; "Instant delete wishes" ("Turn on to delete wishes without warning"); under Price alerts a sub-heading "Notifications" with "Price drops" (on by default) and "Price surges" (off by default) (2.54).

- **App settings, Price alerts (2.54)**: "Check every" with the choices as boxes in one row (Hour, 3 hours, 6, 12, 24); no Check now / Test notification / Notification settings / Battery settings buttons.
- **Saving your name (2.54, 2.55)**: no Save button; the keyboard's tick saves it and closes the keyboard, then a small green tick that draws itself with "Saved", gone after about a second. The field is always the same (shorter) width.

- **Two views only: large cards and two per row** (app, editor and friends' page). The "Images only" view was removed (October 2026): it didn't look good and showed too little about a wish.

## Premium identity (2 November 2026, being designed)
- Owner: the app "still feels cheap … like default settings … doesn't have its own feel". Diagnosis given: stock Material parts at stock sizes, no brand (no wordmark, emoji as pictures), flat type, ~40 half-designed theme combinations, busy cards.
- References: Pinterest / Airbnb, Apple (Wallet, Photos), Revolut / Monzo. Mood: warm and personal, elegant and calm, bold and modern.
- **One signature look, in light and dark, plus accent colours**; the five designs and their colour sets go.
- Emoji placeholders become Material icons.
- **Safety net (owner):** if the owner doesn't like the new look, revert the theme to exactly how it was in **v2.96 (commit 123322e)**.
- Preview of three directions (Keepsake, Quiet, Bold): the owner chose **Quiet** (Apple-calm: one clean typeface, Figtree, bold tight headings, grouped lists with hairlines, frosted floating bottom bar, wordmark, true black in dark, little colour).
- **Quiet can be wrapped in three styles: Neobrutalism, Claymorphism, Neumorphism** (owner; reference https://www.uistyleguide.com/style/neumorphism). Same layout, type and spacing as Quiet; only the surfaces change (owner chose "same layout, different skin").
- Neumorphism keeps readable contrast (text, prices and buttons stay strong; accent-coloured buttons), in light and dark.
- Every style: light + dark + the same 5 accents (Blue, Indigo, Green, Orange, Pink).
- Material 3, Organic and the Standard themes are removed; current users move to Quiet with the closest accent.
- Styles changed (owner): **Claymorphism replaced by Retro futurism; Cyberpunk UI and Gen Z chaos maximalism added** (references on uistyleguide.com, which this cloud session can't open). So: Quiet + Neobrutalism, Neumorphism, Retro futurism, Cyberpunk, Gen Z chaos.
- The styles may use everything that belongs to them, including their own fonts everywhere (owner: "Everything from the theme can be applied"); Gen Z chaos goes full chaos (wish rows tilted too); Cyberpunk has a real light mode; every style uses the 5 accents (retro and cyberpunk add a fixed second colour for effects).
- Retro futurism removed after the preview (owner). **Final: Quiet (default) + Neobrutalism, Neumorphism, Cyberpunk, Gen Z chaos**, each light + dark, 5 accents (Blue, Indigo, Green, Orange, Pink). Preview: https://claude.ai/artifact/NiJ5D8P4Xk8JQiJLwBMtKe
- Wish views: both "Large cards" and "Two per row" stay, restyled per style.
- Bottom bar: the second spot becomes **"Wishlists"** (opens the page with all your wishlists), as in the preview; new lists come from Home's + (replaces the earlier "keep New wishlist").

## Screen transitions (owner, 12 November 2026, built in 2.195)
- **Screens move like Signal's** (owner sent a screen recording of Signal: "I kinda want exactly that"; until 2.194
  every change was instant, kept that way in Stages 1–3 only so nothing visible changed). Chosen with pop-up questions:
  a deeper screen (a list, a chat, a friend's page, Profile, App settings…) slides in a short way from the right while
  it fades in, the old one moves a little left and fades, Back reverses it; the bottom bar's tabs fade through (old
  fades out, new fades in growing slightly; the bar stays still); about 0.25 s, like Signal. Places inside a tab that
  are not routes move the same way (the open chat, a friend's page and lists, an exchange, Home's pages, the Themer).
  The bottom bar slides away and back with them (at once for the keyboard). Android's "Remove animations" makes it
  all instant. **Predictive back** (the peek while swiping back): not now (owner). Samsung's own back arrow is the
  phone's, not the app's.
- **Gen Z chaos is saved for good** (owner, 13 November 2026: "my partner absolutely loves the gen z chaos theme;
  make sure I can always recover it"). The theme as it was in 2.198 is kept in `themes/gen-z-chaos/` (whole files +
  how to put it back); `tests/themes/check.sh` fails if that copy changes or disappears. Ask the owner before changing
  or removing Gen Z chaos in the app.

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

## Back to Quiet and Gen Z chaos; design styles to a professional standard (owner, 14 November 2026)
- Owner, after 2.205: "your implementation of the design styles has disappointed me". The Calm/Normal/Wild steps were
  "incredibly minimal" in difference. **Keep Gen Z chaos and Quiet; remove all the other styles** (Y2K, Retro vintage,
  Pop futurism, Grunge, Flat, Bauhaus, Doodle, Cottage-core, Japandi, Risograph, Synthwave, Anthropomorphic, Swiss,
  British Punk). People who had one get Quiet (their light or dark and accent stay). The code is in the git history
  (commit 057fe42, version 2.205) but is not the model for new work.
- **From now on: one design style at a time**, called **design styles** (not themes). Each is implemented "as though a
  professional would have done it": everything styled, a true integration that feels native to the app and not
  shoehorned in; best practices that matter for this app taken into account; "not a quick one-off implementation";
  "the highest standards"; **personalisation features well thought out**. Owner: "help me to help you". The process
  and the bar are in `docs/DESIGN-STYLES.md` (written for this); follow it for every new design style.

## Polishing Quiet, then Gen Z chaos (owner, 14 November 2026)
- "Let's polish both": **Quiet first**, Gen Z chaos after it (with the partner's input), each through every step of
  `docs/DESIGN-STYLES.md`. Claude challenged doing both at once (the breadth mistake); the owner agreed.
- Quiet's three words: **calm, precise, premium**. Every screen gets the same care ("my whole app should get the most
  care, not specific pages"). Weak spots: "You judge and tell me". Claude's verdict and the style brief:
  `docs/styles/quiet.md`.
- Brief approved (owner, 14 November 2026): "Yes, go on to the plan". **Wish cards keep all three buttons (⋮, share,
  bin), but quieter** (Claude suggested only ⋮; the owner kept them). **The Secret Santa card stays festive, "but make
  it fit the style"**. **Quiet's personalisation: colour and light/dark only** (no density or extra dark options).
- **Quiet's design plan approved** (owner, 14 November 2026: "Yes, build it"; `docs/styles/quiet.md`, mock-ups
  https://claude.ai/artifact/QUmdTreBKMernTMXcRffGh). Also chosen: **photos sit on a light photo surface in dark mode
  too** (white shop photos blend in, dark products stay visible); **labels above the fields** (forms a little
  taller); **Private and I have it as switches** in a wish's sheet.
- **Quiet polished, built in 2.207** (14 November 2026) to the approved plan. It replaces, for Quiet only, three
  older choices (chaos keeps them): photos as small cards with a shadow on a coloured area (2.63) → the photo whole on
  one light photo surface; the "+ New …" block smaller than a list (7 November 2026) → a tile of the same size; tiny
  "+ Add friends" in the numbers → a tonal button beside your name. Kept on purpose: Home's numbers stay Friends,
  Wishes, Total value (the mock-up showed Lists; Friends is the existing decision). New for readability: in light,
  your colour used as text is darkened just enough for 4.5 : 1, and the text on filled buttons is white or ink,
  whichever reads better (so green, orange and pink buttons get dark text).

## Fixes from the owner's review of 15 November 2026 (built in 2.209)
- **Gen Z chaos profile card**: name next to the photo, Friends · Wishes · Total value on their own row under it (like
  Quiet), so amounts fit.
- **Gen Z chaos Secret Santa card**: chaos's own parts on festive red (ink line, hard shadow, slight tilt, Bungee
  heading, a drawn gift on a tilted yellow sticker instead of 🎅, a white button with line and shadow).
- **Gen Z chaos message box** ("Theme applied" and every other message): one box in the style, 48 dp for one line;
  Android's own box with its blurry shadow no longer shows inside it.
- Not chosen this time (ask again later): moving chaos's coloured blobs away from text (they sit under
  "Descriptions" and "Allow" in App settings, "Your wishlists" on Home and "Nike" in My shops).
- Bottom panels set their text colour (2.208): Quiet's lifted grey isn't a scheme colour, so text fell back to black.


## The app's own character (owner, 15 November 2026, interviewed in 5 rounds)
- "It should feel like its own app, not a copy cat": all answers, the diagnosis and the safe point (2.209, commit
  `f5a6fc1`) are in `docs/styles/identity.md`. In short: Apple-calm base, Berry #B0245A as the app's own colour,
  squircles, Plus Jakarta Sans, own rounded-line icons, grouped menus with icon tiles, own switch / dialogs / top bars,
  a soft press instead of the ripple, warm neutral surfaces, a warm voice, vibration only at a few moments,
  celebrations on reserving and on price drops. Every style gets the shared feel; wordmark and app icon later.

## Gen Z chaos settings on cards (owner, 16 November 2026, built in 2.214)
- The big coloured circles sat behind text (App settings hard to read). The owner chose solid cards behind settings
  (not moving or fading the circles): App settings, a list's settings and Profile put each group, its title
  included, on a straight (not tilted) card (`ReadingCard` in `Settings.kt`). Lists, Home and chat keep the open look.

## Forms, menus, settings and themes (owner, 17 November 2026; interviewed, plan in `docs/designs/plan-2026-11-17.md`)
- New wishlist chips: "My own" / "With others" (owner). Full-width form sheet with X, no Cancel, swipe down closes at
  once without asking (owner), same height for both kinds; for the + menu, Wishlist settings and a list's ⋮ menu.
- Chips: keep the tick; the text centred between the chip's top and bottom (owner's meaning of "centred").
- Settings: a full screen instead of the profile pop-up. Themes: 5 hand-tuned themes that tint the whole app, plus
  Gen Z chaos as a 6th bonus; the colour wheel goes; a theme screen like Giftful's but our own, without a logo picker.
- Shared ("With others") lists: "Hide reservations" for the collaborators, on by default (owner); co-owners as such
  were not wanted separately ("Not now"), this setting covers the couple's case.
- Step 6 (hide reservations on shared lists) left for now (owner, 17 November 2026); also in `docs/TODO.md`.

## Themes deeper, theme blocks minimal (owner, 18 November 2026, after comparing with Giftful; 2.227; the deep tint was replaced the same evening, see the next section)
- 2.225's gentle tint felt flat and muddy ("the colours don't blend"): parts that weren't tinted (a sheet's grouped rows
  were a fixed grey-violet, the settings tiles grey, the grey text plain grey) clashed with the tinted page. Owner chose:
  **deep, like Giftful**: one hue per theme for everything, only its lightness steps up (page → cards → panels →
  groups in panels), grey text and lines tinted too; **light mode a soft pastel** of the same hue with near-white cards.
  Every text still at least 4.5 : 1 (`ThemesTest`).
- **Theme blocks:** as minimal as Giftful's but our own shape (owner): the theme's page colour, its name in its own
  colour, one gentle hill of its colour along the bottom (not Giftful's wave), a ring and tick when chosen. No mini
  wish card. The owner chose to skip a preview ("just build it").

## Neutral backdrop, colour on the elements (owner, 18 November 2026, after trying 2.227; replaces the deep tint above)
- 2.227's deep tint went wrong: a deep shade of orange is brown, Forest's dark page went green ("not pretty"), light
  mode was "super bright", dark showed faint colours on the backdrop. Owner: "I don't want the backdrop to be coloured
  but only the elements"; it must be "slick and polished and pleasant for the eyes".
- **Backdrop and cards: a matched grey** (owner): neutral grey leaning only about 3% towards the theme's hue, so it
  reads as grey but harmonises. **Light:** a soft grey page with white cards (not glaring). **Dark:** dark grey like
  most apps. **Black** (AMOLED): a 4th Appearance choice, System / Light / Dark / Black; pure black page, deep-grey
  cards. System follows the phone with light and dark grey.
- **The colour shows "clear but calm"** (owner): full colour on the main button, the + button, switches, ticks, chosen
  chips, links and the wordmark; icon tiles a soft tint of it. Cards, sheets and text neutral.
- **Amber is replaced by Teal** (owner: no brownish colours). An old Amber choice becomes Teal.
- **Gen Z chaos block in its real colours** (owner): cream page with dots (light) or black (dark), a hot-pink hill with a
  thick black edge, a lime and a yellow ball, the name in Bungee.
- **No jump on the Theme screen** (owner's recording): switching between Quiet and Gen Z chaos must not move the blocks;
  the Theme screen keeps the same header height in every style.


## A layout inspector on every build (owner, 20 November 2026: "make absolutely sure mistakes like these aren't anywhere else … I'm tired of finding out afterwards")
- The owner kept finding cut-off text, labels on top of each other and prices broken over two lines after updates.
  From now on an automatic inspector opens the app's screens on a narrow phone, a normal phone and with large text, in
  both styles, light and dark, every list view, with long names and prices, and fails the build when text is cut off,
  a word or number is broken over two lines, two texts overlap, or something sticks out of the screen. Everything it
  finds is fixed before an update goes out. It can't judge taste (balance, spacing): the pictures are still looked at.
- **On GitHub, side by side, and it blocks** (owner): the inspector runs on a second GitHub computer at the same time
  as the app's checks; the new app is only published when both passed (a build takes about 8 minutes instead of 6).

## One font for numbers (owner, 21 November 2026: "the font needs to be the same throughout the whole app")
- The list summary showed "€470.00" in the title font (Fraunces) while Home showed it in the text font. The owner was
  shown the three choices drawn with the real fonts and chose: **titles keep the heading font; every number and price,
  everywhere, is in the text font** (Plus Jakarta Sans in Quiet; Gen Z chaos keeps Bungee for both), with figures of
  equal width. In code: `TextStyle.number(look)` (`DesignType.kt`); `FontRulesTest` fails when a number uses a title
  style without it.
- **People's big names in the title font too** (owner, 21 November 2026: "one of the major titles where a different
  app unique font would fit"): your name on Home and Profile, and a friend's name at the top of their page, like a
  wishlist's name. Small names in rows, chats and cards stay in the text font. `FontRulesTest` checks it.

## One rule for buttons and taps, and Move or copy action-first (owner, 21 November 2026)
- The owner's recording: in Quiet, "Move or copy?" showed Copy and Cancel with an empty gap: Move was there but
  invisible (two buttons in one full-width slot). His friend's Gen Z chaos showed plain words in a row. Owner: one
  style guide, uniform buttons and clicks.
- **Every dialog, every style: buttons stacked and full width; main filled, a second choice outlined
  (`secondButton`), the way out plain words at the bottom; a lone button is the way out, so plain words** (owner chose
  this over words in a row). Gen Z chaos dialogs changed to this too. Choosing between actions = choice rows (icon
  tile, word, one line); pickers show the wish on top. Written in `docs/STYLE.md` ("Dialogs"), drawn on the owner's
  style guide page (https://claude.ai/artifact/CfZBnhRcVPydi8YVtjfV2H), checked by `DialogRulesTest`.
- **Move or copy: the action first, then the wishlist** (owner chose "choose first" of three drawn options, "but make
  sure the wish it's about is visible"): "Move or copy?" with two choice rows (Move: "Takes it off …"; Copy: "Keeps
  it on both lists"), then "Move to…" / "Copy to…"; the wish (or several as photos + "3 wishes") on top of both.
  Move is greyed with the reason when someone reserved the co-op wish. Fixed with it: Your friend code ("Make a new
  code" was in the Cancel spot) and Price alert ("Remove" in the Cancel spot, now "Remove alert" as the second choice).

## The whole-app style guide (owner, 21 November 2026: "make one for the whole app; fix what confuses you first")
- Claude looked at every screen (both styles) and asked about each inconsistency; the owner chose (rules in
  `docs/STYLE.md`, drawn on the style guide page, checked by `StyleRulesTest` and `DialogRulesTest`):
  - **Every ⋮ menu is a bottom sheet** (chat, group, gift exchange, friend, photo menus too; replaces "up to 4 actions
    = a small pop-up", `screens.md` 16 November). Icon tiles, red rows for leave/remove/block, no ✕.
  - **✕: forms top right, in every style (Edit wish moved from the left; its Gen Z chaos Cancel went); menus and
    pickers have none** (a list's ⋮ and the profile sheet lost theirs; the profile sheet closes like a menu).
  - **Buttons do, words go**, and buttons on screens are as wide as their words ("not unnecessarily huge"); plain
    coloured words only for going somewhere, a way out, a top bar; red only for removing or leaving.
  - **Empty and error states: icon, line, one button** (never a line alone; `EmptyState`).
  - **Pick-one lists are sheets with a tick** (Check prices was a dialog with Close).
  - **Big titles everywhere, in both styles** (Gen Z chaos got the shrinking title too, so the Theme screen has it
    without the jump of 18 November).
  - **A chat message's long press is a sheet like the others** (Reply and Delete as icon rows, no Close).

## Lines between rows (owner, 22 November 2026; built in 2.278)
- Owner's screenshot: "Move or copy" in dark mode looked like one block, no divider. Cause: the line was there but
  almost the block's own colour (52,53,57 on 50,51,55); the same in a wish's ⋮ sheet, and the other menus (a chat's,
  a group's, a gift exchange's, a friend's ⋮) had no line at all. Owner chose **a thin line after the icon** (over a
  full-width line or separate cards), the same everywhere, light and dark. Rule in `docs/STYLE.md`; checks: `LookTest`
  (the line visible in every style, theme and mode).

## Pickers as bottom sheets (owner, 22 November 2026; built in 2.279)
- Owner, with a screenshot of "Move or copy?": the two rows looked awkward, lots of space around Cancel, the screen
  "out of place compared to other screens". Claude agreed (block indented further than the wish, a menu's block in a
  centred pop-up, an empty band around a lone Cancel; it opens from a wish's ⋮ sheet). Owner chose: **a bottom sheet
  like the ⋮ menu**; after Move or Copy **the same sheet shows "Move to…"** (← back); **all pickers at once, "not one
  at a time, make sure they are all built and that it looks flawless"**.
- Made sheets: Move or copy + Move/Copy to…, Move/Delete from which list?, Add to my wishlist, Which wishlist?
  (Received gifts), What did you get?, Send a wish (chat), Invite Friends (co-op list), Invite friends (group, gift
  exchange), Add friends (new group). Stayed pop-ups (Claude's reading of the rule, tell the owner): "are you sure?"
  questions, the Leave forms with "who takes over" (choice circles in a form, `STYLE.md`), the group's join form
  (typing), How many will you buy?, the emoji picker. An unused category menu (`CategoryActionsDialog`) was removed.
- Found on the way: every light-mode sheet showed its default text in grey (Quiet light has the same colour for
  surface and surfaceVariant); fixed in `sheetContent`, checked in `LookTest`.

## The review of 22 November 2026 (built in 2.280)
- Owner: "Do you see any inefficiencies or illogical things in the app?" Claude's list of 10 (in the session); owner
  chose: the search starts at the shop you last picked (`shop-search.md`); **Gifts I reserved and Received gifts get
  the app's frame and big title** (they had their own small header and Android's back button); **the stray Android
  buttons use the app's own** (How many − / +, Close on the price page; the old Private toggle was unused code);
  **GitHub builds keep the pretend phone's Android between runs and fetch it from Google's copy of Maven** (a "too
  many requests" failed a good build); **about 15 unused pieces of code removed**; **rows share their parts**
  (`Rows.kt`: `IconTile`, `ValueRow`, `RadioRow`, replacing ProfileRow, NavRow, two ValueRows, PickRow, ChoiceRow and
  four hand-made icon tiles; screens compared picture by picture).
- Not chosen now (to-do): "Open in Chrome" for every shop, the offline message when tapping a shop tile, whether the
  last pop-up pickers (How many?, Leave with "who takes over") should become sheets too.

