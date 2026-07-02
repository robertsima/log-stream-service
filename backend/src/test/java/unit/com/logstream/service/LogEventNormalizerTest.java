package unit.com.logstream.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.logstream.exception.InvalidLogEventException;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.service.LogEventNormalizer;

public class LogEventNormalizerTest {

    private final LogEventNormalizer normalizer = new LogEventNormalizer();

    // --- canonical payloads pass through unchanged ---

    @Test
    void canonicalPayloadIsPreserved() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("id", "01HZABC123");
        raw.put("level", "ERROR");
        raw.put("message", "Failed to process payment.");
        raw.put("occurredAt", "2026-06-08T18:30:00Z");
        raw.put("logger", "com.example.PaymentService");
        raw.put("traceId", "abc-123");
        raw.put("spanId", "def-456");
        raw.put("metadata", Map.of("endpoint", "/payments"));

        LogEventRequest event = normalizer.normalize(raw);

        assertEquals("01HZABC123", event.getId());
        assertEquals(LogLevel.ERROR, event.getLevel());
        assertEquals("Failed to process payment.", event.getMessage());
        assertEquals(OffsetDateTime.parse("2026-06-08T18:30:00Z"), event.getOccurredAt());
        assertEquals("com.example.PaymentService", event.getLogger());
        assertEquals("abc-123", event.getTraceId());
        assertEquals("def-456", event.getSpanId());
        assertEquals("/payments", event.getMetadata().get("endpoint"));
    }

    // --- message aliases ---

    @Test
    void acceptsMsgAliasForMessage() {
        LogEventRequest event = normalizer.normalize(mapOf("msg", "listening on 3000"));
        assertEquals("listening on 3000", event.getMessage());
    }

    @Test
    void acceptsTextAndLogAliasesForMessage() {
        assertEquals("hello", normalizer.normalize(mapOf("text", "hello")).getMessage());
        assertEquals("hello", normalizer.normalize(mapOf("log", "hello")).getMessage());
    }

    @Test
    void canonicalMessageWinsOverAlias() {
        Map<String, Object> raw = mapOf("message", "canonical", "msg", "alias");
        LogEventRequest event = normalizer.normalize(raw);
        assertEquals("canonical", event.getMessage());
        // the unused alias is preserved rather than silently dropped
        assertEquals("alias", event.getMetadata().get("msg"));
    }

    @Test
    void rejectsPayloadWithoutMessage() {
        InvalidLogEventException ex = assertThrows(InvalidLogEventException.class,
                () -> normalizer.normalize(mapOf("level", "INFO")));
        assertTrue(ex.getMessage().contains("message"));
    }

    @Test
    void rejectsBlankMessage() {
        assertThrows(InvalidLogEventException.class,
                () -> normalizer.normalize(mapOf("message", "   ")));
    }

    @Test
    void stringifiesNonStringMessage() {
        assertEquals("42", normalizer.normalize(mapOf("message", 42)).getMessage());
    }

    @Test
    void truncatesOverlongMessage() {
        String longMessage = "x".repeat(6000);
        LogEventRequest event = normalizer.normalize(mapOf("message", longMessage));
        assertEquals(5000, event.getMessage().length());
    }

    // --- level normalization ---

    @Test
    void normalizesCommonLevelStrings() {
        assertEquals(LogLevel.WARN, levelOf("warning"));
        assertEquals(LogLevel.WARN, levelOf("WARN"));
        assertEquals(LogLevel.ERROR, levelOf("critical"));
        assertEquals(LogLevel.ERROR, levelOf("FATAL"));
        assertEquals(LogLevel.ERROR, levelOf("severe"));
        assertEquals(LogLevel.TRACE, levelOf("verbose"));
        assertEquals(LogLevel.DEBUG, levelOf("debug"));
        assertEquals(LogLevel.INFO, levelOf("info"));
        assertEquals(LogLevel.ERROR, levelOf("err"));
    }

    @Test
    void acceptsSeverityAndLevelnameAliases() {
        assertEquals(LogLevel.ERROR,
                normalizer.normalize(mapOf("message", "m", "severity", "error")).getLevel());
        assertEquals(LogLevel.WARN,
                normalizer.normalize(mapOf("message", "m", "levelname", "WARNING")).getLevel());
    }

    @Test
    void normalizesPinoNumericLevels() {
        assertEquals(LogLevel.TRACE, levelOf(10));
        assertEquals(LogLevel.DEBUG, levelOf(20));
        assertEquals(LogLevel.INFO, levelOf(30));
        assertEquals(LogLevel.WARN, levelOf(40));
        assertEquals(LogLevel.ERROR, levelOf(50));
        assertEquals(LogLevel.ERROR, levelOf(60));
    }

    @Test
    void normalizesAndroidNumericPriorities() {
        assertEquals(LogLevel.TRACE, levelOf(2));
        assertEquals(LogLevel.DEBUG, levelOf(3));
        assertEquals(LogLevel.INFO, levelOf(4));
        assertEquals(LogLevel.WARN, levelOf(5));
        assertEquals(LogLevel.ERROR, levelOf(6));
        assertEquals(LogLevel.ERROR, levelOf(7));
    }

    @Test
    void missingLevelDefaultsToInfo() {
        assertEquals(LogLevel.INFO, normalizer.normalize(mapOf("message", "m")).getLevel());
    }

    @Test
    void unrecognizedLevelDefaultsToInfoAndKeepsOriginal() {
        LogEventRequest event = normalizer.normalize(mapOf("message", "m", "level", "banana"));
        assertEquals(LogLevel.INFO, event.getLevel());
        assertEquals("banana", event.getMetadata().get("originalLevel"));
    }

    // --- timestamp normalization ---

    @Test
    void acceptsTimestampAliases() {
        OffsetDateTime expected = OffsetDateTime.parse("2026-06-08T18:30:00Z");
        assertEquals(expected, timeOf("@timestamp", "2026-06-08T18:30:00Z"));
        assertEquals(expected, timeOf("timestamp", "2026-06-08T18:30:00Z"));
        assertEquals(expected, timeOf("time", "2026-06-08T18:30:00Z"));
        assertEquals(expected, timeOf("occurred_at", "2026-06-08T18:30:00Z"));
    }

    @Test
    void acceptsEpochMillis() {
        long millis = 1780943400000L; // 2026-06-08T18:30:00Z
        OffsetDateTime parsed = timeOf("time", millis);
        assertEquals(OffsetDateTime.parse("2026-06-08T18:30:00Z").toInstant(), parsed.toInstant());
    }

    @Test
    void acceptsEpochSeconds() {
        long seconds = 1780943400L; // 2026-06-08T18:30:00Z
        OffsetDateTime parsed = timeOf("ts", seconds);
        assertEquals(OffsetDateTime.parse("2026-06-08T18:30:00Z").toInstant(), parsed.toInstant());
    }

    @Test
    void acceptsLocalDateTimeWithoutOffsetAsUtc() {
        OffsetDateTime parsed = timeOf("timestamp", "2026-06-08T18:30:00");
        assertEquals(OffsetDateTime.of(2026, 6, 8, 18, 30, 0, 0, ZoneOffset.UTC), parsed);
    }

    @Test
    void missingTimestampDefaultsToNow() {
        LogEventRequest event = normalizer.normalize(mapOf("message", "m"));
        assertNotNull(event.getOccurredAt());
        assertTrue(Duration.between(event.getOccurredAt(), OffsetDateTime.now()).abs().toSeconds() < 60);
    }

    @Test
    void unparseableTimestampFallsBackToNow() {
        LogEventRequest event = normalizer.normalize(mapOf("message", "m", "timestamp", "yesterday-ish"));
        assertNotNull(event.getOccurredAt());
        assertTrue(Duration.between(event.getOccurredAt(), OffsetDateTime.now()).abs().toSeconds() < 60);
    }

    // --- id normalization ---

    @Test
    void generatesIdWhenMissing() {
        LogEventRequest event = normalizer.normalize(mapOf("message", "m"));
        assertNotNull(event.getId());
        assertFalse(event.getId().isBlank());
    }

    @Test
    void acceptsIdAliases() {
        assertEquals("evt-1", normalizer.normalize(mapOf("message", "m", "eventId", "evt-1")).getId());
        assertEquals("7", normalizer.normalize(mapOf("message", "m", "event_id", 7)).getId());
    }

    // --- logger / trace / span aliases ---

    @Test
    void acceptsLoggerAliases() {
        assertEquals("MainActivity",
                normalizer.normalize(mapOf("message", "m", "tag", "MainActivity")).getLogger());
        assertEquals("com.example.Foo",
                normalizer.normalize(mapOf("message", "m", "logger_name", "com.example.Foo")).getLogger());
        assertEquals("app.worker",
                normalizer.normalize(mapOf("message", "m", "name", "app.worker")).getLogger());
    }

    @Test
    void acceptsSnakeCaseTraceAndSpanIds() {
        LogEventRequest event = normalizer.normalize(
                mapOf("message", "m", "trace_id", "t-1", "span_id", "s-1"));
        assertEquals("t-1", event.getTraceId());
        assertEquals("s-1", event.getSpanId());
    }

    // --- metadata: unknown fields are preserved ---

    @Test
    void unknownFieldsArePreservedInMetadata() {
        LogEventRequest event = normalizer.normalize(
                mapOf("message", "m", "pid", 123, "hostname", "web-1", "stack", "Error: boom\n  at x"));
        assertEquals(123, event.getMetadata().get("pid"));
        assertEquals("web-1", event.getMetadata().get("hostname"));
        assertEquals("Error: boom\n  at x", event.getMetadata().get("stack"));
    }

    @Test
    void explicitMetadataIsMergedWithLeftoverFields() {
        Map<String, Object> raw = mapOf(
                "message", "m",
                "metadata", Map.of("userId", 42),
                "extraField", "kept");
        LogEventRequest event = normalizer.normalize(raw);
        assertEquals(42, event.getMetadata().get("userId"));
        assertEquals("kept", event.getMetadata().get("extraField"));
    }

    @Test
    void noMetadataWhenNothingLeftOver() {
        LogEventRequest event = normalizer.normalize(mapOf("message", "m", "level", "INFO"));
        assertNull(event.getMetadata());
    }

    // --- realistic full payloads from common stacks ---

    @Test
    void normalizesPinoPayload() {
        Map<String, Object> raw = mapOf(
                "level", 50,
                "time", 1780943400000L,
                "pid", 3020,
                "hostname", "api-1",
                "msg", "request failed");

        LogEventRequest event = normalizer.normalize(raw);

        assertEquals(LogLevel.ERROR, event.getLevel());
        assertEquals("request failed", event.getMessage());
        assertEquals(OffsetDateTime.parse("2026-06-08T18:30:00Z").toInstant(),
                event.getOccurredAt().toInstant());
        assertEquals(3020, event.getMetadata().get("pid"));
    }

    @Test
    void normalizesPythonJsonLoggerPayload() {
        Map<String, Object> raw = mapOf(
                "levelname", "CRITICAL",
                "name", "app.tasks",
                "message", "worker crashed",
                "exc_info", "Traceback (most recent call last): ...");

        LogEventRequest event = normalizer.normalize(raw);

        assertEquals(LogLevel.ERROR, event.getLevel());
        assertEquals("worker crashed", event.getMessage());
        assertEquals("app.tasks", event.getLogger());
        assertTrue(event.getMetadata().get("exc_info").toString().startsWith("Traceback"));
    }

    @Test
    void normalizesLogstashPayload() {
        Map<String, Object> raw = mapOf(
                "@timestamp", "2026-06-08T18:30:00.123Z",
                "level", "WARN",
                "logger_name", "com.example.OrderService",
                "message", "slow query",
                "thread_name", "http-nio-8080-exec-1");

        LogEventRequest event = normalizer.normalize(raw);

        assertEquals(LogLevel.WARN, event.getLevel());
        assertEquals("com.example.OrderService", event.getLogger());
        assertEquals("http-nio-8080-exec-1", event.getMetadata().get("thread_name"));
    }

    // --- helpers ---

    private LogLevel levelOf(Object level) {
        return normalizer.normalize(mapOf("message", "m", "level", level)).getLevel();
    }

    private OffsetDateTime timeOf(String key, Object value) {
        return normalizer.normalize(mapOf("message", "m", key, value)).getOccurredAt();
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
