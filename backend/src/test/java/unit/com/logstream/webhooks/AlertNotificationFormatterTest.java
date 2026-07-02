package unit.com.logstream.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.logstream.service.alerting.AlertBucket;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.webhooks.AlertNotificationFormatter;
import com.logstream.webhooks.AlertSummary;

class AlertNotificationFormatterTest {

    @Test
    void summarize_shouldExtractHostAndEnvironmentFromMetadataKeyAliases() {
        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "fp-1");
        bucket.setAppName("Checkout Service");
        bucket.add(new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Payment failed")
                .occurredAt(OffsetDateTime.now())
                .metadata(Map.of("hostname", "web-3", "env", "production")));

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, 5);

        assertThat(summary.host()).isEqualTo("web-3");
        assertThat(summary.environment()).isEqualTo("production");
    }

    @Test
    void summarize_shouldLeaveHostAndEnvironmentNullWhenMetadataAbsent() {
        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "fp-1");
        bucket.add(new LogEventRequest()
                .id("event-1")
                .level(LogLevel.ERROR)
                .message("Payment failed")
                .occurredAt(OffsetDateTime.now()));

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, 5);

        assertThat(summary.host()).isNull();
        assertThat(summary.environment()).isNull();
    }

    @Test
    void buildSlackText_shouldPutAnalysisBeforeMessagesAndIncludeAppName() {
        AlertSummary summary = new AlertSummary(
                2,
                "Checkout Service",
                "com.example.PaymentService",
                "trace-123",
                "fp-abc",
                List.of("error one", "error two"),
                0,
                "Analysis\nConfidence: HIGH\n\nRoot cause\nStripe declined",
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:02:00 UTC",
                "2m",
                null,
                null);

        String text = AlertNotificationFormatter.buildSlackText(summary);

        assertThat(text.indexOf("*Analysis*")).isLessThan(text.indexOf("*Messages*"));
        assertThat(text).contains("Confidence: HIGH");
        assertThat(text).contains("Checkout Service");
    }

    @Test
    void buildSlackText_shouldIncludeHostAndEnvironmentWhenPresent() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.PaymentService",
                "trace-123",
                "fp-abc",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                "web-3",
                "production");

        String text = AlertNotificationFormatter.buildSlackText(summary);

        assertThat(text).contains("*Host:* `web-3`");
        assertThat(text).contains("*Environment:* `production`");
    }

    @Test
    void buildSlackText_shouldOmitHostAndEnvironmentWhenAbsent() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.PaymentService",
                "trace-123",
                "fp-abc",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                null,
                null);

        String text = AlertNotificationFormatter.buildSlackText(summary);

        assertThat(text).doesNotContain("*Host:*").doesNotContain("*Environment:*");
    }

    @Test
    void buildDiscordDescription_shouldPutAnalysisBeforeMessages() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                "Analysis\nConfidence: MEDIUM\n\nRoot cause\nNull pointer",
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                null,
                null);

        String text = AlertNotificationFormatter.buildDiscordDescription(summary);

        assertThat(text.indexOf("**Analysis**")).isLessThan(text.indexOf("**Messages**"));
    }

    @Test
    void buildDiscordTitle_shouldIncludeAppName() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                null,
                null);

        assertThat(AlertNotificationFormatter.buildDiscordTitle(summary)).isEqualTo("Error Alert — Checkout Service");
    }

    @Test
    void buildDiscordFields_shouldIncludeAppNameAndOmitFingerprint() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                null,
                null);

        assertThat(AlertNotificationFormatter.buildDiscordFields(summary))
                .anySatisfy(field -> assertThat(field).isEqualTo(
                        java.util.Map.of("name", "App", "value", "Checkout Service", "inline", true)))
                .noneSatisfy(field -> assertThat(field.toString()).containsIgnoringCase("fingerprint"));
    }

    @Test
    void buildDiscordFields_shouldIncludeHostAndEnvironmentWhenPresent() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                "web-3",
                "production");

        assertThat(AlertNotificationFormatter.buildDiscordFields(summary))
                .anySatisfy(field -> assertThat(field).isEqualTo(
                        java.util.Map.of("name", "Host", "value", "web-3", "inline", true)))
                .anySatisfy(field -> assertThat(field).isEqualTo(
                        java.util.Map.of("name", "Environment", "value", "production", "inline", true)));
    }

    @Test
    void buildDiscordFields_shouldOmitHostAndEnvironmentWhenAbsent() {
        AlertSummary summary = new AlertSummary(
                1,
                "Checkout Service",
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                null,
                "2026-07-01 18:00:00 UTC",
                "2026-07-01 18:00:00 UTC",
                null,
                null,
                null);

        assertThat(AlertNotificationFormatter.buildDiscordFields(summary))
                .noneSatisfy(field -> assertThat(field.toString()).containsIgnoringCase("host"))
                .noneSatisfy(field -> assertThat(field.toString()).containsIgnoringCase("environment"));
    }
}
