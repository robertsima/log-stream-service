import { PrairieLogClient } from "@prairielog/client";

export const prairieLog = new PrairieLogClient({
  apiUrl: "__API_BASE_URL__",
  ingestionToken: "YOUR_INGESTION_TOKEN",
  defaultLogger: "react-client",
  storageKey: "prairielog-buffer"
});

prairieLog.installGlobalHandlers({
  metadata: {
    runtime: "react"
  }
});

export function logCheckoutLoaded(route: string): void {
  void prairieLog.info("Checkout loaded", { route });
}
