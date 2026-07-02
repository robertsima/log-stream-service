package com.logstream.service.alerting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.logstream.generated.model.LogEventRequest;

public class AlertBucket {

    private final UUID appId;
    private final String fingerprint;
    private final List<LogEventRequest> events = new ArrayList<>();
    private String appName;

    public AlertBucket(UUID appId, String fingerprint) {
        this.appId = appId;
        this.fingerprint = fingerprint;
    }

    public UUID getAppId() {
        return appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public List<LogEventRequest> getEvents() {
        return events;
    }

    public void add(LogEventRequest event) {
        events.add(event);
    }

    public int count() {
        return events.size();
    }
}