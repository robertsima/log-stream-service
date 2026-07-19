# PrairieLog API Backend

This subfolder contains file perinent to the backend service

07/16/2026
- Working on decoupling aggregation/alerting logic from backend service to kafka
- Using a toggle "KAFKA_INTEGRATION_TOGGLE = false" while changes are being made
- Marking files for deletion by using "FLAG - DELETE" after integration

# Local test token 
lss_live_vsQ086T2bWiVB7ohCNDWysGRLT3mlFgZ

## Post-Kafka cleanup candidates (2026-07-19)

Alerting moved to Kafka: `KafkaConfig` (topics + `centralLogStream`/`AlertContextProcessor` topology) produces `AlertTrigger`s to `alert-messages`; `KafkaConsumerService` consumes `alert-messages` → `AlertAnalysisService` → `analyzed-events` → resolves `AlertDestination`s directly → `AlertSenderService`/webhook senders. This left the old in-process aggregation/analysis path stranded. Findings below are grouped by how safe they are to remove. Verified by tracing references, not just grepping for the class name (commented-out and test-only references don't count as "used").

### 1. Dead now — safe to delete — done 2026-07-19

- ~~`src/main/java/com/logstream/config/KafkaStreamsConfig.java`~~ — deleted.
- ~~`src/main/java/com/logstream/webhooks/AlertNotificationFormatter.java`~~ — deleted.
- ~~`src/main/java/com/logstream/webhooks/AlertSummary.java`~~ — deleted.
- ~~`src/main/java/com/logstream/service/alerting/BucketFingerprint.java`~~ — deleted; removed the now-stale import from `AlertAnalysisServiceImpl`.
- ~~`src/main/java/com/logstream/service/alerting/AlertTimeWindow.java`~~ — deleted; removed the now-stale import from `AlertAnalysisServiceImpl`.
- ~~`src/test/java/unit/com/logstream/service/cron/AlertFlushSchedulerTest.java`~~ — deleted.
- ~~`src/test/java/unit/com/logstream/service/AlertDestinationServiceTest.java`~~ — deleted.
- ~~`src/test/java/unit/com/logstream/controller/AlertAnalysisControllerTest.java`~~ — deleted.
- ~~`src/test/java/unit/com/logstream/webhooks/AlertNotificationFormatterTest.java`~~ — deleted.
- ~~`AlertSenderServiceImpl.sendTest(...)`, `DiscordWebhookSender.sendTest(...)`, `SlackWebhookSender.sendTest(...)`~~ — removed. `AlertSenderServiceImplTest` deleted too: its only two active tests exercised `sendTest`, and with that gone the file had no other live coverage. `DiscordWebhookSender`'s now-orphaned private `safe(...)` helper (only caller was `sendTest`) removed as well. `testAlertDestination` decision from bucket 3 unchanged — still a no-op endpoint pending a contract decision.
- ~~`KafkaConsumerService.recentLogs` field~~ — removed, along with the now-unused `ConcurrentHashMap`/`ConcurrentLinkedDeque` imports it left behind.
- ~~`alerts.enabled`, `alerts.aggregation-window-ms`, `alerts.analysis.prompt-preview-enabled`~~ (application.yml) — removed along with `alerts.max-messages-per-alert` as part of bucket 2 (the whole `alerts:` block is gone).

### 2. Superseded but still referenced — done 2026-07-19

- `LogEventServiceImpl.ingestLogEvent`/`ingestLogEventBatch` no longer call into alert aggregation; the `AlertAggregationService` constructor dependency was removed. The sync `/api/v1/log-events` REST path (bucket 3) is otherwise unchanged — still authenticates, rate-limits, normalizes, and logs exactly as before.
- ~~`src/main/java/com/logstream/service/AlertAggregationService.java` / `AlertAggregationServiceImpl.java`~~ — deleted, along with the now-dead `AlertAggregationServiceTest.java`.
- ~~`src/main/java/com/logstream/service/alerting/AlertBucket.java`~~ — deleted.
- ~~`src/main/java/com/logstream/service/alerting/MessageNormalizer.java`~~ — deleted.
- ~~`src/main/java/com/logstream/service/cron/AlertFlushScheduler.java`~~ — deleted (was already 100% commented out).
- ~~`alerts.max-messages-per-alert`~~ (application.yml) — key removed; the `@Value`-injected `maxMessages` constructor param/field removed from `DiscordWebhookSender` and `SlackWebhookSender` (both now take just a `RestClient.Builder`).
- `LogEventServiceTest.java` updated to match the new `LogEventServiceImpl` constructor (no more `AlertAggregationService` mock/verifications); all 9 tests still pass.

### 3. Needs decision — contract-covered or ambiguous

Decision made 2026-07-19: "we update the openapi contracts to reflect whatever current functionality is doing - app should still be contract first." Executed contract-first (spec edits, then regenerate, then delete the now-contractless Java):

- ~~`POST /api/v1/apps/{appId}/alert-destinations/{destinationId}/send-analyzed-alert`~~ — operation removed from `openapi.yaml` and `frontend/resources/openapi.json`. Deleted `AlertDestinationsController.sendAnalyzedAlert`, both `AlertDestinationService.sendAnalyzedAlert(...)` overloads (interface + empty `AlertDestinationServiceImpl` bodies), and the commented-out reference block at the bottom of that file. Pruned the now-fully-unreferenced `SendAnalyzedAlertRequest` schema (no live `com.logstream.generated.model` usage after the controller method was deleted).
- ~~`POST /api/v1/apps/{appId}/alert-destinations/{destinationId}/test`~~ — operation removed from both specs. Deleted `AlertDestinationsController.testAlertDestination` and `AlertDestinationService.test(...)` (interface + empty impl body + commented block).
- ~~`src/main/java/com/logstream/controller/AlertAnalysisController.java`~~ — file deleted (was 100% commented out, no bean). Removed `previewAlertPrompt` (`/api/v1/alert-analysis/preview`) and `analyzeAlertBucket` (`/api/v1/alert-analysis/analyze`) from both specs, and the now-orphaned `Alert Analysis` tag. Pruned `AlertBucketAnalysisRequest` and `AlertAnalysisPreviewResponse` schemas (fully unreferenced, no live Java usage). Kept `AlertAnalysisResponse` and `TokenUsage` in both specs' `components/schemas` — despite no operation referencing them anymore, `com.logstream.generated.model.AlertAnalysisResponse`/`TokenUsage` are live production types built by `KafkaConsumerService` and consumed by the webhook senders on the Kafka alerting path; verified with a clean `mvn clean compile` that openapi-generator (spring generator, `interfaceOnly`) still emits both model classes purely from their `components/schemas` declaration, with no path reference required.
- `POST /api/v1/log-events` and `POST /api/v1/log-events/batch` — kept (live, functional). Descriptions in both specs rewritten to state plainly what they do: authenticate/rate-limit/normalize/log only, no alerting side effect, alerting flows exclusively through `/api/v1/kafka/log-events(/batch)`. No deprecation added, per instruction.

Frontend JS still calls all four removed endpoints — **not edited, flagged for a decision**:
- `frontend/js/rest-service.js:316` `testAlertDestination(...)` → `POST .../alert-destinations/{id}/test`
- `frontend/js/rest-service.js:329` `sendAnalyzedAlert(...)` → `POST .../alert-destinations/{id}/send-analyzed-alert`
- `frontend/js/rest-service.js:351` `previewAlertAnalysis(...)` → `POST /api/v1/alert-analysis/preview`
- `frontend/js/rest-service.js:364` `analyzeAlertBucket(...)` → `POST /api/v1/alert-analysis/analyze`
- Call sites: `frontend/js/dashboard.js:619` (`testAlertDestination`), `frontend/js/dashboard.js:940` (`previewAlertAnalysis`), `frontend/js/dashboard.js:974` (`analyzeAlertBucket`), `frontend/js/dashboard.js:983` (`sendAnalyzedAlert`), `frontend/js/demo.js:557` (`testAlertDestination`). UI wiring for the analysis panel/form lives in `frontend/dashboard.html:249-263` and `frontend/js/dashboard.js:274,1094`.

These calls will now 404 against the live backend. This is a real UI regression risk, not just a spec cleanup — needs a decision on whether to cut this UI surface (test-alert button, send-analyzed-alert delivery, analysis preview/analyze panel) or restore server-side support before the frontend spec/backend spec are deployed together.