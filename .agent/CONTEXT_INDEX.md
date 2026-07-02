# PrairieLog Context Index

Use this file to load the smallest useful context for a task. Read summaries first, then full source only when editing or verifying exact behavior.

## First Reads

| Task type | Read first | Then read only if touched |
|-----------|------------|---------------------------|
| Next practical project scope | `.agent/NEXT_SCOPE.md`, `.agent/CONTEXT_INDEX.md`, relevant summary | phase-specific files listed in `NEXT_SCOPE.md` |
| Routing or agent rules | `AGENTS.md`, `.agent/manifests/router.md`, `.cursor/rules/900-cursor-rule-maintenance.mdc` | `.agent/contracts/*` only when referenced |
| Cross-surface feature | `.agent/manifests/planner.md`, `.agent/contracts/_template.md`, relevant summaries | affected source files after contract exists |
| Backend API/schema | `.agent/manifests/core-data.md`, `100-backend-spring-openapi`, `security.md` | OpenAPI, entities, migrations, generated models |
| Backend feature | `.agent/manifests/backend-feature.md`, `security.md` | controllers, services, mappers, tests for the feature |
| Alert/webhook integration | `.agent/manifests/integration.md`, `security.md` | alert, webhook, Slack, Discord classes |
| Static frontend | `.agent/manifests/frontend-feature.md`, `frontend-docs.md` | edited HTML/CSS/JS page files |
| Frontend docs/examples/security | `frontend-docs.md`, `security.md`, `220-frontend-docs-security` | docs pages, snippets, demo/dashboard flows |
| SDK work | `sdk.md`, `400-sdk-release` | one SDK folder under `frontend/sdk/` |
| Infra/deploy config | `.agent/manifests/infra.md`, `400-deployment-readiness` | env, Docker, CORS, host config, deploy docs |
| Review after contract | `.agent/manifests/reviewer.md`, named contract, changed files | only contract-tagged surfaces |

## Do Not Load Unless Relevant

- Old contracts in `.agent/contracts/` are historical unless the current task names them or reviewer work needs them.
- Backend code is unnecessary for rule-only, frontend-only, docs-only, and SDK-only tasks.
- Frontend pages are unnecessary for backend-only, infra-only, and SDK-only tasks.
- SDK folders are unnecessary unless editing `frontend/sdk/**` or SDK docs/examples.
- Deployment config is unnecessary unless changing env, CORS, host config, Docker/Containerfile, or deploy docs.

## Summaries

- `NEXT_SCOPE.md` - staged handoff plan for SDK readiness, docs, security, UX, SEO/GEO, and publish preparation.
- `summaries/security.md` - token, webhook, auth, and secret handling constraints.
- `summaries/frontend-docs.md` - static frontend, docs, examples, and snippet boundaries.
- `summaries/sdk.md` - SDK locations, no-publish policy, and SDK-local verification.

Optional fingerprint fields in summaries are for stale-summary detection only. If a fingerprint looks stale, update the summary or read the source files before editing. Do not add complex tooling just to maintain fingerprints.
