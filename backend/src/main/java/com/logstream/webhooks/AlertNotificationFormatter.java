package com.logstream.webhooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.logstream.service.alerting.AlertBucket;
import com.logstream.service.alerting.AlertTimeWindow;
import com.logstream.generated.model.LogEventRequest;

public class AlertNotificationFormatter {

    private static final int DEFAULT_MESSAGE_TRUNCATE = 250;
    private static final String[] HOST_KEYS = {"host", "hostname", "server"};
    private static final String[] ENVIRONMENT_KEYS = {"environment", "env"};

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
        AlertTimeWindow window = AlertTimeWindow.from(bucket.getEvents());

        return new AlertSummary(
                bucket.count(),
                safe(bucket.getAppName()),
                safe(first.getLogger()),
                safe(first.getTraceId()),
                bucket.getFingerprint(),
                messages,
                remaining,
                analysis,
                safe(window.firstFormatted()),
                safe(window.lastFormatted()),
                window.spanFormatted(),
                metadataValue(first, HOST_KEYS),
                metadataValue(first, ENVIRONMENT_KEYS)
        );
    }

    /**
     * There's no dedicated host/environment field on LogEventRequest; client SDKs that
     * capture this put it in metadata under one of a few conventional key names.
     */
    private static String metadataValue(LogEventRequest event, String[] keys) {
        Map<String, Object> metadata = event.getMetadata();
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    public static String buildDiscordTitle(AlertSummary summary) {
        return "Error Alert — " + summary.appName();
    }

    public static String buildDiscordDescription(AlertSummary summary) {
        if (hasAnalysis(summary)) {
            return truncate(buildAnalyzedDiscordDescription(summary), 1800);
        }
        return truncate(buildStandardDiscordDescription(summary), 1800);
    }

    public static List<Object> buildDiscordFields(AlertSummary summary) {
        List<Object> fields = new ArrayList<>(List.of(
                MapEntry.of("App", summary.appName(), true),
                MapEntry.of("Count", String.valueOf(summary.count()), true),
                MapEntry.of("Logger", summary.logger(), true),
                MapEntry.of("Trace ID", summary.traceId(), false)
        ));
        if (summary.host() != null) {
            fields.add(MapEntry.of("Host", summary.host(), true));
        }
        if (summary.environment() != null) {
            fields.add(MapEntry.of("Environment", summary.environment(), true));
        }
        fields.add(MapEntry.of("First seen", summary.firstSeen(), true));
        fields.add(MapEntry.of("Last seen", lastSeenLine(summary), true));
        return fields;
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
        sb.append(":rotating_light: *Error Alert — ").append(summary.appName()).append("*\n\n")
                .append("*Analysis*\n")
                .append(summary.analysis())
                .append("\n\n");
        appendSlackContextLine(sb, summary);
        appendSlackMessages(sb, summary);
        return sb.toString();
    }

    private static String buildStandardSlackText(AlertSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(":rotating_light: *Error Alert — ").append(summary.appName()).append("*")
                .append("\n")
                .append("*Count:* ").append(summary.count()).append("\n")
                .append("*Logger:* `").append(summary.logger()).append("`\n")
                .append("*Trace ID:* `").append(summary.traceId()).append("`\n")
                .append("*First seen:* ").append(summary.firstSeen()).append("\n")
                .append("*Last seen:* ").append(lastSeenLine(summary)).append("\n");
        appendSlackContextLine(sb, summary);
        appendSlackMessages(sb, summary);
        return sb.toString();
    }

    private static void appendSlackContextLine(StringBuilder sb, AlertSummary summary) {
        if (summary.host() == null && summary.environment() == null) {
            return;
        }
        if (summary.host() != null) {
            sb.append("*Host:* `").append(summary.host()).append("`\n");
        }
        if (summary.environment() != null) {
            sb.append("*Environment:* `").append(summary.environment()).append("`\n");
        }
    }

    private static String lastSeenLine(AlertSummary summary) {
        if (summary.span() != null && !summary.span().isBlank()) {
            return summary.lastSeen() + " (span " + summary.span() + ")";
        }
        return summary.lastSeen();
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
