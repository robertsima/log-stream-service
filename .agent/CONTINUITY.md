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

- 2026-06-30 [CODE] Rate-limiting review + hardening for MVP scope: added `max-destinations-per-app` quota (default 5, `APP_QUOTAS_MAX_DESTINATIONS_PER_APP`, `<=0` disables) in `AlertDestinationServiceImpl` + repo `countByAppIdAndDeletedAtIsNull`; added per-token ingestion limiter `IngestionRateLimiter` (in-memory fixed window keyed by token hash, default 120/min, `APP_RATE_LIMIT_INGESTION_RPM`) wired into `LogEventServiceImpl` after token validation; new `RateLimitExceededException` -> 429 `RATE_LIMIT_EXCEEDED` in `ApiExceptionHandler`. Tests: unit (AlertDestination quota, LogEvent rate-limited) + ITs (AlertDestinations quota, IngestionRateLimit). Verified: `mvnw test -Dtest=AlertDestinationServiceTest,LogEventServiceTest` green; ITs need Docker (not running locally) — run `cd backend && ./mvnw test`.
- 2026-06-30 [DISCOVERY] Pre-existing broken unit test `AppServiceTest` (fails before these changes: `@InjectMocks` doesn't supply `userService`/`currentUserProvider` mocks -> NPE). Unrelated to rate limiting; left as-is.

- UNCONFIRMED — Completed summaries: what changed, what remains, verification run.

## Entry format

- `YYYY-MM-DDTHH:MM:SSZ [USER|CODE|TOOL|ASSUMPTION] Fact.`
