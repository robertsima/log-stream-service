package com.logstream.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.logstream.exception.InvalidLogEventException;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;

/**
 * Converts free-form log event JSON from common logging stacks (Logback/Logstash,
 * python-json-logger, Winston, Pino, Android, Flutter) into the canonical
 * {@link LogEventRequest}. Accepted aliases are documented on the RawLogEvent
 * schema in openapi.yaml; unrecognized fields are preserved under metadata.
 */
@Component
public class LogEventNormalizer {

    private static final int MAX_MESSAGE_LENGTH = 5000;
    private static final int MAX_LOGGER_LENGTH = 255;
    private static final int MAX_ID_LENGTH = 150;

    private static final List<String> MESSAGE_KEYS = List.of("message", "msg", "text", "log", "body");
    private static final List<String> LEVEL_KEYS =
            List.of("level", "severity", "levelname", "level_name", "loglevel", "log_level", "priority");
    private static final List<String> TIMESTAMP_KEYS =
            List.of("occurredAt", "occurred_at", "timestamp", "@timestamp", "time", "ts", "datetime", "date");
    private static final List<String> ID_KEYS = List.of("id", "eventId", "event_id", "uuid", "messageId");
    private static final List<String> LOGGER_KEYS = List.of("logger", "loggerName", "logger_name", "tag", "source", "name");
    private static final List<String> TRACE_KEYS = List.of("traceId", "trace_id");
    private static final List<String> SPAN_KEYS = List.of("spanId", "span_id");
    private static final List<String> METADATA_KEYS = List.of("metadata", "meta", "context", "extra");

    public LogEventRequest normalize(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidLogEventException("Log event payload must be a non-empty JSON object.");
        }

        Map<String, Object> remaining = new LinkedHashMap<>(raw);

        LogEventRequest event = new LogEventRequest();
        Map<String, Object> metadata = new LinkedHashMap<>();

        event.setMessage(extractMessage(remaining));
        event.setLevel(extractLevel(remaining, metadata));
        event.setOccurredAt(extractOccurredAt(remaining));
        event.setId(extractId(remaining));
        event.setLogger(truncate(extractString(remaining, LOGGER_KEYS), MAX_LOGGER_LENGTH));
        event.setTraceId(truncate(extractString(remaining, TRACE_KEYS), MAX_ID_LENGTH));
        event.setSpanId(truncate(extractString(remaining, SPAN_KEYS), MAX_ID_LENGTH));

        for (String key : METADATA_KEYS) {
            Object value = remaining.get(key);
            if (value instanceof Map<?, ?> map) {
                remaining.remove(key);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    metadata.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Object> entry : remaining.entrySet()) {
            metadata.putIfAbsent(entry.getKey(), entry.getValue());
        }
        event.setMetadata(metadata.isEmpty() ? null : metadata);

        return event;
    }

    private String extractMessage(Map<String, Object> remaining) {
        for (String key : MESSAGE_KEYS) {
            Object value = remaining.get(key);
            if (value != null && !(value instanceof Map) && !(value instanceof List)) {
                String message = String.valueOf(value);
                if (!message.isBlank()) {
                    remaining.remove(key);
                    return truncate(message, MAX_MESSAGE_LENGTH);
                }
            }
        }
        throw new InvalidLogEventException(
                "Log event must include a message field (accepted keys: message, msg, text, log, body).");
    }

    private LogLevel extractLevel(Map<String, Object> remaining, Map<String, Object> metadata) {
        for (String key : LEVEL_KEYS) {
            Object value = remaining.get(key);
            if (value == null) {
                continue;
            }
            remaining.remove(key);
            LogLevel level = mapLevel(value);
            if (level != null) {
                return level;
            }
            metadata.put("originalLevel", value);
            return LogLevel.INFO;
        }
        return LogLevel.INFO;
    }

    private LogLevel mapLevel(Object value) {
        if (value instanceof Number number) {
            return mapNumericLevel(number.intValue());
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return mapNumericLevel(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return switch (text.toLowerCase(Locale.ROOT)) {
            case "trace", "verbose", "finest", "finer" -> LogLevel.TRACE;
            case "debug", "fine", "config" -> LogLevel.DEBUG;
            case "info", "information", "notice", "log", "default" -> LogLevel.INFO;
            case "warn", "warning" -> LogLevel.WARN;
            case "error", "err", "severe", "fatal", "critical", "crit",
                 "alert", "emergency", "panic", "assert", "wtf" -> LogLevel.ERROR;
            default -> null;
        };
    }

    /**
     * Android Log priorities use 2-7; Pino levels use 10-60. The ranges do not
     * overlap, so both are supported.
     */
    private LogLevel mapNumericLevel(int value) {
        if (value >= 2 && value <= 7) {
            return switch (value) {
                case 2 -> LogLevel.TRACE;
                case 3 -> LogLevel.DEBUG;
                case 4 -> LogLevel.INFO;
                case 5 -> LogLevel.WARN;
                default -> LogLevel.ERROR;
            };
        }
        if (value >= 10 && value <= 100) {
            if (value < 20) {
                return LogLevel.TRACE;
            }
            if (value < 30) {
                return LogLevel.DEBUG;
            }
            if (value < 40) {
                return LogLevel.INFO;
            }
            if (value < 50) {
                return LogLevel.WARN;
            }
            return LogLevel.ERROR;
        }
        return null;
    }

    private OffsetDateTime extractOccurredAt(Map<String, Object> remaining) {
        for (String key : TIMESTAMP_KEYS) {
            Object value = remaining.get(key);
            if (value == null) {
                continue;
            }
            OffsetDateTime parsed = parseTimestamp(value);
            if (parsed != null) {
                remaining.remove(key);
                return parsed;
            }
            // unparseable timestamps stay in the map and end up preserved in metadata
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private OffsetDateTime parseTimestamp(Object value) {
        if (value instanceof Number number) {
            return fromEpoch(number.longValue());
        }
        if (!(value instanceof CharSequence)) {
            return null;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return fromEpoch(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return OffsetDateTime.parse(text);
        } catch (RuntimeException ignored) {
            // fall through to offset-less formats
        }
        try {
            return LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Interprets the magnitude of an epoch value: seconds, millis, micros, or nanos.
     */
    private OffsetDateTime fromEpoch(long epoch) {
        if (epoch <= 0) {
            return null;
        }
        Instant instant;
        if (epoch < 100_000_000_000L) {
            instant = Instant.ofEpochSecond(epoch);
        } else if (epoch < 100_000_000_000_000L) {
            instant = Instant.ofEpochMilli(epoch);
        } else if (epoch < 100_000_000_000_000_000L) {
            instant = Instant.ofEpochSecond(epoch / 1_000_000, (epoch % 1_000_000) * 1_000);
        } else {
            instant = Instant.ofEpochSecond(epoch / 1_000_000_000, epoch % 1_000_000_000);
        }
        return instant.atOffset(ZoneOffset.UTC);
    }

    private String extractId(Map<String, Object> remaining) {
        String id = extractString(remaining, ID_KEYS);
        if (id == null || id.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return truncate(id, MAX_ID_LENGTH);
    }

    private String extractString(Map<String, Object> remaining, List<String> keys) {
        for (String key : keys) {
            Object value = remaining.get(key);
            if (value != null && !(value instanceof Map) && !(value instanceof List)) {
                String text = String.valueOf(value);
                if (!text.isBlank()) {
                    remaining.remove(key);
                    return text;
                }
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
