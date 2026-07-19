package com.logstream.domain.model;

import com.logstream.generated.model.LogEventRequest;

import java.util.List;
import java.util.UUID;

public record AlertTrigger(
        UUID appId,
        LogEvent triggeringEvent,
        List<LogEvent> context
) {}