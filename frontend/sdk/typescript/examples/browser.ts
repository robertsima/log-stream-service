import { PrairieLogClient } from "../src/index";

export const prairieLog = new PrairieLogClient({
  apiUrl: "https://log-stream-service.onrender.com",
  ingestionToken: "YOUR_INGESTION_TOKEN",
  defaultLogger: "browser-client",
  storageKey: "prairielog-buffer"
});

prairieLog.installGlobalHandlers();

void prairieLog.info("Browser client started", {
  userAgent: navigator.userAgent
});
