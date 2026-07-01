package com.logstream.webhooks;

import java.util.List;

public class AlertSummary {

    private final int count;
    private final String logger;
    private final String traceId;
    private final String fingerprint;
    private final List<String> messages;
    private final int remaining;
    private final String analysis;

    public AlertSummary(int count,
                        String logger,
                        String traceId,
                        String fingerprint,
                        List<String> messages,
                        int remaining,
                        String analysis) {
        this.count = count;
        this.logger = logger;
        this.traceId = traceId;
        this.fingerprint = fingerprint;
        this.messages = List.copyOf(messages);
        this.remaining = remaining;
        this.analysis = analysis;
    }

    public int count() {
        return count;
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
}
