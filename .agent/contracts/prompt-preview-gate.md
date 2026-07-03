# Contract: prompt-preview-gate

**Status:** done  
**Created:** 2026-07-03  
**Surfaces:** [ ] core-data [x] backend [x] frontend [ ] integration [x] infra

## Goal

Disable exposure of alert-analysis prompt internals in production by gating prompt preview behind an explicit backend configuration flag and hiding the dashboard preview control unless the static frontend is explicitly configured for it. Keep normal alert analysis and the production demo flow working.

## Acceptance criteria

- [x] The dashboard no longer exposes the Preview Prompt control by default.
- [x] The backend preview endpoint refuses requests unless prompt preview is explicitly enabled.
- [x] The Analyze flow continues to work in production/demo.
- [x] Dashboard analysis output hides token usage and raw model JSON unless prompt-preview diagnostics are explicitly enabled.
- [x] API/frontend docs describe prompt preview as disabled by default and keep demo bypass configuration intact.
- [x] Verification covers backend compile and focused behavior where practical.

## Out of scope

- Changing alert analysis output, model prompts, or OpenAI model configuration.
- Removing the preview endpoint from OpenAPI.
- Adding roles/admin authorization.
- Changing demo bypass behavior or Firebase auth behavior.

---

## [core-data] Data and API contract

- OpenAPI paths/schemas: `/api/v1/alert-analysis/preview` remains, but description documents that it is disabled by default and intended for trusted local/dev diagnostics.
- Entity or migration changes: none.
- Breaking changes: callers receive an error when preview is disabled.

## [backend] Service behavior

- Endpoints / services touched: `AlertAnalysisController.previewAlertPrompt`.
- Business rules: prompt preview requires `alerts.analysis.prompt-preview-enabled=true`; default is false.
- Error responses: when disabled, return a client-visible forbidden/not-allowed error without returning system prompt, user prompt, token estimate, or prompt text.

## [frontend] UI and client behavior

- Pages / scripts: dashboard page/script and static config.
- User-visible copy: hide Preview Prompt by default; when enabled for local/dev, keep existing preview behavior. Hide token usage and raw model JSON in normal production/demo analysis output.
- API calls: do not call preview endpoint unless `CONFIG.ALERT_ANALYSIS_PROMPT_PREVIEW_ENABLED === true`.

## [integration] External systems

- Webhook / delivery behavior: unchanged. Analysis-based Slack/Discord alerts remain the default alert path.
- Test strategy (no real webhooks in CI): no webhook calls.

## [infra] Deploy and config

- Env vars: add `ALERT_ANALYSIS_PROMPT_PREVIEW_ENABLED` default false.
- CORS / origins: unchanged.
- Host-specific notes: production/demo should leave prompt preview disabled; local/dev may enable explicitly.

---

## Sign-off

| Step | Owner | Done |
|------|-------|------|
| Planner draft complete | planner | [x] |
| Surfaces acknowledged | router | [x] |
| Implementation complete | implementer(s) | [x] |
| Reviewer pass | reviewer | [x] |

## Reviewer notes

- Pass: diff matches contract. Prompt preview remains in OpenAPI but is documented as disabled by default and returns 403 unless explicitly enabled.
- Pass: production/demo dashboard hides the button through committed static config and does not wire the preview API call.
- Pass: normal analysis remains available; only preview/debug display is gated.
- Verified: `.\mvnw.cmd compile`; `.\mvnw.cmd test "-Dtest=AlertAnalysisControllerTest,AlertFlushSchedulerTest" "-Dmaven.compiler.forceJavacCompilerUse=true"`.
