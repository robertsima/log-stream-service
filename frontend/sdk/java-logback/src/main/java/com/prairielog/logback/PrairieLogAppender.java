package com.prairielog.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PrairieLogAppender extends AppenderBase<ILoggingEvent> {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<String> buffer = new ArrayList<>();

    private String apiUrl;
    private String ingestionToken;
    private int batchSize = 50;

    public void setApiUrl(String apiUrl) {
        this.apiUrl = trimTrailingSlash(apiUrl);
    }

    public void setIngestionToken(String ingestionToken) {
        this.ingestionToken = ingestionToken;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (apiUrl == null || ingestionToken == null || ingestionToken.isBlank()) {
            addWarn("PrairieLog appender missing apiUrl or ingestionToken.");
            return;
        }
        synchronized (buffer) {
            buffer.add(toJson(event));
            if (buffer.size() >= batchSize) {
                flushBuffer();
            }
        }
    }

    @Override
    public void stop() {
        synchronized (buffer) {
            flushBuffer();
        }
        super.stop();
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }
        String payload = "[" + String.join(",", buffer) + "]";
        buffer.clear();

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/api/v1/log-events/batch"))
                .header("Content-Type", "application/json")
                .header("X-Ingestion-Token", ingestionToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                addWarn("PrairieLog request failed with HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            addError("PrairieLog request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            addError("PrairieLog request interrupted.", ex);
        }
    }

    private String toJson(ILoggingEvent event) {
        return "{"
                + field("id", UUID.randomUUID().toString()) + ","
                + field("level", level(event.getLevel())) + ","
                + field("message", event.getFormattedMessage()) + ","
                + field("occurredAt", Instant.ofEpochMilli(event.getTimeStamp()).toString()) + ","
                + field("logger", event.getLoggerName())
                + "}";
    }

    private String field(String name, String value) {
        return "\"" + escape(name) + "\":\"" + escape(value) + "\"";
    }

    private String level(Level level) {
        if (level == null) return "INFO";
        if (level.isGreaterOrEqual(Level.ERROR)) return "ERROR";
        if (level.isGreaterOrEqual(Level.WARN)) return "WARN";
        if (level.isGreaterOrEqual(Level.INFO)) return "INFO";
        if (level.isGreaterOrEqual(Level.DEBUG)) return "DEBUG";
        return "TRACE";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
