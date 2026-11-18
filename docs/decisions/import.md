# Import: bringing a list in from elsewhere

Owner's decisions of 18 November 2026 (interview after Listful's Import sheet). Replaces "importing a whole list: seen,
not chosen" in `ideas.md` (the owner brought it up again).

## What it is for
- For people moving from GoWish, Giftful, Amazon (or keeping wishes in a notes app): their list arrives in one go,
  without typing it again. Goal (owner): work better and do more than Listful's Import.

## Where from
- **Sites with their own reader:** GoWish, Giftful, Amazon wish lists, bol.com wish lists ("verlanglijstje"), Etsy
  favourites, Pinterest boards. Target and Walmart (US only) not chosen.
- **Any other page:** a general reader collects every product it finds (title, photo, price, link); you tick the
  ones you want.
- **Text from notes:** each line is a wish; a link in the line fetches photo and price; a written price ("Lego €39")
  becomes the price; bullets, dashes and numbering are removed; empty lines skipped.
- Pinterest: a pin without a shop becomes a wish with its photo, its title (if any) and the pin's link; a pin that
  points to a shop uses the shop's link.

## How
- Paste the link (or the text) in the Import sheet, or Share a list link from the GoWish/Giftful/Amazon app to
  Wishlist. The app reads it out of sight; when that finds nothing it offers to open the page in its own browser with
  "Import this page", and to paste the list as text.
- A check screen: every wish found with a tick box; a wish already on the chosen list (same shop link) is unticked
  with "Already on this list". Destination: a new list first (the original's name, and its date as the occasion when
  there is one), or any of your own lists. **Own lists only**, never a collab wishlist (move or copy afterwards).
- Wishes come in at once; missing photos and prices fill in quietly afterwards, also later when offline now. Say that
  it can take a minute before all details show (owner).
- What comes along: title, photo, price, shop link, "favourite / most wanted" → ♥ Love this, the list's name and date.
  Not chosen: notes/descriptions, quantity.
- Extras chosen: **real shop links** (tracking and referral links unwrapped, so price-drop alerts work from day one;
  the done message says so) and **sharper photos** (the shop's own big photo replaces a list page's thumbnail).
  Not chosen: undo the whole import, link filled in from the clipboard, fetch new wishes again, a whole profile at once.
- After importing from a site: "Gifts reserved on GoWish don't come along. Send your friends your new list so nobody
  buys the same gift twice." with a Share button.
- Entry points: the + menu ("Import a list"), the empty Home and the welcome, Share from another app.
- App only for now (the browser can't read these sites; the web pages get nothing).
- The Import sheet shows each site's icon live from Google's icon service, like Discover's shop tiles (owner liked
  Listful's logos; copying logos breaks Rule 7 and risks a Google Play refusal).

## Keeping it working (owner: "can't you alert me or build something automatically?")
- **Daily check on GitHub** (free for a public repository, costs no Claude tokens): every day the readers run on a
  public test list per site that the owner makes (3–4 wishes, one with a price, one without a photo; the owner will
  send the links later). A failure fails the job and opens a GitHub issue, so the owner gets an e-mail.
- **Updatable readers:** the readers are one JavaScript file on our web pages (`web/import/reader.js`), also built
  into the app as a fallback; the app uses the newer one, so a fix reaches every phone within a day without a new app.
- **Quiet reports:** when an import finds nothing, the app reports the site, the reader version and the count through
  the crash reports (no link, no names, no wishes).
- Built in two steps (owner): first the sheet, check screen, text import, the any-page reader, the entries, the
  updatable readers and the daily check; then one reader per site once the test lists exist.
