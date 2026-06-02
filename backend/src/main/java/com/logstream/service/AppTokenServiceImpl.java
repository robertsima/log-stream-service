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

import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.model.App;
import com.logstream.model.AppToken;
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
        String tokenHash = hash(rawToken);
        String tokenPrefix = rawToken.substring(0, Math.min(rawToken.length(), 50));

        AppToken appToken = new AppToken();
        appToken.setId(UUID.randomUUID());
        appToken.setApp(app);
        appToken.setName(createAppTokenRequest.getName());
        appToken.setTokenHash(tokenHash);
        appToken.setTokenPrefix(tokenPrefix);
        appToken.setCreatedAt(OffsetDateTime.now());
        if (createAppTokenRequest.getExpiresAt().isPresent()) {
            appToken.setExpiresAt(createAppTokenRequest.getExpiresAt().get());
        }

        AppToken saved = appTokenRepository.save(appToken);

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
                .map(appToken -> {
                    AppTokenResponse response = new AppTokenResponse();
                    response.setId(appToken.getId());
                    response.setAppId(appToken.getApp().getId());
                    response.setName(appToken.getName());
                    response.setTokenPrefix(appToken.getTokenPrefix());
                    response.setCreatedAt(appToken.getCreatedAt());
                    if (appToken.getExpiresAt() != null) {
                        response.expiresAt(appToken.getExpiresAt());
                    }
                    if (appToken.getLastUsedAt() != null) {
                        response.lastUsedAt(appToken.getLastUsedAt());
                    }
                    if (appToken.getRevokedAt() != null) {
                        response.revokedAt(appToken.getRevokedAt());
                    }
                    return response;
                })
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
    public AppToken validateAndRefreshToken(String rawToken) {
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
        return appTokenRepository.save(token);
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
