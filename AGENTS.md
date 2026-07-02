# PrairieLog - Agent Router

This repo uses **stability-based agents**, not domain silos. The default chat session acts as the **router**: it matches work to capability manifests and enforces gates. It does not plan implementations.

## Router job (every session)

1. Read the user goal and `.agent/CONTINUITY.md` if present.
2. Read `.agent/CONTEXT_INDEX.md` to choose the minimum useful context.
3. Classify work:
   - **Single-surface, small** -> route directly to one implementer manifest (no contract).
   - **Cross-surface** (API + UI, schema + feature, deploy + app code) -> **contract required** before implementation.
4. Pick agent(s) from `.agent/manifests/` using **inputs/outputs**, not domain guesses.
5. Enforce: no implementer starts until `.agent/contracts/<slug>.md` exists and lists affected surfaces.
6. After implementation -> route to **reviewer** when a contract was used.

The router knows **interfaces** (manifests, contracts). It does not own API shapes, UI copy, or schema design.

## Capability manifests

| Stability | Agent | Manifest | Typical scope |
|-----------|-------|----------|---------------|
| - | Planner | [planner.md](.agent/manifests/planner.md) | Shared contract only; no code |
| Slow | Core data | [core-data.md](.agent/manifests/core-data.md) | OpenAPI, entities, Liquibase, generated models |
| Slow | Infra | [infra.md](.agent/manifests/infra.md) | Docker, env, CORS, host config |
| Fast | Backend feature | [backend-feature.md](.agent/manifests/backend-feature.md) | Controllers, services, mappers |
| Fast | Frontend feature | [frontend-feature.md](.agent/manifests/frontend-feature.md) | HTML, CSS, vanilla JS pages |
| Fast | Integration | [integration.md](.agent/manifests/integration.md) | Slack/Discord, webhooks, alert delivery |
| - | Reviewer | [reviewer.md](.agent/manifests/reviewer.md) | Contract vs diff; no new features |

Nested entry points: [backend/AGENTS.md](backend/AGENTS.md), [frontend/AGENTS.md](frontend/AGENTS.md).

## Contract gate (cross-surface work)

When API, UI, schema, infra, or integrations must change together:

```text
User goal -> Planner -> .agent/contracts/<slug>.md -> Implementer(s) -> Reviewer
```

Planner uses [.agent/contracts/_template.md](.agent/contracts/_template.md). Implementers edit only their tagged sections and implement against the **frozen** contract body. Reviewer checks the diff against that file.

Skip the contract for: typo fixes, copy tweaks, single-file bugs, tests-only, or changes confined to one manifest's paths.

## Coding rules (scoped)

Implementation constraints live in `.cursor/rules/*.mdc` (loaded by file glob). Manifests reference which rules apply; do not duplicate them here.

Always loaded: `000-project-context`, `010-workflow-and-continuity`, `020-context-loading`, `030-verification-cost-control`.

## Verification

Classify command cost before running checks. Use `.cursor/rules/030-verification-cost-control.mdc`; broad Maven/package checks are for backend-impact, release, or pre-deploy work, not every edit.

## Hard constraints (all agents)

- Vanilla static frontend only (no React/npm/bundlers).
- Contract-first API changes (OpenAPI before Java).
- Never log or persist raw ingestion tokens or full webhook URLs.
- Smallest safe diff; no MVP scope creep (no Kafka/Redis/ES/persistent log store).
