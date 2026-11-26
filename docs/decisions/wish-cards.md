# Wish cards: their buttons, reserving and reacting (decisions)

Moved here from `people.md` on 18 November 2026 (its size limit); newest at the bottom.

## Someone else's wishes: compact Reserve, Add to my wishlist, one reaction (owner, 18 November 2026; 2.229)
- **Reserve / Unreserve on a card is a compact pill** (owner: the regular size "barely feels proportional"): 36 dp to
  see, still 48 dp to tap (`LocalCardButton`), on friends', groups', gift exchanges' and collab wishlists' cards.
- **"Add to my wishlist"** in the ⋮ of someone else's wish (friends' wishlists, wishlist groups, collab wishlists, gift
  exchanges): a small list picker, copied straight in, "Added to …" with Undo (owner chose this over the filled-in
  form). Only link, name, description, photo (online only), price and currency come along; never their note, size,
  quantity, Love this, reservations or reactions. App only (the web pages have no lists of yours).
- **One reaction:** once you reacted, "+ React" hides; tapping your reaction brings it back to pick another; tapping
  yours once more removes it (owner). The same on the web pages (`together.js`).

## The buttons on a wish card, one order everywhere (owner, 18 November 2026; 2.232, "it looks sloppy")
- Reserve / Unreserve first (at the left in Rows, right after the price on a large card), then the icons (share, bin),
  and the ⋮ always last at the right edge, as in Two per row (`CardButtons`, `cardIcon` in `WishCards.kt`). One icon
  size per view (20 dp in Quiet and Rows). The Reserve pill has 16 dp inside it again.
- Two per row keeps its bin next to the price, under the ⋮ (owner).
- On a shared wishlist with Reserve showing, Reserve + share + bin + ⋮ don't fit a phone's Rows card: Share is then only
  in the ⋮ (owner chose this over moving Delete or smaller tap areas).

## Chat about this wish (owner, 20 November 2026)
- **"Chat about this wish"** in the ⋮ of someone else's wish, **wherever the list's owner is your friend**: a friend's
  lists, and wishlist group lists when that person is a friend. **Not on a gift exchange giftee's list** (owner, after
  Claude pointed out it would show you are their Secret Santa). Hidden when they aren't a friend, and
  on shared (collab) lists (they belong to several people).
- It opens your chat with them, the wish **attached above the typing bar** (photo, name, price, ✕ to remove), like
  Reply. Your text and the wish go as **one message**; an older app version shows only the text.
- **Tapping a wish card in a chat opens the wish in its list**, glowing, for both people (the list's owner sees it in
  their own list); the shop is one tap further. This goes for every wish card in chats, also the ones sent with the gift
  button. Wish gone: "This wish is no longer on the list" and the list opens; the whole list gone: only the message.

## Labels on cards, and "I have it" (owner, 20 November 2026, after screenshots of overlapping labels)
- The owner's screenshots: the price label and "Reserved by you" weren't lined up (Large cards), overlapped (Two per
  row) and "Reserved by you" didn't fit its chip (Rows). Owner: "I need to trust that whatever you build is perfect and
  not just done": fix these and any other poorly made UI; check every view, both styles, light and dark, a narrow
  phone and large phone text before pushing.
- **Reservations are no longer on the photo.** "Reserved by you" (or "Reserved" for someone else, "You reserved 2", …)
  is a small line in the card, right above the Reserve / Unreserve button, in every view. The photo keeps the price
  label (and the Love label), on one line that can't overlap. ("✓ You", "Yours", "You're buying", "Your gift" were
  turned down as too vague.)
- **"I have it" goes straight to Received gifts**: no question; the wish leaves the list at once (the total goes
  down) with "Moved to Received gifts" and Undo; whoever reserved it gets the thank-you as before. "Keep it on my list,
  marked Owned" is gone. The page keeps its name "Received gifts".

## Chat about a wish on a shared wishlist (owner, 21 November 2026; replaces "hidden on shared (collab) lists" above)
- **"Chat about this wish" is also in the ⋮ of every wish on a shared (collab) wishlist.** It asks who to chat with:
  "Chat about this wish with…" lists everyone else on the list (friends first). **Friends can be tapped** (their chat
  opens with the wish attached); **the others are greyed, "Not your friend yet", with Add friend** (the normal request
  with approval; their friend code never reaches your phone); "Friend request waiting" when one is open.
