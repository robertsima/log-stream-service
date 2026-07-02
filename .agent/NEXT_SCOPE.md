# PrairieLog Next Practical Scope

Status: ready for next agent
Created: 2026-07-02
Current recommended phase: Phase 5 - SEO/GEO pass (or Phase 6 publish prep once npm/PyPI accounts are ready)
Completed: Phase 1 (see `.agent/SDK_RELEASE_AUDIT.md`), Phase 2 (see `.agent/DOCS_ACCURACY_PASS.md`), Phase 3 (see `.agent/SECURITY_REVIEW.md`), Phase 4 first pass (see `.agent/UX_CLEANUP.md`; UX backlog listed there)

This packet defines the next staged project scope after agent/context optimization. It is not a cross-surface implementation contract. Create a contract only if a later phase requires API shape, schema, backend behavior, and frontend behavior to change together.

## First Output For Next Agent

1. **Recommended current phase:** Phase 1 - SDK release readiness audit.
2. **Files to inspect first:** `.agent/CONTEXT_INDEX.md`, `.agent/summaries/sdk.md`, `.cursor/rules/400-sdk-release.mdc`, `frontend/sdk/typescript/package.json`, `frontend/sdk/python/pyproject.toml`, `frontend/sdk/java-logback/pom.xml`, and SDK READMEs/examples.
3. **Expected verification tier:** Tier 2 for SDK-local audit/fixes. Use Tier 0 if only editing this packet or other agent docs.
4. **Commands to avoid:** no publish/deploy commands, no full backend Maven, no all-SDK checks after touching only one SDK, no production/database/GitHub destructive commands.
5. **Concrete next patch plan:** audit SDK metadata and docs first; patch only package metadata/README/example inaccuracies needed for publish readiness; record blockers and exact SDK-local verification commands.

## General Execution Rules

- Use `.agent/CONTEXT_INDEX.md` and summaries before loading full source.
- Read full source files only when needed for the current phase.
- Classify every task by verification tier before running commands.
- Keep diffs small and page/package-specific.
- Do not run publish, deploy, production, database, destructive git, or real webhook commands.
- Do not claim registry packages are live until verified.
- Stop and route through planner/core-data if OpenAPI contract, schema, backend behavior, and frontend behavior must change together.

## Phase 1 - SDK Release Readiness Audit

Scope:
- `frontend/sdk/typescript/**`
- `frontend/sdk/python/**`
- `frontend/sdk/java-logback/**`
- SDK READMEs/examples
- SDK package metadata files

Check:
- package names, versions, license metadata, homepage/docs URLs, repository metadata
- published install commands versus local install commands
- example correctness
- build/package outputs and `.gitignore` coverage for artifacts
- whether docs claim packages are already published
- root repo license alignment with SDK package licenses before publishing

Verification:
- TypeScript SDK: only build/typecheck/package inspection in `frontend/sdk/typescript/`.
- Python SDK: only build/import/package inspection in `frontend/sdk/python/`.
- Java SDK: Maven only inside `frontend/sdk/java-logback/`.
- Do not run full backend Maven unless SDK changes require API/backend verification.

Output:
- release blockers
- package metadata fixes
- local verification commands
- publish-readiness checklist

## Phase 2 - Docs Accuracy Pass

Scope:
- `frontend/getting-started.html`
- `frontend/docs.html`
- `frontend/resources/openapi.json`
- `frontend/js/docs.js`
- `frontend/js/examples.js`
- SDK examples if referenced by docs

Check:
- Getting Started matches current auth model.
- Deprecated/local bootstrap endpoints are clearly labeled.
- Production dashboard flow is separate from local/demo flow.
- Ingestion token usage is correct.
- Alert webhook usage is not confused with ingestion token usage.
- SDK install commands are honest about unpublished packages.
- Raw API examples match OpenAPI paths, headers, request bodies, and response behavior.
- Docs do not encourage unsafe browser-side token usage unless scoped browser telemetry is explicitly supported.

Verification:
- No Maven for docs-only HTML/JS copy changes.
- Use static review/manual browser checklist.
- If OpenAPI contract changes are required, stop and route through planner/core-data.

Output:
- docs issues found
- exact pages/files changed
- manual test checklist
- remaining doc gaps

## Phase 3 - Security Review

Scope:
- frontend token display/state handling
- dashboard/demo flows
- webhook destination forms
- `frontend/js/state.js`
- `frontend/js/rest-service.js`
- demo/dashboard JS files
- backend security/config files only if needed

Check:
- raw ingestion tokens are shown once only
- raw ingestion tokens are not stored in browser storage
- full webhook URLs are not stored in browser storage
- full webhook URLs are not logged or displayed after creation
- demo bypass is clearly marked and safe for production deployment
- CORS is exact-origin, not wildcard
- Swagger/OpenAPI exposure matches deployment intent
- analytics/external scripts fit the privacy/security posture
- no secrets are committed or referenced in public config

Verification:
- Use grep/static inspection where useful.
- Do not run backend Maven unless backend security code changed.
- Do not touch production resources.

Output:
- security findings by severity
- required fixes before deploy
- optional hardening improvements
- verification checklist

## Phase 4 - Frontend UX Cleanup

Scope:
- static frontend only
- no frameworks
- no build tooling
- no broad redesign unless planned first

Focus:
- landing page clarity
- Getting Started flow
- demo-to-dashboard handoff
- copy clarity
- button hierarchy
- empty/error/loading states
- mobile layout
- code snippet usability
- docs navigation
- token/webhook safety messaging

Verification:
- manual browser checklist
- responsive viewport checks
- no Maven unless backend touched

Output:
- UX issues
- prioritized fixes
- implemented small diffs
- remaining redesign backlog

## Phase 5 - SEO/GEO Pass

Do this only after docs and UX are accurate.

Scope:
- page titles, meta descriptions, OpenGraph/Twitter tags, headings, internal links
- FAQ-style sections and answer-oriented docs copy
- sitemap/robots if appropriate
- product positioning copy

Target concepts:
- lightweight log alerting
- real-time error alerts
- Slack error alerts
- Discord error alerts
- webhook log ingestion
- small app observability
- simple log monitoring for indie developers
- no log warehouse required

Rules:
- Do not keyword stuff.
- Do not make claims the product does not support.
- Do not overstate SDK availability before publishing.
- Prioritize clear technical copy useful to humans and AI retrieval.

Verification:
- static HTML review
- no backend build

## Phase 6 - Publish Preparation

Only after SDKs pass local checks and docs are corrected.

Prepare:
- npm publish checklist
- PyPI publish checklist
- Maven Central checklist
- release notes
- version tags
- package README checks
- dry-run/package inspection commands

Do not publish without explicit user approval.
