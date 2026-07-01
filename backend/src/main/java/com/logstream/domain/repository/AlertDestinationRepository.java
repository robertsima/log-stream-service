package com.logstream.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.domain.entity.AlertDestination;

public interface AlertDestinationRepository extends JpaRepository<AlertDestination, UUID> {

    List<AlertDestination> findByAppIdAndEnabledTrueAndDeletedAtIsNull(UUID appId);

    Optional<AlertDestination> findFirstByAppIdAndWebhookUrlAndEnabledTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID appId,
            String webhookUrl);

    Optional<AlertDestination> findByIdAndAppIdAndDeletedAtIsNull(UUID id, UUID appId);

    long countByAppIdAndDeletedAtIsNull(UUID appId);

}
