// Angular core error-handler hook
import { ErrorHandler, Injectable } from '@angular/core';
import { PrairieLogService } from './prairie-log.service';

// Wire this in AppModule providers: { provide: ErrorHandler, useClass: GlobalErrorHandler }
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  constructor(private readonly prairieLogService: PrairieLogService) {}

  handleError(error: unknown): void {
    this.prairieLogService.error(error, {
      source: 'Angular GlobalErrorHandler'
    });

    // Keep Angular/browser console behavior for local debugging.
    console.error(error);
  }
}
