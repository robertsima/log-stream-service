# PrairieLog Cursor Rules Setup

Copy this into your project root:

```text
AGENTS.md                          ← router + agent system entry (Cursor reads natively)
backend/AGENTS.md                  ← backend routing hints
frontend/AGENTS.md                 ← frontend routing hints
.cursor/rules/
  000-project-context.mdc
  010-workflow-and-continuity.mdc
  100-backend-spring-openapi.mdc
  110-backend-security-alerting.mdc
  200-frontend-vanilla.mdc
  210-frontend-security-state.mdc
  300-testing.mdc
  400-deployment-readiness.mdc
  500-documentation.mdc
  900-cursor-rule-maintenance.mdc
.agent/
  README.md
  CONTINUITY.md
  manifests/                       ← capability manifests (inputs/outputs per role)
  contracts/
    _template.md                   ← shared spec for cross-surface work
```

## Agent model (stability split)

| Stability | Agents | Scope |
|-----------|--------|--------|
| Planning | planner | Writes `.agent/contracts/` only |
| Slow | core-data, infra | OpenAPI/schema; deploy/env — broader scope |
| Fast | backend-feature, frontend-feature, integration | Feature slices — stay narrow |
| Verify | reviewer | Diff vs contract |

The **router** (default chat + `AGENTS.md`) matches manifests — it does not plan implementations.

**Cross-surface flow:** planner → contract → implementer(s) → reviewer.

## Why two layers

- **`.cursor/rules/*.mdc`** — how to write code (glob-scoped constraints).
- **`.agent/manifests/`** — who handles what (routing interfaces).
- **`.agent/contracts/`** — what was agreed before code (prevents silent mismatches).

## Always-loaded rules

Only two rules use `alwaysApply: true`:

- `000-project-context.mdc`
- `010-workflow-and-continuity.mdc` (includes contract gate)

Everything else loads by file glob when editing matching paths.

## Suggested usage in Cursor

1. Restart Cursor or reload the window after copying files.
2. Open Settings → Rules and verify project rules appear.
3. For a cross-cutting feature, say: *"Act as planner: draft a contract for …"*
4. Then: *"Implement [backend] per `.agent/contracts/foo.md`"* or let the router pick manifests.

## Optional

Symlink `CLAUDE.md` → `AGENTS.md` if you use Claude Code and want one canonical file.
