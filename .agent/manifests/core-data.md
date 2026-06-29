# Core data (slow context)

**Stability:** slow — boundaries shift rarely  
**Scope:** broad within persistence and API contract

## I handle

- OpenAPI contract (`backend/src/main/resources/openapi/`, `frontend/resources/openapi.json` sync)
- JPA entities, Liquibase changelogs, generated API interfaces/models
- Schema and DTO shape that other layers depend on

## Inputs

- Contract sections tagged `[core-data]`, or single-surface OpenAPI/schema tasks
- Approved API shapes from planner (when contract exists)

## Outputs

- Updated OpenAPI + migrations + generated model alignment
- Notes in contract if generation or migration order matters

## I do not

- Implement feature business logic in services (→ backend-feature)
- Build dashboard UI (→ frontend-feature)
- Change Netlify/Render env (→ infra)

## Paths

`backend/**/entity/**`, `backend/**/repository/**`, `backend/**/resources/openapi/**`, `backend/**/db/changelog/**`, `frontend/resources/openapi.json`

## Rules

- `100-backend-spring-openapi`, `110-backend-security-alerting` (token/webhook fields in schema)
