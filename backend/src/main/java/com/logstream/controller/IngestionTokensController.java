package com.logstream.controller;

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
        String token = xIngestionToken == null ? null : xIngestionToken.trim();
        // Invalid/missing tokens raise UnauthorizedException -> 401 ErrorResponse.
        return ResponseEntity.ok(appTokenService.resolveIngestionTokenSession(token));
    }
}
