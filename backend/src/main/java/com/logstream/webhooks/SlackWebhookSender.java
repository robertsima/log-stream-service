package com.logstream.webhooks;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertBucket;
import com.logstream.webhooks.AlertNotificationFormatter;
import com.logstream.webhooks.AlertSummary;

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

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages);

        Map<String, Object> payload = Map.of(
                "text", AlertNotificationFormatter.buildSlackText(summary)
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendAnalyzedAlert(AlertDestination destination, AlertBucket bucket, String analysis) {
        if (bucket.getEvents().isEmpty()) {
            return;
        }

        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages, analysis);

        Map<String, Object> payload = Map.of(
                "text", AlertNotificationFormatter.buildSlackText(summary)
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
