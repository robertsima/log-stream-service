export type PrairieLogLevel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR";

export type PrairieLogInputLevel =
  | PrairieLogLevel
  | "trace"
  | "debug"
  | "info"
  | "warn"
  | "warning"
  | "error"
  | "critical"
  | "fatal"
  | "verbose"
  | "assert"
  | number;

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

export type PrairieLogEventInput = {
  level: PrairieLogInputLevel;
  message: string;
  id?: string;
  occurredAt?: string | number | Date;
  logger?: string;
  traceId?: string;
  spanId?: string;
  metadata?: Record<string, unknown>;
};

export interface PrairieLogRetryConfig {
  maxAttempts?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
}

export interface PrairieLogClientConfig {
  apiUrl: string;
  ingestionToken: string;
  defaultLogger?: string;
  batchSize?: number;
  flushIntervalMs?: number;
  maxQueueSize?: number;
  retry?: PrairieLogRetryConfig;
  fetchImpl?: typeof fetch;
  storageKey?: string;
  autoFlush?: boolean;
  onError?: (error: unknown) => void;
}

export interface PrairieLogBatchResponse {
  accepted: number;
  rejected: Array<{
    index: number;
    reason: string;
  }>;
}

export interface PrairieLogGlobalHandlerOptions {
  logger?: string;
  metadata?: Record<string, unknown>;
}

type TimerHandle = ReturnType<typeof setInterval>;

const DEFAULT_BATCH_SIZE = 50;
const MAX_SERVER_BATCH_SIZE = 100;
const DEFAULT_FLUSH_INTERVAL_MS = 5000;
const DEFAULT_MAX_QUEUE_SIZE = 1000;
const DEFAULT_MAX_ATTEMPTS = 3;
const DEFAULT_BASE_DELAY_MS = 250;
const DEFAULT_MAX_DELAY_MS = 5000;

export class PrairieLogClient {
  private readonly apiUrl: string;
  private readonly ingestionToken: string;
  private readonly defaultLogger: string;
  private readonly batchSize: number;
  private readonly flushIntervalMs: number;
  private readonly maxQueueSize: number;
  private readonly retry: Required<PrairieLogRetryConfig>;
  private readonly fetchImpl: typeof fetch;
  private readonly storageKey?: string;
  private readonly onError?: (error: unknown) => void;
  private readonly queue: PrairieLogEvent[] = [];
  private flushTimer?: TimerHandle;
  private flushInFlight = false;
  private kafkaUnavailableReported = false;

  constructor(config: PrairieLogClientConfig) {
    if (!config.apiUrl || !config.ingestionToken) {
      throw new Error("PrairieLog requires apiUrl and ingestionToken.");
    }

    const fetchImpl = config.fetchImpl ?? globalThis.fetch;
    if (!fetchImpl) {
      throw new Error("PrairieLog requires fetch. Pass fetchImpl when running in older Node versions.");
    }

    this.apiUrl = config.apiUrl.replace(/\/$/, "");
    this.ingestionToken = config.ingestionToken;
    this.defaultLogger = config.defaultLogger ?? "prairielog-client";
    this.batchSize = clamp(config.batchSize ?? DEFAULT_BATCH_SIZE, 1, MAX_SERVER_BATCH_SIZE);
    this.flushIntervalMs = Math.max(0, config.flushIntervalMs ?? DEFAULT_FLUSH_INTERVAL_MS);
    this.maxQueueSize = Math.max(1, config.maxQueueSize ?? DEFAULT_MAX_QUEUE_SIZE);
    this.retry = {
      maxAttempts: Math.max(1, config.retry?.maxAttempts ?? DEFAULT_MAX_ATTEMPTS),
      baseDelayMs: Math.max(0, config.retry?.baseDelayMs ?? DEFAULT_BASE_DELAY_MS),
      maxDelayMs: Math.max(0, config.retry?.maxDelayMs ?? DEFAULT_MAX_DELAY_MS)
    };
    this.fetchImpl = fetchImpl.bind(globalThis);
    this.storageKey = config.storageKey;
    this.onError = config.onError;

    this.restoreQueue();

    if (config.autoFlush !== false) {
      this.start();
    }
  }

  trace(message: string, metadata?: Record<string, unknown>): Promise<void> {
    return this.log("TRACE", message, metadata);
  }

  debug(message: string, metadata?: Record<string, unknown>): Promise<void> {
    return this.log("DEBUG", message, metadata);
  }

  info(message: string, metadata?: Record<string, unknown>): Promise<void> {
    return this.log("INFO", message, metadata);
  }

  warn(message: string, metadata?: Record<string, unknown>): Promise<void> {
    return this.log("WARN", message, metadata);
  }

  error(error: unknown, metadata?: Record<string, unknown>, logger = this.defaultLogger): Promise<void> {
    return this.captureException(error, metadata, logger);
  }

  async log(
    level: PrairieLogInputLevel,
    message: string,
    metadata?: Record<string, unknown>,
    logger = this.defaultLogger
  ): Promise<void> {
    await this.send({
      level,
      message,
      logger,
      metadata
    });
  }

