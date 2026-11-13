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
