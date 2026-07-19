package unit.com.logstream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.logstream.config.OpenAIModelConfig;
import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.LogEvent;
import com.logstream.service.KafkaLogEventNormalizer;
import com.logstream.service.analysis.AlertAnalysisServiceImpl;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kafka-path normalization: RawLogEvent aliases → canonical payload for
 * AlertContextProcessor / alert analysis. Demo canonical payloads pass through.
 */
class KafkaLogEventNormalizerTest {

    private static final UUID APP_ID = UUID.randomUUID();
    private static final String APP_NAME = "keycloak-demo";

    private final KafkaLogEventNormalizer normalizer = new KafkaLogEventNormalizer();

    @Test
    void canonicalDemoPayload_passesThrough() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("id", "demo-error-1");
        raw.put("level", "ERROR");
        raw.put("message", "Failed to process payment for user 123.");
        raw.put("occurredAt", "2026-06-08T18:30:00Z");
        raw.put("logger", "com.example.PaymentService");
        raw.put("traceId", "trace-abc");

        List<LogEvent> result = normalizer.normalizeOrDrop(event(raw));

        assertThat(result).hasSize(1);
        JsonNode payload = result.get(0).payload();
        assertThat(payload.path("id").asText()).isEqualTo("demo-error-1");
        assertThat(payload.path("level").asText()).isEqualTo("ERROR");
        assertThat(payload.path("message").asText()).isEqualTo("Failed to process payment for user 123.");
        assertThat(payload.path("occurredAt").asText()).isEqualTo("2026-06-08T18:30:00Z");
        assertThat(payload.path("logger").asText()).isEqualTo("com.example.PaymentService");
        assertThat(payload.path("traceId").asText()).isEqualTo("trace-abc");
    }

    @Test
    void aliasedFields_normalizeToCanonicalPayload() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("levelname", "ERROR");
        raw.put("msg", "Failed to authenticate user: invalid credentials");
        raw.put("loggerName", "org.keycloak.services.managers.AuthenticationManager");
        raw.put("@timestamp", "2026-07-19T18:40:52.510Z");
        raw.put("realm", "master");

        List<LogEvent> result = normalizer.normalizeOrDrop(event(raw));

        assertThat(result).hasSize(1);
        JsonNode payload = result.get(0).payload();
        assertThat(payload.path("level").asText()).isEqualTo("ERROR");
        assertThat(payload.path("message").asText()).isEqualTo("Failed to authenticate user: invalid credentials");
        assertThat(payload.path("logger").asText()).isEqualTo("org.keycloak.services.managers.AuthenticationManager");
        assertThat(payload.path("occurredAt").asText()).isEqualTo("2026-07-19T18:40:52.510Z");
        assertThat(payload.path("metadata").path("realm").asText()).isEqualTo("master");
        assertThat(result.get(0).appId()).isEqualTo(APP_ID);
        assertThat(result.get(0).appName()).isEqualTo(APP_NAME);
    }

    @Test
    void exceptionAndStackTrace_stayTopLevel_forErrorGrouping() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("msg", "boom");
        raw.put("exception", "java.lang.NullPointerException: Cannot invoke foo()");
        raw.put("stackTrace", "at com.example.Foo.bar(Foo.java:42)");

        List<LogEvent> result = normalizer.normalizeOrDrop(event(raw));

        assertThat(result).hasSize(1);
        JsonNode payload = result.get(0).payload();
        assertThat(payload.path("exception").asText())
                .isEqualTo("java.lang.NullPointerException: Cannot invoke foo()");
        assertThat(payload.path("stackTrace").asText()).isEqualTo("at com.example.Foo.bar(Foo.java:42)");
        assertThat(payload.path("metadata").has("exception")).isFalse();
        assertThat(payload.path("metadata").has("stackTrace")).isFalse();
    }

    @Test
    void eventWithoutMessage_isDroppedNotThrown() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("level", "ERROR");

        assertThat(normalizer.normalizeOrDrop(event(raw))).isEmpty();
    }

    @Test
    void nonObjectPayload_isDroppedNotThrown() {
        LogEvent logEvent = new LogEvent(APP_ID, APP_NAME, Instant.now(),
                JsonNodeFactory.instance.textNode("just a string"));

        assertThat(normalizer.normalizeOrDrop(logEvent)).isEmpty();
    }

    @Test
    void normalizesCommonLevelStringsAndAliases() {
        assertThat(levelOf("warning")).isEqualTo("WARN");
        assertThat(levelOf("critical")).isEqualTo("ERROR");
        assertThat(levelOf("severe")).isEqualTo("ERROR");
        assertThat(levelOf(50)).isEqualTo("ERROR"); // Pino
        assertThat(levelOf(6)).isEqualTo("ERROR"); // Android
        assertThat(canonicalize(mapOf("msg", "m", "levelname", "WARNING")).path("level").asText())
                .isEqualTo("WARN");
    }

    @Test
    void missingLevelDefaultsToInfo() {
        assertThat(canonicalize(mapOf("message", "m")).path("level").asText()).isEqualTo("INFO");
    }

    @Test
    void acceptsTimestampAliasesAndEpochs() {
        OffsetDateTime expected = OffsetDateTime.parse("2026-06-08T18:30:00Z");
        assertThat(OffsetDateTime.parse(canonicalize(mapOf("message", "m", "@timestamp", "2026-06-08T18:30:00Z"))
                .path("occurredAt").asText())).isEqualTo(expected);
        assertThat(OffsetDateTime.parse(canonicalize(mapOf("message", "m", "time", 1780943400000L))
                .path("occurredAt").asText()).toInstant()).isEqualTo(expected.toInstant());
        assertThat(OffsetDateTime.parse(canonicalize(mapOf("message", "m", "ts", 1780943400L))
                .path("occurredAt").asText()).toInstant()).isEqualTo(expected.toInstant());
    }

    @Test
    void missingTimestampDefaultsToNow() {
        OffsetDateTime occurredAt = OffsetDateTime.parse(
                canonicalize(mapOf("message", "m")).path("occurredAt").asText());
        assertThat(Duration.between(occurredAt, OffsetDateTime.now(ZoneOffset.UTC)).abs().toSeconds())
                .isLessThan(60);
    }

    @Test
    void unknownFieldsArePreservedInMetadata() {
        ObjectNode payload = canonicalize(mapOf("message", "m", "pid", 123, "hostname", "web-1"));
        assertThat(payload.path("metadata").path("pid").asInt()).isEqualTo(123);
        assertThat(payload.path("metadata").path("hostname").asText()).isEqualTo("web-1");
    }

    @Test
    void normalizesPinoAndPythonJsonLoggerPayloads() {
        ObjectNode pino = canonicalize(mapOf(
                "level", 50,
                "time", 1780943400000L,
                "pid", 3020,
                "msg", "request failed"));
        assertThat(pino.path("level").asText()).isEqualTo("ERROR");
        assertThat(pino.path("message").asText()).isEqualTo("request failed");

        ObjectNode py = canonicalize(mapOf(
                "levelname", "CRITICAL",
                "name", "app.tasks",
                "message", "worker crashed"));
        assertThat(py.path("level").asText()).isEqualTo("ERROR");
        assertThat(py.path("logger").asText()).isEqualTo("app.tasks");
    }

    @Test
    void normalizedAliasedEvent_producesReadablePromptLine() {
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("levelname", "ERROR");
        raw.put("msg", "Failed to authenticate user: invalid credentials");
        raw.put("loggerName", "org.keycloak.services.managers.AuthenticationManager");
        raw.put("@timestamp", "2026-07-19T18:40:52.510Z");

        LogEvent normalized = normalizer.normalizeOrDrop(event(raw)).get(0);
        AlertTrigger alert = new AlertTrigger(APP_ID, normalized, List.of(normalized), 1, "auth failure", 0, 0);

        OpenAIModelConfig config = mock(OpenAIModelConfig.class);
        when(config.formatPreview(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        String prompt = new AlertAnalysisServiceImpl(config).previewPrompt(alert).prompt();

        assertThat(prompt).contains(
                "ERROR AuthenticationManager: Failed to authenticate user: invalid credentials"
                        + " [at=2026-07-19T18:40:52.510Z]");
        assertThat(prompt).doesNotContain("?:");
    }

    private LogEvent event(ObjectNode payload) {
        return new LogEvent(APP_ID, APP_NAME, Instant.now(), payload);
    }

    private String levelOf(Object level) {
        return canonicalize(mapOf("message", "m", "level", level)).path("level").asText();
    }

    private ObjectNode canonicalize(Map<String, Object> raw) {
        return normalizer.canonicalize(raw);
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
