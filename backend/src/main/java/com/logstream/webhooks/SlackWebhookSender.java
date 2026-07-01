package com.logstream.webhooks;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertBucket;
import com.logstream.generated.model.LogEventRequest;

@Service
public class SlackWebhookSender {

    private final RestClient restClient;
    private final int maxMessages;

    public SlackWebhookSender(
            RestClient.Builder builder,
            @Value("${alerts.max-messages-per-alert:5}") int maxMessages
    ) {
        this.restClient = builder.build();
        this.maxMessages = maxMessages;
    }

    public void sendTest(AlertDestination destination) {
        Map<String, Object> payload = Map.of(
                "text",
                """
                :white_check_mark: *Log Stream Service Test Alert*
                Your Slack alert destination is working.

                *Destination:* %s
                *Type:* %s
                """.formatted(destination.getName(), destination.getDestinationType())
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendAggregatedAlert(AlertDestination destination, AlertBucket bucket) {
        if (bucket.getEvents().isEmpty()) {
            return;
        }

        LogEventRequest first = bucket.getEvents().get(0);

        Map<String, Object> payload = Map.of(
                "text", buildAlertText(bucket, first)
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private String buildAlertText(AlertBucket bucket, LogEventRequest first) {
        StringBuilder sb = new StringBuilder();

        sb.append(":rotating_light: *Error Alert*")
                .append("\n")
                .append("*Count:* ").append(bucket.count()).append("\n")
                .append("*Logger:* `").append(safe(first.getLogger())).append("`").append("\n")
                .append("*Trace ID:* `").append(safe(first.getTraceId())).append("`").append("\n\n")
                .append("*Messages:*")
                .append("\n");

        bucket.getEvents().stream()
                .limit(maxMessages)
                .forEach(event -> sb.append("• ")
                        .append(truncate(event.getMessage(), 250))
                        .append("\n"));

        if (bucket.count() > maxMessages) {
            sb.append("\n...and ")
                    .append(bucket.count() - maxMessages)
                    .append(" more.");
        }

        return truncate(sb.toString(), 3000);
    }

    private String safe(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "N/A";
        }

        return String.valueOf(value);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "N/A";
        }

        return value.length() <= max
                ? value
                : value.substring(0, max - 3) + "...";
    }
}
