package com.logstream.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.generated.model.LogEventRequest;

@Service
public class LogEventServiceImpl implements LogEventService {

    private static final Logger log = LoggerFactory.getLogger(LogEventServiceImpl.class);
    private final AppTokenService appTokenService;

    public LogEventServiceImpl(AppTokenService appTokenService) {
        this.appTokenService = appTokenService;
    }

    @Override
    public void ingestLogEvent(LogEventRequest logEventRequest, String rawToken) {
        AppTokenDTO appToken = appTokenService.validateAndRefreshToken(rawToken);

        log.info("[log-event] appId={} appName={} tokenPrefix={} level={} message={} occurredAt={} logger={} traceId={} spanId={}",
                appToken.getAppId(),
                appToken.getAppName(),
                appToken.getTokenPrefix(),
                logEventRequest.getLevel(),
                logEventRequest.getMessage(),
                logEventRequest.getOccurredAt(),
                logEventRequest.getLogger().isPresent() ? logEventRequest.getLogger().get() : null,
                logEventRequest.getTraceId().isPresent() ? logEventRequest.getTraceId().get() : null,
                logEventRequest.getSpanId().isPresent() ? logEventRequest.getSpanId().get() : null);
    }
}
