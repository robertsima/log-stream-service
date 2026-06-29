# Contract: <short-slug>

**Status:** draft | agreed | implementing | review | done  
**Created:** YYYY-MM-DD  
**Surfaces:** [ ] core-data [ ] backend [ ] frontend [ ] integration [ ] infra

## Goal

One paragraph: what we are building or fixing and why.

## Acceptance criteria

- [ ] Criterion 1
- [ ] Criterion 2

## Out of scope

- Explicitly excluded work (prevents scope creep).

---

## [core-data] Data and API contract

<!-- Planner + core-data agent. OpenAPI / entities / Liquibase. -->

- OpenAPI paths/schemas:
- Entity or migration changes:
- Breaking changes:

## [backend] Service behavior

<!-- backend-feature agent. Controllers, services, repos. -->

- Endpoints / services touched:
- Business rules:
- Error responses:

## [frontend] UI and client behavior

<!-- frontend-feature agent. HTML, CSS, JS. -->

- Pages / scripts:
- User-visible copy:
- API calls (headers, payloads):

## [integration] External systems

<!-- integration agent. Slack, Discord, webhooks. -->

- Webhook / delivery behavior:
- Test strategy (no real webhooks in CI):

## [infra] Deploy and config

<!-- infra agent. Env, CORS, Docker, hosts. -->

- Env vars:
- CORS / origins:
- Host-specific notes:

---

## Sign-off

| Step | Owner | Done |
|------|-------|------|
| Planner draft complete | planner | [ ] |
| Surfaces acknowledged | router | [ ] |
| Implementation complete | implementer(s) | [ ] |
| Reviewer pass | reviewer | [ ] |

## Reviewer notes

<!-- reviewer agent fills after comparing diff to sections above -->
