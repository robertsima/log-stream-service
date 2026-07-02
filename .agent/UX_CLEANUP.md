# Phase 4 Frontend UX Cleanup

Status: complete (first pass)
Date: 2026-07-02
Verification: Playwright browser checks on all 6 pages at 1280x900 and
390x844 (overflow scans, screenshots, element checks). No Maven, no
frameworks, no build tooling.

## Issues Found and Fixed (small diffs, branch `ux-cleanup`)

1. **Getting Started broke horizontally on mobile (regression from Phase 2).**
   `.callout > p` is `display:flex; flex-wrap:nowrap` (icon + one text block),
   but the Phase 2 "Auth note" callout put ~10 inline elements directly in the
   `<p>`, each becoming an unshrinkable flex item → page scrolled to 1035px on
   a 375px viewport. Fixed by wrapping the prose in a single `<span>` (both
   callouts on the page, the Dashboard note too, defensively). Verified:
   scrollWidth == viewport after fix.
2. **Landing hero chips invisible on mobile.** `.hero-signal-strip` was a
   horizontal overflow-x scroller with fixed 13rem chips and no scroll
   affordance — the third chip ("No warehouse") sat at x=452 off a 390px
   screen. Changed the mobile rule to `flex-wrap: wrap` with `flex:1 1 10rem`.
   Verified: all three chips visible (2+1 wrap). Desktop 3-column grid
   untouched.
3. **Landing headline copy.** "Real-time app alerting for small apps"
   ("app…apps") → "Real-time error alerts for small apps" (also matches what
   the product does: only ERROR triggers alerts).
4. **"INFO/WARN do not alert as of now."** → "Other levels are accepted but
   stay quiet." (matches Getting Started phrasing).
5. **Demo intro copy.** "To try it out, paste a chat webhook below! API calls
   will show up in the console below." (below…below) → "Paste a chat webhook
   to send yourself a test alert — every API call appears live in the
   console."
6. **Demo → dashboard handoff CTA.** "Open the Dashboard" was a bare text
   link at the end of the demo flow; now a `button-link secondary` with icon,
   consistent with the page's other action links.
7. **Dashboard duplicate copy.** Signed-out card repeated the page header
   almost verbatim; now says what signing in gets you: "Sign in to register
   apps, mint ingestion tokens, and route alerts to Slack or Discord."

## Checked, No Change Needed

- No horizontal overflow on index, demo, dashboard, docs, examples at 390px
  (getting-started fixed above).
- All `<pre>` code blocks scroll internally (`overflow-x: auto`) — usable on
  mobile with copy buttons.
- Empty/error/loading states exist and are styled: demo console placeholder,
  docs "Loading API contract…", dashboard empty lists ("No apps yet…", "No
  tokens yet.", "No destinations yet."), inline error callouts.
- Token/webhook safety messaging is present at every dangerous moment
  (one-time token banner copy, "not shown again" hints, masked console note).
- Button hierarchy on hero and section CTAs is consistent (primary vs
  secondary).

## Remaining Backlog (not done; larger than "small diffs")

- Mobile nav wraps to 3 stacked rows (~150px tall). A collapsible/hamburger
  nav would reclaim space — needs design, touches every page.
- "Explore the portal" grid leaves the 4th card orphaned on desktop
  (3+1 layout). Rebalance to 2x2 or 4-up, or drop to 3 cards.
- `.example-pair` panes on docs endpoint cards have slight internal overflow
  on mobile (~10px); cosmetic.
- Demo page: large empty gap between the API console card and the footer on
  mobile (console aside has generous min-height).
- Dashboard signed-in flows (apps/tokens/destinations) were only smoke-checked
  signed-out; a signed-in UX pass needs a live backend + account.
- Consider removing Google Analytics from dashboard.html (see
  SECURITY_REVIEW.md finding 9) — UX-adjacent trust improvement.

## Manual Verification Checklist (post-deploy)

- [ ] iPhone-width: landing shows all three hero chips; Getting Started has no
      horizontal scroll anywhere.
- [ ] Demo: intro reads correctly; "Open the Dashboard" renders as a button.
- [ ] Landing headline/how-it-works copy updated.
- [ ] Desktop: hero chips still 3-across; nothing regressed visually.
