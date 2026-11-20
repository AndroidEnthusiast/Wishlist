# Decisions: Screenshot to wish (owner, 20 November 2026)

Interview of 20 November 2026, before building. Chosen from `docs/decisions/ideas.md`, "Solo use". Chip in is
not next (owner). Add new decisions on this topic here.

## Who it is for
Someone who finds things on Instagram, TikTok or in a shop app and takes a screenshot: they share it to Wishlist and
it becomes a proper wish (cropped, named) without retyping or saving it to the gallery first.

## One screenshot
- Shared to Wishlist (Android's share menu, a picture): **the photo editor opens first** (the same `HeroEditor` as
  the profile photo, covers and wish photos, in its wish mode), then Add a wish opens with the cut picture.
- **The editor shows how the picture will sit in the list:** live mini cards under the photo, in the user's theme
  (two per row, large card, row), changing while pinching and dragging (owner chose live mini cards over a Preview
  button). Also when editing any wish photo, not only screenshots.
- **A link that comes with the picture** (Instagram, shops) goes into the shop link field; the picture is kept, the
  shop's own photo does not replace it.

## A name for every wish (owner)
- **Every wish needs a name, in the app and in the browser editor** (`web/edit.js`). Until now a wish with only a
  photo could be saved without a name. Old wishes without a name stay as they are; editing one asks for a name.

## Several screenshots at once
- **Up to 10.** More: the first 10 are shown with a short message.
- **First a pick screen:** the screenshots as small tiles, all ticked; "Add to" (any list you can edit: your own
  wishlists and shared wishlists, like "Save to"); Select none / Select all; "Add N wishes".
- **Then one by one:** each picked screenshot goes through the same photo editor and Add a wish form as a single
  one, with "2 of 5" at the top and Skip. The owner asked for it to be smooth and user friendly.
- **Saved is saved:** each wish is saved when its form is saved. The first one makes clear that wishes are saved one
  at a time; **after every saved wish a confirmation shows** that a wish was added.
- **Leaving halfway asks first:** "Stop adding? 2 added, 3 left." (Keep going / Stop); the saved ones stay.
- **At the end a message:** "4 wishes added to My wishlist" with Open the list.
- Not chosen: remembering unfinished screenshots on Home.
- A link shared together with several pictures: the owner skipped "use it for the first wish only", so nothing is
  built for it yet (ask if it comes up).

## Risks the owner chose to handle
- **A picture that won't open** (still in the cloud, not a picture): shown greyed out with "Couldn't open this
  picture", skipped; the rest carry on.
- **Big pictures are always made smaller by the app**, so a picture is never refused for its size (owner).
- **Offline:** as today, wishes are saved on the phone at once; a shared list's photos are sent when back online,
  with a message if that fails.
- **Android closes the app halfway:** the wishes already saved stay, the rest are gone; the confirmations show what
  was saved.
