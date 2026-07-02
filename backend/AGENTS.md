# Backend agents

Work under `backend/` routes by **stability**, not a single "backend domain" agent.
Read [../.agent/CONTEXT_INDEX.md](../.agent/CONTEXT_INDEX.md) first for scoped retrieval and verification tier.

## Pick manifest

| Change type | Manifest |
|-------------|----------|
| OpenAPI, entities, Liquibase, generated models | [core-data.md](../.agent/manifests/core-data.md) |
| Controllers, services, business logic | [backend-feature.md](../.agent/manifests/backend-feature.md) |
| Slack/Discord, alert send, webhooks | [integration.md](../.agent/manifests/integration.md) |
| `application.yml`, CORS, Docker | [infra.md](../.agent/manifests/infra.md) |

If the task also changes `frontend/`, start with [planner.md](../.agent/manifests/planner.md) and a contract in `.agent/contracts/`.

Verification is tiered by impact; see `030-verification-cost-control` before running Maven.

## Parent

Router and contract rules: [AGENTS.md](../AGENTS.md)
