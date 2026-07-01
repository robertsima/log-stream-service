package com.logstream.webhooks;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertBucket;
import com.logstream.generated.model.LogEventRequest;

@Service
public class DiscordWebhookSender {

    private final RestClient restClient;
    private final int maxMessages;

    public DiscordWebhookSender(
            RestClient.Builder builder,
            @Value("${alerts.max-messages-per-alert:5}") int maxMessages
    ) {
        this.restClient = builder.build();
        this.maxMessages = maxMessages;
    }

    public void sendTest(AlertDestination destination) {
        Map<String, Object> payload = Map.of(
                "content", "Log Stream Service test alert is working.",
                "embeds", List.of(Map.of(
                        "title", "Log Stream Service Test Alert",
                        "description", "Your Discord alert destination is working.",
                        "fields", List.of(
                                Map.of("name", "Destination", "value", safe(destination.getName()), "inline", true),
                                Map.of("name", "Type", "value", safe(destination.getDestinationType()), "inline", true)
                        )
                ))
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
                "embeds", List.of(Map.of(
                        "title", "Error Alert",
                        "description", buildDescription(bucket),
                        "fields", List.of(
                                Map.of("name", "Count", "value", String.valueOf(bucket.count()), "inline", true),
                                Map.of("name", "Logger", "value", safe(first.getLogger()), "inline", true),
                                Map.of("name", "Trace ID", "value", safe(first.getTraceId()), "inline", false)
                        )
                ))
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private String buildDescription(AlertBucket bucket) {
        StringBuilder sb = new StringBuilder();

        sb.append("Received ")
                .append(bucket.count())
                .append(" matching ERROR log")
                .append(bucket.count() == 1 ? "" : "s")
                .append(" during the aggregation window.")
                .append("\n\n");

        bucket.getEvents().stream()
                .limit(maxMessages)
                .forEach(event -> sb.append("- ")
                        .append(truncate(event.getMessage(), 250))
                        .append("\n"));

        if (bucket.count() > maxMessages) {
            sb.append("\n...and ")
                    .append(bucket.count() - maxMessages)
                    .append(" more.");
        }

        return truncate(sb.toString(), 1800);
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
