package com.logstream.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.exception.InvalidLogEventException;
import com.logstream.exception.RateLimitExceededException;
import com.logstream.generated.model.LogEventBatchRejection;
import com.logstream.generated.model.LogEventBatchResponse;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.security.IngestionRateLimiter;

@Service
public class LogEventServiceImpl implements LogEventService {

    private static final Logger log = LoggerFactory.getLogger(LogEventServiceImpl.class);
    private final AppTokenService appTokenService;
    private final AlertAggregationService alertAggregationService;
    private final IngestionRateLimiter ingestionRateLimiter;
    private final LogEventNormalizer logEventNormalizer;

    public LogEventServiceImpl(
            AppTokenService appTokenService,
            AlertAggregationService alertAggregationService,
            IngestionRateLimiter ingestionRateLimiter,
            LogEventNormalizer logEventNormalizer) {
        this.appTokenService = appTokenService;
        this.alertAggregationService = alertAggregationService;
        this.ingestionRateLimiter = ingestionRateLimiter;
        this.logEventNormalizer = logEventNormalizer;
    }

    @Override
    public void ingestLogEvent(Map<String, Object> rawEvent, String rawToken) {
        AppTokenDTO appToken = appTokenService.validateAndRefreshToken(rawToken);

        ingestionRateLimiter.check(appToken.getTokenHash());

        LogEventRequest event = logEventNormalizer.normalize(rawEvent);

        logAccepted(appToken, event);

        alertAggregationService.accept(appToken.getAppId(), appToken.getAppName(), event);
    }

    @Override
    public LogEventBatchResponse ingestLogEventBatch(List<Map<String, Object>> rawEvents, String rawToken) {
        AppTokenDTO appToken = appTokenService.validateAndRefreshToken(rawToken);

        int accepted = 0;
        List<LogEventBatchRejection> rejected = new ArrayList<>();

        for (int i = 0; i < rawEvents.size(); i++) {
            try {
                ingestionRateLimiter.check(appToken.getTokenHash());
            } catch (RateLimitExceededException ex) {
                if (i == 0) {
                    throw ex;
                }
                for (int j = i; j < rawEvents.size(); j++) {
                    rejected.add(new LogEventBatchRejection(j, "Ingestion rate limit exceeded."));
                }
                break;
            }

            try {
                LogEventRequest event = logEventNormalizer.normalize(rawEvents.get(i));
                logAccepted(appToken, event);
                alertAggregationService.accept(appToken.getAppId(), appToken.getAppName(), event);
                accepted++;
            } catch (InvalidLogEventException ex) {
                rejected.add(new LogEventBatchRejection(i, ex.getMessage()));
            }
        }

        return new LogEventBatchResponse(accepted, rejected);
    }

    private void logAccepted(AppTokenDTO appToken, LogEventRequest event) {
        log.info("[log-event] appId={} appName={} tokenPrefix={} level={} message={} occurredAt={} logger={} traceId={} spanId={}",
                appToken.getAppId(),
                appToken.getAppName(),
                appToken.getTokenPrefix(),
                event.getLevel(),
                sanitizeForLog(event.getMessage()),
                event.getOccurredAt(),
                event.getLogger(),
                event.getTraceId(),
                event.getSpanId());
    }

    /**
     * Ingested content is untrusted; strip line breaks so a crafted message
     * cannot forge additional service log lines, and cap the logged length.
     */
    private String sanitizeForLog(String message) {
        if (message == null) {
            return null;
        }
        String flattened = message.replace('\r', ' ').replace('\n', ' ');
        return flattened.length() <= 200 ? flattened : flattened.substring(0, 200) + "...";
    }
}
