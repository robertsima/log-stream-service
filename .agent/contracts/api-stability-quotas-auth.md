# Contract: api-stability-quotas-auth

**Status:** agreed  
**Created:** 2026-06-11  
**Decided:** 2026-06-11  
**Surfaces:** [x] core-data [x] backend [x] frontend [ ] integration [x] infra

## Goal

Harden the PrairieLog management API against abuse (unbounded user/app/token creation) and add **simple email-based access control** so only the authenticated owner can manage their resources. Log ingestion via `X-Ingestion-Token` stays unchanged and unauthenticated.

Recommended auth path: **Firebase Authentication** (email link or email/password) issuing ID tokens the backend validates — not “trust `ownerEmail` in the JSON body.” That keeps MVP scope small while proving email ownership.

## Acceptance criteria

- [ ] A user cannot own more than **10 apps** (active, non-deleted).
- [ ] An app cannot have more than **5 active ingestion tokens** (non-revoked; revoked tokens do not count).
- [ ] Write endpoints for management (`users`, `apps`, `tokens`, `alert-destinations`) return **429** when quota or rate limit exceeded, with a clear `ErrorResponse.message`.
- [ ] Management endpoints require a valid **Bearer ID token** when `AUTH_ENABLED=true`; caller may only act on resources they own.
- [ ] `POST /api/v1/log-events` and ingestion-token session resolution remain **`X-Ingestion-Token` only** (no Bearer required).
- [ ] Dashboard works end-to-end: sign in → create/manage apps/tokens/destinations within quotas.
- [ ] Integration tests cover quota violations and unauthorized cross-owner access (no real Firebase in CI — use test JWT or `AUTH_ENABLED=false` test profile).

## Out of scope

- Redis or distributed rate limiting (in-memory per instance is acceptable for MVP).
- Kafka, new databases, or persistent log storage.
- Full admin UI, orgs/teams, or fine-grained permissions beyond **USER** and optional **ADMIN**.
- Replacing Firebase with a custom email-verification system in v1.
- Quotas on alert destinations or log-event volume (future contract).
- Keycloak as primary auth (existing `JWT_ENABLED` / `/secured/*` may remain but is not the dashboard path).

## Phased delivery (implement in order)

| Phase | What | Why |
|-------|------|-----|
| **1** | Quotas + rate limits + ownership checks (behind auth flag) | Stops spam even before UI login ships |
| **2** | Firebase JWT validation + frontend login | Proves email; replaces trusted `ownerEmail` |
| **3** | Remove anonymous bootstrap paths; tighten OpenAPI | Production default `AUTH_ENABLED=true` |

---

## [core-data] Data and API contract

### Schema

- **`users` table**
  - Add `role VARCHAR(32) NOT NULL DEFAULT 'USER'` — values: `USER`, `ADMIN`.
  - Add optional `auth_provider VARCHAR(32) DEFAULT 'firebase'` and `auth_subject VARCHAR(255)` (Firebase `sub`) with unique index on `(auth_provider, auth_subject)` for stable identity beyond email changes.
  - Keep existing `email` unique constraint.

- **No schema change** for `apps` / `app_tokens` quotas — enforce in service layer with `COUNT` queries:
  - Apps: `COUNT(*) WHERE owner_user_id = ? AND deleted_at IS NULL`
  - Tokens: `COUNT(*) WHERE app_id = ? AND revoked_at IS NULL`

- **Liquibase** changelog for new columns (nullable rollout OK, backfill `role = 'USER'`).

### OpenAPI changes (`backend/src/main/resources/openapi/openapi.yaml` → sync `frontend/resources/openapi.json`)

**Security scheme (new):**
```yaml
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: Firebase ID token (management API)
```

**Endpoint changes:**

| Endpoint | Change |
|----------|--------|
| `POST /api/v1/users` | **Deprecate** for production. Replace with `GET /api/v1/users/me` — upsert user from JWT `email` (+ optional `name` from claims), return `UserResponse`. |
| `POST /api/v1/auth/demo-session` | **New.** When `DEMO_BYPASS_ENABLED=true` and email equals `DEMO_BYPASS_EMAIL`, return short-lived server JWT for dashboard demo (no Firebase). |
| `POST /api/v1/apps` | Require `BearerAuth`. Remove `ownerEmail` from body **or** require it matches JWT email. Prefer **derive owner from JWT only** (drop `ownerEmail` from `CreateAppRequest`). |
| `GET /api/v1/apps` | Require `BearerAuth`. Replace `ownerEmail` query with implicit “my apps” (`GET /api/v1/users/me/apps` **or** keep query but must match JWT). |
| `GET /api/v1/apps/{appId}` | Require `BearerAuth` + ownership. |
| `POST/GET/PATCH` tokens, alert-destinations | Require `BearerAuth` + app ownership. |
| `POST /api/v1/log-events` | Unchanged — `X-Ingestion-Token` security only. |
| `GET /api/v1/ingestion-tokens/session` | Unchanged — `X-Ingestion-Token` only. |

