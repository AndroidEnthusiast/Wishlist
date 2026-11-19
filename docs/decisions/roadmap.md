# Decisions: The roadmap: a real app, Stages 0–4 and the reviews

Moved here word for word from `docs/DECISIONS.md` on 12 November 2026 (notes tidy-up, owner). Index and the general
decisions: `docs/DECISIONS.md`. Add new decisions on this topic here.

## A real app (owner, 8 November 2026)
- **The foundations get rebuilt before new features**: "I want the foundations to work for me and my partner but at the same time allow to scale up in the future." The plan is `docs/ROADMAP.md` (Stages 0–5: safety nets, navigation and ViewModels, a Room database on the phone, per-wish sync and live updates, server limits/photos/identity). Features are frozen until Stage 3 is done; small fixes are still fine. Open decisions D1–D5 are asked at the start of Stage 0.

## Roadmap decisions D1–D5 (owner, 8 November 2026, start of Stage 0)
- **D1 Network access: on for the rebuild (Stages 1–3).** Done as a personal environment called **"Wishlist"** (package registries plus `dl.google.com` for the Android SDK, not "Full"), so the app can be compiled and its tests run here before a push. The "Default" environment stays as it was. Setup per session: `bash tests/android-setup.sh`. The owner also let it reach the app's Supabase (8 November 2026), for read-only checks of the real server; nothing real is changed without asking.
- **D2 Crash reports: Firebase Crashlytics.** Crash details only (where it broke, phone model, Android version), never wishes, names, notes or chat; mentioned in the privacy text and credited in Licences. Checked at the start of each session, fixes first.
- **D3 Database backups: a weekly GitHub job**, each copy kept privately for 90 days. Needs one GitHub secret with the Supabase database connection (the owner adds it).
- **D4 Keeping Supabase awake: a simple touch every 2 days** by a scheduled GitHub job. The owner first asked for "only if it wasn't used for 2.5 days"; Claude pointed out that checking is itself a use, so it's the same thing with more parts; the owner chose the simple touch.
- **D5 Identity: Supabase anonymous accounts** (no e-mail, no sign-up screen; later linkable to Google so a new phone gets everything back). Built in Stage 4 (S4.5), step by step, with every existing owner token, device id and link still working. Nothing is built for it before then.
- **Stage 0 signed off, Stage 1 started** (owner, 8 November 2026, session 2): 2.153 "seems to work like before", with
  the wish that anything noticed later is still fixed (fixes come first, roadmap rule 1). S0.3 (saved shop pages) stays
  open for later. The switched-off **side menu is deleted** for good (S1.5). The database password was reset and the
  secret updated; the next backup was green.
- **Back from a screen opened on top returns to where you opened it** (Stage 1, 2.154, following the roadmap's goal
  "Back always goes where you think"): Profile, App settings, Bin and Notifications opened from the Friends tab go back
  to Friends (before: Home). Screens keep changing at once, without slide or fade animations (nothing else visible
  changes in Stages 1–3).
- **S2.5: the database becomes the only source** (owner, 8 November 2026: "ok let's continue, I made a backup"):
  2.171 stops writing the old storage for lists, wishes, bin, history, friends' lists and notifications; the old
  values stay on the phone untouched; the old code stays as the fallback when the database can't be read.
