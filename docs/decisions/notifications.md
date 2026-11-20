# Decisions: Notifications (owner, 20 November 2026)

Earlier notification decisions stay where they were (`docs/decisions/ideas.md` "Notifications section",
`docs/decisions/screens.md`); this file has the review of 20 November 2026 and what comes after. Newest at the bottom.

## No dead ends from a notification (owner, 20 November 2026; found: a price drop of a deleted wish opened an empty list)
- **Clean up with the wish:** when a wish is deleted for good (bin emptied, or after 30 days), its notifications go
  too. While it is in the bin they stay but say so.
- **Say it when tapped:** a notification whose wish is gone says "This wish is no longer on your list" (in the bin:
  "…is in the bin" with Restore) instead of opening an empty list. The same check for every notification that points to
  a wish, a list, a group or an exchange.

## The review (owner, 20 November 2026)
- **Fix dead ends and bugs** found in the notification code, each with an automatic check.
- The owner wants **every notification the app can send listed in the chat** (one line each: when it comes, its words,
  where a tap goes, which switch turns it off), to say what to change.
- **No test page and no test steps** (owner: "We don't have to check. You just need to make sure this app sends relevant
  notifications and make sure they'll arrive."): the code is gone through for what could stop a notification arriving
  (permission, channels, the background check's schedule, instant pushes, Android's battery rules) and fixed.
- Not chosen: a "Check notifications" status page.
