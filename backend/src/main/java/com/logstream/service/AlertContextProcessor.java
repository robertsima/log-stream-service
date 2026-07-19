package com.logstream.service;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.LogEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Emits one AlertTrigger per error signature for an app, with surrounding non-error
 * context: BEFORE_LOGS preceding the first occurrence and AFTER_LOGS following the last.
 *
 * <p>Similar ERRORs coalesce by signature (exception type, else digit-normalized message).
 * Distinct signatures each get their own alert. Non-ERROR events never open a group and
 * are the only events used as before/after context (no cross-signature bleed).
 *
 * <p>Groups close when enough trailing non-error context arrives (AFTER_LOGS), after a
 * quiet period since the last occurrence (MAX_WAIT), or when open too long (GROUP_WINDOW).
 */
public class AlertContextProcessor implements Processor<String, LogEvent, String, AlertTrigger> {

    public static final String STORE_NAME = "alert-context-buffer";

    private static final int BEFORE_LOGS = 10;
    private static final int AFTER_LOGS = 10;
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final Duration GROUP_WINDOW = Duration.ofSeconds(30);
    private static final Duration PUNCTUATE_INTERVAL = Duration.ofSeconds(2);
    private static final int MAX_SIGNATURE_KEY_LENGTH = 160;
    private static final int MAX_SIGNATURE_LABEL_LENGTH = 60;

    public record PendingGroup(
            LogEvent representative,
            String signatureLabel,
            List<LogEvent> before,
            List<LogEvent> after,
            int count,
            long firstSeenAtMs,
            long lastSeenAtMs
    ) { }

    private record ErrorSignature(String key, String label) { }

    public record AppBuffer(List<LogEvent> recent, Map<String, PendingGroup> groups) {
        static AppBuffer empty() {
            return new AppBuffer(new ArrayList<>(), new LinkedHashMap<>());
        }
    }

    private ProcessorContext<String, AlertTrigger> context;
    private KeyValueStore<String, AppBuffer> store;

    @Override
    public void init(ProcessorContext<String, AlertTrigger> context) {
        this.context = context;
        this.store = context.getStateStore(STORE_NAME);
        context.schedule(PUNCTUATE_INTERVAL, PunctuationType.WALL_CLOCK_TIME, this::flushExpired);
    }

    @Override
    public void process(Record<String, LogEvent> record) {
        String appId = record.key();
        LogEvent event = record.value();
        if (appId == null || event == null) {
            return;
        }

        AppBuffer buffer = store.get(appId);
        if (buffer == null) {
            buffer = AppBuffer.empty();
        }

        ErrorSignature signature = isError(event) ? signatureFor(event) : null;
        String matchedKey = signature != null && buffer.groups().containsKey(signature.key())
                ? signature.key()
                : null;

        // Only non-error events pad trailing context / complete AFTER_LOGS.
        if (!isError(event)) {
            List<String> completed = new ArrayList<>();
            for (Map.Entry<String, PendingGroup> entry : buffer.groups().entrySet()) {
                entry.getValue().after().add(event);
                if (entry.getValue().after().size() >= AFTER_LOGS) {
                    completed.add(entry.getKey());
                }
            }
            for (String key : completed) {
                emit(appId, buffer.groups().remove(key));
            }
        }

        if (signature != null) {
            long nowMs = context.currentSystemTimeMs();
            if (matchedKey != null) {
                PendingGroup existing = buffer.groups().get(matchedKey);
                buffer.groups().put(matchedKey, new PendingGroup(
                        existing.representative(),
                        existing.signatureLabel(),
                        existing.before(),
                        existing.after(),
                        existing.count() + 1,
                        existing.firstSeenAtMs(),
                        nowMs));
            } else {
                buffer.groups().put(signature.key(), new PendingGroup(
                        event,
                        signature.label(),
                        List.copyOf(buffer.recent()),
                        new ArrayList<>(),
                        1,
                        nowMs,
                        nowMs));
            }
        }

        if (!isError(event)) {
            buffer.recent().add(event);
            while (buffer.recent().size() > BEFORE_LOGS) {
                buffer.recent().remove(0);
            }
        }

        store.put(appId, buffer);
    }

    private void flushExpired(long nowMs) {
        try (KeyValueIterator<String, AppBuffer> all = store.all()) {
            while (all.hasNext()) {
                KeyValue<String, AppBuffer> entry = all.next();
                List<String> expiredKeys = entry.value.groups().entrySet().stream()
                        .filter(e -> nowMs - e.getValue().lastSeenAtMs() >= MAX_WAIT.toMillis()
                                || nowMs - e.getValue().firstSeenAtMs() >= GROUP_WINDOW.toMillis())
                        .map(Map.Entry::getKey)
                        .toList();
                if (expiredKeys.isEmpty()) {
                    continue;
                }
                for (String key : expiredKeys) {
                    emit(entry.key, entry.value.groups().remove(key));
                }
                store.put(entry.key, entry.value);
            }
        }
    }

    private void emit(String appId, PendingGroup group) {
        long windowEndMs = Math.max(group.lastSeenAtMs(), context.currentSystemTimeMs());
        List<LogEvent> surrounding = new ArrayList<>(group.before());
        surrounding.add(group.representative());
        surrounding.addAll(group.after());
        AlertTrigger alert = new AlertTrigger(
                UUID.fromString(appId),
                group.representative(),
                surrounding,
                group.count(),
                group.signatureLabel(),
                group.firstSeenAtMs(),
                windowEndMs);
        context.forward(new Record<>(appId, alert, windowEndMs));
    }

    /**
     * Only canonical ERROR level opens an alert group. INFO/WARN/DEBUG stay quiet even
     * when they carry exception/stackTrace fields.
     */
    private boolean isError(LogEvent event) {
        JsonNode payload = event.payload();
        return payload != null
                && payload.path("level").asText("").equalsIgnoreCase("ERROR");
    }

    private ErrorSignature signatureFor(LogEvent event) {
        JsonNode payload = event.payload();
        String exception = payload.path("exception").asText(null);
        if (exception != null && !exception.isBlank()) {
            String type = shortClassName(exception.split(":", 2)[0].trim());
            return new ErrorSignature("type:" + type.toLowerCase(Locale.ROOT), type);
        }

        String message = payload.path("message").asText("");
        String label = message.isBlank() ? "error" : truncate(message.strip(), MAX_SIGNATURE_LABEL_LENGTH);
        return new ErrorSignature("msg:" + normalizeMessage(message), label);
    }

    private String shortClassName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 && lastDot < fqcn.length() - 1 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = message.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[0-9]+", "#");
        return truncate(normalized, MAX_SIGNATURE_KEY_LENGTH);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static class Supplier implements ProcessorSupplier<String, LogEvent, String, AlertTrigger> {

        @Override
        public Processor<String, LogEvent, String, AlertTrigger> get() {
            return new AlertContextProcessor();
        }

        @Override
        public Set<StoreBuilder<?>> stores() {
            return Set.of(Stores.keyValueStoreBuilder(
                    Stores.inMemoryKeyValueStore(STORE_NAME),
                    Serdes.String(),
                    new JacksonJsonSerde<>(AppBuffer.class))
                    .withLoggingDisabled());
        }
    }
}
