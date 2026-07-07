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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.domain.entity.App;
import com.logstream.domain.entity.AppToken;
import com.logstream.domain.mapper.AppTokenMapper;
import com.logstream.domain.repository.AppRepository;
import com.logstream.domain.repository.AppTokenRepository;
import com.logstream.exception.QuotaExceededException;
import com.logstream.exception.UnauthorizedException;
import com.logstream.generated.model.AppTokenResponse;
import com.logstream.generated.model.CreateAppTokenRequest;
import com.logstream.generated.model.CreateAppTokenResponse;
import com.logstream.generated.model.IngestionTokenSessionResponse;

@Service
public class AppTokenServiceImpl implements AppTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_PREFIX = "lss_live_";
    private static final int TOKEN_RANDOM_BYTES = 24;
    private static final int TOKEN_PREFIX_DISPLAY_LENGTH = 16;

    private AppTokenRepository appTokenRepository;
    private AppRepository appRepository;
    private AppService appService;
    private int maxActiveTokensPerApp = 5;
    private int maxTotalTokensPerApp = 25;

    @Autowired
    public AppTokenServiceImpl(
            AppTokenRepository appTokenRepository,
            AppRepository appRepository,
            AppService appService,
            @Value("${app.quotas.max-active-tokens-per-app:5}") int maxActiveTokensPerApp,
            @Value("${app.quotas.max-total-tokens-per-app:25}") int maxTotalTokensPerApp) {
        this.appTokenRepository = appTokenRepository;
        this.appRepository = appRepository;
        this.appService = appService;
        this.maxActiveTokensPerApp = maxActiveTokensPerApp;
        this.maxTotalTokensPerApp = maxTotalTokensPerApp;
    }

    public AppTokenServiceImpl(AppTokenRepository appTokenRepository, AppRepository appRepository) {
        this(appTokenRepository, appRepository, null, 5, 25);
    }

    public AppTokenServiceImpl() {
    }

    @Override
    public CreateAppTokenResponse createAppToken(UUID appId, CreateAppTokenRequest createAppTokenRequest) {
        requireOwner(appId);
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new NoSuchElementException("App not found"));

        if (appTokenRepository.countByAppIdAndRevokedAtIsNull(appId) >= maxActiveTokensPerApp) {
            throw new QuotaExceededException(
                    "An app cannot have more than " + maxActiveTokensPerApp + " active ingestion tokens.");
        }

        // Lifetime cap including revoked tokens: revoking frees an active slot but must
        // not allow unbounded row growth through create/revoke churn.
        if (maxTotalTokensPerApp > 0 && appTokenRepository.countByAppId(appId) >= maxTotalTokensPerApp) {
            throw new QuotaExceededException(
                    "This app has reached its lifetime limit of " + maxTotalTokensPerApp
                            + " ingestion tokens (including revoked ones).");
        }

        String rawToken = generateRawToken();
        String tokenPrefix = rawToken.substring(0, Math.min(rawToken.length(), TOKEN_PREFIX_DISPLAY_LENGTH));
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
        requireOwner(appId);
        return appTokenRepository.findByAppId(appId).stream()
                .map(AppTokenMapper::toDto)
                .map(AppTokenMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void revokeAppToken(UUID appId, UUID tokenId) {
        requireOwner(appId);
        AppToken token = appTokenRepository.findByIdAndAppId(tokenId, appId)
                .orElseThrow(() -> new NoSuchElementException("Token not found"));
        token.setRevokedAt(OffsetDateTime.now());
        appTokenRepository.save(token);
    }

    @Override
    @Transactional
    public IngestionTokenSessionResponse resolveIngestionTokenSession(String rawToken) {
        AppTokenDTO token = validateAndRefreshToken(rawToken);

        IngestionTokenSessionResponse response = new IngestionTokenSessionResponse();
        response.setAppId(token.getAppId());
        response.setAppName(token.getAppName());
        response.setTokenPrefix(token.getTokenPrefix());
        response.setTokenName(token.getName());
        return response;
    }

    @Override
    @Transactional
    public AppTokenDTO validateAndRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Missing ingestion token");
        }

        String tokenHash = hash(rawToken);
        AppToken token = appTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid ingestion token"));

        if (token.getRevokedAt() != null) {
            throw new UnauthorizedException("Token revoked");
        }

        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Token expired");
        }

        token.setLastUsedAt(OffsetDateTime.now());
        return AppTokenMapper.toDto(appTokenRepository.save(token));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requireOwner(UUID appId) {
        if (appService != null) {
            appService.requireOwner(appId);
        }
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