- **The picker always shows, except when there is exactly one other person on the list and they are your friend**:
  then that chat opens at once (owner's own answer).
- **Offline:** the last answer is kept per list on the phone, so the picker opens offline too; the first time ever
  offline: "Can't see who's on this list right now" with Try again. Before the server script is run: "This needs an
  update of the app's server first".
- Only people whose app joined the list's members (2.128 and later) show; nobody yet: a line that says why.
- A wish card from a shared list in a chat opens that shared list on the wish. Server: `wl_sh_people` and
  `wl_sh_befriend` (`supabase-features.sql`, version 2); app: `SharedPeople.kt`, `ChatPeople.kt`; checks:
  `ChatPeopleTest`, `ChatPeopleScreenshotTest`, `tests/sql/features_test.sql` part 4.

## A wish you're sent to shows whole before it glows (owner, 21 November 2026, a screen recording)
- Tapping a price-drop notification or a wish card in a chat opened the list and made the wish glow, but half of it
  sat under the filters pinned at the top. Now every way to a wish (notifications, chat cards, All wishes, group
  members' and giftees' lists, friends' lists; own and co-op lists) scrolls it **fully into sight below the pinned
  part, with room for the glow, in every view** (Rows, Two per row, Large cards), then glows. One helper for all:
  `FocusScroll.kt` (`showWhole`), used by `ListScreen.kt` and `Friend.kt`. Check: `FocusScrollTest` (fails on the old
  scroll).

## Reservations on the button, new wishes per list, list icons (owner, 23 November 2026, 2.284)
- **The reservation is the button, no extra line** ("clicking reserve moves the button down… the cards get busy"):
  Reserve → **"Reserved by you" with a lock** (a 2× wish: "You reserved 2"). Version 2.285 had a two-tap
  "Unreserve?" on the button; **since 2.286 (owner: "I don't understand why I have to tap twice") one tap opens the
  "Unreserve?" pop-up**, like every other pop-up: a padlock that opens as it appears (shackle lifts and swings open,
  left leg stays in the lock; shown open at once with animations off), "“…” becomes free again, so someone else can
  reserve it.", **"Yes, unreserve"** (filled) and **"No, keep it"** (the way out; owner asked yes/no, written as the
  style guide's verbs). App `UnreserveDialog` / `OpeningPadlock`, web `askUnreserve` in `reserve.js`. The padlock and
  the words on the button share one middle (owner: "not aligned"). Someone else's: **the card is darkened
  and the button says "Reserved" with a lock**, greyed; tapping it still asks "Is it still being bought?". The line
  under the photo keeps only "Owned", "1 of 3 reserved" and "Over budget". One `ReserveButton` (`WishCardParts.kt`)
  in every view, on friends', group, gift exchange and Co-op lists; the web pages the same (`WLRes.button` in
  `web/reserve.js`, "Unreserve?" inside the card). The ⋮ keeps its plain "Unreserve" row (no question there).
- **Gifts someone else reserved sink below the free ones** on a friend's list (app and web, also group and gift
  exchange pages), owned ones last. Not on Co-op lists (they have "My order"). Owner skipped the other ideas
  ("Hide reserved" filter, a "Did you buy it?" nudge, chipping in together): build nothing from them.
- **New wishes per list** ("clicking the friend with the dot doesn't show the wishes that are added"): a red badge with
  the number on each of a friend's wishlists; opening that list makes only its wishes seen; the new ones carry a
  **diagonal "NEW" ribbon** in the card's top right corner until you leave the list (owner chose the ribbon; first
  royal purple, then **red like the count**: "the ticker and ribbon should be the same colour as they are related"). The friend's row keeps "3 new wishes" but **no red dot**.
  `FriendNews.kt`; the first time a friend is counted nothing is new (as before).
- **"Your wishlist was viewed" stays gone** (owner asked again, 23 November 2026): removed from screen on 15 November;
  the server's unused `wl_view` counter is kept only so very old apps don't break.
- **One person / two people icons for the kind of wishlist in every list picker** (Save to, Move or copy, Which
  wishlist?, Add to my wishlist, Overview): `listKindIcon` in `FormParts.kt`, the same icons as the New wishlist chips.
- Checks: `ReserveAndNewsTest`, `ReserveScreenshotTest`, web `index-page`, `e2e/t3.js`, group, exchange, together.

