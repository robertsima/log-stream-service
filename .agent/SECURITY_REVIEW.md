# Phase 3 Security Review

Status: complete
Date: 2026-07-02
Verification: static inspection + grep + browser checks (Playwright against a
local static server). No backend Maven (no backend code changed). No
production resources touched.

## Verdict

The token/webhook handling discipline the project set for itself is actually
implemented. No high-severity findings. One live functional bug in the demo
page was found and fixed; two hardening fixes applied; the rest are
accepted-risk notes and optional hardening.

## Findings by Severity

### Medium

1. **Demo page init crash (fixed).** `frontend/demo.html` has the raw-token
   banner and copy-token button commented out, but `demo.js` bound
   `integrate-copy-token-button` unguarded → `TypeError` at init, which killed
   the copy-starter-code and clear-console button bindings on the live demo
   page. Fixed by null-guarding the optional bindings. Verified in browser:
   no console errors after fix.
2. **Unpinned third-party script (fixed).** Every page loaded
   `https://unpkg.com/lucide@latest` — an unpinned, mutable dependency
   executing on pages that hold auth tokens and ingestion tokens in memory
   (dashboard, demo). A compromised or malicious release would run arbitrary
   JS there. Pinned to `lucide@1.23.0/dist/umd/lucide.min.js` (resolved from
   @latest at review time, render-verified on demo + dashboard + docs +
   getting-started). Firebase SDKs were already pinned (10.12.5, gstatic).
   Optional next step: self-host the file or add SRI.

### Low / Accepted Risk (documented, no change)

3. **Public shared demo ingestion token in `env.js`**
   (`DEMO_INGESTION_TOKEN`). Deliberate and commented in the source: all demo
   visitors share one token scoped to the throwaway demo app; revocable if
   abused; ingestion-only privilege. Keep the revoke-and-replace runbook in
   mind; backend per-IP rate limits bound the abuse.
4. **Demo fallback caches a minted token in `localStorage`**
   (`prairielog.demo.ingestionToken` in demo.js). Runs only when
   `DEMO_INGESTION_TOKEN` is not configured (local/dev path); deliberate and
   commented. Optional hardening: gate the fallback to
   localhost/127.0.0.1 so a production deploy without the configured token
   fails visibly instead of minting per-visitor tokens.
5. **Magic-link URL briefly written to `localStorage`** (auth.js cross-tab
   delegation payload includes the full Firebase sign-in link with its
   one-time code). Mitigations already present: same-origin only, code is
   single-use and short-lived, payload is overwritten by the ack and cleared
   by `clearSignInPending`. Optional hardening: remove the payload key
   immediately after the delegation wait times out.
6. **`.env.example` shipped `DEMO_BYPASS_ENABLED=true` (fixed).** Template now
   defaults to `false` with a comment explaining when to enable it. Backend
   default was already `false`; the risk was copy-paste enabling the bypass on
   deployments that don't serve the public demo.

### Informational

7. **CORS is correct:** exact-origin list (env-driven), no wildcard,
   `allowCredentials(false)`, methods/headers scoped, `ALLOWED_ORIGINS`
   overrides in prod. Default list includes several localhost dev ports —
   fine for this product; override in prod env if undesired.
8. **Swagger/OpenAPI exposure matches intent:** springdoc UI and `/v3/api-docs`
   default off (`SWAGGER_UI_ENABLED=false`); frontend ships its own static
   `openapi.json` copy on purpose.
9. **Google Analytics (gtag) on all pages including dashboard/demo.** No
   secrets are sent (page paths only), but it is a third-party script on
   token-handling pages. Acceptable for a public portal; consider dropping it
   from dashboard.html if you want to minimize third-party JS where tokens
   live.
10. **No secrets committed:** `.env` gitignored (only `.env.example` +
    `env.local.example.js` tracked, placeholders only); Firebase web config is
    public by design; DB creds/JWT secrets env-injected with safe defaults.

## Verified Controls (the checklist from NEXT_SCOPE)

- [x] Raw ingestion tokens shown once only (dashboard banner at creation;
      hidden on app switch/sign-out; API console output replaces `token` with
      a copy hint; demo console masks any `token`-like key)
- [x] Raw tokens not in browser storage (dashboard/state.js in-memory only;
      demo fallback exception is deliberate + documented, see finding 4)
- [x] Full webhook URLs not stored in browser storage anywhere
- [x] Full webhook URLs not logged or displayed after creation (API list
      responses omit them by contract; demo console masks `webhook`/`url`
      keys via `sanitizeForConsole`; dashboard shows name/type/status only)
- [x] Demo bypass clearly marked, off by default in backend config, email-
      restricted, short-lived server-signed JWT
- [x] CORS exact-origin (finding 7)
- [x] Swagger exposure matches deployment intent (finding 8)
- [x] External scripts reviewed (findings 2, 9)
- [x] No secrets in committed config (finding 10)

## Changes Applied (branch `security-review`)

- `frontend/js/demo.js`: null-guard optional button bindings (fixes live
  demo-page TypeError).
- `frontend/*.html` (6 pages): pin lucide to `1.23.0` instead of `@latest`.
- `.env.example`: `DEMO_BYPASS_ENABLED=false` + explanatory comment.

## Post-Deploy Verification Checklist

- [ ] Demo page: no console errors; webhook test works; copy starter code and
      clear console buttons work.
- [ ] Dashboard: icons render (pinned lucide), sign-in works, token banner
      appears once on creation and never again.
- [ ] Confirm production env sets `DEMO_BYPASS_ENABLED=true` **only** on the
      deployment that serves the public demo, with a strong `DEMO_JWT_SECRET`.
- [ ] Confirm `ALLOWED_ORIGINS` in production is set to the deployed frontend
      origin(s) only.
