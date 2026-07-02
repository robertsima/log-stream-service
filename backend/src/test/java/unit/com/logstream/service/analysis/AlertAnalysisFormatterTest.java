package unit.com.logstream.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.logstream.service.analysis.AlertAnalysisFormatter;

class AlertAnalysisFormatterTest {

    @Test
    void formatModelResponse_shouldRenderStructuredJson() {
        String raw = """
                {
                  "confidence": "high",
                  "rootCause": "Stripe card_declined in PaymentService.processPayment",
                  "affectedComponents": ["PaymentService", "POST /api/v1/checkout"],
                  "urgency": "medium",
                  "remediation": [
                    "Inspect Stripe request req_1PqR2s3T4u5V6w for order ord_8f2k9m",
                    "Add retry guard in PaymentService.processPayment before inventory release"
                  ]
                }
                """;

        String formatted = AlertAnalysisFormatter.formatModelResponse(raw);

        assertThat(formatted)
                .startsWith("Analysis")
                .contains("Confidence: HIGH")
                .contains("Urgency: MEDIUM")
                .contains("Root cause\nStripe card_declined")
                .contains("Affected components")
                .contains("• PaymentService")
                .contains("Remediation")
                .contains("1. Inspect Stripe request");
    }

    @Test
    void formatModelResponse_shouldReturnRawTextWhenJsonInvalid() {
        assertThat(AlertAnalysisFormatter.formatModelResponse("plain text"))
                .isEqualTo("plain text");
    }
}
