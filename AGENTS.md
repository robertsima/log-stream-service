# PrairieLog — Agent Entry Point

All agent context lives in the vault. **Read first, every session:**

`D:\Development\AI Workspace\projects\log-stream-service\index.md`

That hub carries the workflow, contract gate, verification tiers, and first-reads table; `rules.md` (sectioned coding rules) and `roles.md` (capability roles) sit next to it. Load only what the task needs.

This session is the router/orchestrator: match work to a role in the vault's `roles.md`, enforce the contract gate in `index.md`, implement routine work directly against `rules.md`. One standing subagent is registered for longer plan-implementer runs (it has its own memory): `.claude/agents/prairielog-plan-implementer.md`.

Hard constraints (binding even before reading the vault):
- Vanilla static frontend only (no React/npm/bundlers).
- Contract-first API changes (OpenAPI before Java).
- Never log or persist secrets, raw ingestion tokens, or full webhook URLs.
- No Kafka/Redis/Elasticsearch/persistent log store (MVP); smallest safe diff.
- No publish/deploy/production/destructive commands without explicit user approval.

Daily notes: raw `[TAG] fact` lines go in the vault's `daily/<date>.md`; run `/daily-update` manually to file them into `CONTINUITY.md`.