  async captureException(
    error: unknown,
    metadata?: Record<string, unknown>,
    logger = this.defaultLogger
  ): Promise<void> {
    const normalized = normalizeError(error);
    await this.send({
      level: "ERROR",
      message: normalized.message,
      logger,
      metadata: {
        ...metadata,
        errorName: normalized.name,
        stack: normalized.stack
      }
    });
  }

  async send(event: PrairieLogEventInput): Promise<void> {
    this.enqueue(this.toEvent(event));
    if (this.queue.length >= this.batchSize) {
      await this.flush();
    }
  }

  async flush(): Promise<void> {
    if (this.flushInFlight || this.queue.length === 0) {
      return;
    }

    this.flushInFlight = true;
    const batch = this.queue.splice(0, this.batchSize);
    this.persistQueue();

    try {
      await this.postBatchWithRetry(batch);
    } catch (error) {
      this.queue.unshift(...batch);
      this.trimQueue();
      this.persistQueue();
      this.reportError(error);
    } finally {
      this.flushInFlight = false;
    }
  }

  start(): void {
    if (this.flushTimer || this.flushIntervalMs <= 0) {
      return;
    }
    this.flushTimer = setInterval(() => {
      void this.flush();
    }, this.flushIntervalMs);
  }

  stop(): void {
    if (!this.flushTimer) {
      return;
    }
    clearInterval(this.flushTimer);
    this.flushTimer = undefined;
  }

  installGlobalHandlers(options: PrairieLogGlobalHandlerOptions = {}): () => void {
    const target = typeof window === "undefined" ? undefined : window;
    if (!target) {
      return () => undefined;
    }

    const onError = (event: ErrorEvent) => {
      const error = event.error ?? event.message;
      void this.captureException(error, {
        ...options.metadata,
        source: "window.onerror",
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno
      }, options.logger ?? this.defaultLogger);
    };

    const onUnhandledRejection = (event: PromiseRejectionEvent) => {
      void this.captureException(event.reason, {
        ...options.metadata,
        source: "window.unhandledrejection"
      }, options.logger ?? this.defaultLogger);
    };

    target.addEventListener("error", onError);
    target.addEventListener("unhandledrejection", onUnhandledRejection);

    return () => {
      target.removeEventListener("error", onError);
      target.removeEventListener("unhandledrejection", onUnhandledRejection);
    };
  }

  installNodeHandlers(options: PrairieLogGlobalHandlerOptions = {}): () => void {
    const maybeProcess = (globalThis as { process?: NodeLikeProcess }).process;
    if (!maybeProcess?.on || !maybeProcess.off) {
      return () => undefined;
    }

    const onUncaughtException = (error: unknown) => {
      void this.captureException(error, {
        ...options.metadata,
        source: "process.uncaughtException"
      }, options.logger ?? this.defaultLogger);
    };

    const onUnhandledRejection = (reason: unknown) => {
      void this.captureException(reason, {
        ...options.metadata,
        source: "process.unhandledRejection"
      }, options.logger ?? this.defaultLogger);
    };

    maybeProcess.on("uncaughtException", onUncaughtException);
    maybeProcess.on("unhandledRejection", onUnhandledRejection);

    return () => {
      maybeProcess.off("uncaughtException", onUncaughtException);
      maybeProcess.off("unhandledRejection", onUnhandledRejection);
    };
  }

  private toEvent(input: PrairieLogEventInput): PrairieLogEvent {
    return {
      id: input.id ?? randomId(),
      level: normalizeLevel(input.level),
      message: input.message,
      occurredAt: normalizeTimestamp(input.occurredAt),
      logger: input.logger ?? this.defaultLogger,
      traceId: input.traceId,
      spanId: input.spanId,
      metadata: input.metadata
    };
  }

  private enqueue(event: PrairieLogEvent): void {
    this.queue.push(event);
    this.trimQueue();
    this.persistQueue();
  }

  private trimQueue(): void {
    if (this.queue.length <= this.maxQueueSize) {
      return;
    }
    this.queue.splice(0, this.queue.length - this.maxQueueSize);
  }

  private async postBatchWithRetry(batch: PrairieLogEvent[]): Promise<void> {
    let lastError: unknown;

    for (let attempt = 1; attempt <= this.retry.maxAttempts; attempt++) {
      try {
        await this.postBatch(batch);
        return;
      } catch (error) {
        lastError = error;
        if (attempt === this.retry.maxAttempts || !isRetryable(error)) {
          break;
        }
        await delay(backoffDelay(attempt, this.retry.baseDelayMs, this.retry.maxDelayMs));
      }
    }

    throw lastError;
  }

