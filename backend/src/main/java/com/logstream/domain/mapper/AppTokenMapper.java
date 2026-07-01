package com.logstream.domain.mapper;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.domain.entity.App;
import com.logstream.domain.entity.AppToken;
import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;

public final class AppTokenMapper {

    private AppTokenMapper() {
    }

    public static AppToken toEntity(AppTokenDTO dto, App app, String tokenHash) {
        AppToken token = new AppToken();
        token.setId(dto.getId());
        token.setApp(app);
        token.setName(dto.getName());
        token.setTokenHash(tokenHash);
        token.setTokenPrefix(dto.getTokenPrefix());
        token.setCreatedAt(dto.getCreatedAt());
        token.setExpiresAt(dto.getExpiresAt());
        token.setLastUsedAt(dto.getLastUsedAt());
        token.setRevokedAt(dto.getRevokedAt());
        return token;
    }

    public static AppTokenDTO fromRequest(CreateAppTokenRequest request, App app, String tokenPrefix) {
        AppTokenDTO dto = new AppTokenDTO();
        dto.setId(java.util.UUID.randomUUID());
        dto.setAppId(app.getId());
        dto.setAppName(app.getName());
        dto.setName(request.getName());
        dto.setTokenPrefix(tokenPrefix);
        dto.setCreatedAt(java.time.OffsetDateTime.now());
        if (request.getExpiresAt() != null) {
            dto.setExpiresAt(request.getExpiresAt());
        }
        return dto;
    }

    public static AppTokenDTO toDto(AppToken token) {
        AppTokenDTO dto = new AppTokenDTO();
        dto.setId(token.getId());
        dto.setAppId(token.getApp().getId());
        dto.setAppName(token.getApp().getName());
        dto.setName(token.getName());
        dto.setTokenHash(token.getTokenHash());
        dto.setTokenPrefix(token.getTokenPrefix());
        dto.setCreatedAt(token.getCreatedAt());
        dto.setExpiresAt(token.getExpiresAt());
        dto.setLastUsedAt(token.getLastUsedAt());
        dto.setRevokedAt(token.getRevokedAt());
        return dto;
    }

    public static AppTokenResponse toResponse(AppTokenDTO dto) {
        AppTokenResponse response = new AppTokenResponse();
        response.setId(dto.getId());
        response.setAppId(dto.getAppId());
        response.setName(dto.getName());
        response.setTokenPrefix(dto.getTokenPrefix());
        response.setCreatedAt(dto.getCreatedAt());
        if (dto.getExpiresAt() != null) {
            response.expiresAt(dto.getExpiresAt());
        }
        if (dto.getLastUsedAt() != null) {
            response.lastUsedAt(dto.getLastUsedAt());
        }
        if (dto.getRevokedAt() != null) {
            response.revokedAt(dto.getRevokedAt());
        }
        return response;
    }
}
