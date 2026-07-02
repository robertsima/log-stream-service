import { ErrorHandler, Injectable, Provider } from "@angular/core";
import { PrairieLogClient } from "../src/index";

export const prairieLog = new PrairieLogClient({
  apiUrl: "https://log-stream-service.onrender.com",
  ingestionToken: "YOUR_INGESTION_TOKEN",
  defaultLogger: "angular-client",
  storageKey: "prairielog-buffer"
});

@Injectable()
export class PrairieLogErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    void prairieLog.captureException(error, {
      source: "Angular ErrorHandler"
    });
    console.error(error);
  }
}

export const prairieLogErrorHandlerProvider: Provider = {
  provide: ErrorHandler,
  useClass: PrairieLogErrorHandler
};
