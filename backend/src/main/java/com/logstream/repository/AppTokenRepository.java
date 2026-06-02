package com.logstream.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.entity.AppToken;

public interface AppTokenRepository extends JpaRepository<AppToken, UUID> {
    Optional<AppToken> findByTokenHash(String tokenHash);
    List<AppToken> findByAppId(UUID appId);
    List<AppToken> findByAppIdAndRevokedAtIsNull(UUID appId);
    Optional<AppToken> findByIdAndAppId(UUID tokenId, UUID appId);
}
