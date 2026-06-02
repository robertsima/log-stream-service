package com.logstream.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.model.App;
import com.logstream.model.Users;

public interface AppRepository extends JpaRepository<App, UUID> {
    Optional<App> findByOwnerUserAndName(Users ownerUser, String name);
    List<App> findByOwnerUserEmail(String ownerEmail);
    List<App> findByOwnerUserId(UUID ownerUserId);
}
