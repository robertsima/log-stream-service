package com.logstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.domain.entity.AppToken;

public interface AppTokenRepository extends JpaRepository<AppToken, UUID> {
    @EntityGraph(attributePaths = "app")
    Optional<AppToken> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = "app")
    List<AppToken> findByAppId(UUID appId);

    List<AppToken> findByAppIdAndRevokedAtIsNull(UUID appId);
    long countByAppIdAndRevokedAtIsNull(UUID appId);
    Optional<AppToken> findByIdAndAppId(UUID tokenId, UUID appId);
}
