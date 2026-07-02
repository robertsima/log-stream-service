package com.logstream.webhooks;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.webhooks.AlertNotificationFormatter;
import com.logstream.webhooks.AlertSummary;

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

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages);

        Map<String, Object> payload = Map.of(
                "embeds", List.of(Map.of(
                        "title", AlertNotificationFormatter.buildDiscordTitle(summary),
                        "description", AlertNotificationFormatter.buildDiscordDescription(summary),
                        "fields", AlertNotificationFormatter.buildDiscordFields(summary)
                ))
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
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

    public void sendAnalyzedAlert(AlertDestination destination, AlertBucket bucket, String analysis) {
        if (bucket.getEvents().isEmpty()) {
            return;
        }

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages, analysis);

        Map<String, Object> payload = Map.of(
                "embeds", List.of(Map.of(
                        "title", AlertNotificationFormatter.buildDiscordTitle(summary),
                        "description", AlertNotificationFormatter.buildDiscordDescription(summary),
                        "fields", AlertNotificationFormatter.buildDiscordFields(summary)
                ))
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
