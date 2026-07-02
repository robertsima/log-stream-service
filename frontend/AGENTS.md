# Frontend agents

Work under `frontend/` is **fast context** unless you are only syncing OpenAPI JSON from the backend contract.
Read [../.agent/CONTEXT_INDEX.md](../.agent/CONTEXT_INDEX.md) first for scoped retrieval and verification tier.

## Pick manifest

| Change type | Manifest |
|-------------|----------|
| Pages, CSS, vanilla JS, portal copy | [frontend-feature.md](../.agent/manifests/frontend-feature.md) |
| `env.js`, deploy URL, CORS-related client config | [infra.md](../.agent/manifests/infra.md) |
| OpenAPI JSON sync / API docs structure | [core-data.md](../.agent/manifests/core-data.md) |

If the task adds or changes API behavior, use [planner.md](../.agent/manifests/planner.md) first; do not guess request/response shapes in UI code.

Use `frontend-feature` for static pages/scripts, `infra` for environment URL changes, and `core-data` for OpenAPI JSON sync. Do not load SDK or backend details unless the task touches them.

## Parent

Router and contract rules: [AGENTS.md](../AGENTS.md)
