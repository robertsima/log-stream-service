# Infra (slow context)

**Stability:** slow — deploy and platform config  
**Scope:** broad across hosting, env, and cross-cutting config

## I handle

- `application.yml`, Docker/Containerfile, compose files
- `ALLOWED_ORIGINS`, `DB_*`, `PORT`, alert toggles, Swagger flags
- `frontend/js/env.js` production URL, `.env*.example`, README deploy sections
- VS Code launch env wiring (local only)

## Inputs

- Contract sections `[infra]`, or isolated deploy/CORS/env issues
- Known frontend URL(s) and backend host URL(s)

## Outputs

- Env-driven config changes (no secrets in repo)
- Deploy checklist updates when behavior changes

## I do not

- Add product features or API endpoints (→ planner + feature agents)
- Store real credentials in tracked files

## Paths

`**/application*.yml`, `Dockerfile`, `docker-compose*`, `frontend/js/env*.js`, `.env*.example`, `README.md` deploy sections

## Rules

- `400-deployment-readiness`
