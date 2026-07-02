package unit.com.logstream.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.logstream.service.alerting.AlertBucket;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.service.AlertAggregationServiceImpl;

class AlertAggregationServiceTest {

    private final AlertAggregationServiceImpl alertAggregationService = new AlertAggregationServiceImpl();

    @Test
    void accept_shouldIgnoreNonErrorLogs() {
        UUID appId = UUID.randomUUID();

        LogEventRequest event = new LogEventRequest()
                .id("event-1")
                .level(LogLevel.INFO)
                .message("User logged in")
                .occurredAt(OffsetDateTime.now())
                .logger("com.example.AuthService");

        alertAggregationService.accept(appId, "Test App", event);

        Map<String, AlertBucket> buckets = alertAggregationService.drainBuckets();

        assertThat(buckets).isEmpty();
    }

    @Test
    void accept_shouldAddErrorLogToBucket() {
        UUID appId = UUID.randomUUID();

        LogEventRequest event = new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Payment failed for user 123")
                .occurredAt(OffsetDateTime.now())
                .logger("com.example.PaymentService")
                .traceId("trace-1");

        alertAggregationService.accept(appId, "Test App", event);

        Map<String, AlertBucket> buckets = alertAggregationService.drainBuckets();

        assertThat(buckets).hasSize(1);

        AlertBucket bucket = buckets.values().iterator().next();

        assertThat(bucket.getAppId()).isEqualTo(appId);
        assertThat(bucket.getAppName()).isEqualTo("Test App");
        assertThat(bucket.count()).isEqualTo(1);
        assertThat(bucket.getEvents().get(0).getMessage()).isEqualTo("Payment failed for user 123");
    }

    @Test
    void accept_shouldAggregateMatchingErrorsWithDifferentNumbers() {
        UUID appId = UUID.randomUUID();

        LogEventRequest event1 = new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Payment failed for user 123")
                .occurredAt(OffsetDateTime.now())
                .logger("com.example.PaymentService");

        LogEventRequest event2 = new LogEventRequest()
                .id("event-2")
                .level(LogLevel.ERROR)
                .message("Payment failed for user 456")
                .occurredAt(OffsetDateTime.now())
                .logger("com.example.PaymentService");

        alertAggregationService.accept(appId, "Test App", event1);
        alertAggregationService.accept(appId, "Test App", event2);

        Map<String, AlertBucket> buckets = alertAggregationService.drainBuckets();

        assertThat(buckets).hasSize(1);
        assertThat(buckets.values().iterator().next().count()).isEqualTo(2);
    }

    @Test
    void drainBuckets_shouldClearExistingBuckets() {
        UUID appId = UUID.randomUUID();

        LogEventRequest event = new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Something failed")
                .occurredAt(OffsetDateTime.now())
                .logger("com.example.TestService");

        alertAggregationService.accept(appId, "Test App", event);

        assertThat(alertAggregationService.drainBuckets()).hasSize(1);
        assertThat(alertAggregationService.drainBuckets()).isEmpty();
    }
}