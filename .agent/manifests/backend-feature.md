# Backend feature (fast context)

**Stability:** fast — feature code changes often  
**Scope:** narrow — one feature or bugfix at a time

## I handle

- Controllers implementing generated APIs
- Services: ingestion, tokens, alert aggregation, destination CRUD
- Mappers and request validation at the controller edge

## Inputs

- Contract sections `[backend]` with frozen OpenAPI from core-data (when applicable)
- Existing generated interfaces in `com.logstream.generated.api`

## Outputs

- Java changes in services/controllers only (no schema drift without core-data)

## I do not

- Redesign OpenAPI or entities without core-data / contract update
- Plan cross-frontend workflows (→ planner)
- Change CORS or Docker (→ infra)

## Paths

`backend/src/main/java/**/controller/**`, `backend/src/main/java/**/service/**`, `backend/src/main/java/**/mapper/**`

## Rules

- `100-backend-spring-openapi`, `110-backend-security-alerting`, `300-testing`
