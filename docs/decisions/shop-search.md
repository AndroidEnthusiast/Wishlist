# Search the shops, and earning money (owner, 22 November 2026)

## Search the shops (built in 2.277)
- **Who it is for:** someone looking for a gift who typed the same search into five shops one by one. Now: type once.
- **What it is:** My shops' search box opens **each shop's own results page** inside the app, one shop at a time; a
  **row of shops** under the search field switches to the next shop with the same words (owner chose the row in the
  browser over going back to My shops). "+ Add wish" works as in every shop.
- **Not a combined list of results from every shop** (Claude advised against it, owner agreed): shops block apps
  that read their search pages, it breaks whenever a shop changes its site, it is slow and most shops' terms forbid
  it. Merged results only exist through paid product data or affiliate feeds.
- **Which shops:** the six big all-round shops, **bol.com, Amazon, Coolblue, Wehkamp, Zalando, de Bijenkorf**, then
  **Google** last for everything else (owner: "only the most and biggest shops, not all 20"). **Always the same set**,
  also when you removed one of them from My shops (owner). Set in `Shops.searchShops`.
- **Where:** the one search box at the top of My shops ("Search in shops"; it used to search Google only, which is now
  the last chip in the row). My shops' tiles stay as they are.
- **bol.com refuses the app's browser** (seen on 2.274; Chrome works): owner chose **try in the app first**, with an
  **"Open in Chrome"** button at the end of the row that opens the page on screen in the phone's own browser (the
  phone's default browser, usually Chrome). Only in a search; a shop opened from its tile has no extra button (2.122).
- **Offline:** "You're offline. Search again when you're connected." with **Try again**; your words stay in the box.
- **Where it starts** (owner, 22 November 2026, 2.280; the review found the first shop was bol.com, the one that may
  refuse the app's browser): **the shop you last picked in the row**, the first time **Amazon**
  (`Shops.startSearchShop`, `ShopsModelTest`).
- The search addresses could not be opened from Claude's computer (shops refuse it): if one shop shows an error page,
  its address in `Shops.searchShops` needs fixing.
- Checks: `ShopsModelTest` (first shop opens, offline, empty search, the fixed set and its addresses),
  `ShopsScreenshotTest.searchOpen` / `searchOffline`.

## Earning money (discussed, nothing decided yet)
- The owner's frame: **no sponsored results, no gift guides from partners, no ads, no account needed**; the app is
  a personal wish companion. Competitors (GoWish, Giftful, Giftster) do show sponsored ideas you can't switch off.
- Claude's advice: earn only from what the user asked for. (1) **Affiliate links** on shops the user opens or
  searches (later, once there are users; bol.com's partner programme first), with one line "We may earn a small
  commission. Your price stays the same.", a switch in App settings to turn it off, and results never ordered by
  commission. (2) Later an optional **"Wishlist Plus"** (extras, never sharing or reserving), only possible once the
  app is on the Play Store (it is downloaded from GitHub now). (3) A "support the maker" tip as a small extra.
  Not: ads, sponsored ideas, selling data.
- This would revisit the 16 November choice "no affiliate links" (`ideas.md`): ask the owner before building any of it.
