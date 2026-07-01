package com.logstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.domain.entity.App;
import com.logstream.domain.entity.Users;

public interface AppRepository extends JpaRepository<App, UUID> {
    Optional<App> findByOwnerUserAndName(Users ownerUser, String name);
    List<App> findByOwnerUserEmail(String ownerEmail);
    List<App> findByOwnerUserId(UUID ownerUserId);
    long countByOwnerUserIdAndDeletedAtIsNull(UUID ownerUserId);
    boolean existsByIdAndOwnerUserId(UUID appId, UUID ownerUserId);
}
