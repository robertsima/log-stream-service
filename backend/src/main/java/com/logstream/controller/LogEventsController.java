package com.logstream.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.LogEventsApi;
import com.logstream.generated.model.LogEventBatchResponse;
import com.logstream.service.LogEventService;

@RestController
public class LogEventsController implements LogEventsApi {

    private final LogEventService logEventService;

    public LogEventsController(LogEventService logEventService) {
        this.logEventService = logEventService;
    }

    @Override
    public ResponseEntity<Void> ingestLogEvent(String xIngestionToken, Map<String, Object> requestBody) {
        logEventService.ingestLogEvent(requestBody, trimmed(xIngestionToken));
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<LogEventBatchResponse> ingestLogEventBatch(String xIngestionToken,
            List<Map<String, Object>> requestBody) {
        LogEventBatchResponse response = logEventService.ingestLogEventBatch(requestBody, trimmed(xIngestionToken));
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Token validation (missing/blank/unknown/revoked/expired) throws
     * {@link com.logstream.exception.UnauthorizedException}, which the
     * {@link ApiExceptionHandler} maps to the documented 401 ErrorResponse.
     */
    private String trimmed(String token) {
        return token == null ? null : token.trim();
    }
}
