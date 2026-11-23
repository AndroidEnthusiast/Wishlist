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
  Reserve → **"Reserved by you" with a lock** (a 2× wish: "You reserved 2"); tapping it asks **"Unreserve “…”?"**
  (Unreserve / Keep it; a 2× wish opens "How many will you buy?" in the app). Someone else's: **the card is darkened
  and the button says "Reserved" with a lock**, greyed; tapping it still asks "Is it still being bought?". The line
  under the photo keeps only "Owned", "1 of 3 reserved" and "Over budget". One `ReserveButton` (`WishCardParts.kt`)
  in every view, on friends', group, gift exchange and Co-op lists; the web pages the same (`WLRes.button` in
  `web/reserve.js`, "Unreserve?" inside the card). The ⋮ keeps its plain "Unreserve" row (no question there).
- **Gifts someone else reserved sink below the free ones** on a friend's list (app and web, also group and gift
  exchange pages), owned ones last. Not on Co-op lists (they have "My order"). Owner skipped the other ideas
  ("Hide reserved" filter, a "Did you buy it?" nudge, chipping in together): build nothing from them.
- **New wishes per list** ("clicking the friend with the dot doesn't show the wishes that are added"): a red badge with
  the number on each of a friend's wishlists; opening that list makes only its wishes seen; the new ones carry a
  **royal purple diagonal "NEW" ribbon** in the card's top right corner until you leave the list (owner chose the
  ribbon and the colour, the same in every theme). The friend's row keeps "3 new wishes" but **no red dot**.
  `FriendNews.kt`; the first time a friend is counted nothing is new (as before).
- **One person / two people icons for the kind of wishlist in every list picker** (Save to, Move or copy, Which
  wishlist?, Add to my wishlist, Overview): `listKindIcon` in `FormParts.kt`, the same icons as the New wishlist chips.
- Checks: `ReserveAndNewsTest`, `ReserveScreenshotTest`, web `index-page`, `e2e/t3.js`, group, exchange, together.
