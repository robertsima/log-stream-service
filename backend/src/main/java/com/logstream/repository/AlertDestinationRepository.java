package com.logstream.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.entity.AlertDestination;

public interface AlertDestinationRepository extends JpaRepository<AlertDestination, UUID> {

    List<AlertDestination> findByAppIdAndEnabledTrueAndDeletedAtIsNull(UUID appId);

    Optional<AlertDestination> findByIdAndAppIdAndDeletedAtIsNull(UUID id, UUID appId);
}