- **Stage 3 answers** (owner, 8 November 2026, pop-up questions at the start of Stage 3; Stage 2 signed off the same
  moment: "Yes, all works"):
  - **S3.1 a failed save of your own list keeps your change** and retries later (next change, next start, background
    check); no undo and no message (Claude pushed back on the roadmap's "undo like collab wishlists": on your own list
    the phone holds the real copy). The full-list save stays for older apps and the group/exchange pages.
  - **S3.2 live updates in the app** for collab wishlists, a friend's list and the open chat. **The web pages keep
    polling for now: remind the owner later** ("remind me of web pages later"). When the live connection can't be
    reached, **quietly fall back to the old polling** (20 s lists, 8 s chat) and try live again later.
  - **S3.3 the background check stays every 30 minutes**, but asks the server "what changed for me?" in one call first.
  - **S3.4 the server calls are counted on the phone** and shown in App settings > About (today, yesterday, Details).
  - **All of Stage 3 in a row, tested at the end** (no separate "before" day on the phone; Claude's estimate instead).
- **Stage 2, the slimmer route** (owner, 8 November 2026, after Claude pushed back on rebuilding every screen around
  ViewModels: screens already update by themselves, and that rebuild changes nothing for the user): finish Stage 2
  with one-wish-at-a-time saving, Export/Import from the database and then removing the old storage; ViewModels come
  screen by screen, only when a screen is changed for another reason.
- **Stage 2 ground rules** (owner, 8 November 2026): the data move is tested first with the owner's real Export
  (kept only in the session's scratch folder, never in the project or on GitHub, deleted after the move is tested);
  **only the owner's phone tests Stage 2** (the partner isn't available to test, so they update later); no extra
  Export reminder (the app's own untouched copy of the old data is enough); **the data moves in its own version**
  (first a version that adds the database and moves nothing, then the move, then the rest). Export stops taking the
  phone's own Firebase files along, now (2.164).
- **Stage 1 signed off** (owner, 8 November 2026): "All seems to work and if I overlooked something I want to fix it
  later." ViewModels moved from S1.1 to S2.2 (they need the data layer).
- **A notification about one wish opens its list on that wish** (owner, 8 November 2026, 2.161, a small fix during
  the roadmap): scroll + the glow of "All your wishes"; own lists and collab wishlists only.
- **The tabs** (owner, 8 November 2026, 2.158): each tab (Messages, My shops, Friends) **remembers where you were**
  when you switch away and back; tapping the tab you're on goes to its first page. **Back on a tab's first page goes
  Home**, also when a list was open before; Back on Home closes the app.
- **Price changes has no bottom bar** (owner, 8 November 2026, 2.156): like every screen opened on top (App settings,
  Bin, Notifications), with ← and Back returning to where it was opened. The owner didn't remember where "All price
  changes" lives: a wish's price page and a notification about several prices. A wish's price page opens from a
  price-drop card on Home or a price notification, **not** by tapping a price in a list (Claude wrongly said it did).
  The owner doesn't want that built now: "the price changes is a feature we will improve later. As long as price
  change notifications work it's fine." 
- **No download line during the roadmap** (owner, 8 November 2026): "I don't need to see an app download link. Instead I need your guidance on what to do next." Until the roadmap's stages are done, messages end with the next steps for the owner, not the download link (say it in a line when an update needs installing).

## Second review (owner, 10 November 2026, built in 2.178; the review itself is in `docs/ROADMAP.md`)
- **A changed price is believed after two readings that agree, in the same check** (owner chose this over waiting for
  the next scheduled check, which is safer but tells every real drop one check later, and over believing the first
  reading). When the second reading fails, the first waits for the next check.
- **The first check of a new, re-linked or hand-edited wish is quiet**: "my typed price stays, read prices fix
  quietly". A price typed or corrected by hand is never overwritten by that check; a price the app read itself is
  corrected without a notification or a crossed-out old price. From then on only a change at the shop counts.
  (Claude, not asked, said to the owner: once the shop's own price changes, a typed price follows it: the wish takes
  the shop's price and the notification compares it with the typed one. Collab wishlists only get the two readings
  and the currency rule, because their price is shared between everyone's phones.)
- **A wish's price page shows one grey line**: "Price checked 2 hours ago", or "This shop’s price couldn’t be read
  since 3 November." after a day of failed checks. Nothing on the wish cards (owner chose the line only).
- **Never across currencies, never from an error page** (Claude, part of the fix): a page that names another currency
  than last time, or a "not found" page, gives no price. Shops are asked in the phone's language.
- **Google Play: "for sure", but the download is fine for now** (ROADMAP Stage 6). Nothing may be built that makes
  Play harder; nothing is submitted without the owner.
- **Privacy page and "Delete my profile and data": yes** (ROADMAP S4.6, S4.7).
- **GitHub runs the web checks before it publishes the web pages: yes, "as long as Claude can fix the issue"** (the
  `web-checks` job in `build.yml`). A session that sees it fail fixes it before anything else.
- **Signing key**: the owner has a 16-character password saved and wasn't sure about the key file (ROADMAP R0; not
  settled yet).
- **The Firebase key in `Config.kt` is meant to be public** (checked on 10 November 2026 after a scanner's e-mail: the
  key can't use Gemini, the project has it switched off). No history rewrite, no new key. The owner restricts the key
  in Google Cloud Console (ROADMAP R2). E-mails from scanners the owner never signed up for are not acted on; GitHub's
  own alerts are.

## Stage R (owner, 10–11 November 2026)
- **2.178 "went well"** (owner, 10 November 2026). **Both key steps come later** (the signing key copy, R0, and
  restricting the Firebase key, R2): the owner said "we'll do the key later". Keep reminding; R0 stays urgent.
- **R1 Lists that can't be opened: a stop screen with ways out** (Get my lists back, Try again, Import a backup file);
  not an automatic repair and not "warn but continue".
- **R6 Update notice: a slim bar on Home**, only once a version has been out for a day, Later hides it for a week; a
  full "Please update" screen only below the minimum version. (Claude: built as a small file next to the web pages
  instead of a database function: fewer moving parts.)
- **R3 Backups move to a private repository** (the owner creates it; steps to be given), and the pictures are copied too.
- **R4 A "Price is wrong?" button** on a wish's price page that saves the shop's page to a file and opens the share
  sheet; nothing is sent by itself.
- **Moving wishes to and from collab wishlists** (owner, 11 November 2026): in the app only, every direction (own →
  collab, collab → own, collab → collab), from the wish's menu. **Leaving a collab wishlist asks each time: Move or
  Copy.** **A wish someone else reserved on a collab wishlist can't be moved away** (your own reservation just ends).
  **Private wishes move without a question** and become visible to everyone on the collab wishlist (owner chose this
  over a warning). Top wish, owned, price history and price alert don't exist on collab wishlists and are dropped.
- **Finishing Stage R** (owner, 11 November 2026, on the Chromebook): the signing key file and its password were
  found (R0, a second place still to do); **R3 private backup repository, R2 Firebase key restriction and Dependabot
  alerts: now**; **two-step sign-in: "remind me later"**; **the code is "all rights reserved"** (the owner asked
  whether that is legally allowed: yes, the owner holds the copyright of the project; parts made by others keep their
  own licences, listed in `LICENSE`); **the two-phone test (R7) is written down for later**, not done today.
- **R3 backup job** (Claude, not asked: details): weekly as before, now also the pictures in Supabase Storage's public
  buckets, a restore into a throw-away database that must have the same tables and rows (a difference of a row or two
  from a save during the copy is a warning), everything locked in one file, kept 60 days (a private repository has
  500 MB for job files on the free plan; a warning above 40 MB a copy).

## Stage 4 (owner, 11 November 2026; first round of questions)
- **Order: A groundwork** (photos as files, limits against abuse, clean-up, database speed; nothing visible), **then B
  accounts that survive a new phone, then C privacy page + "Delete my profile and data", then D report and block.**
- **Clean-up: lists, profiles and links unused for 12 months are deleted online**, with a warning to the owner's
  phone **one month before** ("Open Wishlist to keep your lists online"). **"Used" means the owner's app was opened**
  (owner chose this over also counting friends' visits, knowing that a list friends still look at can then go). The
  copy on the phone is never touched.
- **Limits against abuse: generous, half of Claude's examples: 100 wishes an hour, 30 messages a minute** (other
  actions get limits in the same spirit, far above normal use). Someone who hits a limit sees **"Please try again in
  a moment"**.
- **A new phone: a recovery key** (no e-mail, no Google account link). The key screen has **"Save to password
  manager"** (Android's own pop-up, one tap; on a new phone "I already have lists" offers the saved key by itself),
  then **Share** and **Copy**. The line next to it: **"Keep your wishes safe: never share this key with anyone."**
  It is asked **during the welcome** for now, and always available in Profile.
- **Remind the owner** (owner, 11 November 2026): they want to work on a better welcome screen and first-start flow
  later, visually and in its texts ("I don't see that screen because I'm always in the app").
- **Photos move to file storage now** (S4.1; owner, 11 November 2026, after Claude recommended postponing: the
  database was 14 MB of 500 and the photos 58 kB): profile, group and gift exchange photos become files with a long
  random address, **a new address with every change** (the old one stops working), handed out only through the
  server's existing checks (friends, group and exchange members), like wish photos. Not signed addresses (they would
  need an extra function with a secret key).
- **Clean-up and people without a profile** (owner, 11 November 2026): their lists can't be warned about, so they are
  removed online after 12 months like the others, and **the app quietly puts them back online** under the same link
  when it is opened again (reservations made before are gone).
- **Every finished build ends with the download link** (owner, 11 November 2026: "always end a build by sending me a
  link to the build"), also during the roadmap; replaces "No download line during the roadmap" of 8 November.
- **Recovery key, details** (owner, 11 November 2026): a new phone gets **everything** back: what is online anyway,
  plus a **locked copy of what only the phone has** (private wishes, "Only me" lists, bin, price history, chat keys),
  locked with the recovery key so the server can't read it. **Restoring signs out the old phone** (a new key is made;
  the old phone sees that the profile moved). **Existing users get one card on Home** after the update ("Keep your
  wishlists safe on a new phone": Save your key / Later, again after a week, at most 3 times). Called **"Recovery
  key"** in **Profile**; a new phone has **"I already have lists"** in the welcome. Export/Import stay as they are.
  (Claude, said to the owner: built in two versions, 2.187 the key and the locked copy, 2.188 restoring; the locked
  copy leaves out picture files, so a private wish's own photo doesn't come back.)

## Stage 4, second part (owner, 12 November 2026)
- **The old phone after restoring elsewhere**: title "Your wishlists are on your new phone", text "You used your
  recovery key on another phone, so your wishlists, friends and chats moved there. This phone is signed out." One
  button, **"Start fresh"** (empties the app, opens the welcome). **No Export offer** there: everything is on the new
  phone already, so it would only confuse.
- **Restore is also in Profile** ("Restore with a recovery key"), for someone who tapped past the welcome. When the phone
  already has lists: title **"Delete the 2 wishlists on this phone?"**, text **"They are replaced by the wishlists saved
  with your recovery key."**, buttons **"Delete and restore"** / Cancel. Copy must be concrete, short and exact (owner:
  "yours" was too vague, the long version too long).
- **Delete my profile and data**: collab wishlists, groups and gift exchanges with other people on them are **handed
  over with the same "pick who takes over" step** the app already has (gift exchange hand-over, a group's next
  organiser, a collab list's new owner); only when nobody else is on it, it is deleted. Owner: reuse logic from other
  parts of the app so the experience stays the same where it is logical. **The phone is emptied too** (back to the
  welcome); Export is offered before.
- **Privacy page contact**: **wishlist.ctf70@simplelogin.com** for now (until the app has its final name).
- **Block** = unfriend + no friend requests from them + their chat hidden and no messages from them (they are not
  told). Not in groups, exchanges or reactions.
- **Report** = a reason (Spam, Hurtful, Something else) **plus a note that is required: at least 5 characters, spaces
  don't count**, plus the last 20 messages of that chat, unlocked by the reporter's phone (the pop-up says they are
  sent). Saved in a locked Supabase table the owner reads in Table editor; **no alert for now** (owner: find a better
  way later, not a failing backup). **Report and Block only in a chat's menu and on a friend's page.**
- **Deleting, details** (owner, 12 November 2026): a gift exchange you organise where nobody else has the app (so
  nobody can organise it) **is cancelled** (everyone told, as with Cancel; the list before deleting says so). Wishes you
  added to someone else's collab wishlist **stay, but "Added by" with your name is removed**. The last warning: title
  **"Delete your profile and data?"**, text **"Your profile, friends, chats, reservations and wishlists are deleted, on
  this phone and online. This can't be undone."**, buttons **"Save a copy first"** (Export), **"Delete everything"**
  (red), Cancel. In Profile at the very bottom: **"Delete my profile and data"** in red.
- **Later, not now** (owner, 12 November 2026): R7 (the two-phone test) and R8 (account and repository safety).
- **Copy is its own project** (owner, 12 November 2026, after trying 2.188/2.189: "I don't like the copy, but it's
  fine"): one pass over every text in the app and on the web pages, so it is crystal clear and concise UX writing that
  leaves nothing to the imagination. Until then, new texts follow STYLE.md and are asked as before.
- **Android's own backup is switched off** (owner, 12 November 2026, P20): `allowBackup="false"`; a new phone uses the
  recovery key. **The shop-page helper (`fetch-page`) reads at most 60 pages an hour per list, group or exchange
  link** (owner); above it: "Please try again in a moment".

## The code clean-up (owner, 12 November 2026, built in 2.192–2.194)
- **"The clean-up" is the code clean-up** (how screens remember things), not the online database. The owner chose all
  steps, carefully: screenshots before and after each step, all checks, one build per step (steps 2 and 3 shipped
  together as 2.193). The `*Nav` objects are gone (`LocalGo`, `Places`).
- **A bug found while building: ask first** (owner), then fix it once the owner says so. The first one: no message
  notification for the chat left open in Messages; fixed in 2.194 (owner: "I do want you to fix the bug").

## The rebuild of review points 1 and 2 (owner, 18 November 2026)
- Owner: fix the review's points, 1 (big files, screen state in shared objects) and 2 (no record of which server
  scripts run) first. Point 2 first (small, separate): version stamps in every script and a daily check that opens a
  GitHub issue (owner chose this over showing it in the app or keeping a list in the notes).
- Point 1: **Home first**, then the other screens, **one screen per version**; the owner uses each for a day or two.
  **Bug fixes only during the rebuild** (each rebuilt version looks exactly the same). Asked whether it makes building
  faster: working on the app yes (smaller files, fewer side effects); GitHub's build time hardly (splitting into Gradle
  modules would, a bigger project not chosen now).
- **Guards so the rebuild never has to be repeated** (owner, 19 November 2026: "I don't want to repeat this exercise
  over and over again as the app grows"; all four chosen, in now before the next screen): a file size limit (400 lines;
  the 29 files bigger on that day are frozen at their size and may only shrink), "rebuilt screens only draw" (no
  shared data changed outside a state holder), the recipe in the notes (CLAUDE.md, MAP "How a screen is built"), and a
  check-up issue every three months. All in `ArchitectureTest` and `.github/workflows/checkup.yml`. Honest scope told to
  the owner: the guards enforce structure, not good taste.
- **Data.kt waits for its own plan** (owner, 19 November 2026): it is not a screen but the app's memory (saving your
  lists, wishes and settings), so splitting it changes how saving works and a mistake could lose data, which
  screenshots can't catch. Asked with options (later with its own plan / only the parts that save nothing / all now);
  the owner chose **later, with its own plan** and extra checks that saving and loading give back exactly the same
  lists. The screens go first (next: the photo editor, then Shops).
- **Shared.kt, Exchange.kt and Alerts.kt join Data.kt's own plan** (owner, 19 November 2026, "Continue" on the
  recommendation): like Data.kt they save and send data (shared lists kept in step, gift exchanges and the draw,
  background price checks and notifications), where a mistake could lose or mix up data and screenshots can't catch
  it. The screens go first; next AppScreen.kt (moving between screens).
