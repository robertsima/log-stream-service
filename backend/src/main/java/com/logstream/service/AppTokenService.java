package com.logstream.service;

import java.util.List;
import java.util.UUID;

import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.model.AppToken;

public interface AppTokenService {
    CreateAppTokenResponse createAppToken(UUID appId, CreateAppTokenRequest createAppTokenRequest);
    List<AppTokenResponse> getAppTokens(UUID appId);
    void revokeAppToken(UUID appId, UUID tokenId);
    AppToken validateAndRefreshToken(String rawToken);
}
