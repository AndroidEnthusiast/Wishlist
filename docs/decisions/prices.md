# Decisions: the price tracker's accuracy

Index: `docs/DECISIONS.md`. Earlier price decisions: `screens.md` ("Prices"), `PriceWatch.kt` (rules 1–4, 10 November
2026). Add new decisions about reading prices and price checks here.

## The false "−20%" on bol.com (owner, 23 November 2026; built in 2.282)
- Owner: an Elho pot on bol.com showed "−20%" (€7.50 → €6.01) while the shop showed €8.09; "the price tracker is a USP
  of the app … it needs to be flawless … the accuracy of a surgeon's scalpel"; "learn from it and upgrade the price
  tracker"; the owner gladly sends "Price is wrong?" files.
- **What happened** (the owner's file): the page is one family of 13 sizes and colours (a ProductGroup). The link ended
  in `?cid=…` (bol.com's tracking), so no variant matched; the reading then took the family's "from" price
  (AggregateOffer lowPrice €6.01 = a Mini 9 in white, another product). It read that twice, so rule 1 believed it.
- **Owner chose:** "same place on the page" and "never the lowest seller" (not chosen: "Not right?" on the alert,
  comparing with the page you see in the app).
- **Built (app `Fetch*.kt` and `web/parse.js` together, Rule 2):**
  - A family's variant is found by its address with tracking bits ignored (cid, utm_…, gclid, fbclid, ref, tag, …),
    every detail of the variant's link (size=m) also in the page's link; two equally good: unsure = no price.
  - No match: only a price every variant shares; never the family's "from" price or the first variant's.
  - Never the lowest seller: an AggregateOffer counts only when lowest = highest (or one seller); a list of offers only
    when all ask the same. Otherwise no price from the product data (the later tries may still find the shown price).
  - Prices per unit ("€ 19,98 per kg", "/l", "per stuk", unit-price / base-price classes) are never the price.
  - Every reading says where it came from (`Meta.src`: tags, amazon, data, micro, rawdata, shown, text).
  - `PriceWatch` rule 5, **same place on the page**: the place of the believed price is kept (`price_watch.src`,
    database step 2 → 3). A different price from another place is "couldn't be read", never a change; another place
    that agrees is learnt. Notes from before 2.282 have no place: their first different price is taken quietly (no
    alert) and recorded in the history, so the owner's wish corrects itself to €8.09 without a false "rise".
- Checks: `PriceWatchTest` (rule 5), `tests/shared/same-answers.json` (the real bol.com page, cut to its product data,
  and six more cases), `parse_test.js`, `FetchSameTest` re-recorded (no existing page changed its price except the
  reversed "several sellers" case).
- **Known limits, told to the owner:** co-op wishlists' price checks (`SharedPrices`) get the better reading but not
  rule 5 (that would need the server); a page whose price is read wrongly from the very first look (and the same way
  every time) still needs "Price is wrong?"; shops change their pages without warning, so 100% doesn't exist; the aim
  is no false alerts.

## Sizes with their own price, two readings in one check, an old-price note (owner, 23 November 2026; planned, not built)
- Found: Zalando perfumes (30/50/100 ml) whose link names no size: since 2.282 no "from" price is taken, so the check
  read nothing and the old €79.95 stayed silently while the shop said "Vanaf €59,95" (the co-op list "Ola, Paulina,
  Zuza": La Vie est Belle, Alien, My Way). Zalando's page lists every size with its own price (JSON-LD
  `ProductGroup.hasVariant[].size` + `offers.price`, seen on the Barbour page).
- **Owner's choices:** (1) **pick the size and track it**: when a link has sizes with different prices the wish asks
  "Which size?" (each with its price), the size is remembered (the wish's existing `size` field) and every check reads
  that size's price; a wish without a size shows "from €59.95" with a "Choose size" tap until someone picks one.
  (2) **read twice within one check**: each check opens the page twice a few seconds apart and changes the price at once
  when both agree (still two readings, Rule 8). (3) **a check that can't read a price** keeps the last price and, after
  more than a day without a reading, shows a small grey "Price from 3 days ago" line.
- Waiting for: the owner's "Price is wrong?" file of the La Vie est Belle page (Zalando can't be opened from Claude's
  computer), to build and check against the real page. App (`Fetch*.kt`, `PriceWatch`, `SharedPrices`) and web
  (`parse.js`, `edit.js`) together, with cases in `same-answers.json` and `PriceWatchTest`.

## Tapping a notification about several price changes (owner, 27 November 2026, 2.296)
- Found (owner's screen recording): "2 wishes got cheaper" about two Co-op wishes opened Price changes, which only
  shows your own lists, so only an unrelated wish of your own was there.
- **Owner's choices:** (1) **one notification per list**: drops on a Co-op list and on your own list in the same check
  are separate notifications (the list's name sits next to the app's name at the top). (2) **2 or more changes on the
  same list open that list**, for Co-op lists **and** your own lists (before: Price changes). One change still opens
  as before: your own wish its price page, a Co-op wish its list on that wish. Check: `AlertsPartsTest`
  (`priceSummariesComeOnePerListAndOpenThatList`).
