# PrairieLog TypeScript Client

Dependency-free TypeScript client for sending browser, React, Angular, vanilla JS, and Node logs to PrairieLog.

```bash
npm install @prairielog/client
```

For local development from this repository, use `npm install ./frontend/sdk/typescript`.

```ts
import { PrairieLogClient } from "@prairielog/client";

const prairieLog = new PrairieLogClient({
  apiUrl: "https://log-stream-service.onrender.com",
  ingestionToken: "lss_live_...",
  defaultLogger: "web-app"
});

prairieLog.installGlobalHandlers();
prairieLog.info("Checkout loaded", { route: "/checkout" });
```

The client sends events to `POST /api/v1/kafka/log-events/batch`, the Kafka-backed ingestion pipeline that also drives alert aggregation, analysis, and Slack/Discord delivery. If the server answers HTTP 503 (Kafka integration disabled or broker unavailable), the client automatically falls back to the synchronous `POST /api/v1/log-events/batch` endpoint so logs are never lost — but that endpoint has no alerting side effect, so alerts are degraded until Kafka is back. The fallback is reported once per outage through `onError` (or `console.error` when no `onError` is configured).

The client generates `id` and `occurredAt` when omitted, normalizes common level aliases, retries transient failures with backoff, and keeps an in-memory queue while offline. In browsers, pass `storageKey` to persist the queue in `localStorage`.

```ts
const prairieLog = new PrairieLogClient({
  apiUrl: "https://log-stream-service.onrender.com",
  ingestionToken: "lss_live_...",
  defaultLogger: "react-client",
  batchSize: 25,
  flushIntervalMs: 5000,
  storageKey: "prairielog-buffer"
});
```

Angular can provide a configured `PrairieLogClient` through dependency injection; Node can call `installNodeHandlers()` for uncaught exceptions and unhandled promise rejections.

Examples live in `examples/`:

- `browser.ts` for vanilla browser or React entrypoints
- `angular-provider.ts` for Angular `ErrorHandler`
- `node.ts` for Node services

Build with `npm run build` after installing dev dependencies.
