package com.logstream.webhooks;

import java.util.List;
import java.util.Map;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;

@Service
public class DiscordWebhookSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscordWebhookSender.class);

    private final RestClient restClient;

    public DiscordWebhookSender(RestClient.Builder builder) {
        this.restClient = builder.build();
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
