# Decisions: Open ideas (not decided)

Moved here word for word from `docs/DECISIONS.md` on 12 November 2026 (notes tidy-up, owner). Index and the general
decisions: `docs/DECISIONS.md`. Add new decisions on this topic here.

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
- **Messages tabs** (owner, 2.101): no + in the top bar (confusing next to the bottom bar's + for wishes). Two tabs: "Chats" (default) and "Friends" (tap a friend: the chat opens). With no conversations, Chats shows a "Start a chat" chip that opens the list of friends; it goes once there is a conversation. **Replaced (owner, 15 November 2026, 2.209):** see `people.md`, "Owner's review of 15 November 2026".
- **No flicker when switching views** (owner, 2.101): photos must not flash when switching large cards / two per row.
- **Gift lists** (owner, 2.102): "Wishlists for someone" are called **Gift lists** (Dutch: Cadeaulijsten), next to "My wishlist". The new-list form: title "Create a gift list for someone", text "Collect gift ideas together for someone you want to surprise. Everyone you invite can add ideas and reserve what they'll buy, so no gift is bought twice.", field "Gift list name*". No "Your name": the profile name is used for "Added by" (also when joining); the per-list name in ⚙ is removed. The browser editor (no profile) still asks a name.
- **"Owned by …"** (owner, 2.102) under the name of a friend's wishlist, in the app and on the friends' web page. Not on gift lists.
- **Colour picker** (owner, 2.102): the same colour in light and dark (no lighter dark tone); the two previews keep small labels "Light mode" and "Dark mode".
- **Chat** (owner, 2.102): tap the friend's picture or name at the top of a chat to open their page; read ticks closer together; the gift button lined up with the text box.
- **No flashing photos** (owner, 2.102): switching tabs or views must never reload a product photo that was already shown; toggles keep their style's shape when on; the bottom bar must not jump when coming back to the app (cause: keyboard of another app; the app now draws edge to edge).
- **Number tiles follow the style** (owner, 2.103): the Wishlists / Wishes / Top wishes tiles next to the profile picture are a small card of the style in use (Quiet stays subtle).
- **Friend code as six blocks** (owner, 2.104): three blocks, a dash, three blocks, in the style; capitals; paste works (also the whole shared message). Full code: wait for the button, no auto-send (owner).

## Solo use: what the owner wants next (interview of 16 November 2026, not built yet)
- The owner uses the app alone for: waiting for a price drop, saving up / deciding, remembering ideas, comparing options.
- **Sold out / back in stock** (chosen): a grey "Sold out" label on the picture and a "Back in stock" notification,
  through the same two-readings rule as prices (`PriceWatch.judge`), own lists. Not per size.
- **Screenshot to wish** (chosen): share a screenshot (Instagram, TikTok…) to Wishlist: Add a wish opens with that
  picture. *(Planned in detail on 20 November 2026: `docs/decisions/screenshots.md`.)* Video links as ideas, cropping and a "Find in a shop" search were not chosen.
- **Cheaper alternatives from the internet:** wanted, but left for now (a free version would be janky; a real one
  needs a paid shopping search). Owner asked how people save finds on TikTok: mostly the platform's own Save, screenshots
  and shared links.
- Not chosen: "Bought it myself" history, think-it-over timer, budget per list, savings overview, same product in other
  shops, a widget, quick note wishes.

## Features seen in competitor reviews (owner, 16 November 2026)
- **Not wanted:** product search inside the app and recommended products (they need a paid product database or
  affiliate links, which clashes with a clean, ad-free app). "Search in browser" in My shops stays.
  (22 November 2026: the owner chose "Search the shops", each shop's own results, not a product database:
  `shop-search.md`.)
- **Seen, not chosen (the owner skipped the question, so nothing is built; ask again only if the owner brings it up):**
  compare prices (cheap version: a "Compare prices" row opening a Google Shopping search), adding your own wishes on
  the web page (laptop/Chromebook), importing a whole list (Amazon blocks reading its lists; chosen later, 18 November 2026: `import.md`), a public profile with
  @handle and following without a friend request (big, with safety risks for young users), stickers on a profile.

## The idea list of 22 November 2026 (owner: "I need a vast selection of serious ideas"; none decided)
- Owner's answers that day: **lists for your children: build later, remember it**; the two-phone test (R7) and the
  Play Store path: **"neither yet"**; "simplify the concepts" was explained (start from "What are you planning?" in the
  person's own words; the four kinds stay), not chosen yet. Pop-ups hold at most 4 options: give long lists as text.
- The list (S small, M medium, L large; "skipped" = passed on before):
  Saving faster: 1 home-screen widget / quick tile (S, skipped) · 2 barcode scan in a shop, searches your shops (M) ·
  3 saving from a Chromebook or laptop (extension or web adding, M–L, skipped) · 4 voice "Hey Google" (L).
  Saying what you want: 5 "No thanks" list shown to givers (S) · 6 options on one wish (S–M) · 7 wishes without a
  product: an idea, experience, donation (S) · 8 price range (S) · 9 must-have / nice-to-have (S) · 10 several shops
  per wish (M, skipped).
  Givers: 11 private gift ideas per person, brought up before their birthday (M) · 12 filter a friend's list by budget
  (S) · 13 own note on a reservation + arrival reminder (S) · 14 reminder to givers before an occasion, if the wisher
  allows (M) · 15 chip in (L, planned) · 16 print / picture / PDF of a list (S–M) · 17 "Bought" after "Reserved" (M).
  Occasions: 18 nudge 3 weeks before your birthday to share (S) · 19 friends' birthdays in the phone's calendar (S) ·
  20 reuse last year's list (S) · 21 countdown to an occasion (S).
  For yourself: 22 sold out / back in stock (M, chosen) · 23 savings goal per wish (S) · 24 think-it-over timer (S,
  skipped) · 25 your year in wishes (S–M).
  Family: 26 lists for your children (M–L, later) · 27 family overview (L).
  Ideas on your own terms: 28 search shortcuts in Search in shops (S) · 29 "new wishes from friends", opt-in (M) ·
  30 "surprise me" search for a budget (S).
  Trust: 31 export everything (S–M) · 32 automatic backup to your own Google Drive (M) · 33 privacy promise page (S) ·
  34 accessibility / TalkBack check (M) · 35 Dutch (M).
- Claude's strongest candidates: 5, 11, 18, 31. Big or upkeep-heavy: 4, 15, 27, 29. Serious issues named the same day:
  R7 never done (the "no gift twice" promise untested on two phones), APK from GitHub (Play Store needs the final
  name), too many concepts for a newcomer (co-op wishlist vs wishlist group), a full chat is heavy upkeep next to
  WhatsApp, inconsistent copy ("Invite Friends" / "Invite friends", "No Friends yet. Add Friends on the Friends tab").


## The "groundbreaking ideas" interview of 26 November 2026 (owner: "truly useful, not gimmicky")
- Owner: many first ideas felt gimmicky; not sure people would use thank-yous or reminders. Round 1 (20 ideas), only
  chosen: **price drop for givers**: every gift I reserved that drops in price gives me a notification, at any time
  (not tied to a birthday), **only when the person turned it on** (a setting). Skipped (= none of these): Reveal day,
  time-capsule message, QR gift tag, hint drop, "Still wanted" badge, doesn't-fit rescue, vote on co-op lists,
  dead-link rescue, this-or-that poll, happy-with-it check, second life, gift memory, near-a-shop nudge, AI gift
  helper, send to door without the address, occasion autopilot, mood-board wish, photo → find it, gift budget guard.
- Round 2, chosen: **reservations run out** (a forgotten reservation frees the wish again after the giver is asked
  "Still buying this?"; needs a server script). **Shopping list by shop**, own wishes only (not reserved gifts):
  a "Shopping list" row on Home for all your lists plus a "By shop" view inside a list; one card per shop with
  ticks and a total, "Order at …" opens the ticked products one by one ("Next (2 of 3)"; no shop lets an app fill
  its basket), "Mark as ordered"; **an App settings switch, off by default** (owner). **Historic low**, only that:
  the price is the lowest since the wish was added (label and/or alert wording: ask), no fake-deal warning.
- **"How many people in the world added this wish": later, after Google Play** (owner): small numbers at first,
  shop links would have to go online (opt-in, a hidden code, nothing below 5 people), a new server table.
- Round 2, skipped: ask the wisher anonymously, reserve together, "I already have" list, open in the shop's app,
  discount codes, fresh price at Buy, compare side by side, star rating, find it second-hand, only notable drops,
  daily price summary, price in your currency, free-delivery helper.
- Already in the app (not offered): target prices (`Targets.kt`), gift exchange exclusions, search, drag to order,
  selecting several wishes after a long press.
- Round 3 (14 ideas), all skipped (= none of these): gift-proof lock, who sees which wish, hide prices from givers,
  who has looked, "always welcome" wish, gift card wish, bundle wish, wish for an occasion, list check-up, sizes per
  brand, suggestions inbox, text-only shares, clean-up screen, warranty and receipts.
