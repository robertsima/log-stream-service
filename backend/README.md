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

### 1. Dead now — safe to delete

- `src/main/java/com/logstream/config/KafkaStreamsConfig.java` — earlier draft of the Streams topology (topics + `centralLogStream`). Entire file is commented out; fully superseded by `KafkaConfig.java`. No references anywhere.
- `src/main/java/com/logstream/webhooks/AlertNotificationFormatter.java` — built Discord/Slack payload text (title/description/fields) from an `AlertBucket`. Only call sites left are commented-out `sendAggregatedAlert` methods in `DiscordWebhookSender`/`SlackWebhookSender`; the live `sendAnalyzedAlert` methods build payloads inline instead. Otherwise referenced only by its own test.
- `src/main/java/com/logstream/webhooks/AlertSummary.java` — DTO produced by `AlertNotificationFormatter.summarize(...)`. Same fate as above; no live producer or consumer.
- `src/main/java/com/logstream/service/alerting/BucketFingerprint.java` — SHA-256 fingerprint over an `AlertBucket`'s events. `BucketFingerprint.from(...)` is never called anywhere; `AlertAggregationServiceImpl.fingerprint(...)` builds its own string fingerprint inline instead. Only referenced via unused imports (e.g. in `AlertAnalysisServiceImpl`).
- `src/main/java/com/logstream/service/alerting/AlertTimeWindow.java` — computed first/last/span over a bucket's events. Only consumer is `AlertNotificationFormatter.summarize(...)` (itself dead above).
- `src/test/java/unit/com/logstream/service/cron/AlertFlushSchedulerTest.java` — 100% commented out; tests the dead `AlertFlushScheduler`.
- `src/test/java/unit/com/logstream/service/AlertDestinationServiceTest.java` — 100% commented out (342/342 lines).
- `src/test/java/unit/com/logstream/controller/AlertAnalysisControllerTest.java` — 100% commented out; also imports a stale `com.logstream.service.alerting.AlertTrigger` type that no longer exists at that path (the real `AlertTrigger` now lives in `com.logstream.domain.model`).
- `src/test/java/unit/com/logstream/webhooks/AlertNotificationFormatterTest.java` — live/passing, but exercises only the dead `AlertNotificationFormatter`/`AlertSummary` above, not any reachable production path.
- `AlertSenderServiceImpl.sendTest(...)` (`src/main/java/com/logstream/service/AlertSenderServiceImpl.java`) plus `DiscordWebhookSender.sendTest(...)` and `SlackWebhookSender.sendTest(...)` — dropped from the `AlertSenderService` interface (commented out there); the only caller was `AlertDestinationServiceImpl`'s commented-out `test()` body. Unreachable from any controller today; kept "alive" only by `AlertSenderServiceImplTest`'s two active `sendTest_*` tests. See related contract note in bucket 3 (`testAlertDestination`).
- `KafkaConsumerService.recentLogs` field (`src/main/java/com/logstream/service/KafkaConsumerService.java:41`, `Map<UUID, Deque<AlertTrigger>> recentLogs`) — declared, never read or written anywhere in the class. Leftover in-memory ring-buffer attempt; the Kafka Streams state store inside `AlertContextProcessor` now does this job.
- `alerts.enabled` (application.yml) — only read by the commented-out `AlertFlushScheduler` constructor.
- `alerts.aggregation-window-ms` (application.yml) — only read by the commented-out `AlertFlushScheduler`'s `@Scheduled` annotation.
- `alerts.analysis.prompt-preview-enabled` (application.yml) — only read by the commented-out `AlertAnalysisController` (see bucket 3 if that controller comes back).

### 2. Superseded but still referenced — needs refactor before deletion

