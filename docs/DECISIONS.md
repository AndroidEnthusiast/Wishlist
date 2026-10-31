# Decisions

What the owner has decided, and why. Follow these unless the owner changes their mind; when they decide something new, add a line here (newest at the bottom of each section) in the same commit as the change.

## Working together
- **Changes go straight to `main`.** No pull requests, no test builds, no test release. The owner found branches, test APKs and two install links confusing ("app not installed") and wants to iterate on one app. (October 2026)
- **One Claude Code session per feature or topic.** Long sessions get summarised and lose detail; this file and `CLAUDE.md` are what a new session reads. Before ending a topic, write any new decision here.
- **The project lives as normal files in the repository**, not in `wishlist-android.zip`, so every session reads `CLAUDE.md` and this file automatically. Never bring the zip back.
- **Explain plainly, in UK English, tap by tap.** Say what you are changing before or as you change it, and be honest about what was and wasn't tested.
- **Show previews of visible changes** (an HTML mock-up or simulation) when the owner asks to see something before it is built.

## Language
- **UK English everywhere, and everything also in Dutch.** App: App settings > Language (English (UK) / Nederlands). Web pages: a language switch. Wishes themselves are never translated.

## Look and feel
- **Colours and themes apply to the app only.** The app has palettes (indigo by default, plus sage, rose, ocean, amber, lavender), the phone's wallpaper colours (Android 12+), light/dark/system and AMOLED black.
- **Web pages only offer light and dark** (plus following the device), as a bare minimum. No palettes on the web.
- **Font choice in the app**: a library of 14 popular fonts, Google Sans is the default and is built in.
- **The friends' page looks the same as the app and the browser editor**, for both "My wishlist" and shared lists.
- **List settings vs App settings**: each list has its own settings (opened with the settings button where the refresh button used to be); things that are about the whole app sit in "App settings". Pull down to refresh; the refresh circle is hidden when idle.
- **Wording settled by the owner**: "+ Add wish"; "Instant delete wishes" ("Turn on to delete wishes without warning"); "Notifications for price drops" (on by default) and "Notifications for price surges" (off by default).

## Prices
- **Price rises are shown too**: the old price stays visible, with a red ↑ and the percentage. Price drops as before.

## Lists and sharing
- **No email accounts.** The owner prefers that users never have to sign up with an email address. A list belongs to the phone that created it (its edit key).
- **Lists are link-only, never public.**
- **"My wishlist"**: friends reserve gifts; the owner never sees reservations (surprise).
- **Shared wishlists are only for the givers** (the person the gifts are for is not on it). So everyone on it sees and makes reservations. Shared lists have no "most wanted", no "got it" and no price history, but every phone on the list gets price-change checks and alerts.
- **Only the phone that created a list can make a new invite link or delete the list.** Consequence: uninstalling the app loses those rights (it happened once; the old list had to be deleted by hand in Supabase). A recovery key backup was suggested to avoid this; not decided yet.
- **Invitation page**: title "You're invited to add gifts for:" with the list name centred, bigger and bold in the same font; gift emoji; confetti that never moves the text; no "Already have the Wishlist app?" line.
- **Link previews**: invites use `web/join/` and friends' links use `web/list/`, each with its own preview picture; sharing from the app also sends a picture with the list name.

- **Bin (October 2026)**: a deleted wish goes to the bin (Menu > Bin) at once. It stays there 10 days, then it is deleted for good. One bin for all lists. Wishes in the bin get no price-drop or price-rise notifications. The bin has no refresh button: it is always up to date by itself.
- **The delete message is short**: just "Wish moved to the bin" with Undo, and it goes away after a few seconds (about 4).
- **Everyone on a shared wishlist sees its bin** and can restore or delete its wishes. On My wishlist only the owner sees the bin (and still never sees reservations).
- **"Your reserved gift has been moved to the bin"**: if someone other than the person who reserved a gift moves it to the bin, that person gets this notification. Tapping it opens the bin, scrolls to the gift when it is out of sight, and lets it glow softly.

- **Price checks on shared lists are shared between the phones**: when one phone has checked a shop's price, the new price shows for everyone and the other phones skip that wish until it is due again. Everyone still gets the alerts.

## Security
- **The app is signed with a private key** kept only in the GitHub repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (since version 2.31). Never put the key or its password in the project and never change the key, or updates stop installing over the app.
- **No import/export feature** for now; the owner accepted losing wishes when the signing key changed.

## Backend
- **Free Supabase plan** (500 MB database). Usage is small (estimated 20–30 MB in October 2026). SQL scripts are run by the owner by hand; give exact steps.

## Open ideas (not decided)
- Personal recovery key: an encrypted online backup of your lists, keys, creator rights and settings, restored with a key (optionally saved in Google Password Manager), plus automatic clean-up of lists nobody uses. Ask the owner before building.
