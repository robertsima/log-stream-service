package unit.com.logstream.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.logstream.webhooks.AlertNotificationFormatter;
import com.logstream.webhooks.AlertSummary;

class AlertNotificationFormatterTest {

    @Test
    void buildSlackText_shouldPutAnalysisBeforeAlertDetails() {
        AlertSummary summary = new AlertSummary(
                2,
                "com.example.PaymentService",
                "trace-123",
                "fp-abc",
                List.of("error one", "error two"),
                0,
                "Analysis\nConfidence: HIGH\n\nRoot cause\nStripe declined");

        String text = AlertNotificationFormatter.buildSlackText(summary);

        assertThat(text.indexOf("*Analysis*")).isLessThan(text.indexOf("*Alert details*"));
        assertThat(text.indexOf("*Analysis*")).isLessThan(text.indexOf("*Messages*"));
        assertThat(text).contains("Confidence: HIGH");
    }

    @Test
    void buildDiscordDescription_shouldPutAnalysisBeforeMessages() {
        AlertSummary summary = new AlertSummary(
                1,
                "com.example.Service",
                "trace-456",
                "fp-def",
                List.of("boom"),
                0,
                "Analysis\nConfidence: MEDIUM\n\nRoot cause\nNull pointer");

        String text = AlertNotificationFormatter.buildDiscordDescription(summary);

        assertThat(text.indexOf("**Analysis**")).isLessThan(text.indexOf("**Alert details**"));
        assertThat(text.indexOf("**Analysis**")).isLessThan(text.indexOf("**Messages**"));
    }
}
