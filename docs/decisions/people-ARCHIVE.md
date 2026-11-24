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
