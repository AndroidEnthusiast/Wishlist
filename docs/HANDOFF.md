# Wishlist: handoff

The current plan and the last 3 sessions. Everything older (the history since October, every earlier plan) is in
`docs/HANDOFF-ARCHIVE.md`: search it when needed. Limits (stop hook, `tests/notes/limits.sh`): only "Next session"
and "Last sessions"; at most 3 sessions, each at most 5 lines; move the oldest to the archive when adding one.

## Next session
- **START HERE (12 November 2026, night, after 2.192 and 2.193: the code clean-up).** Built: the screenshot tests work
  again (they never settled because "What's your name?" opened at the start and November's snow moved for ever; now a
  name, animations off, a fresh database per picture; "Price watch" → "My shops"). **2.192** one way to move between
  screens (`LocalGo`); **2.193** where you are inside a tab is `Places` (owned by the screen), and the last `*Nav`
  objects are gone. All 14 screenshots were identical before and after. Ask the owner how 2.193 feels: Messages and
  Friends should still open where you left them; Back from a friend's page opened from a chat goes back to the chat;
  turning the phone on a friend's list keeps it; a new group/exchange opens with "Invite friends"; a "Reserve it
  now?" notification opens the list on that wish; the side sheet, share card, Themer, the shop's "Added to …".
  **2.194** (owner asked): a message now notifies unless that chat is on screen with the app in front (also gift
  exchange chats); ask the owner to test it with a second phone (chat open, go Home or lock the phone, get a message).
  Owner ran supabase-limits.sql again and deployed `fetch-page` (checked read-only: `wl_fetch_ok` answers, the helper
  says `no_apikey`, its new message). **Crash review done (owner's screenshot, 12 November 2026):** 3 crashes in the
  last 7 days, all on 8 November, all on 2.154, one user (the owner); two issues, both NullPointerExceptions inside
  Firebase's own thread factory and Android's `IMessenger.send`, not our code; nothing since. Advised the owner to close
  both (Crashlytics reopens them if they come back). Crashlytics' "latest release" said 2.182: check next session
  that 2.194 shows up (it can take a day); if not, look at `Crash.kt`/`WishlistApp.kt` after the 2.191 library update. **Next (owner's order):** the app's final name, then the copy project. R7 and
  R8: later.
- **Everything open** (the complete to-do list, owner): `docs/TODO.md`. Owner wants a new feature built first.

## Last sessions

### 12 November 2026, night: the code clean-up and the notes tidy-up (2.192–2.194)
Screenshot tests fixed (a name, animations off). Clean-up: one way between screens (`LocalGo`, 2.192), where you are
is `Places` owned by the screen and every `*Nav` object gone (2.193), screenshots identical before and after. Bug
fixed on request: a message now notifies unless that chat is on screen (2.194). Crash review: 3 old crashes on 2.154,
none since. Notes tidy-up: `CLAUDE.md` slimmed, `docs/MAP.md`, decisions by topic, archives, a stop hook for limits.

### 12 November 2026, day: Stage 4 finished and Android 16 (2.188–2.191)
Restoring on a new phone with the recovery key and signing the old one out (2.188); privacy page, delete my profile
and data, block and report (2.189); Android's own backup off and the shop-page helper limited to 60 pages an hour per
key (2.190); building blocks up to date: Android 16 target, Kotlin 2.1, Compose 2025.06, Room 2.7 (2.191).

### 10–11 November 2026: the second review, Stage R and Stage 4 part A (2.178–2.187)
Second review of the roadmap; price checks believe two agreeing readings (2.178). Stage R repairs: "new version is
ready" (2.179) up to "Price is wrong?" (2.182) and screens that come back after Android closed the app (2.183). Stage 4
part A: limits against abuse (2.184), photos as files (2.185), clean-up after 12 months (2.186), the recovery key (2.187).
