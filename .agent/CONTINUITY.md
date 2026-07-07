# PrairieLog Continuity

Session memory for agents — **not** a substitute for `.agent/contracts/<slug>.md` on cross-surface tasks.

Read [AGENTS.md](../AGENTS.md) for routing. Use contracts when planner runs.

## [PLANS]

- 2026-07-02 [USER] Next practical project scope captured in `.agent/NEXT_SCOPE.md`; recommended current phase is SDK release readiness audit before docs/security/UX/SEO/publish prep.
- 2026-06-11 [USER] Contract **agreed**: magic link auth; demo bypass `admin@email.com`; non-revoked token quota; no ADMIN role v1.

## [DECISIONS]

- 2026-06-11 [USER] Agent split by **stability** (slow: core-data, infra; fast: backend/frontend/integration), not domain silos.
- 2026-06-11 [USER] Router enforces contract before cross-surface implementation; planner/reviewer separated from implementers.
- UNCONFIRMED — Add durable product/architecture decisions here when confirmed.

## [PROGRESS]

- UNCONFIRMED — Material progress only; prefer contract status field when a contract exists.

## [DISCOVERIES]

- UNCONFIRMED — Bugs, deploy quirks, tool behavior with short evidence.

## [OUTCOMES]

- 2026-07-07 [CODE] Anti-churn lifetime quotas + Neon connection fix: (1) new lifetime caps counting revoked/soft-deleted rows — `app.quotas.max-total-tokens-per-app` (`APP_QUOTAS_MAX_TOTAL_TOKENS_PER_APP`, default 25) in `AppTokenServiceImpl` and `app.quotas.max-total-destinations-per-app` (`APP_QUOTAS_MAX_TOTAL_DESTINATIONS_PER_APP`, default 25) in `AlertDestinationServiceImpl`, both `<=0` disables, new `countByAppId` repo methods — closes unbounded DB row growth via create→revoke/delete churn (esp. from the credential-free shared demo identity). Caveat: heavy churn on the shared demo app can exhaust its lifetime cap over time; raise env var or purge rows if demo token minting starts 429ing. (2) Hikari tuned for Neon free-tier autosuspend (drops connections after ~5 min idle → first request after idle failed on dead pooled socket): `minimum-idle: 0`, `idle-timeout: 120000`, `max-lifetime: 240000` in application.yml. Deliberately no keepalive ping — would burn Neon free compute hours. Tests: +2 lifetime-cap unit tests; targeted suite green with `-Dmaven.compiler.forceJavacCompilerUse=true`.
- 2026-07-07 [CODE] Auth/authz tightening pass (backend-only, under `api-stability-quotas-auth` contract): (1) restored null guard on `DemoSessionRequest.email` in `AuthController` (working tree had dropped it -> NPE/500 on `{}` body); (2) `AlertAnalysisController` analyze+preview now call `appService.requireOwner(appId)` — closes any-signed-in-user burning OpenAI credits / reading cached analyses for other users' apps; (3) `UserServiceImpl.createUser` rejects (403) creating/fetching a user whose email differs from the signed-in principal (blocks enumeration via `POST /api/v1/users`; anonymous path only when AUTH_ENABLED=false); (4) `ManagementRateLimitFilter` keys on rightmost X-Forwarded-For hop instead of client-spoofable leftmost. Demo/dashboard unaffected: no frontend caller of `createUser`; dashboard/demo analyze their own (demo-identity-owned) apps. Tests: AlertAnalysisControllerTest (+2 ownership), UserServiceTest (+2 principal checks, added CurrentUserProvider mock), ManagementFilterExemptionTest — green via `.\mvnw.cmd test "-Dtest=..." "-Dmaven.compiler.forceJavacCompilerUse=true"` (without the flag, surefire hits spurious NoClassDefFoundError). ITs need Docker; run `cd backend && ./mvnw test` later.
- 2026-06-30 [CODE] Rate-limiting review + hardening for MVP scope: added `max-destinations-per-app` quota (default 5, `APP_QUOTAS_MAX_DESTINATIONS_PER_APP`, `<=0` disables) in `AlertDestinationServiceImpl` + repo `countByAppIdAndDeletedAtIsNull`; added per-token ingestion limiter `IngestionRateLimiter` (in-memory fixed window keyed by token hash, default 120/min, `APP_RATE_LIMIT_INGESTION_RPM`) wired into `LogEventServiceImpl` after token validation; new `RateLimitExceededException` -> 429 `RATE_LIMIT_EXCEEDED` in `ApiExceptionHandler`. Tests: unit (AlertDestination quota, LogEvent rate-limited) + ITs (AlertDestinations quota, IngestionRateLimit). Verified: `mvnw test -Dtest=AlertDestinationServiceTest,LogEventServiceTest` green; ITs need Docker (not running locally) — run `cd backend && ./mvnw test`.
- 2026-06-30 [DISCOVERY] Pre-existing broken unit test `AppServiceTest` (fails before these changes: `@InjectMocks` doesn't supply `userService`/`currentUserProvider` mocks -> NPE). Unrelated to rate limiting; left as-is.

- UNCONFIRMED — Completed summaries: what changed, what remains, verification run.

## Entry format

- `YYYY-MM-DDTHH:MM:SSZ [USER|CODE|TOOL|ASSUMPTION] Fact.`
