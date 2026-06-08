package com.logstream.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.LogEventsApi;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.service.LogEventService;

@RestController
public class LogEventsController implements LogEventsApi {

    private final LogEventService logEventService;

    public LogEventsController(LogEventService logEventService) {
        this.logEventService = logEventService;
    }

    @Override
    public ResponseEntity<Void> ingestLogEvent(String xIngestionToken, LogEventRequest logEventRequest) {
        String rawToken = xIngestionToken;

        if (rawToken == null || rawToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            logEventService.ingestLogEvent(logEventRequest, rawToken.trim());
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}