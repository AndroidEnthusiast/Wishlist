# Decisions: Lists, sharing, security and the server

Moved here word for word from `docs/DECISIONS.md` on 12 November 2026 (notes tidy-up, owner). Index and the general
decisions: `docs/DECISIONS.md`. Add new decisions on this topic here.

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

## Decisions and why (moved from `docs/HANDOFF.md` on 12 November 2026)
- Lists are link-only, never listed publicly. Reservations are hidden from the owner and from editors so gifts stay a surprise.
- Shared lists are online-first: no internet means the change is undone with a message (simple and safe). Same field changed by two people at the same moment: the later save wins.
- Only the creating phone can reset the invite link or delete the list; the browser editor therefore has no such buttons.
- One GitHub workflow for build, release and web pages. No second workflow (an earlier idea caused confusion).
- The Supabase URL and publishable key are public on purpose.
- The browser editor reads shop pages via the helper function, because browsers may not load other sites. The page itself parses the HTML (`web/parse.js`).