## The golden padlock (owner, 24 November 2026, 2.288)
- Owner: "prettier animations… an icon that looks in colour like the lock emoji 🔒"; on Reserve "the metal bar swings
  into the padlock", on "Unreserve?" the padlock opens. Our own drawing (rule 7, not the emoji's art): gold body with a
  shine and darker rim, silver shackle drawn behind it, keyhole (`Padlock.kt`; web `padlock()` in `reserve.js`).
- **Where** (asked): in the animations and on "Reserved by you"; **someone else's "Reserved" is grey**. **Reserve:
  on the button itself** (owner: "small but not so small it's not visible"): it starts open at 1.6× (1.9× poked out
  of the small web button), swings shut and drops in (0.42 s), then settles with a small bounce. Only right after
  your own tap (also when the web page draws the card again meanwhile: it carries on). **Unreserve?**: the same
  padlock lifts and swings open. "Remove animations" / reduced motion: shown in its end state. **App and web pages.**
- Other "3D" animations offered (gift exchange card flip, Most wanted star spin, price tag flip, gift box lid): the
  owner chose none, so nothing is built. Advised: few, short (under half a second), never in the way.

## The owner's review of 25 November 2026 (2.290, phone screenshots)
- **Price tag removed** (owner: "the price tag is missing… in hindsight I don't want it"). It only showed on a wish's
  price page; the "−21%" on cards is the drop badge, which stays. See look.md (little real objects).
- **Two per row: cards side by side the same height** (owner: the candle cards differed). The old price wrapped below
  only when it didn't fit. Asked: **always on its own line, and that line kept empty when there is none** (chosen over
  "no empty line"). `PriceLine(stacked = true)` in `CompactCard.kt`.
- **Received gifts show the gift's photo** ("just text makes it unclear"; `WishThumb`).
- **"Unreserve?"**: offered "photo + padlock on its corner" / "photo, no padlock"; owner: **"the padlock needs to be
  prominent at the top indicating you're unlocking something."** Built: the padlock (72 dp) opening above the title,
  then the gift's photo (64 dp) and name, then "Someone else can reserve it again." instead of the long sentence.
  App and web pages (`askUnreserve`; a 🎁 when the wish has no usable picture).
- **The open shackle looked too long** ("a bit silly"): both legs reached deep into the body, so lifting it showed
  that part. Now a long left leg (stays inside) and a short right leg (pops out). Owner picked option A from a
  picture (A swung open / B slightly / C straight up): the same swing as before.
- **Gifts I reserved with photos** (owner, 25 November 2026: "people don't remember or read text. Wishes are inherently
  visual"). The phone kept only titles; now also the picture ("fresi_<code>", next to the titles so older versions
  still read them), saved on Reserve and filled in by FriendWatch.check for older reservations; gift lists use their
  copy on the phone. Fixed for every place with the same gap: the "Still buying …?" questions, "Someone reserved
  this" and "Reserve it now?" (the gift's photo and name instead of the name in the sentence). Not yet: the "Still
  buying" question on the web pages (`together.js` may only shrink; it needs splitting first).

## Reservations show at once; buttons never touch (owner, 26 November 2026, a screen recording; 2.294)
- **"A gift I reserved seems unreserved at first upon opening a wishlist, then changes to Reserved by you."** Only
  Co-op lists did it (they show their saved copy first; friends', group and exchange lists load list and
  reservations together). Fixed everywhere it happened: the app keeps the server's last answer per Co-op list
  (`Shared.saveReservations` / `cachedReservations`, counts for 2× wishes included) and the browser editor too
  (`WLRes.keep` / `kept` in reserve.js; edit.js may only shrink, so only existing lines changed). A change that only
  arrives while a list is loading (the first 2 seconds) is not "reserved while you look" (app and web).
- **"Share and Add a friend are touching"** (Friends tab). Fixed there and wherever else it happened: Later / Update
  (new-version banner), Add to the draw / Remove (gift exchange). The layout inspector now reports two buttons with
  words less than 6 dp apart in the same row (`LayoutGuard`, `LayoutGuardTest.catchesButtonsThatTouch`), so every
  screenshot check guards it; docs/STYLE.md Buttons says 8 dp.
- Found by the GitHub check while building 2.294: leaving a Co-op list, closing it, or finding it deleted by someone
  else now also clears its reservations, on screen and on the phone (they could linger on screen after a close).
