package unit.com.logstream.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.LogEvent;
import com.logstream.service.AlertContextProcessor;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Per-signature coalescing: similar ERRORs merge by signature; distinct signatures
 * each emit their own AlertTrigger. Non-ERROR context only; AFTER_LOGS / MAX_WAIT /
 * GROUP_WINDOW close groups.
 */
@SuppressWarnings("unchecked")
class AlertContextProcessorTest {

    private static final String APP_NAME = "checkout-service";

    private AlertContextProcessor processor;
    private ProcessorContext<String, AlertTrigger> context;
    private KeyValueStore<String, AlertContextProcessor.AppBuffer> store;
    private Map<String, AlertContextProcessor.AppBuffer> backing;
    private final AtomicLong clock = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        backing = new HashMap<>();
        store = mock(KeyValueStore.class);
        when(store.get(any())).thenAnswer(inv -> backing.get((String) inv.getArgument(0)));
        doAnswer(inv -> {
            backing.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(store).put(any(), any());
        when(store.all()).thenAnswer(inv -> iteratorOver(backing));

        context = mock(ProcessorContext.class);
        when(context.<KeyValueStore<String, AlertContextProcessor.AppBuffer>>getStateStore(AlertContextProcessor.STORE_NAME))
                .thenReturn(store);
        when(context.currentSystemTimeMs()).thenAnswer(inv -> clock.get());

        processor = new AlertContextProcessor();
        processor.init(context);
    }

    @Test
    void similarErrors_groupIntoOneAlertTrigger_withOccurrenceCount() {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, errorEvent("Cannot invoke foo()", "java.lang.NullPointerException")));
        clock.set(1_000);
        processor.process(recordFor(appId, errorEvent("Cannot invoke bar()", "java.lang.NullPointerException")));
        clock.set(2_000);
        processor.process(recordFor(appId, errorEvent("Cannot invoke baz()", "java.lang.NullPointerException")));

        for (int i = 0; i < 10; i++) {
            clock.set(3_000 + i);
            processor.process(recordFor(appId, infoEvent("heartbeat " + i)));
        }

