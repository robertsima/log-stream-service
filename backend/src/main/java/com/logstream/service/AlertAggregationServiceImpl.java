package com.logstream.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.logstream.service.alerting.AlertBucket;
import com.logstream.service.alerting.MessageNormalizer;
import com.logstream.generated.model.LogEventRequest;

@Service
public class AlertAggregationServiceImpl implements AlertAggregationService {

    private final Map<String, AlertBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void accept(UUID appId, String appName, LogEventRequest event) {
        if (event.getLevel() == null || !"ERROR".equalsIgnoreCase(event.getLevel().getValue())) {
            return;
        }

        String fingerprint = fingerprint(appId, event);

        buckets.compute(fingerprint, (key, bucket) -> {
            AlertBucket next = bucket;
            if (next == null) {
                next = new AlertBucket(appId, fingerprint);
                next.setAppName(appName);
            }
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
        return MessageNormalizer.normalize(message);
    }
}