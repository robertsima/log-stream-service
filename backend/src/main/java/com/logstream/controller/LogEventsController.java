package com.logstream.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.LogEventsApi;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.service.LogEventService;

@RestController
public class LogEventsController implements LogEventsApi {

    private final LogEventService logEventService;
    private final HttpServletRequest request;

    public LogEventsController(LogEventService logEventService, HttpServletRequest request) {
        this.logEventService = logEventService;
        this.request = request;
    }

    @Override
    public ResponseEntity<Void> ingestLogEvent(LogEventRequest logEventRequest) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String rawToken = authorization.substring("Bearer ".length()).trim();
        try {
            logEventService.ingestLogEvent(logEventRequest, rawToken);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
