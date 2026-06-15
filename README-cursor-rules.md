# PrairieLog Cursor Rules Setup

Copy this into your project root:

```text
.cursor/
  rules/
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
  CONTINUITY.md
```

## Why this structure

Cursor's native rule system uses `.cursor/rules/*.mdc` files.

This set keeps only two rules always loaded:

- `000-project-context.mdc`
- `010-workflow-and-continuity.mdc`

Everything else is scoped by file glob so Cursor only loads relevant rules when editing backend, frontend, tests, deployment config, docs, or rule files.

## What changed from the previous rule set

The old files were useful but had issues:

- Too many important rules had `alwaysApply: false` with no glob, so Cursor could skip them.
- Some files had duplicate frontmatter fields.
- Some `globs` blocks were malformed.
- `alwaysApply` appeared inside a `globs` section in at least one file.
- Several rules overlapped, especially the vanilla frontend and frontend security rules.

This version makes rules easier for Cursor to load consistently.

## Suggested usage in Cursor

After copying the files:

1. Restart Cursor or reload the window.
2. Open Cursor Settings -> Rules and verify the project rules appear.
3. Start a new chat and ask: `What PrairieLog rules are active for this repo?`
4. When working on a specific area, mention the area clearly, for example: `Fix the CORS deployment issue in the Spring Boot backend.`

## Optional

If you also use Codex CLI later, you can still add an `AGENTS.md`, but for Cursor this `.cursor/rules` setup should be the main source of truth.
