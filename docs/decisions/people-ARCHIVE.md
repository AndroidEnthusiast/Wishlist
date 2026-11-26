# Decisions: Friends, chat, groups (archive)

Older sections of `docs/decisions/people.md`, moved word for word. Only searched.

## Owner's review of 15 November 2026 (built in 2.209)
- **Messages has no tabs**: only the conversations (Friends is in the bottom bar anyway). A **round button at the
  bottom right with the chat icon** (no + and no words, the owner's choice over a wide "Start chat" button) opens your
  friends to pick from; it shows once there is a chat, before that the empty page's "Start a chat" does the same. With
  no friends the panel says "No friends yet" with "Add a friend". Friends come from the phone, so it opens offline.
- **"Viewed 23 times" removed** ("that functionality shouldn't be there"): the app and the web pages no longer count
  or show views; `wl_view` / `wl_view_count` stay on the server, unused (no script to run, old apps keep working).

## Reactions: one tap on yours takes it away (owner, 20 November 2026)
- The owner reacted on a friend's wish and tapped the reaction: "+ React" came back but the reaction stayed (since 18
  November a first tap on your own reaction only brought "+ React" back; a second tap took it away). Owner: tapping
  your reaction should remove it and then "+ React" reappears. Built in 2.264, in the app (`ReactionModel.kt`) and on
  the friends' and group pages (`web/together.js`); checks `ReactionModelTest`, `FriendWishesTest`, `tests/together`.

## A friend's page, person first (owner, 16 November 2026, after comparing with Giftful and competitor reviews; 2.217)
- Replaces the Home-like friend page of 2.95: the photo (112 dp) and name once, centred (no title in the top bar, no
  chat icon there); chips for the birthday ("1 April · in 136 days", in your colour within 2 weeks), sizes, "Loves 3
  wishes" (only when more than 0; opens their loved wishes) and each thing they like (split at commas and "and");
  a "Message" button ("Send a message" when they have no visible list, with "… hasn't added a wishlist yet.").
- No number tiles on friend pages (their wishes page is reached through the lists; Loved through its chip). On your own
  Home the tiles stay as shortcuts.
- Their lists as photo collages like your own (they showed one photo); a collage with 2 photos is side by side
  everywhere (Home, friends, groups).
- The private note: a small row under the lists ("Add a private note" or the note in 2 lines), written in a sheet.
- Not chosen from the reviews: price comparison, bulk import, adding on the web, recommended products, public following.

- Someone else's wishes (compact Reserve, Add to my wishlist, one reaction) and the buttons on a wish card (18 November
  2026): moved to `docs/decisions/wish-cards.md`.
