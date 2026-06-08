package com.logstream.service;

import java.util.Map;
import java.util.UUID;

import com.logstream.generated.model.LogEventRequest;
import com.logstream.model.AlertBucket;

public interface AlertAggregationService {


    public void accept(UUID appId, LogEventRequest event);

    public Map<String, AlertBucket> drainBuckets();

    public String fingerprint(UUID appId, LogEventRequest event);

    public String normalize(String message);

}