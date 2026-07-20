package com.logstream.webhooks;

import java.util.Map;

import com.logstream.domain.model.AlertGroupSummary;
import com.logstream.generated.model.AlertAnalysisResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.domain.entity.AlertDestination;


@Service
public class SlackWebhookSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SlackWebhookSender.class);

    private final RestClient restClient;

    public SlackWebhookSender(RestClient.Builder builder) {
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
//                "text", AlertNotificationFormatter.buildSlackText(summary)
//        );
//
//        restClient.post()
//                .uri(destination.getWebhookUrl())
//                .body(payload)
//                .retrieve()
//                .toBodilessEntity();
//    }

    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis, AlertGroupSummary summary) {
        if (analysis.getAnalysis() == null || analysis.getAnalysis().isBlank()) {
            log.warn("Slack destination {} skipped: analysis text is blank", destination.getId());
            return;
        }
        if (destination.getWebhookUrl() == null || destination.getWebhookUrl().isBlank()) {
            log.warn("Slack destination {} has no webhook URL configured; skipping", destination.getId());
            return;
        }

        // Slack incoming webhooks only accept their own payload shape ({"text": ...}),
        // not an arbitrary JSON bean
        Map<String, Object> payload = Map.of(
                "text", header(summary) + analysis.getAnalysis()
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Collapses a burst of similar errors into one message via a summarizing header
     * instead of firing one Slack message per error (see AlertContextProcessor grouping).
     */
    private String header(AlertGroupSummary summary) {
        if (summary == null || !summary.isGrouped()) {
            return ":rotating_light: *Log Stream Alert Analysis*\n";
        }
        return String.format(
                ":rotating_light: *%d similar %s errors in %s (last %ds)*\n",
                summary.occurrenceCount(),
                nonBlank(summary.signatureLabel(), "grouped"),
                nonBlank(summary.appName(), "app"),
                Math.max(summary.windowSeconds(), 1));
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
