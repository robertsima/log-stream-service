package com.logstream.webhooks;

import java.util.List;
import java.util.Map;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;

@Service
public class DiscordWebhookSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscordWebhookSender.class);

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

//    public void sendAggregatedAlert(AlertDestination destination, @org.checkerframework.checker.nullness.qual.MonotonicNonNull AlertTrigger bucket) {
//        if (bucket.getEvents().isEmpty()) {
//            return;
//        }
//
//        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages);
//
//        Map<String, Object> payload = Map.of(
//                "embeds", List.of(Map.of(
//                        "title", AlertNotificationFormatter.buildDiscordTitle(summary),
//                        "description", AlertNotificationFormatter.buildDiscordDescription(summary),
//                        "fields", AlertNotificationFormatter.buildDiscordFields(summary)
//                ))
//        );
//
//        restClient.post()
//                .uri(destination.getWebhookUrl())
//                .body(payload)
//                .retrieve()
//                .toBodilessEntity();
//    }

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

    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis) {
        if (analysis.getAnalysis() == null || analysis.getAnalysis().isBlank()) {
            return;
        }
        if (destination.getWebhookUrl() == null || destination.getWebhookUrl().isBlank()) {
            log.warn("Discord destination {} has no webhook URL configured; skipping", destination.getId());
            return;
        }

//        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages, analysis);

        // Discord webhooks only accept their own payload shape (content/embeds),
        // not an arbitrary JSON bean; embed descriptions cap at 4096 chars
        Map<String, Object> payload = Map.of(
                "embeds", List.of(Map.of(
                        "title", "Log Stream Alert Analysis",
                        "description", truncate(analysis.getAnalysis(), 4000)
                ))
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
