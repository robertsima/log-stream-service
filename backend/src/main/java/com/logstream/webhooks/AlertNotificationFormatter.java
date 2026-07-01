package com.logstream.webhooks;

import java.util.List;
import java.util.stream.Collectors;

import com.logstream.domain.model.AlertBucket;
import com.logstream.generated.model.LogEventRequest;

public class AlertNotificationFormatter {

    private static final int DEFAULT_MESSAGE_TRUNCATE = 250;

    private AlertNotificationFormatter() {
    }

    public static AlertSummary summarize(AlertBucket bucket, int maxMessages) {
        return summarize(bucket, maxMessages, null);
    }

    public static AlertSummary summarize(AlertBucket bucket, int maxMessages, String analysis) {
        LogEventRequest first = bucket.getEvents().get(0);
        List<String> messages = bucket.getEvents().stream()
                .limit(maxMessages)
                .map(event -> truncate(event.getMessage(), DEFAULT_MESSAGE_TRUNCATE))
                .collect(Collectors.toList());

        int remaining = Math.max(0, bucket.count() - messages.size());

        return new AlertSummary(
                bucket.count(),
                safe(first.getLogger()),
                safe(first.getTraceId()),
                bucket.getFingerprint(),
                messages,
                remaining,
                analysis
        );
    }

    public static String buildDiscordDescription(AlertSummary summary) {
        if (hasAnalysis(summary)) {
            return truncate(buildAnalyzedDiscordDescription(summary), 1800);
        }
        return truncate(buildStandardDiscordDescription(summary), 1800);
    }

    public static List<Object> buildDiscordFields(AlertSummary summary) {
        return List.of(
                MapEntry.of("Count", String.valueOf(summary.count()), true),
                MapEntry.of("Logger", summary.logger(), true),
                MapEntry.of("Trace ID", summary.traceId(), false),
                MapEntry.of("Fingerprint", summary.fingerprint(), false)
        );
    }

    public static String buildSlackText(AlertSummary summary) {
        if (hasAnalysis(summary)) {
            return truncate(buildAnalyzedSlackText(summary), 3000);
        }
        return truncate(buildStandardSlackText(summary), 3000);
    }

    private static boolean hasAnalysis(AlertSummary summary) {
        return summary.analysis() != null && !summary.analysis().isBlank();
    }

    private static String buildAnalyzedDiscordDescription(AlertSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Analysis**\n").append(summary.analysis());
        appendDiscordAlertDetails(sb, summary);
        appendDiscordMessages(sb, summary);
        return sb.toString();
    }

    private static String buildStandardDiscordDescription(AlertSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Received ")
                .append(summary.count())
                .append(" matching ERROR log")
                .append(summary.count() == 1 ? "" : "s")
                .append(" during the aggregation window.");
        appendDiscordMessages(sb, summary);
        return sb.toString();
    }

    private static void appendDiscordAlertDetails(StringBuilder sb, AlertSummary summary) {
        sb.append("\n\n---\n\n**Alert details**\n")
                .append("Count: ").append(summary.count()).append('\n')
                .append("Logger: ").append(summary.logger()).append('\n')
                .append("Trace ID: ").append(summary.traceId()).append('\n')
                .append("Fingerprint: ").append(summary.fingerprint());
    }

    private static void appendDiscordMessages(StringBuilder sb, AlertSummary summary) {
        sb.append("\n\n**Messages**\n");
        summary.messages().forEach(message -> sb.append("- ").append(message).append('\n'));
        if (summary.remaining() > 0) {
            sb.append("\n...and ")
                    .append(summary.remaining())
                    .append(" more.");
        }
    }

    private static String buildAnalyzedSlackText(AlertSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(":rotating_light: *Error Alert*\n\n")
                .append("*Analysis*\n")
                .append(summary.analysis());
        appendSlackAlertDetails(sb, summary);
        appendSlackMessages(sb, summary);
        return sb.toString();
    }

    private static String buildStandardSlackText(AlertSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(":rotating_light: *Error Alert*")
                .append("\n")
                .append("*Count:* ").append(summary.count()).append("\n")
                .append("*Logger:* `").append(summary.logger()).append("`\n")
                .append("*Trace ID:* `").append(summary.traceId()).append("`\n")
                .append("*Fingerprint:* `").append(summary.fingerprint()).append("`\n");
        appendSlackMessages(sb, summary);
        return sb.toString();
    }

    private static void appendSlackAlertDetails(StringBuilder sb, AlertSummary summary) {
        sb.append("\n\n*Alert details*\n")
                .append("• *Count:* ").append(summary.count()).append('\n')
                .append("• *Logger:* `").append(summary.logger()).append("`\n")
                .append("• *Trace ID:* `").append(summary.traceId()).append("`\n")
                .append("• *Fingerprint:* `").append(summary.fingerprint()).append('`');
    }

    private static void appendSlackMessages(StringBuilder sb, AlertSummary summary) {
        sb.append("\n\n*Messages*\n");
        summary.messages().forEach(message -> sb.append("• ").append(message).append('\n'));
        if (summary.remaining() > 0) {
            sb.append("\n...and ")
                    .append(summary.remaining())
                    .append(" more.");
        }
    }

    private static String safe(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "N/A";
        }
        return String.valueOf(value);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "N/A";
        }
        return value.length() <= max
                ? value
                : value.substring(0, max - 3) + "...";
    }

    private static final class MapEntry {
        private MapEntry() {}

        public static java.util.Map<String, Object> of(String name, String value, boolean inline) {
            return java.util.Map.of("name", name, "value", value, "inline", inline);
        }
    }
}
