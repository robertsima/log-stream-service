package com.logstream.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.IngestionTokensApi;
import com.logstream.generated.model.IngestionTokenSessionResponse;
import com.logstream.service.AppTokenService;

@RestController
public class IngestionTokensController implements IngestionTokensApi {

    private final AppTokenService appTokenService;

    public IngestionTokensController(AppTokenService appTokenService) {
        this.appTokenService = appTokenService;
    }

    @Override
    public ResponseEntity<IngestionTokenSessionResponse> getIngestionTokenSession(String xIngestionToken) {
        if (xIngestionToken == null || xIngestionToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            return ResponseEntity.ok(appTokenService.resolveIngestionTokenSession(xIngestionToken.trim()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
