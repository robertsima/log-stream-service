package com.logstream.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.entity.App;
import com.logstream.entity.AppToken;
import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.mapper.AppTokenMapper;
import com.logstream.repository.AppRepository;
import com.logstream.repository.AppTokenRepository;

@Service
public class AppTokenServiceImpl implements AppTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "lss_live_";
    private static final int TOKEN_RANDOM_BYTES = 24;

    private final AppTokenRepository appTokenRepository;
    private final AppRepository appRepository;

    public AppTokenServiceImpl(AppTokenRepository appTokenRepository, AppRepository appRepository) {
        this.appTokenRepository = appTokenRepository;
        this.appRepository = appRepository;
    }

    @Override
    public CreateAppTokenResponse createAppToken(UUID appId, CreateAppTokenRequest createAppTokenRequest) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new NoSuchElementException("App not found"));

        String rawToken = generateRawToken();
        String tokenPrefix = rawToken.substring(0, Math.min(rawToken.length(), 50));
        AppTokenDTO dto = AppTokenMapper.fromRequest(createAppTokenRequest, app, tokenPrefix);

        AppToken saved = appTokenRepository.save(AppTokenMapper.toEntity(dto, app, hash(rawToken)));

        CreateAppTokenResponse response = new CreateAppTokenResponse();
        response.setId(saved.getId());
        response.setAppId(saved.getApp().getId());
        response.setName(saved.getName());
        response.setTokenPrefix(saved.getTokenPrefix());
        response.setCreatedAt(saved.getCreatedAt());
        response.setToken(rawToken);
        if (saved.getExpiresAt() != null) {
            response.expiresAt(saved.getExpiresAt());
        }

        return response;
    }

    @Override
    public List<AppTokenResponse> getAppTokens(UUID appId) {
        return appTokenRepository.findByAppId(appId).stream()
                .map(AppTokenMapper::toDto)
                .map(AppTokenMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void revokeAppToken(UUID appId, UUID tokenId) {
        AppToken token = appTokenRepository.findByIdAndAppId(tokenId, appId)
                .orElseThrow(() -> new NoSuchElementException("Token not found"));
        token.setRevokedAt(OffsetDateTime.now());
        appTokenRepository.save(token);
    }

    @Override
    public AppTokenDTO validateAndRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Missing ingestion token");
        }

        String tokenHash = hash(rawToken);
        AppToken token = appTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ingestion token"));

        if (token.getRevokedAt() != null) {
            throw new IllegalArgumentException("Token revoked");
        }

        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Token expired");
        }

        token.setLastUsedAt(OffsetDateTime.now());
        return AppTokenMapper.toDto(appTokenRepository.save(token));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to create token hash", ex);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
