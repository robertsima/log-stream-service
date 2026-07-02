package com.logstream.service.alerting;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.logstream.generated.model.LogEventRequest;

public final class AlertTimeWindow {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final OffsetDateTime first;
    private final OffsetDateTime last;

    private AlertTimeWindow(OffsetDateTime first, OffsetDateTime last) {
        this.first = first;
        this.last = last;
    }

    public static AlertTimeWindow from(List<LogEventRequest> events) {
        OffsetDateTime first = null;
        OffsetDateTime last = null;

        for (LogEventRequest event : events) {
            OffsetDateTime occurredAt = event.getOccurredAt();
            if (occurredAt == null) {
                continue;
            }
            if (first == null || occurredAt.isBefore(first)) {
                first = occurredAt;
            }
            if (last == null || occurredAt.isAfter(last)) {
                last = occurredAt;
            }
        }

        return new AlertTimeWindow(first, last);
    }

    public String firstFormatted() {
        return format(first);
    }

    public String lastFormatted() {
        return format(last);
    }

    public String spanFormatted() {
        if (first == null || last == null) {
            return null;
        }

        Duration duration = Duration.between(first, last);
        if (duration.isZero() || duration.isNegative()) {
            return null;
        }

        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder span = new StringBuilder();
        if (hours > 0) {
            span.append(hours).append('h');
        }
        if (minutes > 0) {
            span.append(minutes).append('m');
        }
        if (hours == 0 && seconds > 0) {
            span.append(seconds).append('s');
        }
        return span.toString();
    }

    private static String format(OffsetDateTime value) {
        return value == null ? null : TIMESTAMP_FORMAT.format(value) + " UTC";
    }
}
