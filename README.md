# Log Stream Service

A lightweight Spring Boot microservice for real-time application log ingestion and alerting.

Log Stream Service lets external applications send structured log events through a webhook-style ingestion endpoint. Logs are processed in real time and are not stored long term. The service can aggregate repeated error logs and send summarized alerts to Slack or Discord using configured webhook destinations.

## Features

* Register apps/log sources for authenticated users
* Generate one-time visible ingestion tokens for registered apps
* Validate ingestion tokens for app-level authentication
* Accept structured log event requests
* Configure Slack or Discord webhook alert destinations
* Send test alerts to configured webhook destinations
* Aggregate matching error logs to prevent alert spam
* Send summarized error alerts once per aggregation window
* Database storage for users, apps, app tokens, and alert destinations
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
| `ALLOWED_ORIGINS` | No (has deploy default) | includes `https://prairie-log-api.netlify.app` | Comma- or pipe-separated CORS origins for the static frontend |
| `ALERTS_ENABLED` | No | `true` | Enable alert aggregation and dispatch |
| `ALERTS_AGGREGATION_WINDOW_MS` | No | `60000` | Error aggregation window (1 minute) |
| `ALERTS_MAX_MESSAGES_PER_ALERT` | No | `5` | Max sample messages included per alert |
| `JWT_ENABLED` | No | `false` | Enable JWT validation for `/secured/*` routes |
| `JWT_ISSUER_URIS` | If JWT enabled | — | Comma-separated trusted JWT issuer URIs |
| `AUTH_ENABLED` | Prod | `false` | Require bearer auth on management API routes under `/api/v1/**` |
| `FIREBASE_PROJECT_ID` | When auth enabled for real users | none | Firebase project id used to validate dashboard ID tokens |
| `DEMO_BYPASS_ENABLED` | Optional | `false` | Enable demo JWT creation through `/api/v1/auth/demo-session` |
| `DEMO_BYPASS_EMAIL` | When demo enabled | `admin@email.com` | Only email allowed to use the demo bypass |
| `DEMO_JWT_SECRET` | Recommended when demo enabled | ephemeral startup secret | HMAC secret for server-issued demo JWTs |
| `APP_QUOTAS_MAX_APPS_PER_USER` | No | `10` | Max active, non-deleted apps per user |
| `APP_QUOTAS_MAX_ACTIVE_TOKENS_PER_APP` | No | `5` | Max non-revoked ingestion tokens per app |
| `APP_RATE_LIMIT_MANAGEMENT_RPM` | No | `60` | In-memory management API requests per minute per IP/user |
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

Production note: set `AUTH_ENABLED=true` for the dashboard and management API. `POST /api/v1/log-events` and `/api/v1/ingestion-tokens/session` continue to use `X-Ingestion-Token` only. Set `JWT_ENABLED=true` only when legacy `/secured/*` routes are needed.

### Firebase dashboard auth

1. Create a Firebase project.
2. In Firebase Console, enable Authentication -> Sign-in method -> Email/Password -> Email link sign-in.
3. In Firebase Console, add authorized domains for the deployed frontend, plus `localhost` and `127.0.0.1` for local preview.
4. On the backend host, set `AUTH_ENABLED=true` and `FIREBASE_PROJECT_ID=<your Firebase project id>`.
5. In the frontend, set the public Firebase web app values in `frontend/js/env.js` for deployment, or copy `frontend/js/env.local.example.js` to `frontend/js/env.local.js` for local preview.

Backend-only values:

```bash
AUTH_ENABLED=true
FIREBASE_PROJECT_ID=your-firebase-project-id
ALLOWED_ORIGINS=https://your-frontend.example.com
```

Frontend public values:

```js
FIREBASE: {
  apiKey: "your-web-api-key",
  authDomain: "your-firebase-project-id.firebaseapp.com",
  projectId: "your-firebase-project-id",
  appId: "your-web-app-id",
  messagingSenderId: "your-sender-id"
}
```

`FIREBASE_PROJECT_ID` on the backend must match `CONFIG.FIREBASE.projectId` on the frontend. The backend validates Firebase ID tokens against that project id as the token issuer and audience.

For the public demo, set `DEMO_BYPASS_ENABLED=true`, `DEMO_BYPASS_EMAIL=admin@email.com`, and a strong server-only `DEMO_JWT_SECRET`.

### Frontend

Static files live in `frontend/`. Deploy the folder to any static host (Netlify, S3, nginx, etc.).

`frontend/js/env.js` defaults to the production API (`https://log-stream-service.onrender.com`). Before publishing to a different backend, update `CONFIG.API_BASE_URL` there.

For local static preview against a local Spring Boot instance, copy `frontend/js/env.local.example.js` to `frontend/js/env.local.js` and load it after `env.js` on the pages you are testing.

Ensure `ALLOWED_ORIGINS` on the backend includes your frontend URL exactly.

No npm build step is required.

### Pre-deploy checklist

- [ ] PostgreSQL reachable from the backend host
- [ ] `DB_*` credentials set via secrets / host env
- [ ] `ALLOWED_ORIGINS` matches deployed frontend URL(s)
- [ ] `frontend/js/env.js` points at deployed backend
- [ ] `mvn clean package -DskipTests` succeeds
- [ ] Docker image builds if you use containers
- [x] Swagger UI disabled by default (`SWAGGER_UI_ENABLED=false`)

## API Flow

Typical setup flow:

1. Sign in on the dashboard, or use the demo identity when enabled
2. Register an app/log source for the signed-in user
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

## License

This repository is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE): you may use, copy, and modify the service for noncommercial purposes, but you may not sell it or use it commercially.

**Exception:** the client SDKs under `frontend/sdk/` (TypeScript, Python, Java Logback) are licensed under the [MIT License](frontend/sdk/typescript/LICENSE) so that any application — including commercial ones — can embed them to send logs to a PrairieLog instance. Each SDK directory contains its own MIT `LICENSE` file.

## Future Plans

* [ ] Add user authentication and role-based access control
* [ ] Add configurable alert settings per app
* [ ] Add optional AI-assisted error analysis and remediation suggestions
* [ ] Add better observability around alert delivery failures
* [ ] Add persistent audit records for alert delivery attempts
* [ ] Add a lightweight dashboard or CLI client
