package com.logstream.service;

import java.util.List;
import java.util.Map;

import com.logstream.generated.model.LogEventBatchResponse;

public interface LogEventService {
    void ingestLogEvent(Map<String, Object> rawEvent, String rawToken);

    LogEventBatchResponse ingestLogEventBatch(List<Map<String, Object>> rawEvents, String rawToken);
}
