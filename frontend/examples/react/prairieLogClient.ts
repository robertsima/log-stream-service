// Lightweight client for sending log events from React apps
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

export interface PrairieLogClientConfig {
  apiUrl: string;
  ingestionToken: string;
  defaultLogger?: string;
}

export class PrairieLogClient {
  private readonly apiUrl: string;
  private readonly ingestionToken: string;
  private readonly defaultLogger: string;

  constructor(config: PrairieLogClientConfig) {
    this.apiUrl = config.apiUrl.replace(/\/$/, '');
    this.ingestionToken = config.ingestionToken;
    this.defaultLogger = config.defaultLogger || 'react-client';
  }

  info(message: string, metadata?: Record<string, unknown>): void {
    this.send({
      level: 'INFO',
      message,
      metadata
    });
  }

  warn(message: string, metadata?: Record<string, unknown>): void {
    this.send({
      level: 'WARN',
      message,
      metadata
    });
  }

  error(
    error: unknown,
    metadata?: Record<string, unknown>,
    logger = this.defaultLogger
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

  async send(
    event: Partial<PrairieLogEvent> & Pick<PrairieLogEvent, 'level' | 'message'>
  ): Promise<void> {
    const payload: PrairieLogEvent = {
      id: crypto.randomUUID(),
      occurredAt: new Date().toISOString(),
      logger: this.defaultLogger,
      traceId: crypto.randomUUID(),
      ...event
    };

    try {
      const response = await fetch(`${this.apiUrl}/api/v1/log-events`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          // PrairieLog authenticates ingestion with this header
          'X-Ingestion-Token': this.ingestionToken
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        console.error('PrairieLog request failed', response.status);
      }
    } catch (sendError) {
      // Do not recursively send this error to PrairieLog.
      console.error('Failed to send log to PrairieLog', sendError);
    }
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
