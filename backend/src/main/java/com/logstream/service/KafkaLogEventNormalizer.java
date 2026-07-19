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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.logstream.domain.model.LogEvent;
import com.logstream.exception.InvalidLogEventException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kafka-path log normalization: maps tolerant RawLogEvent aliases
 * (msg/levelname/loggerName/@timestamp/…) onto the canonical fields
 * {@link AlertContextProcessor} and alert analysis read (level, message, logger,
 * occurredAt, …).
 *
 * <p>Producer publishes the caller's raw JSON untouched; this runs in the stream
 * topology via {@code flatMapValues}. Failures are logged and dropped — never
 * thrown — so a malformed record cannot kill the Streams thread. Demo sample
 * payloads already use canonical names and pass through unchanged.
 *
 * <p>"exception" and "stackTrace" are not RawLogEvent fields but stay top-level
 * for error detection / exception-type grouping.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.toggle.enabled", havingValue = "true")
public class KafkaLogEventNormalizer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogEventNormalizer.class);

    private static final int MAX_MESSAGE_LENGTH = 5000;
    private static final int MAX_LOGGER_LENGTH = 255;
    private static final int MAX_ID_LENGTH = 150;

    private static final List<String> MESSAGE_KEYS = List.of("message", "msg", "text", "log", "body");
    private static final List<String> LEVEL_KEYS =
            List.of("level", "severity", "levelname", "level_name", "loglevel", "log_level", "priority");
    private static final List<String> TIMESTAMP_KEYS =
            List.of("occurredAt", "occurred_at", "timestamp", "@timestamp", "time", "ts", "datetime", "date");
    private static final List<String> ID_KEYS = List.of("id", "eventId", "event_id", "uuid", "messageId");
    private static final List<String> LOGGER_KEYS =
            List.of("logger", "loggerName", "logger_name", "tag", "source", "name");
    private static final List<String> TRACE_KEYS = List.of("traceId", "trace_id");
    private static final List<String> SPAN_KEYS = List.of("spanId", "span_id");
    private static final List<String> METADATA_KEYS = List.of("metadata", "meta", "context", "extra");

    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Returns the normalized event as a single-element list, or an empty list when
     * the event is unusable (no message alias, non-object payload). Shaped for
     * {@code KStream.flatMapValues}.
     */
    public List<LogEvent> normalizeOrDrop(LogEvent event) {
        if (event == null || event.payload() == null || !event.payload().isObject()) {
            log.warn("[kafka-normalize] dropping log event with missing or non-object payload appId={}",
                    event == null ? null : event.appId());
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = mapper.convertValue(event.payload(), Map.class);
            // keep exception/stackTrace out of the metadata fold; re-attach top-level below
            JsonNode originalException = event.payload().get("exception");
            JsonNode originalStackTrace = event.payload().get("stackTrace");
            raw.remove("exception");
            raw.remove("stackTrace");

            ObjectNode payload = canonicalize(raw);
            if (originalException != null) {
                payload.set("exception", originalException);
            }
            if (originalStackTrace != null) {
                payload.set("stackTrace", originalStackTrace);
            }
            return List.of(new LogEvent(event.appId(), event.appName(), event.receivedAt(), payload));
        } catch (InvalidLogEventException ex) {
            log.warn("[kafka-normalize] dropping invalid log event appId={} reason={}",
                    event.appId(), ex.getMessage());
            return List.of();
        } catch (RuntimeException ex) {
            log.warn("[kafka-normalize] dropping unprocessable log event appId={}", event.appId(), ex);
            return List.of();
        }
    }

    /**
     * Maps a raw field map to the canonical ObjectNode shape. Used by
     * {@link #normalizeOrDrop} and exposed for unit tests of alias handling.
     */
    public ObjectNode canonicalize(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidLogEventException("Log event payload must be a non-empty JSON object.");
        }

        Map<String, Object> remaining = new LinkedHashMap<>(raw);
        Map<String, Object> metadata = new LinkedHashMap<>();

        String message = extractMessage(remaining);
        String level = extractLevel(remaining, metadata);
        OffsetDateTime occurredAt = extractOccurredAt(remaining);
        String id = extractId(remaining);
        String loggerName = truncate(extractString(remaining, LOGGER_KEYS), MAX_LOGGER_LENGTH);
        String traceId = truncate(extractString(remaining, TRACE_KEYS), MAX_ID_LENGTH);
        String spanId = truncate(extractString(remaining, SPAN_KEYS), MAX_ID_LENGTH);

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

        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.put("id", id);
        out.put("level", level);
        out.put("message", message);
        out.put("occurredAt", occurredAt.toInstant().toString());
        putIfPresent(out, "logger", loggerName);
        putIfPresent(out, "traceId", traceId);
        putIfPresent(out, "spanId", spanId);
        if (!metadata.isEmpty()) {
            out.set("metadata", mapper.valueToTree(metadata));
        }
        return out;
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

    private String extractLevel(Map<String, Object> remaining, Map<String, Object> metadata) {
        for (String key : LEVEL_KEYS) {
            Object value = remaining.get(key);
            if (value == null) {
                continue;
            }
            remaining.remove(key);
            String level = mapLevel(value);
            if (level != null) {
                return level;
            }
            metadata.put("originalLevel", value);
            return "INFO";
        }
        return "INFO";
    }

    private String mapLevel(Object value) {
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
            case "trace", "verbose", "finest", "finer" -> "TRACE";
            case "debug", "fine", "config" -> "DEBUG";
            case "info", "information", "notice", "log", "default" -> "INFO";
            case "warn", "warning" -> "WARN";
            case "error", "err", "severe", "fatal", "critical", "crit",
                 "alert", "emergency", "panic", "assert", "wtf" -> "ERROR";
            default -> null;
        };
    }

    /** Android Log priorities use 2–7; Pino levels use 10–60. Ranges do not overlap. */
    private String mapNumericLevel(int value) {
        if (value >= 2 && value <= 7) {
            return switch (value) {
                case 2 -> "TRACE";
                case 3 -> "DEBUG";
                case 4 -> "INFO";
                case 5 -> "WARN";
                default -> "ERROR";
            };
        }
        if (value >= 10 && value <= 100) {
            if (value < 20) {
                return "TRACE";
            }
            if (value < 30) {
                return "DEBUG";
            }
            if (value < 40) {
                return "INFO";
            }
            if (value < 50) {
                return "WARN";
            }
            return "ERROR";
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
            // unparseable timestamps stay in the map and end up in metadata
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

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }
}
