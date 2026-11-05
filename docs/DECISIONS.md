# Decisions

What the owner has decided, and why. Follow these unless the owner changes their mind; when they decide something new, add a line here (newest at the bottom of each section) in the same commit as the change.

## Working together
- **Changes go straight to `main`.** No pull requests, no test builds, no test release. The owner found branches, test APKs and two install links confusing ("app not installed") and wants to iterate on one app. (October 2026)
- **Push only to `main`, never to any other branch** (owner, 31 October 2026), also when a Claude Code session starts on its own `claude/...` branch. Pushes to other branches made the workflow fail (Pages only deploys from main) and sent the owner "build failed" e-mails. Keep the GitHub repository clean: one branch, `main`. The old branch `claude/kind-fermat-ozfdx2` may still exist; the owner can delete it (Branches, bin icon).
- **One Claude Code session per feature or topic.** Long sessions get summarised and lose detail; this file and `CLAUDE.md` are what a new session reads. Before ending a topic, write any new decision here.
- **The project lives as normal files in the repository**, not in `wishlist-android.zip`, so every session reads `CLAUDE.md` and this file automatically. Never bring the zip back.
- **On screen, always speak to the user** ("you"), never "we" (owner, 2.82): for example "You’ll get a notification…", not "We’ll let you know…".
- **Explain plainly, in UK English, tap by tap.** Say what you are changing before or as you change it, and be honest about what was and wasn't tested.
- **When a new app build is ready to download, say so explicitly in bold: "App available for download", as the very last line of the message** (under everything else, so the owner sees it without scrolling up). (October 2026) The word "download" links straight to the newest APK: `**App available for [download](https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk)**` (November 2026).
- **Show previews of visible changes** (an HTML mock-up or simulation) when the owner asks to see something before it is built.
- **Push back and ask before building** (owner, 1 November 2026): don't just agree. Point out weak spots, offer a better option when there is one, and ask the open questions first (preferably as a pop-up with choices, AskUserQuestion) before building, so nothing has to be redone. Small, clear requests can be built straight away.
- **Every feature is for a happy user** (owner, 4 November 2026): each change starts from who it is for and what gets better for them; be critical, not agreeable. Keep dead ends, safety and upkeep in mind for the part you touch, without auditing the whole app each session (see `CLAUDE.md`, "Who you are working with"). A review of 4 November 2026 found the web pages and database well protected; the open dead ends are listed in `docs/HANDOFF.md`.
- **Browser editor Bin: restore only** (owner, 4 November 2026): menu > Bin lists a gift list's deleted wishes (30 days) with Restore. Deleting for good and emptying the bin stay in the app: a browser link can be passed on, and nobody should wipe a list's bin by accident.
- **Always sharp shop icons, and the first product photo** (owner, 5 November 2026): a shop icon must be a normal-size, good-quality picture (no blur, no letter tile); the picture of a wish added from a shop is the product's first photo (the clean one), not a later one with dimensions. The Share tip under the product link was removed.
- **In-app shop behaviour** (owner, 5 November 2026): the shop stays open while adding (after Save it says "Added to …"; after Cancel you're back in the shop); Back goes to the shop's previous page and closes only on its first page; ✕ closes the shop. Add to wishlist fills in the product picture straight away.
- **Shop icons** (owner, 5 November 2026): the icon the shop's homepage names first, then Google's icon service (it only sees the shop's address); light grey tiles so white icons show.
- **Find a gift** (owner, 4 November 2026): a row of web shops on Home (20 Dutch shops: bol.com, Amazon, Coolblue, Zalando, IKEA, Wehkamp, HEMA, de Bijenkorf, Intertoys, LEGO, Decathlon, Douglas, Rituals, Blokker, fonQ, Nike, H&M, Etsy, Bever, Apple; not MediaMarkt), users add and remove their own. Shown with each website's own icon loaded live (never copied into the app). A shop opens inside the app with a big "Add to wishlist" button that reads the product on screen. App only (browser users paste links). No affiliate links for now; maybe discuss later.
- **Wish buttons and menus** (owner, 4 November 2026): two per row has no long press; its ⋮ is Share, Edit, Private, Mark as owned, Delete. Large cards show Share, Private (eye) and Delete; their long press is Edit and Owned. (Replaces the long-press notes of 2.111.)
- **Owned** (owner, 4 November 2026): owned wishes go to the bottom everywhere; whoever reserved a wish that is then marked as owned is told once (app notification, or a note on the friends' page). Friends still see owned wishes, greyed out with "Owned". Not chosen: hiding owned wishes from friends, a "Received" overview.
- **New wishlist form**: the optional field is called "Note for friends", with the hint where it shows (owner, 4 November 2026).
- **No floating "Add wish" button on a list** (owner, 4 November 2026): the + in the bottom bar is the one way to add a wish.
- **Long-press menu of a wish** (owner, 4 November 2026): in the large-card view only Edit and Private (gift list: only Edit, a one-item menu by choice); Share is the card's own icon, Price history is on Price watch. Two per row keeps Price history and Share. Smaller title and rows, not cramped.
- **"Owned by you"** (owner, 4 November 2026): a small grey line under the name in the top bar of your own open lists; not on gift lists.
- **The top of a list stays in sight** (owner, 4 November 2026): the summary card (number of wishes, total value) and the category, sort and view choices stay pinned at the top while scrolling; the cover photo scrolls away. The number of wishes takes less room than the total value, both centred; the number pulses when it changes.
- **"Saved!"** (owner, 4 November 2026): the green tick gets little lines that circle around it once and fade, and the text ends with "!"; it stays as short as before.
- **Sharing a wish** from your own list: "Look what’s on my wishlist!" (owner, 4 November 2026).
- **Cover photo ⋮** (owner, 4 November 2026: removing a cover was a dead end): a ⋮ in the bottom right corner of a list's cover, only when there is one: Change (Choose a photo, Take a photo, Adjust this photo) and Delete (at once, with Undo). List settings keeps its cover section (the only way to add a first cover).
- **Categories are edited from the wish form only** (owner, 4 November 2026): "Edit categories" under "+ New category" in the form's category drop-down; add, rename (tap the name), delete. Under each name the number of wishes. No wishes: deleted at once; otherwise a short warning that the wishes stay, without the category. Your own wishlists share one set; each gift list keeps its own set (owner: keep them separate). Gone from List settings and from the long press in a list's filter.
- **Home without "Recently opened"** (owner, 4 November 2026): each section starts with the list (or group) you opened last.
- **Back arrow on an open wishlist** (owner, 4 November 2026): top left shows ← to Home instead of the menu button; the menu stays on Home and the other screens.
- **Photos in messages** (owner, 4 November 2026): camera button next to the gift: Take a photo / Choose from gallery; a full-screen preview with Cancel and Send first (no accidental sends); no caption (send a text after it); tap a photo for full screen with pinch to zoom, no Save button (a saved photo leaves the encrypted chat). Locked end to end like messages: the server only stores a locked copy, for 30 days (saves the free Supabase space); the phones keep theirs. Not in Export, like messages.
- **The web pages only run their own script files** (Content-Security-Policy, 4 November 2026): a safety net if a bug ever lets someone's text in as code.

## Language
- **UK English only** (owner, 4 November 2026): Dutch was removed (writing every text twice cost too much); App settings > Language and the web pages' language switch are gone. Texts stay wrapped in `tr()` / `T()` so the whole app can be translated later in one go when the owner asks. ~~Everything also in Dutch~~ (until 2.110).

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

## Private wishes
- **"Private wish" switch on each wish of My wishlist, off by default** (October 2026). A private wish stays on the phone and never goes online, so friends never see it. No separate private list.
- **Shown with an eye with a line through it** (Google's free Material "visibility off" icon) in the bottom left corner of the wish's picture, in both views (since 2.76; the top left is for the price change). Menu wording: "Make private" / "Visible to friends".
- **"Save to" lists only real lists** (2.76): the "· Private wish" choices were removed (confusing: there is no private list); the form's "Private wish" switch, or later the menu, makes a wish private. It remembers the last list.
- **Private wishes keep getting price-drop notifications.**
- **If a reserved gift is taken off the list** (made private or deleted), the person who reserved it is nudged to pick another gift: a notification on their phone when they use the app, a note on the friends' page when they use the browser. The owner still never learns it was reserved.

## Top wish
- **"Most wanted" is now "Top wish"** (October 2026): for the wish maker "my number one", for the giver "a safe bet that makes them happy". Gold label with a star, as before, with bolder words. (A heart with "Love it" in pink was tried in 2.38 and dropped.) Sort: "Top wishes first". Still stored as `star`.

## Wishes on screen and the wish form (October 2026)
- **Tapping a picture opens it full screen** on a dark background (pinch or double-tap to zoom, drag to look around, X or Back to close), with the name and "View in shop" at the bottom. Chosen over an enlarged picture on a blurred screen, which leaves too little room to zoom. Tapping the name and details opens the shop page.
- **Wish form order**: link, picture, Name (not "Title"), price, Category (one drop-down, not a row of chips), Top wish ("Shows people you *really* like this", since 2.57), Private wish ("Hides this wish from everyone you share your wishlist with"), then "Product information" folded away (description, size, note for givers).
- **Tapping the eye on a private wish makes it visible**: "Wish is now visible to friends" with Undo, for 5 seconds (also "Wish is now private" from the menus).
- **Delete question**: "Deleting this wish moves "…" to the bin. After 10 days it's permanently deleted."

- **Tapping "Other information" only turns its arrow** (2.57): no flash on the bar, it distracted from the arrow.
- **Name and price on one line in the wish form**: the price field is just wide enough for "9999,99" but takes any amount (a longer one scrolls inside the field); the name gets the rest and stays on one line. Opening "Other information" scrolls down so all of it is in sight.
- **No "(optional)" in the wish form; the name is "Name*"**: saving without a name turns the field red with "What are you wishing for?" (kind, and it leads to the answer; the label stays "Name*").
- **Wish form, later tweaks**: the folded section is called "Other information" ("Description, size, notes"); the price field shows the currency's symbol (€ for EUR) in front of the amount, label "Price (EUR)"; no "Fetch details" button (pasting a link reads the page; the keyboard's Done key does too).

## Prices
- **Price changes use + and −, not arrows** (owner, 2.83: "it isn't a stock trade"): "−20%", "+5%", in the app and on the web pages.
- **Price rises are shown too**: the old price stays visible, with a red ↑ and the percentage. Price drops as before.

## Lists and sharing
- **No email accounts.** The owner prefers that users never have to sign up with an email address. A list belongs to the phone that created it (its edit key).
- **Lists are link-only, never public.**
- **"My wishlist"**: friends reserve gifts; the owner never sees reservations (surprise).
- **Shared wishlists are only for the givers** (the person the gifts are for is not on it). So everyone on it sees and makes reservations. Shared lists have no "most wanted", no "got it" and no price history, but every phone on the list gets price-change checks and alerts.
- **A shared wishlist has one link: the invite** (October 2026). Whoever gets it can add wishes and reserve gifts. The separate friends' link for shared lists was dropped: since shared lists are only for givers, it was an unnecessary second kind of link. Links sent earlier keep working. My wishlist keeps its friends' link.
- **Only the phone that created a list can make a new invite link or delete the list.** Consequence: uninstalling the app loses those rights (it happened once; the old list had to be deleted by hand in Supabase). A recovery key backup was suggested to avoid this; not decided yet.
- **Invitation page**: title "You're invited to add gifts for:" with the list name centred, bigger and bold in the same font; gift emoji; confetti that never moves the text; no "Already have the Wishlist app?" line.
- **iPhone, iPad and computers see the invitation page too**, with only "Edit in the browser" (no Download app: they can't install the Android app). Skipping the page lost the context of what they were invited to. (October 2026)
- **Link previews**: invites use `web/join/` and friends' links use `web/list/`, each with its own preview picture; sharing from the app also sends a picture with the list name.

- **Bin (October 2026)**: a deleted wish goes to the bin (Menu > Bin) at once. It stays there 10 days, then it is deleted for good. One bin for all lists. Wishes in the bin get no price-drop or price-rise notifications. The bin has no refresh button: it is always up to date by itself.
- **The delete message is short**: just "Wish moved to the bin" with Undo.
- **Every message at the bottom stays 5 seconds and can be swiped away** left or right, Undo or not (owner, 2.84).
- **Everyone on a shared wishlist sees its bin** and can restore or delete its wishes. On My wishlist only the owner sees the bin (and still never sees reservations).
- **"Your reserved gift has been moved to the bin"**: if someone other than the person who reserved a gift moves it to the bin, that person gets this notification. Tapping it opens the bin, scrolls to the gift when it is out of sight, and lets it glow softly.

- **Price checks on shared lists are shared between the phones**: when one phone has checked a shop's price, the new price shows for everyone and the other phones skip that wish until it is due again. Everyone still gets the alerts.

## Security
- **The app is signed with a private key** kept only in the GitHub repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (since version 2.31). Never put the key or its password in the project and never change the key, or updates stop installing over the app.
- ~~No import/export feature for now~~: replaced by Export and import (1 November 2026, see "App review").

## Backend
- **Free Supabase plan** (500 MB database). Usage is small (estimated 20–30 MB in October 2026). SQL scripts are run by the owner by hand; give exact steps.

## Friends and Groups (November 2026)
- **Friends are profiles** (owner): Profile page (menu > Profile, or your picture on Friends) with photo, name (moved from App settings) and a friend code to share. Profiles belong to the phone (no e-mail accounts).
- **Adding a friend needs approval** (owner): sending your code says you want to be friends; when someone enters it, **you (the code's owner) approve or decline**. Waiting requests show on Friends (red dot on the bottom bar) and as a notification. If you both enter each other's code, that counts as yes.
- ~~Lists are invisible to friends by default~~ (owner, until 2.104): replaced in 2.105, see "3 November 2026" below (new lists start on "My friends").
- **Per friend**: "Hide my lists from …" hides all your lists from that one friend (stay friends, they aren't told); "Unfollow" ends the friendship for both (nobody is told; add each other again with a code). This is new in 2.87: before, only single wishes could be private (hidden from everyone).
- **Make a new code**: the old code stops working; friends you already have stay.
- **Profile photo is edited like the cover photo, in a round frame** (owner, 2.88): rotate, crop, lighter or darker; no text.
- **Your profile picture is top right on Home, Prices and Friends** (owner, 2.88), and opens Profile.
- **The menu's heading is "Menu"** (owner, 2.88): you can have several wishlists, so not one list's name.
- **Your name is only on Profile**, not in App settings (owner, 2.88).
- **Names save with the keyboard's tick and a green "Saved" tick** (owner, 2.88): your name, a list's name, target prices. No Save buttons for these.
- **Home layout** (owner, 2.89): no cover photo card on Home. At the top: your picture with your name under it, then the numbers wishlists / wishes / top wishes, each opening its own page (all wishlists; all wishes with picture, name, "Added to …", price; top wishes with the "Top wish" label on top and a star to unstar). "Recently opened" stays.
- **A list's picture is its newest wish** (owner, 2.89), never a cover photo. Discussed: GoWish shows a cover photo per list; the owner prefers seeing the wishes.
- **Cover photo per list: optional, off by default** (owner, 2.89): list settings > Cover photo, the same editor as the profile photo but rectangular, with a **Preview** of the list before saving. Only shown when chosen. (Since 2.105 friends see it too, see below.)
- **Summary card at the top of your own lists**: number of wishes and total value (owner, 2.89; not on group wishlists since 2.90). The total value is no longer on Home.
- **Home keeps its list sections** next to the "wishlists" page (owner, 2.90): one tap to any list beats a tidier Home.
- **Lists on Home are blocks, two per row** (owner, 2.91, like GoWish's home): newest wish's photo, the number of wishes in a white circle top right (also 0), the name under the block. Empty list: "Empty" and "+ Add wish" (the wish form with that list chosen) on the theme's soft colour. Group wishlists use the phone's copy for their picture. "Recently opened" uses smaller blocks. Long press menu on Home only.
- **Labels under numbers start with a capital** ("Wishes", "Wishlists", "Top wishes"), on Home and in the summary card; no wish count on "Recently opened" blocks (owner, 2.92).
- **The "wishlists" page only opens lists**: no + buttons, no long-press menu (owner, 2.91).
- **Tapping a wish on "All your wishes" / "Top wishes"** opens its list, scrolls to it and lets it glow softly twice (owner, 2.91).
- **Page texts** (owner, 2.91): "All your wishes" with "Every wish from all your wishlists, in one place."; "Top wishes" with its gold label and "The wishes you’d love most, from all your wishlists."
- **A copied wish is one wish** in the counts and the All wishes / Top wishes pages, with all its lists in "Added to …" (owner, 2.90).
- **Taking a top wish off from the Top wishes page asks first** ("Remove from top wishes?" No / Yes) (owner, 2.90).
- **Groups: only the organiser invites by default**; the organiser can switch on "Members can invite too" (owner). Not built yet.
- **Joining a group means bringing exactly one wishlist**: before joining you pick one of your lists or make a new one (optionally with wishes copied from your other lists). Without a wishlist you can't join (owner). Not built yet.
- **"Groups" is the name for a group of people** who each bring their own list (owner). The existing "Group wishlists" (one list edited together for someone else) needs a new name; the owner is still choosing (offered: "Gift lists", "Gift plans", "Joint wishlists").
- **Still to decide**: whether hiding your lists from a friend also hides the list you brought to a group you're both in (Claude suggested: no, the group is a separate place you chose to join).

## App review (1 November 2026, owner interviewed; built in 2.95)
- Side menu keeps everything (lists included).
- "Price changes" leaves Home; price news lives on the Prices tab and in Notifications.
- One sharing choice per list: list settings > "Who can see this list": Only me / My friends / Friends + anyone with the link (replaces the separate friends' link and "Visible to friends").
- The bottom bar keeps "New wishlist".
- One wording to open a shop page: "View in shop" (not "View product" / "Open in store").
- "Price surges" becomes "Price increases" (with "Price drops").
- **The lists edited together are called "Wishlist for someone"** (NL "Verlanglijstje voor iemand"), not "Group wishlist" or "Shared wishlist": the owner plans a future feature "Wishlist sharing group" (friends in a group see each other's wishlists); "Gift list" was too close to "wishlist", "Joint wishlist" not liked, "Gift planner" sounded like a tool. Home heading: "Wishlists for someone" (NL "Verlanglijstjes voor iemand"). Explainer (in the "New wishlist" choice and under the empty heading): "Make a wishlist with other gift givers and surprise someone with a gift they’ll love." Renamed everywhere, app and web pages.
- **The Prices tab is "Price watch"** (page title; owner).
- Friends' links (?c=CODE) stay, for people without the app ("Friends + anyone with the link").
- Price watch shows only your own wishes.
- "Got it" stays but is called **"Owned"** (NL "In bezit") everywhere, also for friends.
- Tidy up the main screen's navigation under the hood, in its own version (owner).
- Deleting a wish: no question first; bin + Undo is enough. The "Instant delete wishes" setting goes.
- The Notifications page lists everything: price changes, friend requests (tap: Friends), approvals, reserved-gift alerts, newest first, with the red dot.
- A short, skippable 3-step welcome on first start: your name (photo optional), name your first wishlist, one line on how friends reserve gifts; then the permission questions.
- The browser editor keeps to what givers need (lists for someone, friends' links); new personal features stay app-only.
- Wish cards keep their buttons (pencil, Owned tick, bin, long-press menu). (2.105: the pencil became a Share button.)
- The wish form always shows a small tip under the link field: share from a shop's app or browser to Wishlist (faster).
- List settings grouped in sections: "About this list" (name, cover, note), "Who can see it", "Wishes" (categories, copy, export), "Danger zone" (delete).
- Categories: one set for all your own lists; a category made on one list can be used on every list.
  Wishlists for someone keep their own categories (their givers edit them). Deleting a category removes it from all your own lists; their wishes stay, without a category (the question says how many wishes on how many lists).
- A friend's page looks like your own Home profile: their picture and name, the numbers (wishlists, wishes, top wishes) and their visible lists as blocks.
- Optional birthday on the profile (day and month), visible to friends. Reminders: 14 days before, and again 7 days before only if you haven't reserved anything on their lists yet. Needs a new Supabase script (owner agreed).
- On a friend's page, their numbers open their wishes / top wishes; tapping a wish opens its list, scrolls to it and glows; reserving happens on the list.
- The bin keeps wishes 30 days (was 10). Bin stays in the menu only.
- Profile gets a short "I like…" line (visible to friends, gift inspiration). No sizes on the profile.
- **Export and import** (owner, 1 November 2026; replaces "No import/export for now"): a simple way to move your whole profile to a new phone. App settings > Backup: "Export everything" makes one file (lists, wishes, own photos, covers, profile photo, profile and friends keys, lists for someone you're on, settings); you keep it yourself (Drive, e-mail, USB). No password (the owner's choice; warn that the file holds your keys). "Import" on the new phone replaces everything after a warning. A line on Profile points to it.
- Price check interval stays a choice; all themes and colour sets stay.
- **All review changes go out together in one update**, which the owner then tests (owner, 1 November 2026).

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

## Messages (2 November 2026, owner interviewed; built in 2.98; run `supabase-chat.sql`)
- "Messages" replaces "Wishlists" in the bottom bar (Home, Messages, +, Price watch, Friends). Wishlists stay on Home and behind its number.
- Chat only with friends; a WhatsApp-like list (picture, name, last message, time, unread bubble, newest on top, + to start a chat) and conversations in the selected style.
- No push service: messages appear within ~3 s while a chat is open; when the app is closed a notification can take 15–30 minutes (background check). Push (Firebase) can come later. (2.105: Firebase push built, see below.)
- Send text and wishes (a small card; tapping opens it). No photos.
- Ticks: one when sent, two coloured when read. No typing or last seen.
- Deleting is always for everyone, and only your own messages.
- **End-to-end encrypted** (owner: "No one should feel their messages can be read by me"): only the two phones can read them; the server only stores locked text. Chat keys and messages are **not** in the backup file; a new phone or reinstall can't read old chats ("This message can't be read on this phone"), new messages work straight away.
- Unfollowing keeps the history readable, but no new messages.
- Messages older than **1 year** are deleted automatically (space).
- **Chat layouts** (owner, 2.99): four layouts inspired by well-known chat apps but without their names (trademarks): **Chat** (like WhatsApp: small tail, time and ticks inside, a date chip per day), **Calm** (like Signal: rounded, no tail, more space, time under a group), **Text** (like SMS: simple rectangles, no ticks, times between groups), **Round** (like Messenger: very round merging bubbles, their picture next to their last message). One layout for all chats, chosen in Messages ⋮ > Chat layout (with a preview) and in App settings. Always the theme's colours and fonts: the Themer's choice never changes.

## Open ideas (not decided)
- Personal recovery key: an encrypted online backup of your lists, keys, creator rights and settings, restored with a key (optionally saved in Google Password Manager), plus automatic clean-up of lists nobody uses. Ask the owner before building.
  - Owner asked (1 November 2026, not a priority yet): pictures would go to Supabase file storage (free plan about 1 GB, separate from the 500 MB database). Back up only the cut-out pictures as shown (profile about 20 KB, list cover about 150–250 KB, own wish photos about 50 KB), not the originals: roughly 1–2 MB per active person. After a restore you can choose a new photo but not re-crop the old one.
- **Wish pictures: Blur stage** (owner, 2.100, chosen in a preview with Now / Blur stage / Fill / White stage: https://claude.ai/artifact/4RDcyoYx7MEP3Exhi6ebKp): a soft blur of the photo fills the picture area, the photo big and uncut in the middle. Everywhere wishes show (both views, a friend's lists, Home blocks, friends' page, editor); small row thumbnails stay plain. Fill was rejected because it cuts products off, White stage because it is a bright block in dark mode. The styles keep their frame on the photo; Neumorphism keeps its dent, with the blur filling it.
- **Two per row is the default view** (owner, 2.100); the large cards stay as the "details" view (description, note, size, Added by, old price), now with a taller 4:3 picture. People who already use the app keep their view.
- **No "Added on" on wish cards** (owner, 2.100): too busy on a list; it stays in Price watch only.
- **What you see** (owner, 2.101): App settings > "What you see", for all your lists, only on your phone, everything on by default: the price change badge on pictures, the description on large cards, the list summary (Wishes / Total value).
- **Notifications section** (owner, 2.101): App settings > "Notifications" lists every kind the app sends, each with a switch and one line. On by default: price drops, reserved gift moved to the bin, reserved gift removed from a list, friend requests, friend request accepted ("it confirms something the user wants"), new messages. Off by default: price increases, friends' birthdays.
- **Any accent colour** (owner, 2.101): a rainbow dot after the five accents opens a colour ball (slide over it) with a brightness slider, a preview and "Use this colour", which closes it. A colour that may be hard to read gets a warning ("This colour may be hard to read") but is allowed. Apply in the Themer shows "Theme applied".
- **Chat layouts removed** (owner, 2.101): they looked almost the same; one default style (the former "Chat": tail, time and ticks inside). To be fleshed out later.
- **Messages tabs** (owner, 2.101): no + in the top bar (confusing next to the bottom bar's + for wishes). Two tabs: "Chats" (default) and "Friends" (tap a friend: the chat opens). With no conversations, Chats shows a "Start a chat" chip that opens the list of friends; it goes once there is a conversation.
- **No flicker when switching views** (owner, 2.101): photos must not flash when switching large cards / two per row.
- **Gift lists** (owner, 2.102): "Wishlists for someone" are called **Gift lists** (Dutch: Cadeaulijsten), next to "My wishlist". The new-list form: title "Create a gift list for someone", text "Collect gift ideas together for someone you want to surprise. Everyone you invite can add ideas and reserve what they'll buy, so no gift is bought twice.", field "Gift list name*". No "Your name": the profile name is used for "Added by" (also when joining); the per-list name in ⚙ is removed. The browser editor (no profile) still asks a name.
- **"Owned by …"** (owner, 2.102) under the name of a friend's wishlist, in the app and on the friends' web page. Not on gift lists.
- **Colour picker** (owner, 2.102): the same colour in light and dark (no lighter dark tone); the two previews keep small labels "Light mode" and "Dark mode".
- **Chat** (owner, 2.102): tap the friend's picture or name at the top of a chat to open their page; read ticks closer together; the gift button lined up with the text box.
- **No flashing photos** (owner, 2.102): switching tabs or views must never reload a product photo that was already shown; toggles keep their style's shape when on; the bottom bar must not jump when coming back to the app (cause: keyboard of another app; the app now draws edge to edge).
- **Number tiles follow the style** (owner, 2.103): the Wishlists / Wishes / Top wishes tiles next to the profile picture are a small card of the style in use (Quiet stays subtle).
- **Friend code as six blocks** (owner, 2.104): three blocks, a dash, three blocks, in the style; capitals; paste works (also the whole shared message). Full code: wait for the button, no auto-send (owner).

## Owner's list of 3 November 2026 (built in 2.105; owner interviewed with pop-up questions)
- **New wishlists start on "My friends"**; existing "Only me" lists were switched to "My friends" once, **silently** (owner chose no message). A list you set to "Only me" yourself afterwards stays so.
- **Long press a list on Home: Edit name · Visible to · Delete wishlist.** "Visible to" opens the same three radio choices as list settings (Only me / My friends / Friends + anyone with the link).
- **Wishes keep the word "Private"** (owner changed their mind about "Hide": hidden sounds like hidden from yourself). In menus it is one short item **"Private" with a tick box** (ticked = private). The form switch stays "Private wish".
- **Long press a wish: Edit, Private (tick).** Price history and Share stayed in it too (Claude kept them so price history is still reachable from a list; the owner may still remove them). Top wish left the menus: the star on the picture does that.
- **Two per row ⋮: Edit, Private (tick), Share, Mark as owned, Delete.** Owned only here because it doesn't fit on the small card (owner). No "wish" after every word.
- **Large cards: Share instead of the pencil** (owner: editing shouldn't be easier to reach than sharing). Edit is in the long-press menu.
- **Sharing a wish: "Look what’s on my wishlist" + the shop link** (owner chose the shop link: it works for anyone whatever the list's visibility). Gift lists say "Look at this gift idea".
- **"Wish marked as owned"** message (5 s, Undo), and "Wish marked as not owned".
- **Back from a friend's page that was opened from a chat returns to that chat.**
- **Gift lists line: "Plan gifts for someone, alone or together with others."** (owner chose it from three; replaces the explainer of 2.95 on Home and in the New wishlist choice; the gift list form keeps its own text).
- **Friends see your list's cover photo** (app and friends' web page), with its text. Gift lists keep their cover on the phone only (not asked; would need a Supabase change). Settings text: "Personalise your wishlist with a cover photo at the top." (owner chose it from three; no "Optional").
- **The note for friends has no Save button**: the keyboard's tick saves it with the green "Saved" tick, like names (owner); leaving the field saves too, so nothing typed is lost.
- **"Read" only when really seen** (owner): the app is open, the chat is open and their newest message is on screen (or the chat was opened from the notification, which shows it). Fetching messages no longer marks them read.
- **Instant message notifications with Firebase Cloud Messaging** (owner: yes, as long as it's free and needs no PC; it is free and set up in a browser). The push carries no text, so messages stay end-to-end encrypted. Until the owner has set up Firebase, the old 15–30 minute check stays.
- **Wishlist groups get their own session** (owner): interview first, then build. What the owner asked for so far: "Wishlist groups" in the menu and on Home with "New group"; invite friends from your friend list (they get a notification and a message with a link to confirm) and share a link with people without the app; people without the app must first name and create their first wishlist (in the browser) before they can join; as frictionless as possible for app and web users. Earlier decisions in "Friends and Groups" still apply (one wishlist per member, organiser invites by default).

## Wishlist groups (3 November 2026, owner interviewed; update 1 = groups, update 2 = group chat)
- **"Hide my lists from …" is removed** (owner: private wishes are enough). Friends hidden before see the lists again (switched off quietly, for everyone). **"Unfollow" is now "Unfriend"** (still nobody is told).
- **Group page**: the group's name and optional photo at the top, then each member as a block (picture, name, their list's newest wish); tap opens their list.
- **Joining**: pick one of your own wishlists, or make a new one (empty, or with wishes copied from your other lists). Exactly one list per group. Gift lists can't be brought. The organiser also brings a list ("New group": name, then the list, then invite friends).
- **One list can be in several groups**; reservations are per wish, so a gift reserved in one group is reserved in the other too.
- **Swap your list**: group ⋮ > "Change my wishlist" (same picker as joining).
- **Reservations in a group**: members see "Reserved" / "Reserved by you", never who. The list's owner never sees reservations.
- **The group always sees the list you brought**, whatever its "Visible to" says (private wishes stay hidden). Friends outside the group still follow "Visible to".
- **Leaving**: any member can leave; the organiser can remove members. The brought list stays yours. **The organiser must pick the next organiser before leaving** (no automatic choice); alone in the group, they can only delete it. Deleting the group is organiser-only and never deletes anyone's list.
- **A removed member is told**: "You are removed from [group]".
- **Name required, photo optional**; only the organiser changes them. Only the organiser invites unless "Members can invite too" is on.
- **Invites**: friends from your friend list get a notification and a chat message with a link. Tapping either opens the join screen. An invite card also waits on Home under "Wishlist groups" with "Join" and "No thanks". **The organiser is told when someone says "No thanks".**
- **Group link**: anyone with the link can join (no approval); the organiser can make a new link (the old one stops working).
- **Members who aren't friends** see each other's name, picture and list, and can tap "Add friend" (normal request with approval). Messages stay friends-only; a group chat comes in update 2.
- **Home order**: My wishlists, Wishlist groups (+ "New group"), Gift lists. Also in the side menu.
- **Browser-only members**: open the group link, give their name and name their first wishlist, then join. The browser remembers them; they also get their own private "My link" (Copy, "Save this link to get back on another device") and a "Get the app" button. Their list: add by shop link (auto-fill) or by hand (name, price, picture, description, note), edit, delete, **top wish**. No categories, no price checks (app only).
- **Browser member moves to the app (now, owner)**: opening "My link" in the app takes over their browser list and their group places.
- **Group chat (update 2)**: only on the group page (not under Messages). End-to-end encrypted, browser members included, and browser members must be able to open the group link at any time and read the saved conversation. To discuss in update 2: the key must then travel in the member's link, so anyone holding that link could read the chat.
- **Built choices (Claude, 2.108, not asked; the owner may change them):** the group link goes through `…/group/?g=KEY` (its own link preview "Join our wishlist group 🎁", reusing og.png); a browser member's own list deletes at once with Undo (no bin on the web); the organiser's settings (name, photo, members can invite, new link, delete, remove members) are app-only, browser members can leave and copy the link when allowed; the group photo is stored in the database like profile photos; a group's block on Home shows its photo, else a member's newest wish, with the number of members; a list made while joining starts on "My friends" like every new list; deleting your own list says it also leaves its groups; group notifications have their own switch in App settings > Notifications ("Wishlist groups", on).

## Working together (4 November 2026)
- **A changelog after every update** (owner): once an update is pushed, list everything that changed in bullets, so the owner knows what's new.

## Compile and screenshots before a push (5 November 2026, owner interviewed)
- **Screenshot tests run only here, by Claude**, not in the GitHub build (owner chose the cheap option; the app build stays as fast as before and can't fail on a picture). Setup: `bash tests/android-setup.sh`, then `gradle testDebugUnitTest` (see `tests/README.md`, "App screenshots").
- **Default style only, light and dark** (owner): not all five styles.
- **Show the owner pictures only when an update changes how a screen looks**, and keep it cheap in tokens (owner): only the screens that changed, as one small before/after picture, not every screen every time.
- **No product-page check of the shops for now** (owner): from the cloud only about 9 of the 20 shops open, so products are tested on the phone.
