# Log Stream Service

A lightweight Spring Boot microservice for real-time application log ingestion and alerting.

Log Stream Service lets external applications send log events through a webhook-style ingestion endpoint. Logs are processed in real time, printed/relayed by the service, and are not stored long term. The service can aggregate repeated error logs and send summarized alerts to Slack or Discord using configured webhook destinations.

## MVP

* [x] Create database schema for users, apps, app tokens, and alert destinations
* [x] Register apps/log sources
* [x] Generate one-time visible ingestion tokens for registered apps
* [x] Validate ingestion tokens for app-level authentication
* [x] Expose webhook-style log ingestion endpoint
* [x] Accept structured log event requests
* [x] Print incoming log events for real-time visibility
* [x] Configure Discord webhook alert destinations
* [x] Configure Slack webhook alert destinations
* [x] Send test alerts to configured webhook destinations
* [x] Aggregate matching error logs to prevent alert spam
* [x] Send summarized error alerts once per aggregation window

## Current Behavior

Applications send logs to the service using an ingestion token:

```http
POST /api/v1/log-events
X-Ingestion-Token: lss_live_example_token
Content-Type: application/json
```

Example payload:

```json
{
  "id": "01HZA5C124",
  "level": "ERROR",
  "message": "Failed to process payment.",
  "occurredAt": "2026-06-08T18:30Z",
  "logger": "com.example.PaymentService",
  "traceId": "abc-123"
}
```

When matching `ERROR` logs are received, the service groups them over a short time window and sends one summarized alert to configured Slack or Discord webhook destinations.

## Design Notes

* Logs are not persisted long term
* App tokens are used for lightweight ingestion authentication
* Slack and Discord webhooks are stored as alert destinations
* Error alerts are aggregated in memory to avoid spamming channels
* The service is intentionally small and focused on real-time log relay and alerting

## Future Plans

* [ ] Add user authentication and role-based access control
* [ ] Add configurable alert settings per app
* [ ] Add optional AI-assisted error analysis and remediation suggestions
* [ ] Add better observability around alert delivery failures
* [ ] Add integration tests for ingestion, token validation, and alert dispatching
