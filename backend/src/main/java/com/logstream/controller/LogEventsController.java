package com.logstream.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.LogEventsApi;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.service.LogEventService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class LogEventsController implements LogEventsApi {

    private static final String INGESTION_TOKEN_HEADER = "X-Ingestion-Token";

    private final LogEventService logEventService;
    private final HttpServletRequest request;

    public LogEventsController(LogEventService logEventService, HttpServletRequest request) {
        this.logEventService = logEventService;
        this.request = request;
    }

    @Override
    public ResponseEntity<Void> ingestLogEvent(LogEventRequest logEventRequest) {
        String rawToken = request.getHeader(INGESTION_TOKEN_HEADER);

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