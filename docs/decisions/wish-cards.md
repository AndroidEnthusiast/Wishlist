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
