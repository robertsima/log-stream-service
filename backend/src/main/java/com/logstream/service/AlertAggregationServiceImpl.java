package com.logstream.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.logstream.generated.model.LogEventRequest;
import com.logstream.model.AlertBucket;

@Service
public class AlertAggregationServiceImpl implements AlertAggregationService {

    private final Map<String, AlertBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void accept(UUID appId, LogEventRequest event) {
        if (event.getLevel() == null || !"ERROR".equalsIgnoreCase(event.getLevel().getValue())) {
            return;
        }

        String fingerprint = fingerprint(appId, event);

        buckets.compute(fingerprint, (key, bucket) -> {
            AlertBucket next = bucket == null ? new AlertBucket(appId, fingerprint) : bucket;
            next.add(event);
            return next;
        });
    }

    @Override
    public Map<String, AlertBucket> drainBuckets() {
        Map<String, AlertBucket> drained = new HashMap<>(buckets);
        buckets.clear();
        return drained;
    }

    @Override
    public String fingerprint(UUID appId, LogEventRequest event) {
        String logger = event.getLogger() == null ? "unknown" : event.getLogger();
        String message = normalize(event.getMessage());
        return appId + "|ERROR|" + logger + "|" + message;
    }

    @Override
    public String normalize(String message) {
        if (message == null) {
            return "";
        }

        return message
                .toLowerCase()
                .replaceAll("\\b\\d+\\b", "{number}")
                .replaceAll("\\s+", " ")
                .trim();
    }
}