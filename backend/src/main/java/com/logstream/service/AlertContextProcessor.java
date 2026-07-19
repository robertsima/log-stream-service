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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Emits an AlertTrigger for each error log with surrounding context:
 * the BEFORE_LOGS events preceding the error and the AFTER_LOGS events
 * following it. Because the "after" events haven't arrived yet when the
 * error does, each alert is held open in the state store until enough
 * later events show up, or MAX_WAIT elapses (quiet apps still alert,
 * just with fewer after-events).
 *
 *
 */
public class AlertContextProcessor implements Processor<String, LogEvent, String, AlertTrigger> {

    public static final String STORE_NAME = "alert-context-buffer";

    private static final int BEFORE_LOGS = 10;
    private static final int AFTER_LOGS = 10;
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final Duration PUNCTUATE_INTERVAL = Duration.ofSeconds(2);

    // held in the store per app id; lists deserialize as mutable ArrayLists
    public record PendingAlert(LogEvent error, List<LogEvent> before, List<LogEvent> after, long firstSeenAtMs) { }

    public record AppBuffer(List<LogEvent> recent, List<PendingAlert> pending) {
        static AppBuffer empty() {
            return new AppBuffer(new ArrayList<>(), new ArrayList<>());
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

        // every event (errors included) counts as "after" context for alerts still waiting
        List<PendingAlert> completed = new ArrayList<>();
        for (PendingAlert pending : buffer.pending()) {
            pending.after().add(event);
            if (pending.after().size() >= AFTER_LOGS) {
                completed.add(pending);
            }
        }
        for (PendingAlert done : completed) {
            emit(appId, done, record.timestamp());
        }
        buffer.pending().removeAll(completed);

        if (isError(event)) {
            buffer.pending().add(new PendingAlert(
                    event,
                    List.copyOf(buffer.recent()),
                    new ArrayList<>(),
                    context.currentSystemTimeMs()));
        }

        buffer.recent().add(event);
        while (buffer.recent().size() > BEFORE_LOGS) {
            buffer.recent().remove(0);
        }

        store.put(appId, buffer);
    }

    private void flushExpired(long nowMs) {
        try (KeyValueIterator<String, AppBuffer> all = store.all()) {
            while (all.hasNext()) {
                KeyValue<String, AppBuffer> entry = all.next();
                List<PendingAlert> expired = entry.value.pending().stream()
                        .filter(p -> nowMs - p.firstSeenAtMs() >= MAX_WAIT.toMillis())
                        .toList();
                if (expired.isEmpty()) {
                    continue;
                }
                for (PendingAlert done : expired) {
                    emit(entry.key, done, nowMs);
                }
                entry.value.pending().removeAll(expired);
                store.put(entry.key, entry.value);
            }
        }
    }

    private void emit(String appId, PendingAlert pending, long timestampMs) {
        List<LogEvent> surrounding = new ArrayList<>(pending.before());
        surrounding.add(pending.error());
        surrounding.addAll(pending.after());
        AlertTrigger alert = new AlertTrigger(UUID.fromString(appId), pending.error(), surrounding);
        context.forward(new Record<>(appId, alert, timestampMs));
    }

    private boolean isError(LogEvent event) {
        JsonNode payload = event.payload();
        return payload.path("level").asText("").equalsIgnoreCase("ERROR")
                || payload.has("exception")
                || payload.has("stackTrace");
    }

    public static class Supplier implements ProcessorSupplier<String, LogEvent, String, AlertTrigger> {

        @Override
        public Processor<String, LogEvent, String, AlertTrigger> get() {
            return new AlertContextProcessor();
        }

        @Override
        public Set<StoreBuilder<?>> stores() {
            // in-memory + no changelog: context buffers are ephemeral working state,
            // not log storage (MVP constraint) — lost on restart/rebalance, which is fine
            return Set.of(Stores.keyValueStoreBuilder(
                    Stores.inMemoryKeyValueStore(STORE_NAME),
                    Serdes.String(),
                    new JacksonJsonSerde<>(AppBuffer.class))
                    .withLoggingDisabled());
        }
    }
}
