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
  Cloudflare Worker. **Owner: drop the idea** (the general preview stays; don't suggest it again unless asked).
- When a shop reads badly (message at the field, spotting logo photos, asking before saving €0): owner skipped the
  question, so nothing is built (ask again only if the owner brings it up). The real-people test: "not now".

## The Co-op form and "Add friends" (owner, 23 November 2026, 2.283)
- "The Co-op screen has too many options": **"Gifts for someone? (optional)" and its surprise line are removed.** New
  Co-op lists no longer store who they're for; lists made before keep theirs (share screen warning, Invite Friends
  leaves that person out).
- **Description stays, without "Shown at the top of the list."** under it, on Personal too (both forms match; Personal got
  the same placeholder "What is this wishlist for?").
- **Friends are picked while making a Co-op list** ("instead of afterwards"): the "Add friends" card under Description;
  the invitations go out the moment the list is made. If some can't be sent, the list opens anyway with "Couldn't send
  2 invitations. Tap Share, then Invite Friends, to try again." (no automatic retrying).
- **No Friends (or no profile) yet: the card still shows**; tapping it says "No Friends yet. After creating, you can send
  the invite link." (also on New group and New gift exchange, which hid it before).
- **Owner's rule: never plain "+ …" lines anywhere in the app; the same kind of element looks the same everywhere, no
  differences between menus.** "Add friends" is one card (`FriendsCard`, `ChoiceCard.kt`) in New wishlist, New group and
  New gift exchange, the same frame as "Add wishes from other wishlists". Claude did NOT add "Add wishes from other
  wishlists" to the Co-op form (fewer options was the point). Check: `CoopFormTest`.


## Value levels on Total value (owner, 27 November 2026; built in 2.299)
- Owner's idea: micro gamification on your own Total value. Claude's push-back: it rewards expensive wishes, and in a
  gift app could feel greedy if others saw it. **Owner's choices:** levels by total value, **only you see them** (your
  Home summary and your own lists' summary; never friends' pages, groups, Co-op lists or the web). **Gen Z names:**
  Rising (€1,000) → Main character (€2,500) → Big wish energy (€5,000) → Iconic (€10,000, holographic). **App
  settings › Display › "Value levels"**, on by default ("A sticker on Total value as your wishes add up.").
- How it looks: three versions in a preview (https://claude.ai/artifact/1pBVEqa1i6htkWofk7JD87): A a sticker above
  the amount, B the amount in the level's colour with the name under it, C a small badge. Waiting for the owner's pick.
- **Chosen after two more previews (built in 2.299):** the green and red of the first sketch looked like price drops
  and rises, so every level is a **gradient** (Rising sky blue, Main character gold, Big wish energy purple, Iconic
  holographic with a slow shimmer, which the owner loved: "keep that the way it is"). Each has a **drawn icon on top
  in the middle** of its label (rising sun, star, lightning bolt; Iconic a bigger crown), no arrows (they read as
  price changes). **A thin line under the amount** in the level's colours, its light gliding along it **three
  times when the screen opens**, then resting (owner's pick over a moving edge, a sheen and a glow; still with Remove
  animations). Other currencies: the same numbers close to the euro, converted at fixed rates for far-off ones
  (yen, kroner, złoty…; an unknown currency gets no level). Gen Z chaos gives the label and icon its outline and hard
  shadow. Code: `ValueLevels.kt`, `ValueLevelViews.kt`; checks `ValueLevelsTest`, `ValueLevelScreenshotTest`.

## The levels page and the Iconic prize (owner, 27 November 2026; built in 2.300)
- **Tapping your badge** (Home or a list) opens a page (preview https://claude.ai/artifact/8NADXG5DPKVKqS1BN9qh5n):
  your level's icon and "You're the main character", the line "Every wish you add moves you up a level. Only you can
  see this.", your total with a progress bar and "€2,020 to go until Big wish energy", then the four levels with
  their amount and one-liner: Rising "Your wishlist is officially taking off." · Main character "All eyes on your
  wishlist." · Big wish energy "Dreaming big looks good on you." · Iconic "Legendary taste, unlocked." with a lock
  and "Something special unlocks here" (**the prizes stay a secret** until you reach Iconic; no "tap to see").
- **The badge follows your total** (owner chose this over "reached is yours to keep"), but **the Iconic prize, once
  reached, stays yours** even when the total drops.
- **The Iconic prize, three things in one** (owner): a **holo ring round your profile photo** (seen by everyone who
  sees your photo: friends, groups, chats), a **holographic share card** when sharing a wishlist, and **glowing
  reactions** (your reactions on friends' wishes glow). No holo app theme. Claude's warnings, accepted: the ring tells
  friends your list is worth €10,000+; the server can't check the mark (the total is worked out on the phone), so it
  could be faked: decoration only. Ring and glow need the mark online (a Supabase script, app and web pages).
- **Reaching Iconic:** a pop-up "You're Iconic" ("Your wishlist just hit €10,000. Legendary taste deserves a little
  shine:"), the three prizes each with a picture and a line, "Love it", "Choose what's on", and "You can change
  these any time in App settings › Iconic."
- **App settings › Iconic** (only once you're Iconic): a switch per prize, each with a **Preview** button that shows
  the prize before you turn it on. **Build everything at once** (owner), after the preview is approved.
- **More choices (owner, 27 November 2026):** all three prizes **on at first** when reached; each **Preview** is
  personal (your own photo with the ring, your own list's share card, a sample wish with your reaction glowing),
  then "Turn on" / "Not now". **Friends see it:** the holo share picture, your ring, your glowing reactions, and
  **a holo edge at the top of an Iconic member's list** (app and friends' web page). Old app versions simply show
  nothing new. Preview updated; waiting for "go".
- **Built (2.300), and what Claude decided while building (tell the owner):** the page opens by tapping the Total
  value tile with its level (Home or a list; the whole tile, for a big enough tap). "You're Iconic" opens once, the
  first time Home's total reaches Iconic (also with Value levels switched off); "See your prizes" on the page shows it
  again. Your own lists are shared as a link only, so the holo share card is **a picture sent with the link** (like
  the Co-op invite card): list name, number of wishes, a crown, never an amount; the card switch also controls the
  holo edge friends see on your list. The ring shows for **friends and group mates** (the server tells your phone
  whose); a Co-op list's people only when they are also friends. On the web: glowing reactions on every page and the
  holo edge or band on the friends' page; no rings on the web (it shows no profile photos). Server: three columns on
  wl_profiles and `wl_pf_iconic`, `wl_ic_people`, `wl_ic_list` in `supabase-together.sql` (version 2).
- **Always tappable (owner, 27 November 2026, 2.301: "lean on serendipity"):** the Total value tile opens the levels
  page also before the first level, with no hint; below €1,000 the page says "Your journey starts here". With Value
  levels switched off nothing happens on a tap.

