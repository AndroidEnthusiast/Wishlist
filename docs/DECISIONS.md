# Decisions

What the owner has decided, and why. Follow these unless the owner changes their mind; when they decide something new, add a line here (newest at the bottom of each section) in the same commit as the change.
(Since 12 November 2026 "here" means the topic file in `docs/decisions/` listed below.)

## Where the decisions are (by topic, since 12 November 2026)
Read the topic you work on; search all of them (and `docs/HANDOFF-ARCHIVE.md`) when unsure whether the owner already
decided something. Every decision was moved word for word, nothing was dropped.
- `docs/decisions/screens.md`: Screens, wishes and prices (Private wishes; Top wish; Wishes on screen and the wish form; Prices; App review; Owner's list of 3 November 2026; Settings review; Home tiles, Collab wishlists, moving wishes, notifications and web search).
- `docs/decisions/look.md`: Look and feel, the app's identity and screen movement (Look and feel; Screen transitions).
- `docs/decisions/sharing.md`: Lists, sharing, security and the server (Lists and sharing; Security; Backend).
- `docs/decisions/people.md`: Friends, chat, groups and gift exchanges (Friends and Groups; Messages; Wishlist groups; Side menu, Gifts I reserved and Gift exchange).
- `docs/decisions/roadmap.md`: The roadmap: a real app, Stages 0–4 and the reviews (A real app; Roadmap decisions D1–D5; Second review; Stage R; Stage 4; Stage 4, second part).
- `docs/decisions/ideas.md`: Open ideas (not decided) (Open ideas).

## Notes tidy-up (owner, 12 November 2026)
- **Notes stay small, automatically:** `CLAUDE.md` holds only the rules for every session (under 150 lines and
  12,000 characters; the "Rules that must stay true", the safety and the interview rules never move out of it);
  details are in `docs/MAP.md`; the handoff keeps "Next session" and the last 3 sessions (at most 5 lines each), older
  ones in `docs/HANDOFF-ARCHIVE.md`; other docs stay under about 400 lines and 40,000 characters (archives exempt:
  they are only searched); decisions are split by topic. Never delete a rule or decision: move it and leave a pointer.
  The session's stop hook (`.claude/settings.json` → `tests/notes/limits.sh`) blocks finishing while a limit is passed
  or the notes are not pushed. Owner chose: limits on lines and characters, decisions by topic, archives exempt,
  "Next session" only the current plan.
- **One complete to-do list** (owner, 12 November 2026): `docs/TODO.md` holds every open item (the app name, the
  file split, Google Play, tests waiting on the owner, known gaps, ideas); asked what is open or next, show all of it.
  Owner also chose: a new feature first; split the big files and move data out of shared objects later (remind them).
- **Every finished feature and every fixed bug gets an automatic check**; before changing a feature, read its checks
  and its decisions first.

## Working together
- **Changes go straight to `main`.** No pull requests, no test builds, no test release. The owner found branches, test APKs and two install links confusing ("app not installed") and wants to iterate on one app. (October 2026)
- **Push only to `main`, never to any other branch** (owner, 31 October 2026), also when a Claude Code session starts on its own `claude/...` branch. Pushes to other branches made the workflow fail (Pages only deploys from main) and sent the owner "build failed" e-mails. Keep the GitHub repository clean: one branch, `main`. The old branch `claude/kind-fermat-ozfdx2` may still exist; the owner can delete it (Branches, bin icon).
- **One Claude Code session per feature or topic.** Long sessions get summarised and lose detail; this file and `CLAUDE.md` are what a new session reads. Before ending a topic, write any new decision here.
- **The project lives as normal files in the repository**, not in `wishlist-android.zip`, so every session reads `CLAUDE.md` and this file automatically. Never bring the zip back.
- **On screen, always speak to the user** ("you"), never "we" (owner, 2.82): for example "You’ll get a notification…", not "We’ll let you know…".
- **Explain plainly, in UK English, tap by tap.** Say what you are changing before or as you change it, and be honest about what was and wasn't tested.
- **When a new app build is ready to download, say so explicitly in bold: "App available for download", as the very last line of the message** (under everything else, so the owner sees it without scrolling up). (October 2026) The word "download" links straight to the newest APK: `**App available for [download](https://github.com/AndroidEnthusiast/Wishlist/releases/download/latest/Wishlist.apk)**` (November 2026).
- **Show previews of visible changes** (an HTML mock-up or simulation) when the owner asks to see something before it is built.
- **Push back and ask before building** (owner, 1 November 2026): don't just agree. Point out weak spots, offer a better option when there is one, and ask the open questions first (preferably as a pop-up with choices, AskUserQuestion) before building, so nothing has to be redone. Small, clear requests can be built straight away.
- **Challenge the owner** (owner, 14 November 2026, after the design styles of 2.199–2.205 were removed): "I want you to challenge me as well when it's appropriate", especially on big builds such as features and design styles. Before building one: say whether it is worth it now and what it costs (time, upkeep, risk), name a smaller or better alternative when there is one, push back on scope that is too big for one update (e.g. several design styles at once), and say so when a request repeats a mistake from the past. Ask with pop-up questions, a recommended option first; the owner decides.
- **Every feature is for a happy user** (owner, 4 November 2026): each change starts from who it is for and what gets better for them; be critical, not agreeable. Keep dead ends, safety and upkeep in mind for the part you touch, without auditing the whole app each session (see `CLAUDE.md`, "Who you are working with"). A review of 4 November 2026 found the web pages and database well protected; the open dead ends are listed in `docs/HANDOFF.md`.
- **Browser editor Bin: restore only** (owner, 4 November 2026): menu > Bin lists a gift list's deleted wishes (30 days) with Restore. Deleting for good and emptying the bin stay in the app: a browser link can be passed on, and nobody should wipe a list's bin by accident.
- **Always sharp shop icons, and the first product photo** (owner, 5 November 2026): a shop icon must be a normal-size, good-quality picture (no blur, no letter tile); the picture of a wish added from a shop is the product's first photo (the clean one), not a later one with dimensions. The Share tip under the product link was removed.
- **In-app shop behaviour** (owner, 5 November 2026): the shop stays open while adding (after Save it says "Added to …"; after Cancel you're back in the shop); Back goes to the shop's previous page and closes only on its first page; ✕ closes the shop. Add to wishlist fills in the product picture straight away.
- **Shop icons** (owner, 5 November 2026): the icon the shop's homepage names first, then Google's icon service (it only sees the shop's address); light grey tiles so white icons show.
- **Find a gift** (owner, 4 November 2026): a row of web shops on Home (20 Dutch shops: bol.com, Amazon, Coolblue, Zalando, IKEA, Wehkamp, HEMA, de Bijenkorf, Intertoys, LEGO, Decathlon, Douglas, Rituals, Blokker, fonQ, Nike, H&M, Etsy, Bever, Apple; not MediaMarkt), users add and remove their own. Shown with each website's own icon loaded live (never copied into the app). A shop opens inside the app with a big "Add to wishlist" button that reads the product on screen. App only (browser users paste links). No affiliate links for now; maybe discuss later.
- **Wish buttons and menus** (owner, 4 November 2026): two per row has no long press; its ⋮ is Share, Edit, Private, Mark as owned, Delete. Large cards show Share, Private (eye) and Delete; their long press is Edit and Owned. (Replaces the long-press notes of 2.111.)
- **Owned** (owner, 4 November 2026): owned wishes go to the bottom everywhere; whoever reserved a wish that is then marked as owned is told once (app notification, or a note on the friends' page). Friends still see owned wishes, greyed out with "Owned". Not chosen: hiding owned wishes from friends, a "Received" overview.
- **New wishlist form**: the optional field is called "Note for friends", with the hint where it shows (owner, 4 November 2026).
- **No floating "Add wish" button on a list** (owner, 4 November 2026): the + in the bottom bar is the one way to add a wish.
- **Long-press menu of a wish** (owner, 4 November 2026): in the large-card view only Edit and Private (gift list: only Edit, a one-item menu by choice); Share is the card's own icon, Price history is on Price watch. Two per row keeps Price history and Share. Smaller title and rows, not cramped.
- **"Owned by you"** (owner, 4 November 2026): a small grey line under the name in the top bar of your own open lists; not on gift lists.
- **The top of a list stays in sight** (owner, 4 November 2026): the summary card (number of wishes, total value) and the category, sort and view choices stay pinned at the top while scrolling; the cover photo scrolls away. The number of wishes takes less room than the total value, both centred; the number pulses when it changes.
- **"Saved!"** (owner, 4 November 2026): the green tick gets little lines that circle around it once and fade, and the text ends with "!"; it stays as short as before.
- **Sharing a wish** from your own list: "Look what’s on my wishlist!" (owner, 4 November 2026).
- **Cover photo ⋮** (owner, 4 November 2026: removing a cover was a dead end): a ⋮ in the bottom right corner of a list's cover, only when there is one: Change (Choose a photo, Take a photo, Adjust this photo) and Delete (at once, with Undo). List settings keeps its cover section (the only way to add a first cover).
- **Categories are edited from the wish form only** (owner, 4 November 2026): "Edit categories" under "+ New category" in the form's category drop-down; add, rename (tap the name), delete. Under each name the number of wishes. No wishes: deleted at once; otherwise a short warning that the wishes stay, without the category. Your own wishlists share one set; each gift list keeps its own set (owner: keep them separate). Gone from List settings and from the long press in a list's filter.
- **Home without "Recently opened"** (owner, 4 November 2026): each section starts with the list (or group) you opened last.
- **Back arrow on an open wishlist** (owner, 4 November 2026): top left shows ← to Home instead of the menu button; the menu stays on Home and the other screens.
- **Photos in messages** (owner, 4 November 2026): camera button next to the gift: Take a photo / Choose from gallery; a full-screen preview with Cancel and Send first (no accidental sends); no caption (send a text after it); tap a photo for full screen with pinch to zoom, no Save button (a saved photo leaves the encrypted chat). Locked end to end like messages: the server only stores a locked copy, for 30 days (saves the free Supabase space); the phones keep theirs. Not in Export, like messages.
- **The web pages only run their own script files** (Content-Security-Policy, 4 November 2026): a safety net if a bug ever lets someone's text in as code.

## Working together (4 November 2026)
- **A changelog after every update** (owner): once an update is pushed, list everything that changed in bullets, so the owner knows what's new.

## Language
- **UK English only** (owner, 4 November 2026): Dutch was removed (writing every text twice cost too much); App settings > Language and the web pages' language switch are gone. Texts stay wrapped in `tr()` / `T()` so the whole app can be translated later in one go when the owner asks. ~~Everything also in Dutch~~ (until 2.110).

## Compile and screenshots before a push (5 November 2026, owner interviewed)
- **Screenshot tests run only here, by Claude**, not in the GitHub build (owner chose the cheap option; the app build stays as fast as before and can't fail on a picture). Setup: `bash tests/android-setup.sh`, then `gradle testDebugUnitTest` (see `tests/README.md`, "App screenshots").
- **Default style only, light and dark** (owner): not all five styles.
- **Show the owner pictures only when an update changes how a screen looks**, and keep it cheap in tokens (owner): only the screens that changed, as one small before/after picture, not every screen every time.
- **No product-page check of the shops for now** (owner): from the cloud only about 9 of the 20 shops open, so products are tested on the phone.
- **Full network access stays only if downloads work smoothly** (owner: "if Maven Central is ruining the full network access experience I don't want it"). Fixed the same day: `tests/android-setup.sh` makes Gradle on the cloud computer download from Google's copy of Maven Central. If downloads fail again, fix it or tell the owner plainly; switching Network access back means no compiling or screenshots here.
- **Currency when nothing says** (owner, 2.120): a new wish and a shop page without a currency get the currency of the phone's (or browser's) country, else the euro. Not the dollar any more. A page that shows "$" stays USD.
- **"+ Add wish" floats over the shop page** in its bottom-right corner (owner, 2.120), always at full size (owner chose this over shrinking it while scrolling, knowing it may sometimes cover a shop's own button).
- **Network access is back off "Full"** (owner, 5 November 2026, same evening): the setup, downloads and screenshot runs made every update slower and showed no benefit yet. No Android SDK, compiling or screenshots in sessions; GitHub compiles. The screenshot and shop-icon tests stay in `app/src/test` (harmless, not in the app) for if Full is ever turned on again.
- **Shops, round 2 (owner, 2.121)**: "Shop for gifts" (not "Find a gift"); shops sorted by last opened; "Add shop" tile framed like a shop tile; a shop you already have can't be added twice and says so. "+ Add wish" in a shop is flat (no shadow), smaller, grey until a product page is recognised; tapping it grey says "No product recognised" (Claude added "Add anyway" as the way out when recognition fails). Shop browser: only ✕ at the top (2.121 added a back arrow, reload and "open in your browser" for a question the owner skipped; removed in 2.122). Icons: owner wants full-size, sharp, legal icons: the shop's own published icons, learnt from its page while it's open. Bottom bar a little bigger (72 dp, icons 26).
- **A skipped question means "none of these"** (owner, 5 November 2026): when the owner skips a pop-up question, build none of its options. Don't fill in your own recommendation.