**New / updated responses:**

- `403 Forbidden` — authenticated but not owner (or missing role).
- `429 Too Many Requests` — quota or rate limit (`ErrorResponse` with `code: "QUOTA_EXCEEDED"` or `"RATE_LIMIT_EXCEEDED"`).

**ErrorResponse extension (optional but recommended):**
```yaml
code: string   # e.g. QUOTA_EXCEEDED, RATE_LIMIT_EXCEEDED, FORBIDDEN
```

### Breaking changes

- Clients that call management APIs without Bearer tokens will fail when `AUTH_ENABLED=true`.
- `CreateAppRequest.ownerEmail` removal is breaking — document in README; migration: use JWT.
- `POST /api/v1/users` bootstrap removed in favor of `/users/me`.

### Configurable limits (defaults)

| Key | Default |
|-----|---------|
| `app.quotas.max-apps-per-user` | 10 |
| `app.quotas.max-active-tokens-per-app` | 5 |
| `app.rate-limit.management-requests-per-minute` | 60 (per IP + per authenticated user) |

---

## [backend] Service behavior

### Quota enforcement

- **`AppServiceImpl.createApp`**: before insert, if count ≥ max → throw `QuotaExceededException` → 429.
- **`AppTokenServiceImpl.createAppToken`**: count tokens where `revoked_at IS NULL`; if count ≥ max → 429. Revoking a token frees a slot.
- **`UserServiceImpl`**: stop unauthenticated open creation when auth enabled; `getOrCreateCurrentUser(JwtClaims)` for `/users/me`.

### Rate limiting

- Servlet filter or interceptor on management paths only (`/api/v1/users/**`, `/api/v1/apps/**`, …).
- In-memory token bucket per `(clientIp, optional userId)` — document that multi-instance deploy means per-instance limits (acceptable MVP).
- Exclude `/api/v1/log-events` and health/swagger paths.

### Authentication & RBAC

**Recommended: Firebase ID token validation**

- Env: `AUTH_ENABLED` (default `false` locally, `true` on Render).
- Env: `FIREBASE_PROJECT_ID` — validate JWT via Google JWKS (`securetoken@google.com` issuer).
- Extract claims: `email` (required), `sub`, optional `name`.
- Map to `users` row: lookup by `auth_subject` first, else by `email`, else create with `role=USER`.
- **ADMIN role / `ADMIN_EMAILS`:** **deferred for v1** — no elevated admin endpoints in this contract. All authenticated users are `USER` and manage only their own resources.

**Demo bypass (separate from ADMIN):**

- Env: `DEMO_BYPASS_ENABLED` (default `true` on Netlify demo, `false` optional on strict prod).
- Env: `DEMO_BYPASS_EMAIL=admin@email.com` (fixed fake demo identity).
- When bypass enabled and client presents matching demo credential (see below), treat as authenticated session for `admin@email.com` **without** Firebase magic link.
- Demo credential: `POST /api/v1/auth/demo-session` with body `{ "email": "admin@email.com" }` returns short-lived server-signed JWT **only** if email matches `DEMO_BYPASS_EMAIL` and bypass is enabled. Quotas still apply to demo user.
- Quick demo on dashboard uses this path automatically (no magic link step).

**Spring Security split:**

| Path pattern | Auth |
|--------------|------|
| `/api/v1/log-events` | Permit all; `X-Ingestion-Token` in controller |
| `/api/v1/ingestion-tokens/**` | Permit all; header token |
| `/api/v1/**` (management) | `AUTH_ENABLED=true` → require authenticated JWT |
| `/secured/**` | Existing optional Keycloak JWT unchanged |

**Ownership:**

- `AppRepository` helper: `existsByIdAndOwnerUserId(appId, userId)`.
- All app-scoped operations verify ownership before mutate/read (except ingestion).

### Error handling

| Condition | HTTP | code |
|-----------|------|------|
| No/invalid Bearer | 401 | `UNAUTHORIZED` |
| Wrong owner | 403 | `FORBIDDEN` |
| App/token quota | 429 | `QUOTA_EXCEEDED` |
| Rate limit | 429 | `RATE_LIMIT_EXCEEDED` |

### Tests (`300-testing`)

- Quota: create 10 apps OK, 11th 429; 5 tokens OK, 6th 429 (revoke one → can create again).
- Auth: user A cannot create token on user B's app → 403.
- Log ingestion still works with only ingestion token when `AUTH_ENABLED=true`.
- Rate limit: burst POST returns 429 (adjust threshold in test profile).

---

## [frontend] UI and client behavior

### Auth flow (Firebase JS SDK)

- Add Firebase config via **public** env in `env.js` / `env.example.js` (apiKey, authDomain, projectId — not secrets).
- New minimal module e.g. `js/auth.js`:
  - **Primary:** `sendSignInLinkToEmail` / `signInWithEmailLink` (magic link).
  - `signOut()`, `getIdToken()`, `onAuthStateChanged`.
