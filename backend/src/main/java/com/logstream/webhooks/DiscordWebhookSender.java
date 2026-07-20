package com.logstream.webhooks;

import java.util.List;
import java.util.Map;

import com.logstream.domain.model.AlertGroupSummary;
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

    private String truncate(String value, int max) {
        if (value == null) {
            return "N/A";
        }

        return value.length() <= max
                ? value
                : value.substring(0, max - 3) + "...";
    }

    public void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis, AlertGroupSummary summary) {
        if (analysis.getAnalysis() == null || analysis.getAnalysis().isBlank()) {
            log.warn("Discord destination {} skipped: analysis text is blank", destination.getId());
            return;
        }
        if (destination.getWebhookUrl() == null || destination.getWebhookUrl().isBlank()) {
            log.warn("Discord destination {} has no webhook URL configured; skipping", destination.getId());
            return;
        }

        // Discord webhooks only accept their own payload shape (content/embeds),
        // not an arbitrary JSON bean; embed descriptions cap at 4096 chars
        Map<String, Object> payload = Map.of(
                "embeds", List.of(Map.of(
                        "title", title(summary),
                        "description", truncate(analysis.getAnalysis(), 4000)
                ))
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Collapses a burst of similar errors into one message via a summarizing title
     * instead of firing one Discord message per error (see AlertContextProcessor grouping).
     */
    private String title(AlertGroupSummary summary) {
        if (summary == null || !summary.isGrouped()) {
            return "Prairie Log Alert Analysis";
        }
        return String.format(
                "%d similar %s errors in %s over the last %ds",
                summary.occurrenceCount(),
                nonBlank(summary.signatureLabel(), "grouped"),
                nonBlank(summary.appName(), "app"),
                Math.max(summary.windowSeconds(), 1));
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
