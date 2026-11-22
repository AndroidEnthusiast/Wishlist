# Home's tabs and the kinds of wishlist

## Personal and Co-op, Home's tabs, taking chosen wishes out (owner, 21 November 2026, after 2.272)
- **"Shared" only means "shared with someone".** The wishlist you make together with others is called **Co-op**
  (owner chose it over Collab, Crew, Squad, Team, Together, Joint, Ours; "Collaborative" felt too official), "co-op
  wishlist" in sentences. Everywhere: New wishlist, Home, the + menu, the bin, the delete-account list, Move, the
  welcome name question, the web editor's default name and the privacy page. (The code still says shared/collab.)
- **New wishlist: "Personal" (one person) / "Co-op" (two people)**, with one line about what it is, never about
  reserving: Personal "Only you add wishes to it.", Co-op "Collaborate: everyone you invite adds wishes." (one line;
  a little smaller rather than wrapping).
- **Home has a tab per kind: Personal | Co-op | Groups | Exchanges**, in that order, **each only while you have one**
  (a group or exchange you're in, until you leave it or it's deleted). **Only one kind: no tab bar.** Short labels so
  four fit; on a narrow phone with large text they get a little smaller, never cut (`HomeTabsScreenshotTest`).
  Your gift exchanges moved from under the lists into their tab; the seasonal "Start a gift exchange" card stays
  under the tabs (1 November–20 December, until you have or are invited to one or close it; back each November).
  App settings > Display > "Tabs on Home" off: everything under each other, as before. Replaces "My wishlists |
  Shared" (`decisions/settings-profile.md`, 18 November).
- **Chosen wishes can be taken out again** (owner: "why didn't you tell me about this obvious flaw"; Claude had
  checked adding but not changing your mind): the picker opens with your choice ticked and "Done" works with none
  left, and **each chosen wish's photo in the card has a ✕**. Also in New group's "New wishlist" (which now uses the
  same card). Checks: `NewListChoicesTest`, `ProfileHomeTest`, `HomeSwipeTest`.

## Clear lines in the + menu (owner, 22 November 2026; built in 2.281)
- The plan after "what does a wishlist user truly need": the five moments (save fast, one link, a giver knows in five
  seconds, reserve without duplicates, received gifts) are built; what's missing is quality and proof, not features.
- Names stay (owner): **Co-op**, **Wishlist group**, **Gift exchange**; everything stays in the **+ menu** (not also
  offered "where needed"); no "What are you planning?" screen (owner: too vague for a wishlist app).
- Lines say what each is truly for (owner's own wording, polished, not examples of occasions): + menu: New wishlist
  "Your own wishes, or a co-op wishlist with others." · New wishlist group "Everyone you invite can see each other's
  wishlists." · New gift exchange "Link your wishlist, draw names and surprise each other with a thoughtful gift." ·
  New wishlist form: Personal "Your own wishes." · Co-op "Collaborate on a wishlist with two or more people." (the
  owner's line as is; Claude warned it invites a couple's own wishlist, where everyone sees the reservations; owner
  chose it anyway: don't change it without asking).
- **Personal link preview** (owner chose "Supabase link now"): not built, because Supabase functions can't serve web
  pages on their own address (HTML becomes plain text; only with a paid custom domain), so WhatsApp would get no
  preview and the link couldn't send people on. Options given to the owner: wait for the own domain, or a free
  Cloudflare Worker.
- When a shop reads badly (message at the field, spotting logo photos, asking before saving €0): owner skipped the
  question, so nothing is built (ask again only if the owner brings it up). The real-people test: "not now".

