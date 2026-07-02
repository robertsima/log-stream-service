package com.logstream.webhooks;

import java.util.List;

public class AlertSummary {

    private final int count;
    private final String appName;
    private final String logger;
    private final String traceId;
    private final String fingerprint;
    private final List<String> messages;
    private final int remaining;
    private final String analysis;
    private final String firstSeen;
    private final String lastSeen;
    private final String span;
    private final String host;
    private final String environment;

    public AlertSummary(int count,
                        String appName,
                        String logger,
                        String traceId,
                        String fingerprint,
                        List<String> messages,
                        int remaining,
                        String analysis,
                        String firstSeen,
                        String lastSeen,
                        String span,
                        String host,
                        String environment) {
        this.count = count;
        this.appName = appName;
        this.logger = logger;
        this.traceId = traceId;
        this.fingerprint = fingerprint;
        this.messages = List.copyOf(messages);
        this.remaining = remaining;
        this.analysis = analysis;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.span = span;
        this.host = host;
        this.environment = environment;
    }

    public int count() {
        return count;
    }

    public String appName() {
        return appName;
    }

    public String logger() {
        return logger;
    }

    public String traceId() {
        return traceId;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public List<String> messages() {
        return messages;
    }

    public int remaining() {
        return remaining;
    }

    public String analysis() {
        return analysis;
    }

    public String firstSeen() {
        return firstSeen;
    }

    public String lastSeen() {
        return lastSeen;
    }

    public String span() {
        return span;
    }

    public String host() {
        return host;
    }

    public String environment() {
        return environment;
    }
}
