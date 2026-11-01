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

## Language
- **UK English everywhere, and everything also in Dutch.** App: App settings > Language (English (UK) / Nederlands). Web pages: a language switch. Wishes themselves are never translated.

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
- **No import/export feature** for now; the owner accepted losing wishes when the signing key changed.

## Backend
- **Free Supabase plan** (500 MB database). Usage is small (estimated 20–30 MB in October 2026). SQL scripts are run by the owner by hand; give exact steps.

## Friends and Groups (November 2026)
- **Friends are profiles** (owner): Profile page (menu > Profile, or your picture on Friends) with photo, name (moved from App settings) and a friend code to share. Profiles belong to the phone (no e-mail accounts).
- **Adding a friend needs approval** (owner): sending your code says you want to be friends; when someone enters it, **you (the code's owner) approve or decline**. Waiting requests show on Friends (red dot on the bottom bar) and as a notification. If you both enter each other's code, that counts as yes.
- **Lists are invisible to friends by default** (owner): each own wishlist has a "Visible to friends" switch in its settings. Turning it on puts the list online (it gets its friends' link). Private wishes stay hidden.
- **Per friend**: "Hide my lists from …" hides all your lists from that one friend (stay friends, they aren't told); "Unfollow" ends the friendship for both (nobody is told; add each other again with a code). This is new in 2.87: before, only single wishes could be private (hidden from everyone).
- **Make a new code**: the old code stops working; friends you already have stay.
- **Groups: only the organiser invites by default**; the organiser can switch on "Members can invite too" (owner). Not built yet.
- **Joining a group means bringing exactly one wishlist**: before joining you pick one of your lists or make a new one (optionally with wishes copied from your other lists). Without a wishlist you can't join (owner). Not built yet.
- **"Groups" is the name for a group of people** who each bring their own list (owner). The existing "Group wishlists" (one list edited together for someone else) needs a new name; the owner is still choosing (offered: "Gift lists", "Gift plans", "Joint wishlists").
- **Still to decide**: whether hiding your lists from a friend also hides the list you brought to a group you're both in (Claude suggested: no, the group is a separate place you chose to join).

## Open ideas (not decided)
- Personal recovery key: an encrypted online backup of your lists, keys, creator rights and settings, restored with a key (optionally saved in Google Password Manager), plus automatic clean-up of lists nobody uses. Ask the owner before building.
