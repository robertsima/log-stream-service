# Frontend agents

Work under `frontend/` is **fast context** unless you are only syncing OpenAPI JSON from the backend contract.

## Pick manifest

| Change type | Manifest |
|-------------|----------|
| Pages, CSS, vanilla JS, portal copy | [frontend-feature.md](../.agent/manifests/frontend-feature.md) |
| `env.js`, deploy URL, CORS-related client config | [infra.md](../.agent/manifests/infra.md) |
| OpenAPI JSON sync / API docs structure | [core-data.md](../.agent/manifests/core-data.md) |

If the task adds or changes API behavior, use [planner.md](../.agent/manifests/planner.md) first — do not guess request/response shapes in UI code.

## Config

- Production API: `frontend/js/env.js` → `https://log-stream-service.onrender.com`
- Local backend override: `frontend/js/env.local.js` (gitignored), loaded after `env.js`

## Parent

Router and contract rules: [AGENTS.md](../AGENTS.md)
