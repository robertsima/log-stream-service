package com.logstream.service;

import com.logstream.generated.model.LogEventRequest;

public interface LogEventService {
    void ingestLogEvent(LogEventRequest logEventRequest, String rawToken);
}
