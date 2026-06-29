# Agent workspace

Artifacts for multi-step and cross-surface work. Coding standards stay in `.cursor/rules/`; this folder holds **routing, contracts, and continuity**.

## Layout

```text
.agent/
  README.md           ← this file
  CONTINUITY.md       ← durable facts between sessions (not a contract)
  manifests/          ← capability manifests (router reads these)
  contracts/          ← shared specs (planner writes; implementers follow)
    _template.md
    <slug>.md         ← one file per cross-surface task
```

## Roles

| Role | Writes code? | Primary output |
|------|----------------|----------------|
| Router (default session / AGENTS.md) | No | Route + enforce contract gate |
| Planner | No | `.agent/contracts/<slug>.md` |
| Slow implementers (core-data, infra) | Yes | Broad, stable layers |
| Fast implementers (backend/frontend/integration) | Yes | Narrow feature slices |
| Reviewer | No | Contract compliance report |

## When to add a contract

**Yes:** new endpoint + dashboard UI, schema migration + service behavior, deploy config + frontend origin + CORS together.

**No:** one HTML page copy fix, one service bug in an existing endpoint, test-only change, README typo.

## Continuity vs contract

- **CONTINUITY.md** — session memory (decisions, discoveries, outcomes). Update sparingly.
- **contracts/** — binding spec for a specific task. Planner creates; reviewer validates against it.

Do not put implementation plans only in CONTINUITY when a contract is required — use a contract file.
