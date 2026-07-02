# Phase 2 Docs Accuracy Pass

Status: complete
Date: 2026-07-02
Verification: static review + Node harness for docs.js curl generation + browser
render check (Playwright against `python -m http.server`). No Maven.

## Issues Found and Fixed

1. **`frontend/getting-started.html` — auth model was stale/wrong.**
   - "Full login is not implemented yet" removed. The create-user section now
     says it is a deprecated local bootstrap endpoint for `AUTH_ENABLED=false`
     and that production users sign in on the dashboard (user record created
     from the Firebase sign-in), matching the OpenAPI `deprecated: true` +
     description on `POST /api/v1/users`.
   - Added an "Auth note" callout at the top of the raw API bootstrap section:
     management endpoints require `Authorization: Bearer` in production (or use
     the dashboard); the unauthenticated curl flow is for local
     `AUTH_ENABLED=false`; ingestion always uses only `X-Ingestion-Token`.
   - Register-app curl was broken for the local flow it documents: backend
     `AppServiceImpl.resolveLegacyOwner` requires `ownerEmail` when auth is
     disabled (else 404 "Owner user not found"). Example now includes
     `ownerEmail` with a note that it is ignored under bearer auth.
   - Removed the claim that browser apps should use a "scoped browser token" —
     no token scoping exists in the API. Replaced with honest guidance: tokens
     embedded in browser code are visible to users; use a separate revocable
     token for browser logging.
2. **`frontend/js/docs.js` — generated curl/request examples did not match the
   contract.**
   - curl generation ignored `operation.security`: every Bearer-protected
     management endpoint rendered curl with no `Authorization` header (would
     401 in production). Now `Authorization: Bearer YOUR_ID_TOKEN` is added to
     the Request pane and curl for operations with BearerAuth.
   - GET/DELETE/PATCH curls dropped header parameters entirely — e.g.
     `GET /api/v1/ingestion-tokens/session` rendered without its required
     `X-Ingestion-Token` header. All methods now include headers.
   - POST curls with a Headers section omitted `Content-Type: application/json`
     even when a JSON body followed (e.g. batch ingest). Now added whenever a
     body is present.
   - Header placeholder for `X-Ingestion-Token` is now `YOUR_INGESTION_TOKEN`
     instead of `<X-Ingestion-Token>`.
   - Operations marked `deprecated: true` (createUser) now render a
     "Deprecated" chip on the endpoint card.

## Checked and Already Accurate (no change)

- `frontend/docs.html`: SDK install block honest about unpublished packages;
  ingestion token vs alert webhook distinction stated in the header copy.
- `frontend/js/examples.js` and all snippet files under
  `frontend/resources/snippets/` exist, load, and match the real SDK APIs.
- Getting Started batch curl (no `id`/`occurredAt`) is valid: `RawLogEvent`
  normalization makes `message` the only required field.
- Alert webhook vs ingestion token separation is correct on both pages.
- Production vs demo dashboard flow: demo-session endpoint documented in
  OpenAPI with its DEMO_BYPASS constraints; docs render that description.

## Verification Evidence

- Node harness (10/10 checks) exercised docs.js against the real
  `openapi.json`: bearer on all management curls, ingestion token on ingest
  curls, no bearer on ingest/deprecated-bootstrap curls, Content-Type present,
  session GET header present.
- Browser render (Playwright, static server): docs.html renders 19 endpoint
  cards + 1 Deprecated chip, no JS errors (only expected `env.local.js`/favicon
  404s); getting-started.html shows auth callout, deprecated label,
  `ownerEmail` in step 2, no "scoped browser token" text, `__API_BASE_URL__`
  substitution working, 4 framework snippets loaded.

## Manual Browser Checklist (for the user, post-deploy)

- [ ] Open docs.html: endpoint cards load; Apps/Tokens/Destinations curls show
      `Authorization: Bearer YOUR_ID_TOKEN`; POST /api/v1/users card shows
      "Deprecated" chip.
- [ ] Open getting-started.html: auth callout visible above raw bootstrap
      steps; register-app example includes ownerEmail.
- [ ] Copy buttons still copy the full multi-line curl commands.
- [ ] Run the copied batch-ingest curl against production with a real token →
      202 with accepted/rejected counts.

## Remaining Doc Gaps (candidates for later phases)

- Getting Started does not yet show a signed-in (bearer) variant of each
  bootstrap curl; the callout covers it in prose.
- `frontend/index.html` / landing copy not audited in this phase (Phase 4/5).
- OpenAPI `servers` lists only `http://localhost:8080`; the docs page injects
  the deployed base URL via `getApiBaseUrl()`, so this is cosmetic in the
  downloaded openapi.json.
