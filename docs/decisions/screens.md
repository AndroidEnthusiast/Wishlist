# Decisions: Screens, look, wishes and prices

Moved here word for word from `docs/DECISIONS.md` on 12 November 2026 (notes tidy-up, owner). Index and the general
decisions: `docs/DECISIONS.md`. Add new decisions on this topic here. Look and feel, the identity and screen movement: `docs/decisions/look.md`.

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

## Settings review (5 November 2026, owner interviewed; built in 2.123)
- **Bell at the top right** next to the profile photo, in the chosen style's icon set, red dot for news, opens Notifications (owner preferred it over a dot on the photo with a menu).
- **App settings**: one "Prices" section (interval, alerts, currency); one "About Wishlist" section (update, share, licences). Backup stays where it was.
- **Currency for new wishes** in App settings: Automatic (phone's country, else €) or a picked one. App only; the web pages use the browser's country.
- **Buzz or not**: price increases still buzz (owner). "Friend request accepted" and group changes only appear on the Notifications page. Group invitations, being removed from a group (earlier owner decision), messages, friend requests, price drops, reserved-gift alerts and birthdays still buzz.
- **One summary per price check** when several prices change ("3 wishes got cheaper").
- **Occasions (built in 2.124; the owner's answers of 5 November 2026)**: not on Home; yearly occasions move on to next year by themselves, your own occasion disappears the day after; no reminders for friends (for now); gift lists' Valentine's Day, Mother's Day and Father's Day fill in Dutch dates themselves; the occasion can be added, changed and removed later in list settings; it shows in the summary card at the top of the list (web: a line under the list's name); the browser editor only shows it (set in the app).
- **Cookie questions in the in-app shop** (owner, 2.124): "Refuse for me" on every shop: the app taps "only necessary" / "refuse" (or opens the settings and saves them unchanged), and removes an empty dark layer that blocks the page.
- **Shop browser, empty layers** (owner, 2.125, recording of bol.com): keep empty dark/grey layers away on every shop, without breaking the shop (a layer with content or with a pop-up on top stays). Pages asking for location or other permissions get a "no". No "Open in browser" or "Report a problem" (not chosen).
- **Texts** (owner, 2.125): "Wishlist name*"; "Description" instead of "Note for friends" / "Notes", everywhere, with only "Shown at the top of your wishlist." (gift lists: "…of the list."); no "Optional" under "Add wishes from your other lists". Occasions: "Custom" with a small pencil instead of "Your own…".
- **New group form** (owner, 2.125): "Add friends" under the group name, like "Add wishes from your other lists"; hidden when you have no friends; invitations go out when the group is made; any that fail are told on the group page to the organiser only ("Tap Invite friends to try again"), no silent retries.
- **Notifications** (owner, 2.126): one "A gift you reserved is no longer on …" notification (taken off, private, owned or in the bin), naming the list ("Sam’s wishlist" / “Mum’s 60th”), text "… Tap to pick another gift.", tapping opens that list; always on. Group invitations always on. Switches only for Friend requests, Friends’ birthdays, New messages and the prices. Every push is also on the Notifications page; a tapped push shows there without a red dot. Chat messages stay on the Messages tab only. Own Android channel per kind, and a warning in App settings when Android blocks the important ones.
- **Views** (owner, 2.126): "Rows" (picture left, shop, name, price with the old price crossed out, Top wish and labels, buttons under the details) first and the default for new installs and new web visitors (saved choices kept), then Two per row (now also with the old price crossed out), then Large cards; app and web alike; icons look like each view.
- **Private eye** (owner, 2.126): open eye = visible, crossed-out eye = private, never blue.
- **Shop browser** (owner, 2.126): "+ Add wish" always in colour. Bottom bar: more room around the + (own wider slot, labels one size smaller).
- **Sorting** (owner, 2.127): no "Name A–Z". The sort button always names the order (also "Newest") and looks like the category button, with room between them. Rows' icons smaller than on large cards (bigger than the private badge).
- **Review interview, 6 November 2026 (decided, not built yet; build in this order unless the owner says otherwise):**
  - **#1 Giver reminder**: once, 7 days before a list's occasion, only to givers who reserved something there (push + Notifications page, tap opens the list). App only. Only lists with an occasion; a missed day is told once within 3 days after, never twice; a changed date counts as new.
  - **#2 "Bought"**: no (owner: givers won't do the extra admin).
  - **#3 Chip in**: not now, but **important: remind the owner** at the start of sessions until it is planned.
  - **#4 Drawing names**: not now.
  - **#5 Wish details**: Quantity (default 1, hidden); reserving a 2× wish asks "How many?", label "1 of 2 reserved" until all are taken (database change for app and web; old apps reserve all). One three-way choice: "Exactly this" / "Similar is fine" / nothing. The wish form becomes **one long scrolling page with small headings** (owner: don't hide or drop important fields; organise smartly).
  - **#6 Received**: the day after a list's occasion, "Did you get any of your wishes?" with tick boxes (same catch-up rule as #1). Ticked wishes leave the list for friends; the giver who reserved it gets a **thank-you** ("Sam received the gift you reserved"), never the "pick another gift" message, and Sam never learns who.
  - **#7 List kinds**: a short line under each Home section title.
  - **#8 New lists**: the side menu's three "New …" items become one "New list" that opens a Create screen with three choices (My wishlist / Gift list / Group, one line each).
  - **#9 Automatic backup**: not now.
  - **#10 Chat**: keep and grow it: fix the photo and gift buttons' alignment with the text box, add reactions (long press) and replies (swipe to quote). Old apps: a reply shows as a normal message, a reaction as nothing.
  - **#11 "What you see" switches**: keep as they are.
  - **#12 Texts**: shorten every long text in the app; show the owner a before/after list in chat before pushing.
  - **Private note about a friend**: one free text field on a friend's page, only on this phone (included in Backup), never online.
  - **Sizes on the profile**: clothing and shoes; friends see them on your page and on the friends' web page; a wish's own size still wins.
  - **Not now**: "Wrapped"-style gift overview (needs more thought), wishes without a link (photo / money towards), gift ideas per friend.
- **Built in 2.128 from the review interview** (see "Review interview" above for the details): the copy review (`docs/COPY.md`, every text decided one by one; new texts there too), received gifts go to a "Received gifts" archive (side menu, under Bin, kept for good, "Put back"), browser givers see the thank-you on their next visit, and **leaving a gift list you made** asks to "Pick a new owner" from its app members (they can renew the link and delete it) or, with nobody left, "Delete “…” for everyone?". The SQL for this update is in `supabase-setup.sql` (reservations with a number) and the new `supabase-features.sql` (reactions, sizes, members).
- **SQL links** (owner, 6 November 2026): every message that asks to run a Supabase script gives a direct GitHub link to each script, in order.
- **Risks first** (owner, 5 November 2026): with every feature, think of what could spoil the experience and ask with options to mitigate it; fix for every case, not only the example reported.
- **Occasions (first decision)**: in the New wishlist form, pick an occasion or type your own; only after picking one, ask for the date (not both at once). Own lists: Birthday (date from your profile when set), Christmas and Sinterklaas (dates fill themselves), Your own…. Gift lists: Birthday, Valentine's Day, Mother's Day, Father's Day, Anniversary, Your own…. Friends see "Birthday · in 3 weeks" at the top of the list. Very little text (owner).

## Home tiles, Collab wishlists, moving wishes, notifications and web search (7 November 2026, owner interviewed in 4 rounds; built in 2.146; run `supabase-collab.sql`)
- **Home tiles: Friends, Wishes, Total value** (in that order). The Reserved tile goes (Gifts I reserved stays in the side menu). **Total value** = what your own wishlists still wish for: not owned, not in the bin, a 2× wish twice, private wishes too (only you see it), a wish copied to two lists once; collab wishlists left out. **No tap** (owner). (Claude, not asked: no cents; other currencies can't be added up without exchange rates, so the biggest currency shows with a "+" when there are others; 0 in your currency without prices.)
- **"Gift list" is called "Collab wishlist"** everywhere (app, notifications, New list choice, browser editor), **without a warning line** in the create form (owner chose this after Claude pointed out the surprise risk: the person the gifts are for shouldn't join). Home headings: **"Your wishlists ›", "Wishlist groups ›", "Collab wishlists ›"**.
- **People on a collab wishlist's block**: three circles in the bottom right of its picture (about 26 dp: a bit smaller than a chat's send button), overlapping with a white edge, then a white "+N". Everyone with the app (you included; photo or first letter) **and browser people as letters** from "Added by" (owner accepted that a name typed in two ways counts twice). Only from 2 people.
- **Two per row**: the ⋮ is raised to line up with the name's first line, with a **bin under it**; Delete leaves the ⋮ menu.
- **Moving wishes between your own wishlists** (owner: "a simple way"): **"Move to…" in each wish's ⋮** and **a long press starts selecting** (owner chose long press over a Select button; Edit and Private were already in the ⋮ of every view). Selected: Move to… or Delete (one Undo). Collab wishlists keep their long-press Edit menu. **Reservations move along** (server first, `wl_move_item`), and the giver's phone or browser follows the wish to its new list; a list that isn't online (Only me): the wish disappears for friends and a giver is told as for a removal. "+ New wishlist" in the Move picker.
- **A Friend's list**: only the categories its wishes carry; none: no row and the wishes move up. **No refresh button** (it refreshes itself every 20 s). The friends' web page does the same.
- **Notifications** (owner: a friend got many at once; Reactions and invites/groups/exchange came together): **bundle + buzz once**: news of the same kind about the same thing becomes one notification that updates while it shows; only the first in 3 minutes buzzes; all stacked under one heading. **Catch-up bundled per kind** (not one big summary). **No quiet hours** (owner). Reactions stay once a day around 19:00.
- **My shops: "Search in browser"** (owner's wording) with **Google**; the browser shows **✕ + a search field** to search again. Adding a site to My shops: **"+ My shops" in the browser's top bar** while the site isn't one of yours ("✓ In My shops" right after) **and** "Add … to My shops?" after adding a wish from that site (once per site).
- **Feedback on 2.146 (owner, 7 November 2026; built in 2.147)**: reactions on large cards on **their own line under the price**; in Rows of your own list on the line of ⋮ and the bin, **2 then "+N"**; in Two per row under the name, sized to the card (also on Friends' lists); after the shown ones a **"+N" circle that lists every reaction** (the shown ones too). "**+ React**" with a capital. Emoji picker tabs: **grey icons**, not emoji (they looked tappable). **"Unreserve"** replaces "Undo" for reservations everywhere. Reserving on a Friend's list changes at once (only that wish waits). **Share** next to Reserve on a Friend's wish (the shop link). **"Wishlist of …"** replaces "Owned by …" (it suggested the wishes are already owned). A Friend's list: **the cover scrolls away, the categories stay pinned**. **Home ⋮ on Collab wishlists**: Share (= the invitation, like Invite Friends), Edit name, Delete/Leave; no "Visible to". In-app browser: **"+ Add shop"** (not "+ My shops"); the "Add … to My shops?" bar gets a filled accent button. (Claude, not asked: the friends' web page also got Share; without a share sheet it copies the link.)
- **Small fixes (owner, 7 November 2026; 2.148)**: "+ Add shop" in the browser is a filled accent button; **"Bring back removed shops" is removed** (replaces "Put back the starter shops stays" of 2.144); Total value **with cents**; the "New …" blocks on Home are **smaller and centred** on the lists' pictures; less space between price and reactions in Two per row.
- **Owner, 7 November 2026 (2.149)**: bottom bar **icons only**, a little bigger; My shops icon **shopping bag**; same price font in every view; more room between picture and words in Rows. **"Create a list"**: "Wishlist" / "For things you'd love to get. Shows under Your wishlists." · "Collab wishlist" / "Find and save gifts for someone, together with others." · "Wishlist group" / "A circle of people who can see each other's wishlists." My shops: "Find must-haves in popular shops, or with a quick search." (Claude's wording after the owner found "search the whole web" old-fashioned; the owner may change it) and a "Popular shops" title. **No snow on the menu item**; 🎅 instead of the icon in the season. **Secret Santa card**: red with snow dots and a white pill (a themed illustration: its colours are fixed in every theme, a deliberate exception to STYLE's theme colours).
- **No side menu** (owner, 7 November 2026; 2.150, the owner is trying it out): the profile picture at the top right opens a **sheet from the bottom** with Profile, **Gifts I reserved**, **Received gifts**, Bin and App settings (Claude pushed back on dropping Gifts I reserved; the owner agreed to keep both in the sheet). **Gift exchanges on Home** under the Santa card, all year (your exchanges + "New gift exchange"). The drawer code is only switched off, so it can come back quickly.
- **Profile card on Home** (owner, 7 November 2026; 2.151): photo, name and the three numbers sit in one soft card in the style's look, set apart from the lists; the numbers lose their own tiles inside it (no cards in a card) and get thin dividers.
- **Prices of other products never count** (owner, 8 November 2026, after Zalando's sponsored tile gave a wish its price): sponsored, related and "you might also like" blocks and links to other pages are skipped, for every shop, app and browser editor alike. The correction of wrong prices is **quiet** (owner's choice). Not changed: which price a page's size list gives (owner: no evidence that it was the cause).

## One way to act on wishes (owner, 12 November 2026, built in 2.196)
- **Replaces the earlier per-view notes** (the long-press menus of 2.105/2.111, "Two per row ⋮", "Large cards: Share
  instead of the pencil", 4 November 2026; collab wishlists keeping their long-press menu, 7 November 2026). Owner:
  "I don't want long pressing on a wish to differ between lists"; asked with pop-up questions:
  - **Long press on a wish starts selecting**, on every list you can change (your own and collab wishlists), in all
    three views. Selected: **Move to…** (a filled button: the text button was hard to read in dark mode) and Delete.
  - **The ⋮ on a wish opens a sheet from the bottom** (not a drop-down), the same everywhere: the wish's photo, name
    and price at the top, then big rows with icons. Order: **Reserve first** on collab wishlists and friends' lists;
    then Edit, Share, Move to…; on your own lists Private, Owned and Price history. The bin stays on the card.
  - A page per wish was considered and not chosen (it would change what a tap does).
  - **Long press on things that aren't wishes** (lists on Home, friends, group members): left as it is for now
    (owner); listed in `docs/TODO.md` (group members' Remove is only behind a long press, groups and exchanges on
    Home have no ⋮).
