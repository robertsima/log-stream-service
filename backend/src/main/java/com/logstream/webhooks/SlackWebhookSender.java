package com.logstream.webhooks;

import java.util.Map;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;


@Service
public class SlackWebhookSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlackWebhookSender.class);

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

//    public void sendAggregatedAlert(AlertDestination destination, @org.checkerframework.checker.nullness.qual.MonotonicNonNull AlertTrigger bucket) {
//        if (bucket.getEvents().isEmpty()) {
//            return;
//        }
//
//        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages);
//
//        Map<String, Object> payload = Map.of(
//                "text", AlertNotificationFormatter.buildSlackText(summary)
//        );
//
//        restClient.post()
//                .uri(destination.getWebhookUrl())
//                .body(payload)
//                .retrieve()
//                .toBodilessEntity();
//    }

    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis) {
        if (analysis.getAnalysis() == null || analysis.getAnalysis().isBlank()) {
            return;
        }
        if (destination.getWebhookUrl() == null || destination.getWebhookUrl().isBlank()) {
            log.warn("Slack destination {} has no webhook URL configured; skipping", destination.getId());
            return;
        }

//        AlertSummary summary = AlertNotificationFormatter.summarize(bucket, maxMessages, analysis);
//
//        Map<String, Object> payload = Map.of(
//                "text", AlertNotificationFormatter.buildSlackText(summary)
//        );

        // Slack incoming webhooks only accept their own payload shape ({"text": ...}),
        // not an arbitrary JSON bean
        Map<String, Object> payload = Map.of(
                "text", ":rotating_light: *Log Stream Alert Analysis*\n" + analysis.getAnalysis()
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