  private async postBatch(batch: PrairieLogEvent[]): Promise<void> {
    const kafkaResponse = await this.post("/api/v1/kafka/log-events/batch", batch);

    if (kafkaResponse.ok) {
      this.kafkaUnavailableReported = false;
      return;
    }

    if (kafkaResponse.status !== 503) {
      throw new PrairieLogHttpError(kafkaResponse.status, kafkaResponse.statusText);
    }

    // 503 means the server's Kafka integration is disabled or the broker is down.
    // Alerting flows exclusively through Kafka, so alerts are degraded — but fall
    // back to the synchronous ingestion endpoint so logs are not lost.
    if (!this.kafkaUnavailableReported) {
      this.kafkaUnavailableReported = true;
      this.reportError(new Error(
        "PrairieLog Kafka ingestion unavailable (HTTP 503); falling back to POST /api/v1/log-events/batch. Logs are still ingested but alerting is degraded until Kafka is back."
      ));
    }

    const fallbackResponse = await this.post("/api/v1/log-events/batch", batch);
    if (!fallbackResponse.ok) {
      throw new PrairieLogHttpError(fallbackResponse.status, fallbackResponse.statusText);
    }

    const result = await safeJson<PrairieLogBatchResponse>(fallbackResponse);
    if (result?.rejected?.length) {
      this.reportError(new Error(`PrairieLog rejected ${result.rejected.length} event(s).`));
    }
  }

  private post(path: string, batch: PrairieLogEvent[]): Promise<Response> {
    return this.fetchImpl(`${this.apiUrl}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Ingestion-Token": this.ingestionToken
      },
      body: JSON.stringify(batch)
    });
  }

  private restoreQueue(): void {
    const storage = getStorage();
    if (!this.storageKey || !storage) {
      return;
    }

    try {
      const raw = storage.getItem(this.storageKey);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        this.queue.push(...parsed.filter(isPrairieLogEvent));
        this.trimQueue();
      }
    } catch (error) {
      this.reportError(error);
    }
  }

  private persistQueue(): void {
    const storage = getStorage();
    if (!this.storageKey || !storage) {
      return;
    }

    try {
      if (this.queue.length === 0) {
        storage.removeItem(this.storageKey);
      } else {
        storage.setItem(this.storageKey, JSON.stringify(this.queue));
      }
    } catch (error) {
      this.reportError(error);
    }
  }

  private reportError(error: unknown): void {
    if (this.onError) {
      this.onError(error);
      return;
    }
    if (typeof console !== "undefined") {
      console.error("PrairieLog client error", error);
    }
  }
}

export class PrairieLogHttpError extends Error {
  constructor(readonly status: number, readonly statusText: string) {
    super(`PrairieLog request failed with ${status} ${statusText}`.trim());
    this.name = "PrairieLogHttpError";
  }
}

export function normalizeLevel(level: PrairieLogInputLevel): PrairieLogLevel {
  if (typeof level === "number") {
    if (level <= 10) return "TRACE";
    if (level <= 20) return "DEBUG";
    if (level <= 30) return "INFO";
    if (level <= 40) return "WARN";
    return "ERROR";
  }

  switch (level.trim().toUpperCase()) {
    case "TRACE":
    case "VERBOSE":
      return "TRACE";
    case "DEBUG":
      return "DEBUG";
    case "INFO":
      return "INFO";
    case "WARN":
    case "WARNING":
      return "WARN";
    case "ERROR":
    case "CRITICAL":
    case "FATAL":
    case "ASSERT":
      return "ERROR";
    default:
      return "INFO";
  }
}

function normalizeTimestamp(value: string | number | Date | undefined): string {
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === "number") {
    const millis = value < 10_000_000_000 ? value * 1000 : value;
    return new Date(millis).toISOString();
  }
  if (typeof value === "string" && value.trim()) {
    return value;
  }
  return new Date().toISOString();
}

function normalizeError(error: unknown): { name: string; message: string; stack?: string } {
  if (error instanceof Error) {
    return {
      name: error.name,
      message: error.message,
      stack: error.stack
    };
  }

  return {
    name: "UnknownError",
    message: String(error)
  };
}

function randomId(): string {
  const cryptoLike = globalThis.crypto;
  if (cryptoLike?.randomUUID) {
    return cryptoLike.randomUUID();
  }
  return `plog_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function backoffDelay(attempt: number, baseDelayMs: number, maxDelayMs: number): number {
  return Math.min(maxDelayMs, baseDelayMs * 2 ** (attempt - 1));
}

function isRetryable(error: unknown): boolean {
  if (!(error instanceof PrairieLogHttpError)) {
    return true;
  }
  return error.status === 429 || error.status >= 500;
}

async function safeJson<T>(response: Response): Promise<T | undefined> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  return JSON.parse(text) as T;
}

function getStorage(): Storage | undefined {
  try {
    return typeof window === "undefined" ? undefined : window.localStorage;
  } catch {
    return undefined;
  }
}

function isPrairieLogEvent(value: unknown): value is PrairieLogEvent {
  if (!value || typeof value !== "object") {
    return false;
  }
  const event = value as Partial<PrairieLogEvent>;
  return typeof event.id === "string"
    && typeof event.level === "string"
    && typeof event.message === "string"
    && typeof event.occurredAt === "string";
}

interface NodeLikeProcess {
  on(event: "uncaughtException" | "unhandledRejection", listener: (reason: unknown) => void): void;
  off(event: "uncaughtException" | "unhandledRejection", listener: (reason: unknown) => void): void;
}
