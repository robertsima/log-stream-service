# Log Stream Service

A lightweight Spring Boot microservice for real-time application log ingestion and alerting.

Log Stream Service lets external applications send structured log events through a webhook-style ingestion endpoint. Logs are processed in real time and are not stored long term. The service can aggregate repeated error logs and send summarized alerts to Slack or Discord using configured webhook destinations.

## Features

* Register apps/log sources
* Generate one-time visible ingestion tokens for registered apps
* Validate ingestion tokens for app-level authentication
* Accept structured log event requests
* Configure Slack or Discord webhook alert destinations
* Send test alerts to configured webhook destinations
* Aggregate matching error logs to prevent alert spam
* Send summarized error alerts once per aggregation window
* Database-backed storage for users, apps, app tokens, and alert destinations
* OpenAPI contract-first API generation
* Liquibase-managed database schema
* Unit and integration test coverage

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
* Error alerts are aggregated in memory to avoid spamming channels and offload server
* Small intentional real-time log relay and alerting for small apps and services

## Tech Stack

* Java 17
* Spring Boot
* PostgreSQL
* Liquibase
* OpenAPI Generator
* Maven
* JUnit
* Testcontainers
* Docker/Podman

## Maven Commands

Run tests:

```bash
mvn clean test
```

Compile the project:

```bash
mvn clean compile
```

Package the application:

```bash
mvn clean package
```

Run locally with Maven:

```bash
mvn spring-boot:run
```

## Docker Build

Build the application image:

```bash
docker build -t log-stream-service:latest .
```

Run the image:

```bash
docker run --name log-stream-service -p 8080:8080 log-stream-service:latest
```

## Podman Build

Build the application image:

```bash
podman build -t log-stream-service:latest .
```

If using a Kubernetes-style Podman YAML file:

```bash
podman play kube podman.yaml
```

## Environment Variables

The service supports environment-based configuration for local and containerized runs.

```bash
DB_URL=jdbc:postgresql://localhost:5432/appdb
DB_USER=appuser
DB_PASSWORD=apppassword
```

Example:

```bash
DB_URL=jdbc:postgresql://localhost:5432/appdb \
DB_USER=appuser \
DB_PASSWORD=apppassword \
mvn spring-boot:run
```

## API Flow

Typical setup flow:

1. Create a user
2. Register an app/log source
3. Generate an ingestion token for the app
4. Configure a Slack or Discord alert destination
5. Send logs to the ingestion endpoint using the generated token
6. Receive real time aggregated error alerts through configured webhooks

## MVP Status

* [x] Database schema for users, apps, app tokens, and alert destinations
* [x] App/source registration
* [x] Ingestion token generation
* [x] Token-based ingestion authentication
* [x] Webhook-style log ingestion endpoint
* [x] Structured log event contract
* [x] Discord webhook integration
* [x] Slack webhook integration
* [x] Test alert endpoint
* [x] Error log aggregation
* [x] Aggregated Slack/Discord alert dispatching
* [x] Unit and integration test coverage

## Future Plans

* [ ] Add user authentication and role-based access control
* [ ] Add configurable alert settings per app
* [ ] Add optional AI-assisted error analysis and remediation suggestions
* [ ] Add better observability around alert delivery failures
* [ ] Add persistent audit records for alert delivery attempts
* [ ] Add a lightweight dashboard or CLI client