        List<AlertTrigger> forwarded = capturedAlerts();
        assertThat(forwarded).hasSize(1);
        AlertTrigger alert = forwarded.get(0);
        assertThat(alert.occurrenceCount()).isEqualTo(3);
        assertThat(alert.signatureLabel()).isEqualTo("NullPointerException");
        assertThat(alert.triggeringEvent().payload().path("message").asString("")).isEqualTo("Cannot invoke foo()");
        assertThat(alert.windowEndMs() - alert.windowStartMs()).isGreaterThanOrEqualTo(3_000);
        assertThat(alert.context()).allMatch(e ->
                e == alert.triggeringEvent()
                        || !"ERROR".equalsIgnoreCase(e.payload().path("level").asText("")));
    }

    @Test
    void distinctSignatures_eachEmitTheirOwnAlert() {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, errorEvent("npe here", "java.lang.NullPointerException")));
        clock.set(1_000);
        processor.process(recordFor(appId, errorEvent("bad state here", "java.lang.IllegalStateException")));

        for (int i = 0; i < 10; i++) {
            clock.set(2_000 + i);
            processor.process(recordFor(appId, infoEvent("heartbeat " + i)));
        }

        List<AlertTrigger> alerts = capturedAlerts();
        assertThat(alerts).hasSize(2);
        assertThat(alerts).allSatisfy(a -> assertThat(a.occurrenceCount()).isEqualTo(1));
        assertThat(alerts).extracting(AlertTrigger::signatureLabel)
                .containsExactlyInAnyOrder("NullPointerException", "IllegalStateException");

        for (AlertTrigger alert : alerts) {
            long otherErrors = alert.context().stream()
                    .filter(e -> e != alert.triggeringEvent())
                    .filter(e -> "ERROR".equalsIgnoreCase(e.payload().path("level").asText("")))
                    .count();
            assertThat(otherErrors).isZero();
        }
    }

    @Test
    void otherErrors_doNotCountTowardAfterLogsOrBleedIntoContext() throws Exception {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, errorEvent("smtp down", "jakarta.mail.MessagingException")));

        for (int i = 0; i < 9; i++) {
            clock.set(100 + i);
            processor.process(recordFor(appId, errorEvent("other failure " + i, "java.lang.RuntimeException")));
        }
        assertThat(capturedAlerts()).isEmpty();

        clock.set(11_000);
        invokeFlushExpired(11_000);

        List<AlertTrigger> alerts = capturedAlerts();
        AlertTrigger smtp = alerts.stream()
                .filter(a -> a.signatureLabel().equals("MessagingException"))
                .findFirst()
                .orElseThrow();
        assertThat(smtp.occurrenceCount()).isEqualTo(1);
        assertThat(smtp.context()).filteredOn(e -> e != smtp.triggeringEvent()).isEmpty();
        assertThat(smtp.windowEndMs() - smtp.windowStartMs()).isEqualTo(11_000);
    }

    @Test
    void quietGroup_flushesAfterMaxWaitSinceLastOccurrence() throws Exception {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, errorEvent("boom", "java.lang.RuntimeException")));

        clock.set(11_000);
        invokeFlushExpired(11_000);

        List<AlertTrigger> alerts = capturedAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).occurrenceCount()).isEqualTo(1);
        assertThat(alerts.get(0).windowEndMs() - alerts.get(0).windowStartMs()).isEqualTo(11_000);
    }

    @Test
    void sustainedBurst_flushesAtGroupWindowCap_evenWhileStillActive() throws Exception {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, errorEvent("boom 1", "java.lang.RuntimeException")));
        clock.set(5_000);
        processor.process(recordFor(appId, errorEvent("boom 2", "java.lang.RuntimeException")));

        clock.set(31_000);
        invokeFlushExpired(31_000);

        List<AlertTrigger> alerts = capturedAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).occurrenceCount()).isEqualTo(2);
        assertThat(alerts.get(0).windowEndMs() - alerts.get(0).windowStartMs()).isEqualTo(31_000);
    }

    @Test
    void nonErrorLevels_doNotOpenAlertGroups_evenWithExceptionOrStackTrace() throws Exception {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(recordFor(appId, leveledEvent("INFO", "heartbeat", null, null)));
        clock.set(1_000);
        processor.process(recordFor(appId, leveledEvent("WARN", "login failed", null, "at foo.Bar.baz(Bar.java:1)")));
        clock.set(2_000);
        processor.process(recordFor(appId, leveledEvent("DEBUG", "trace detail",
                "java.lang.RuntimeException: ignored", null)));

        clock.set(20_000);
        invokeFlushExpired(20_000);

        assertThat(capturedAlerts()).isEmpty();
    }

    @Test
    void usesEventAppId_notKafkaKey_soSimilarErrorsStillCoalesce() throws Exception {
        UUID appId = UUID.randomUUID();

        clock.set(0);
        processor.process(new Record<>(UUID.randomUUID().toString(),
                new LogEvent(appId, APP_NAME, Instant.now(), errorPayload(
                        "worker crashed while settling invoice 88421", null)), 0));
        clock.set(500);
        processor.process(new Record<>(UUID.randomUUID().toString(),
                new LogEvent(appId, APP_NAME, Instant.now(), errorPayload(
                        "worker crashed while settling invoice 88422", null)), 500));

        clock.set(15_000);
        invokeFlushExpired(15_000);

        List<AlertTrigger> alerts = capturedAlerts();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).appId()).isEqualTo(appId);
        assertThat(alerts.get(0).occurrenceCount()).isEqualTo(2);
    }

    private static ObjectNode errorPayload(String message, String exceptionType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("level", "ERROR");
        node.put("message", message);
        if (exceptionType != null) {
            node.put("exception", exceptionType + ": " + message);
        }
        return node;
    }

    private List<AlertTrigger> capturedAlerts() {
        ArgumentCaptor<Record> captor = ArgumentCaptor.forClass(Record.class);
        verify(context, org.mockito.Mockito.atLeast(0)).forward(captor.capture());
        List<AlertTrigger> result = new ArrayList<>();
        for (Record r : captor.getAllValues()) {
            result.add((AlertTrigger) r.value());
        }
        return result;
    }

    private void invokeFlushExpired(long nowMs) throws Exception {
        Method m = AlertContextProcessor.class.getDeclaredMethod("flushExpired", long.class);
        m.setAccessible(true);
        m.invoke(processor, nowMs);
    }

    private Record<String, LogEvent> recordFor(UUID appId, LogEvent event) {
        LogEvent withApp = new LogEvent(appId, event.appName(), event.receivedAt(), event.payload());
        return new Record<>(appId.toString(), withApp, clock.get());
    }

    private LogEvent errorEvent(String message, String exceptionType) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("level", "ERROR");
        node.put("message", message);
        if (exceptionType != null) {
            node.put("exception", exceptionType + ": " + message);
        }
        return new LogEvent(null, APP_NAME, Instant.now(), node);
    }

    private LogEvent infoEvent(String message) {
        return leveledEvent("INFO", message, null, null);
    }

    private LogEvent leveledEvent(String level, String message, String exception, String stackTrace) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("level", level);
        node.put("message", message);
        if (exception != null) {
            node.put("exception", exception);
        }
        if (stackTrace != null) {
            node.put("stackTrace", stackTrace);
        }
        return new LogEvent(null, APP_NAME, Instant.now(), node);
    }

    private static KeyValueIterator<String, AlertContextProcessor.AppBuffer> iteratorOver(
            Map<String, AlertContextProcessor.AppBuffer> source) {
        List<Map.Entry<String, AlertContextProcessor.AppBuffer>> snapshot = new ArrayList<>(source.entrySet());
        java.util.Iterator<Map.Entry<String, AlertContextProcessor.AppBuffer>> it = snapshot.iterator();
        return new KeyValueIterator<>() {
            @Override
            public void close() {
            }

            @Override
            public String peekNextKey() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public KeyValue<String, AlertContextProcessor.AppBuffer> next() {
                Map.Entry<String, AlertContextProcessor.AppBuffer> e = it.next();
                return KeyValue.pair(e.getKey(), e.getValue());
            }
        };
    }
}
