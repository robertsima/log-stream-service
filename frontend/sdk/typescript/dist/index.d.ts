export type PrairieLogLevel = "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR";
export type PrairieLogInputLevel = PrairieLogLevel | "trace" | "debug" | "info" | "warn" | "warning" | "error" | "critical" | "fatal" | "verbose" | "assert" | number;
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
export declare class PrairieLogClient {
    private readonly apiUrl;
    private readonly ingestionToken;
    private readonly defaultLogger;
    private readonly batchSize;
    private readonly flushIntervalMs;
    private readonly maxQueueSize;
    private readonly retry;
    private readonly fetchImpl;
    private readonly storageKey?;
    private readonly onError?;
    private readonly queue;
    private flushTimer?;
    private flushInFlight;
    constructor(config: PrairieLogClientConfig);
    trace(message: string, metadata?: Record<string, unknown>): Promise<void>;
    debug(message: string, metadata?: Record<string, unknown>): Promise<void>;
    info(message: string, metadata?: Record<string, unknown>): Promise<void>;
    warn(message: string, metadata?: Record<string, unknown>): Promise<void>;
    error(error: unknown, metadata?: Record<string, unknown>, logger?: string): Promise<void>;
    log(level: PrairieLogInputLevel, message: string, metadata?: Record<string, unknown>, logger?: string): Promise<void>;
    captureException(error: unknown, metadata?: Record<string, unknown>, logger?: string): Promise<void>;
    send(event: PrairieLogEventInput): Promise<void>;
    flush(): Promise<void>;
    start(): void;
    stop(): void;
    installGlobalHandlers(options?: PrairieLogGlobalHandlerOptions): () => void;
    installNodeHandlers(options?: PrairieLogGlobalHandlerOptions): () => void;
    private toEvent;
    private enqueue;
    private trimQueue;
    private postBatchWithRetry;
    private postBatch;
    private restoreQueue;
    private persistQueue;
    private reportError;
}
export declare class PrairieLogHttpError extends Error {
    readonly status: number;
    readonly statusText: string;
    constructor(status: number, statusText: string);
}
export declare function normalizeLevel(level: PrairieLogInputLevel): PrairieLogLevel;
//# sourceMappingURL=index.d.ts.map