- **Demo bypass:** if email is `admin@email.com` (from `DEMO_BYPASS_EMAIL` config exposed read-only to frontend), skip magic link → call `POST /api/v1/auth/demo-session` → use returned JWT for management APIs. Quick demo path uses this automatically.
- **Do not** store ingestion tokens in browser storage; never log tokens.

### `rest-service.js`

- Management methods attach `Authorization: Bearer <idToken>` when user is signed in.
- New: `getCurrentUser()` → `GET /api/v1/users/me`.
- Remove or gate `createUser()` — replaced by `/users/me` after sign-in.
- `createApp`: drop `ownerEmail` from body when API updated.
- Handle **401** → prompt sign-in; **429** → show quota/rate-limit message; **403** → not your resource.

### Dashboard (`dashboard.html`, `dashboard.js`)

- **Signed out:** show sign-in (email field + “Send sign-in link” or password); hide resource management.
- **Signed in:** show email in toolbar; quick demo creates app/token **for authenticated user** (still subject to quotas).
- Display quota hints: “Apps: 3/10”, “Tokens: 2/5” when API exposes counts (optional `GET /users/me/limits` or derive from list lengths).
- Copy: clarify management requires sign-in; ingestion token flow for apps unchanged.

### Pages

- `getting-started.html` — update bootstrap steps: sign in first, then register app.
- `index.html` — mention authenticated dashboard.

---

## [integration] External systems

- **No changes** to Slack/Discord delivery or alert aggregation.
- Test-alert endpoint: require app ownership when auth enabled (same as other management routes).
- CI: mock Firebase JWT with test signing key **or** `@SpringBootTest` with `AUTH_ENABLED=false` for legacy ITs, plus dedicated auth ITs with mock decoder bean.

---

## [infra] Deploy and config

### New environment variables

| Variable | Required when | Example |
|----------|---------------|---------|
| `AUTH_ENABLED` | prod | `true` |
| `FIREBASE_PROJECT_ID` | `AUTH_ENABLED=true` | `prairielog-prod` |
| `DEMO_BYPASS_ENABLED` | optional | `true` |
| `DEMO_BYPASS_EMAIL` | when demo on | `admin@email.com` |
| `APP_QUOTAS_MAX_APPS_PER_USER` | optional | `10` |
| `APP_QUOTAS_MAX_ACTIVE_TOKENS_PER_APP` | optional | `5` |
| `APP_RATE_LIMIT_MANAGEMENT_RPM` | optional | `60` |

### Firebase console (one-time, documented in README)

1. Create Firebase project; enable **Email link** sign-in (disable password if unused).
2. Add authorized domain: `prairie-log-api.netlify.app` (+ `localhost` for dev).
3. Copy web app config into `frontend/js/env.js` (or `env.local.js`).

### CORS

- `ALLOWED_ORIGINS` unchanged; ensure `Authorization` header allowed (already should be for future JWT).

### Local dev

- `AUTH_ENABLED=false` preserves current frictionless local testing **or** use Firebase emulator (optional follow-up).
- Document that production must set `AUTH_ENABLED=true`.

---

## Resolved decisions (2026-06-11)

| Question | Decision |
|----------|----------|
| Sign-in | **Firebase email magic link** for real users |
| Quick demo | **Bypass magic link** via `admin@email.com` → `POST /api/v1/auth/demo-session` when `DEMO_BYPASS_ENABLED=true` |
| Token quota | Count **non-revoked tokens only** toward limit of 5 |
| ADMIN / email allowlist | **Skip for v1** — no `ADMIN_EMAILS`, no elevated role. Demo email is not admin; it is a no-login demo identity only. |

### What `ADMIN_EMAILS` was (and why we skipped it)

The allowlist was only for granting an **ADMIN** role (e.g. future endpoints to list all users or change global quotas). This contract has **no admin features** — every user manages only their own apps. Revisit when you add operator/admin tooling.

---

## Decision record (planner recommendation)

| Option | Verdict |
|--------|---------|
| **Firebase Auth + JWT on management API** | **Recommended** — proves email, minimal backend code, fits static frontend. |
| Trust `ownerEmail` in request body only | **Reject for prod** — anyone can impersonate any email; quotas alone do not fix this. |
| Keycloak for dashboard | **Defer** — already wired for `/secured/*`; heavier ops than Firebase for this portal. |

---

## Implementer routing (after agreement)

1. **core-data** — OpenAPI + Liquibase + regenerate models  
2. **backend-feature** — quotas, rate limit, Firebase JWT filter, ownership  
3. **frontend-feature** — Firebase sign-in, rest-service, dashboard UX  
4. **infra** — env vars, README, Firebase setup notes  
5. **reviewer** — contract vs diff + test checklist  

---

## Sign-off

| Step | Owner | Done |
|------|-------|------|
| Planner draft complete | planner | [x] |
| Surfaces acknowledged | router / user | [x] |
| Open questions resolved | user | [x] |
| Implementation complete | implementer(s) | [ ] |
| Reviewer pass | reviewer | [ ] |

## Reviewer notes

<!-- pending implementation -->
