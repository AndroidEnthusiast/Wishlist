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
  lists, and wishlist group and gift exchange lists when that person is a friend. Hidden when they aren't a friend, and
  on shared (collab) lists (they belong to several people).
- It opens your chat with them, the wish **attached above the typing bar** (photo, name, price, ✕ to remove), like
  Reply. Your text and the wish go as **one message**; an older app version shows only the text.
- **Tapping a wish card in a chat opens the wish in its list**, glowing, for both people (the list's owner sees it in
  their own list); the shop is one tap further. This goes for every wish card in chats, also the ones sent with the gift
  button. Wish gone: "This wish is no longer on the list" and the list opens; the whole list gone: only the message.
