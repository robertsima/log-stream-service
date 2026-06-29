# Frontend feature (fast context)

**Stability:** fast — pages and flows change often  
**Scope:** narrow — one page or flow at a time

## I handle

- Static HTML pages, CSS, vanilla JS (`dashboard.js`, `app.js`, etc.)
- Developer portal copy, dashboard UX, examples/snippets presentation
- Client calls via `rest-service.js` and `window.CONFIG`

## Inputs

- Contract sections `[frontend]` (API shape, copy, headers)
- `frontend/js/env.js` API base URL (production default)

## Outputs

- Frontend file edits only; no backend or OpenAPI changes unless contract says so

## I do not

- Add frameworks, npm, or bundlers
- Store ingestion tokens or webhook URLs in browser storage
- Define new API shapes (→ planner + core-data)

## Paths

`frontend/**/*.html`, `frontend/**/*.css`, `frontend/**/*.js`, `frontend/resources/**`

## Rules

- `200-frontend-vanilla`, `210-frontend-security-state`
