package com.logstream.controller;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.AppTokensApi;
import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.service.AppTokenService;

@RestController
public class AppTokensController implements AppTokensApi {

    private final AppTokenService appTokenService;

    public AppTokensController(AppTokenService appTokenService) {
        this.appTokenService = appTokenService;
    }

    @Override
    public ResponseEntity<CreateAppTokenResponse> createAppToken(UUID appId, CreateAppTokenRequest createAppTokenRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(appTokenService.createAppToken(appId, createAppTokenRequest));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @Override
    public ResponseEntity<List<AppTokenResponse>> getAppTokens(UUID appId) {
        return ResponseEntity.ok(appTokenService.getAppTokens(appId));
    }

    @Override
    public ResponseEntity<Void> revokeAppToken(UUID appId, UUID tokenId) {
        try {
            appTokenService.revokeAppToken(appId, tokenId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
