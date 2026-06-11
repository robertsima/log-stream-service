# Log Stream Service

A lightweight Spring Boot microservice for real-time application log ingestion and alerting.

Log Stream Service lets external applications send structured log events through a webhook-style ingestion endpoint. Logs are processed in real time and are not stored long term. The service can aggregate repeated error logs and send summarized alerts to Slack or Discord using configured webhook destinations.

## Features

* Register apps/log sources and users
* Generate one-time visible ingestion tokens for registered apps
* Validate ingestion tokens for app-level authentication
* Accept structured log event requests
* Configure Slack or Discord webhook alert destinations
* Send test alerts to configured webhook destinations
* Aggregate matching error logs to prevent alert spam
* Send summarized error alerts once per aggregation window
* Daabase storage for users, apps, app tokens, and alert destinations
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

* Java 25
* Spring Boot 4
* PostgreSQL
* Liquibase
* OpenAPI Generator
* Maven
* JUnit
* Testcontainers
* Docker/Podman

## Swagger UI (local dev only)

Swagger UI and `/v3/api-docs` are **disabled by default** for deployment. The static frontend ships its own OpenAPI spec at `frontend/resources/openapi.json`.

To enable locally:

```bash
SWAGGER_UI_ENABLED=true mvn spring-boot:run
```

Then open `http://localhost:8080/swagger-ui/index.html`.

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

The service is configured through environment variables for deployment.

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | Yes (prod) | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DB_USER` | Yes (prod) | `appuser` | Database username |
| `DB_PASSWORD` | Yes (prod) | `apppassword` | Database password |
| `PORT` | No | `8080` | HTTP server port |
| `ALLOWED_ORIGINS` | Yes (prod) | `http://localhost:5500,http://127.0.0.1:5500` | Comma-separated CORS origins for the static frontend |
| `ALERTS_ENABLED` | No | `true` | Enable alert aggregation and dispatch |
| `ALERTS_AGGREGATION_WINDOW_MS` | No | `60000` | Error aggregation window (1 minute) |
| `ALERTS_MAX_MESSAGES_PER_ALERT` | No | `5` | Max sample messages included per alert |
| `JWT_ENABLED` | No | `false` | Enable JWT validation for `/secured/*` routes |
| `JWT_ISSUER_URIS` | If JWT enabled | — | Comma-separated trusted JWT issuer URIs |
| `SWAGGER_UI_ENABLED` | No | `false` | Enable Swagger UI and `/v3/api-docs` (local dev only) |

Copy `.env.example` to your host or container environment and replace placeholder values.

Local example:

```bash
DB_URL=jdbc:postgresql://localhost:5432/appdb \
DB_USER=appuser \
DB_PASSWORD=apppassword \
ALLOWED_ORIGINS=http://localhost:5500,http://127.0.0.1:5500 \
mvn spring-boot:run
```

Docker example:

```bash
docker run --name log-stream-service -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/appdb \
  -e DB_USER=appuser \
  -e DB_PASSWORD=apppassword \
  -e ALLOWED_ORIGINS=https://your-frontend.example.com \
  log-stream-service:latest
```

## Deployment

### Backend

1. Provision **PostgreSQL** (external managed database).
2. Set environment variables from `.env.example` (never commit real credentials).
3. Build and run:

```bash
cd backend
mvn clean package -DskipTests
java -jar target/*.jar
```

Or with Docker (from `backend/`):

```bash
docker build -t log-stream-service:latest .
```

Liquibase runs on startup and applies schema migrations to `DB_URL`.

MVP note: `/api/v1/**` is open without user login. Set `JWT_ENABLED=true` only when `/secured/*` routes are needed.

### Frontend

Static files live in `frontend/`. Deploy the folder to any static host (Netlify, S3, nginx, etc.).

Before publishing:

1. Copy `frontend/js/config.example.js` to `frontend/js/config.js` (or edit `config.js` in place).
2. Set `API_BASE_URL` to your deployed backend URL (HTTPS in production).
3. Ensure `ALLOWED_ORIGINS` on the backend includes your frontend URL exactly.

No npm build step is required.

### Pre-deploy checklist

- [ ] PostgreSQL reachable from the backend host
- [ ] `DB_*` credentials set via secrets / host env
- [ ] `ALLOWED_ORIGINS` matches deployed frontend URL(s)
- [ ] `frontend/js/config.js` points at deployed backend
- [ ] `mvn clean package -DskipTests` succeeds
- [ ] Docker image builds if you use containers
- [x] Swagger UI disabled by default (`SWAGGER_UI_ENABLED=false`)

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
* [x] Static developer portal (vanilla HTML/JS)
* [x] Deployment-ready env-based configuration

## Future Plans

* [ ] Add user authentication and role-based access control
* [ ] Add configurable alert settings per app
* [ ] Add optional AI-assisted error analysis and remediation suggestions
* [ ] Add better observability around alert delivery failures
* [ ] Add persistent audit records for alert delivery attempts
* [ ] Add a lightweight dashboard or CLI client
