# PrairieLog - Agent Entry Point

Full agent context (routing, manifests, contracts, continuity, historical audits) lives outside this repo, in the vault:

**Read first, every session:** `D:\Development\AI Workspace\projects\log-stream-service\index.md`

That file replaces what used to be `.agent/README.md` + `.agent/CONTEXT_INDEX.md` — it has the role table, the manifest picker, and the "first reads by task type" table. Read it before loading any other context.

## Orchestration role

This session is the **router/orchestrator**: it matches work to capability manifests (in the vault) and enforces the contract gate below. It does not plan implementations itself. It can spin up scoped Claude Code subagents:

- `frontend-agent` (`.claude/agents/frontend-agent.md`) — static frontend, docs, demo/dashboard flows
- `backend-agent` (`.claude/agents/backend-agent.md`) — Spring Boot, OpenAPI/schema, infra, integrations
- `prairielog-plan-implementer` (`.claude/agents/prairielog-plan-implementer.md`) — longer-running implementer with its own project memory

Each subagent file is short; it reads its full manifest/context from the vault before starting work.

## Contract gate (cross-surface work)

When API, UI, schema, infra, or integrations must change together:

```text
User goal -> Planner -> <vault>/contracts/<slug>.md -> Implementer(s) -> Reviewer
```

Implementers edit only their tagged sections and implement against the **frozen** contract body. Reviewer checks the diff against that file.

Skip the contract for: typo fixes, copy tweaks, single-file bugs, tests-only, or changes confined to one manifest's paths.

## Coding rules (stay in this repo)

`.cursor/rules/*.mdc` stay here — Cursor only auto-loads rules at this path, and they're small and self-contained (no vault lookup needed for day-to-day edits). Always loaded: `000-project-context`, `010-workflow-and-continuity`, `020-context-loading`, `030-verification-cost-control`.

## Verification

Classify command cost before running checks — see `.cursor/rules/030-verification-cost-control.mdc`. Broad Maven/package checks are for backend-impact, release, or pre-deploy work, not every edit.

## Hard constraints (all agents)

- Vanilla static frontend only (no React/npm/bundlers).
- Contract-first API changes (OpenAPI before Java).
- Never log or persist raw ingestion tokens or full webhook URLs.
- Smallest safe diff; no MVP scope creep (no Kafka/Redis/ES/persistent log store).

## Daily update

Raw same-day tagged notes go in `D:\Development\AI Workspace\projects\log-stream-service\daily\<date>.md`. Run `/daily-update` (see `.claude/commands/daily-update.md`) to file them into the vault's `CONTINUITY.md` and archive the raw file — this is manual, run it yourself, not on a schedule.
