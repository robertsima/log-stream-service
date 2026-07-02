// Angular core error-handler hook
import { ErrorHandler, Injectable } from '@angular/core';
import { prairieLog } from './prairie-log.service';

// Wire this in AppModule providers: { provide: ErrorHandler, useClass: GlobalErrorHandler }
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    void prairieLog.captureException(error, {
      source: 'Angular GlobalErrorHandler'
    });

    // Keep Angular/browser console behavior for local debugging.
    console.error(error);
  }
}
