// Angular HTTP client + DI imports
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';

export type PrairieLogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface PrairieLogEvent {
  id: string;
  level: PrairieLogLevel;
  message: string;
  occurredAt: string;
  logger?: string;
  traceId?: string;
  spanId?: string;
  metadata?: Record<string, unknown>;
}

// Register once in root so any component can inject this service
@Injectable({
  providedIn: 'root'
})
export class PrairieLogService {
  // Point these at your PrairieLog API and app ingestion token
  private readonly apiUrl = 'https://your-prairielog-api.com/api/v1/log-events';
  private readonly ingestionToken = 'lss_live_replace_with_your_token';

  constructor(private readonly http: HttpClient) {}

  info(message: string, metadata?: Record<string, unknown>): void {
    this.send({
      level: 'INFO',
      message,
      logger: 'angular-client',
      metadata
    });
  }

  warn(message: string, metadata?: Record<string, unknown>): void {
    this.send({
      level: 'WARN',
      message,
      logger: 'angular-client',
      metadata
    });
  }

  error(
    error: unknown,
    metadata?: Record<string, unknown>,
    logger = 'angular-client'
  ): void {
    const normalized = this.normalizeError(error);

    this.send({
      level: 'ERROR',
      message: normalized.message,
      logger,
      metadata: {
        ...metadata,
        errorName: normalized.name,
        stack: normalized.stack
      }
    });
  }

  send(event: Partial<PrairieLogEvent> & Pick<PrairieLogEvent, 'level' | 'message'>): void {
    const payload: PrairieLogEvent = {
      id: crypto.randomUUID(),
      occurredAt: new Date().toISOString(),
      logger: 'angular-client',
      traceId: crypto.randomUUID(),
      ...event
    };

    // PrairieLog authenticates ingestion with this header
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'X-Ingestion-Token': this.ingestionToken
    });

    this.http.post<void>(this.apiUrl, payload, { headers }).subscribe({
      error: sendError => {
        // Do not call PrairieLog again here or you can create a loop.
        console.error('Failed to send log to PrairieLog', sendError);
      }
    });
  }

  private normalizeError(error: unknown): {
    name: string;
    message: string;
    stack?: string;
  } {
    if (error instanceof Error) {
      return {
        name: error.name,
        message: error.message,
        stack: error.stack
      };
    }

    return {
      name: 'UnknownError',
      message: String(error)
    };
  }
}
