const DEFAULT_BATCH_SIZE = 50;
const MAX_SERVER_BATCH_SIZE = 100;
const DEFAULT_FLUSH_INTERVAL_MS = 5000;
const DEFAULT_MAX_QUEUE_SIZE = 1000;
const DEFAULT_MAX_ATTEMPTS = 3;
const DEFAULT_BASE_DELAY_MS = 250;
const DEFAULT_MAX_DELAY_MS = 5000;
export class PrairieLogClient {
    constructor(config) {
        this.queue = [];
        this.flushInFlight = false;
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
    trace(message, metadata) {
        return this.log("TRACE", message, metadata);
    }
    debug(message, metadata) {
        return this.log("DEBUG", message, metadata);
    }
    info(message, metadata) {
        return this.log("INFO", message, metadata);
    }
    warn(message, metadata) {
        return this.log("WARN", message, metadata);
    }
    error(error, metadata, logger = this.defaultLogger) {
        return this.captureException(error, metadata, logger);
    }
    async log(level, message, metadata, logger = this.defaultLogger) {
        await this.send({
            level,
            message,
            logger,
            metadata
        });
    }
    async captureException(error, metadata, logger = this.defaultLogger) {
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
    async send(event) {
        this.enqueue(this.toEvent(event));
        if (this.queue.length >= this.batchSize) {
            await this.flush();
        }
    }
    async flush() {
        if (this.flushInFlight || this.queue.length === 0) {
            return;
        }
        this.flushInFlight = true;
        const batch = this.queue.splice(0, this.batchSize);
        this.persistQueue();
        try {
            await this.postBatchWithRetry(batch);
        }
        catch (error) {
            this.queue.unshift(...batch);
            this.trimQueue();
            this.persistQueue();
            this.reportError(error);
        }
        finally {
            this.flushInFlight = false;
        }
    }
    start() {
        if (this.flushTimer || this.flushIntervalMs <= 0) {
            return;
        }
        this.flushTimer = setInterval(() => {
            void this.flush();
        }, this.flushIntervalMs);
    }
    stop() {
        if (!this.flushTimer) {
            return;
        }
        clearInterval(this.flushTimer);
        this.flushTimer = undefined;
    }
    installGlobalHandlers(options = {}) {
        const target = typeof window === "undefined" ? undefined : window;
        if (!target) {
            return () => undefined;
        }
        const onError = (event) => {
            const error = event.error ?? event.message;
            void this.captureException(error, {
                ...options.metadata,
                source: "window.onerror",
                filename: event.filename,
                lineno: event.lineno,
                colno: event.colno
            }, options.logger ?? this.defaultLogger);
        };
        const onUnhandledRejection = (event) => {
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
    installNodeHandlers(options = {}) {
        const maybeProcess = globalThis.process;
        if (!maybeProcess?.on || !maybeProcess.off) {
            return () => undefined;
        }
        const onUncaughtException = (error) => {
            void this.captureException(error, {
                ...options.metadata,
                source: "process.uncaughtException"
            }, options.logger ?? this.defaultLogger);
        };
        const onUnhandledRejection = (reason) => {
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
    toEvent(input) {
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
    enqueue(event) {
        this.queue.push(event);
        this.trimQueue();
        this.persistQueue();
    }
    trimQueue() {
        if (this.queue.length <= this.maxQueueSize) {
            return;
        }
        this.queue.splice(0, this.queue.length - this.maxQueueSize);
    }
    async postBatchWithRetry(batch) {
        let lastError;
        for (let attempt = 1; attempt <= this.retry.maxAttempts; attempt++) {
            try {
                await this.postBatch(batch);
                return;
            }
            catch (error) {
                lastError = error;
                if (attempt === this.retry.maxAttempts || !isRetryable(error)) {
                    break;
                }
                await delay(backoffDelay(attempt, this.retry.baseDelayMs, this.retry.maxDelayMs));
            }
        }
        throw lastError;
    }
    async postBatch(batch) {
        const response = await this.fetchImpl(`${this.apiUrl}/api/v1/log-events/batch`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-Ingestion-Token": this.ingestionToken
            },
            body: JSON.stringify(batch)
        });
        if (!response.ok) {
            throw new PrairieLogHttpError(response.status, response.statusText);
        }
        const result = await safeJson(response);
        if (result?.rejected?.length) {
            this.reportError(new Error(`PrairieLog rejected ${result.rejected.length} event(s).`));
        }
    }
    restoreQueue() {
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
        }
        catch (error) {
            this.reportError(error);
        }
    }
    persistQueue() {
        const storage = getStorage();
        if (!this.storageKey || !storage) {
            return;
        }
        try {
            if (this.queue.length === 0) {
                storage.removeItem(this.storageKey);
            }
            else {
                storage.setItem(this.storageKey, JSON.stringify(this.queue));
            }
        }
        catch (error) {
            this.reportError(error);
        }
    }
    reportError(error) {
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
    constructor(status, statusText) {
        super(`PrairieLog request failed with ${status} ${statusText}`.trim());
        this.status = status;
        this.statusText = statusText;
        this.name = "PrairieLogHttpError";
    }
}
export function normalizeLevel(level) {
    if (typeof level === "number") {
        if (level <= 10)
            return "TRACE";
        if (level <= 20)
            return "DEBUG";
        if (level <= 30)
            return "INFO";
        if (level <= 40)
            return "WARN";
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
function normalizeTimestamp(value) {
    if (value instanceof Date) {
        return value.toISOString();
    }
    if (typeof value === "number") {
        const millis = value < 10000000000 ? value * 1000 : value;
        return new Date(millis).toISOString();
    }
    if (typeof value === "string" && value.trim()) {
        return value;
    }
    return new Date().toISOString();
}
function normalizeError(error) {
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
function randomId() {
    const cryptoLike = globalThis.crypto;
    if (cryptoLike?.randomUUID) {
        return cryptoLike.randomUUID();
    }
    return `plog_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
}
function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
}
function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}
function backoffDelay(attempt, baseDelayMs, maxDelayMs) {
    return Math.min(maxDelayMs, baseDelayMs * 2 ** (attempt - 1));
}
function isRetryable(error) {
    if (!(error instanceof PrairieLogHttpError)) {
        return true;
    }
    return error.status === 429 || error.status >= 500;
}
async function safeJson(response) {
    const text = await response.text();
    if (!text) {
        return undefined;
    }
    return JSON.parse(text);
}
function getStorage() {
    try {
        return typeof window === "undefined" ? undefined : window.localStorage;
    }
    catch {
        return undefined;
    }
}
function isPrairieLogEvent(value) {
    if (!value || typeof value !== "object") {
        return false;
    }
    const event = value;
    return typeof event.id === "string"
        && typeof event.level === "string"
        && typeof event.message === "string"
        && typeof event.occurredAt === "string";
}
//# sourceMappingURL=index.js.map