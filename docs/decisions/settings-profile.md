# App settings, Profile and Home's top (decisions)

Moved here from `screens.md` on 18 November 2026 (its size limit); newest at the bottom.

## App settings redone (owner, 18 November 2026: "insanely long and bloated with copy")
- **A short list of rows** on one screen, each with an icon in a soft tile (like the + menu), its current value in grey
  and ›, each opening its own small page: Notifications, Prices, New wishlists, Backup, Display, About. No explanation
  lines unless a label is unclear, then a few words.
- **Theme leaves App settings** (it is in the side sheet). **Notifications** holds every alert, the price drops and
  increases included. **Prices**: "Check prices · Every 3 hours" as one row opening a small list with a tick (chips
  were too big, owner), and the currency. **Backup** stays a row (Export, Import, one warning line).
- **Display:** only "List summary" stays a switch. "Price labels on photos" and "Descriptions" are removed and always
  on; their code goes too (owner: remove the code of whatever is removed).
- **About:** the version; "Download" only when a newer version exists; Share app; Licences; Privacy. The crash report
  text moves into Privacy. "Server calls today" is hidden: a long press on the version shows it.
- Earlier decisions this changes: App settings > Backup stays (screens.md "Export and import"), the crash reports line
  "said in App settings > About" now lives in Privacy, the server calls counter (`Calls.kt`) moves behind the long press.

## Profile redone, Home blocks and swipe, shared links (owner, 18 November 2026, night; 2.230)
- **Profile like App settings:** photo and name big and centred at the top (tap to change), then short rows with their
  value in grey and ›: Birthday, About me (each edits in a small sheet), Keep your lists safe, Blocked people (only when
  there are any) and Delete my account (red, last). No explanation paragraphs.
- **Friend code only on the Friends tab** (owner); **"Wishlists your friends can see" removed** with its code.
- **Backups kept, tucked away** (owner wanted them removed; Claude advised against: the recovery key needs the server,
  a file is the last safety net and the rescue screen's way back). One "Keep your lists safe" page on Profile: the
  recovery key first, "Backup file" (Export / Import) as a small row at the bottom. App settings loses its Backup row.
- **Home blocks back** (owner): your photo on the left with three blocks on its right: Wishes, Wishlists, Total value,
  counting your own lists only. Tapping Wishes opens All your wishes, Wishlists all your wishlists; Total value does
  nothing. App settings > Display: "Home summary" (on by default); off puts the photo back in the middle.
- **A divider under the profile part**, and only what is below it swipes: two pages "My wishlists" | "Shared" (your
  wishlists with others and your wishlist groups), with tabs; gift exchanges under them on both. App settings > Display:
  a switch for it (on by default); off shows the lists one under the other as before.
- **Shared text with one shop link is one wish** ("I just found this on AliExpress: … https://a.aliexpress.com/…"):
  Add a wish with only the link, never Import. Import is for list links, two or more links, or lines without links.
- **AliExpress shows its logo instead of the photo:** not fixed yet; Claude can't open AliExpress from its computer and
  the owner declined (the tool was refused) to look through GitHub's. The owner sends the page from "Price is wrong?".
