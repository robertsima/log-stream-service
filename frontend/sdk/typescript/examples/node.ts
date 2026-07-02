/// <reference types="node" />

import { PrairieLogClient } from "../src/index";

const prairieLog = new PrairieLogClient({
  apiUrl: process.env.PRAIRIELOG_API_URL ?? "https://log-stream-service.onrender.com",
  ingestionToken: process.env.PRAIRIELOG_INGESTION_TOKEN ?? "",
  defaultLogger: "node-service",
  batchSize: 50
});

prairieLog.installNodeHandlers();

void prairieLog.info("Node service started", {
  pid: process.pid
});