- **Friends.kt joins Data.kt's own plan too** (owner, 19 November 2026, "Continue" on the recommendation): it saves
  and sends your friends and requests. The owner also said nothing of the rebuild (2.233–2.246) was tried on the phone
  yet: one combined test list is in `docs/TODO.md` ("Try the rebuild").
- **The 8 remaining screens first, testing after** (owner, 19 November 2026: "Do the 8 screens first, I'll test
  after"): Overview, Reactions, SharedUi, GiftInvites, PriceHistory, Covers, Settings, Friend, one per version. Groups,
  Import and Fetch (they save data or read prices; Fetch must stay in step with `web/parse.js`) were proposed to wait
  with Data too; the owner didn't object.
- **The 8 screens get the full rebuild** (owner, 19 November 2026; asked with options: split only, proven / full
  rebuild): split plus a state holder each, like Home and Messages. The owner asked whether they would "run out of
  space": told that the 400-line limit is a tidiness rule for code files, not storage; the app, phone and GitHub have
  plenty of room, and Claude says so plainly if a real limit (build minutes, Supabase's free plan, phone space) comes near.
- **"Is this cleaning up or future-proofing, like a team?"** (owner, 19 November 2026): both; told what is in place
  (one screen recipe, guards that fail the build, the recipe in the notes, checks, the quarterly check-up) and what a
  team would still do (repositories for the data, real-phone checks, fewer web copies). The owner asked to be reminded
  of that later (TODO, "Later, remind the owner").

## The data rebuild (owner, 19 November 2026, after the screens)
- **Started now, before the owner tested the screen rebuild** (asked with options; recommended: only the safety net
  now, test first). The owner chose to start moving Data.kt straight away; told that if both turn out to have a
  problem it is harder to tell which caused it.
- **"Split inside, same doors"** (owner asked which is better; recommendation accepted): Repo stays the front door that
  every screen uses; inside it becomes small parts with their own checks (your lists, copies, the bin and Undo,
  categories, App settings, friends' lists). Not "full layers" (rewriting 79 screen files): the rebuilt screens already
  change data only through their state holders, so that would add risk for little gain.
- **Order:** Data.kt first, then Friends, then Shared, then the rest (Groups, Exchange, Alerts, Import, Fetch), one
  area per version, each tried by the owner for a day or two.
- **Data safety, both chosen:** the way things are stored (phone and online) does not change, so older versions still
  read it; and a before/after check (`DataSameTest`) that compares a whole phone's saved data word for word with a
  record made with the old code. ("Remind me to back up" was not chosen.)
- **Data.kt ships as one version** (2.256), built one part at a time with every check after each.
- **Friends next, shipped at once** (owner, 19 November 2026, asked with options; recommended: build it but ship after
  testing 2.256): the owner chose "build and ship now" (2.257), knowing untested layers make a problem harder to trace.
  A **pretend server** for the checks (`Cloud.pretendServer`, never set in the app), so the before/after check also
  proves what is sent to the server. **Five parts** (requests and the Friends tab; photos; birthday and About me;
  visible lists; links and codes). **The owner's four risks, all checked:** notifications told once (also after a
  restart), the offline switch goes back, photos kept after a restart, an older saved Friends tab still reads.
- **Shared next, shipped at once** (owner, 19 November 2026; asked with options, recommended: ship after testing
  2.256/2.257): the owner chose to ship now: "You simply need to make sure everything works. That counts for all the
  things we do right now." Told honestly: the checks prove the new code does exactly what the old did (also with two
  people, offline and a late answer), but not how it feels on a real phone. **Five parts** (lists you're on; open list
  and offline copy; saving wishes; details; reservations). **All four risks checked:** two people one wish, failed save
  undone, old answer ignored, offline / deleted / left.
- **Groups next** (owner, 19 November 2026; timing not asked again: the owner's standing answer is "ship now, make sure
  everything works"). **Four parts** (groups and invites; photos; making/joining/leaving/settings/invites; links and
  the browser member moving in). **All four risks checked:** an "Only me" list stays private outside the group, news
  told once, offline and old saved state, a browser member moving in (and a wrong link changing nothing).
- **Gift exchanges next** (owner, 19 November 2026): four parts (loading and helpers; news and reminders; making,
  joining, settings, invites, photo; rules, draw, reveal, chats, links) plus the data shapes in their own file. **All
  four risks checked:** only your own pair is kept (the draw's secrecy itself is the server's, `exchange_test.sql`),
  news and reminders told once, offline and old saved state, anonymous chats.

