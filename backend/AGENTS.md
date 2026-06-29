# Backend agents

Work under `backend/` routes by **stability**, not a single "backend domain" agent.

## Pick manifest

| Change type | Manifest |
|-------------|----------|
| OpenAPI, entities, Liquibase, generated models | [core-data.md](../.agent/manifests/core-data.md) |
| Controllers, services, business logic | [backend-feature.md](../.agent/manifests/backend-feature.md) |
| Slack/Discord, alert send, webhooks | [integration.md](../.agent/manifests/integration.md) |
| `application.yml`, CORS, Docker | [infra.md](../.agent/manifests/infra.md) |

If the task also changes `frontend/`, start with [planner.md](../.agent/manifests/planner.md) and a contract in `.agent/contracts/`.

## Build / test

```bash
cd backend && mvn -q clean package -DskipTests
cd backend && mvn -q test
```

## Parent

Router and contract rules: [AGENTS.md](../AGENTS.md)
