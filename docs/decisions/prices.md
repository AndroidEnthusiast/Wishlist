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