- `src/main/java/com/logstream/service/AlertAggregationService.java` / `AlertAggregationServiceImpl.java` — in-memory `ConcurrentHashMap` of ERROR-log buckets. Still injected into and called from `LogEventServiceImpl.ingestLogEvent`/`ingestLogEventBatch` (the live synchronous `/api/v1/log-events` REST path — see bucket 3). Superseded by the Kafka Streams `AlertContextProcessor` state store (10-before/10-after buffering) on the async path. Since the scheduled flusher that used to drain these buckets (`AlertFlushScheduler`) is now commented out, buckets accumulate and are **never drained** — this is an unbounded-growth leak for any app still hitting the sync endpoint.
- `src/main/java/com/logstream/service/alerting/AlertBucket.java` — bucket model (`List<LogEventRequest>` per app/fingerprint) backing `AlertAggregationServiceImpl` above. Still constructed/populated there.
- `src/main/java/com/logstream/service/alerting/MessageNormalizer.java` — message normalization used by `AlertAggregationServiceImpl.normalize(...)`. Still called from that live-but-superseded chain.
- `alerts.max-messages-per-alert` (application.yml) — still injected via `@Value` into `DiscordWebhookSender`/`SlackWebhookSender` constructors, but the resulting `maxMessages` field is now only read by their commented-out `sendAggregatedAlert` methods. Dead in practice, but tied to the same cleanup as the two classes above.

### 3. Needs decision — contract-covered or ambiguous

- `POST /api/v1/apps/{appId}/alert-destinations/{destinationId}/send-analyzed-alert` (`AlertDestinationsController.sendAnalyzedAlert`, `AlertDestinationService.sendAnalyzedAlert(...)` — both overloads, `AlertDestinationServiceImpl`'s empty-body implementations) — operation `sendAnalyzedAlert` is still documented in `src/main/resources/openapi/openapi.yaml` (line ~402). Both service overloads are currently no-ops; the real logic is commented out at the bottom of `AlertDestinationServiceImpl`. The Kafka path now delivers analyzed alerts directly via `KafkaConsumerService.analyzedEventsListener` → `AlertSenderService.sendAnalyzedAlert(AlertDestination, AlertAnalysisResponse)`, bypassing this endpoint entirely. Decide: drop the endpoint from the contract and delete the stub, or reimplement it against the new `AlertTrigger`/`AlertAnalysisResponse` model.
- `POST /api/v1/apps/{appId}/alert-destinations/{destinationId}/test` (`AlertDestinationsController.testAlertDestination`, `AlertDestinationService.test(...)`) — operation `testAlertDestination` is still documented (openapi.yaml line ~381), but `AlertDestinationServiceImpl.test(...)` is now an empty no-op; the real implementation (calling `alertSenderService.sendTest(destination)`) is commented out, and `sendTest` was removed from the `AlertSenderService` interface entirely (see bucket 1). The endpoint currently returns 202 and sends nothing. Found while tracing bucket 1's `sendTest` findings — same root cause, not confirmed to be Kafka-caused vs. concurrent unrelated churn, but worth the same decision.
- `src/main/java/com/logstream/controller/AlertAnalysisController.java` and its endpoints (`operationId: analyzeAlertTrigger` at `/api/v1/alert-analysis/analyze`, `operationId: previewAlertPrompt`) — both still documented in openapi.yaml (lines ~558-616), but the controller implementing them is 100% commented out, so no bean is registered and these documented endpoints are currently unreachable. Decide: remove from the contract (analysis now happens automatically via the `alert-messages` → `analyzed-events` pipeline) or restore a controller wired to the new `AlertTrigger` model.
- `POST /api/v1/log-events` and `POST /api/v1/log-events/batch` (`LogEventsController`, `LogEventService`, `LogEventServiceImpl`) — still the primary documented ingestion endpoints, but openapi.yaml's own description of `/api/v1/kafka/log-events` calls itself "intended to replace" the synchronous endpoint. Internally `LogEventServiceImpl` now only feeds the orphaned `AlertAggregationService` (bucket 2) with no consumer, so accepted events are authenticated/logged but no longer produce any alert through this path. Decide the deprecation timeline for the sync endpoints vs. their `/api/v1/kafka/log-events*` counterparts.