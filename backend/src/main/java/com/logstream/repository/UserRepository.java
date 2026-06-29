package com.logstream.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.logstream.entity.Users;

public interface UserRepository extends JpaRepository<Users, UUID> {
    Optional<Users> findByEmail(String email);
    Optional<Users> findByUsername(String username);
    Optional<Users> findByAuthProviderAndAuthSubject(String authProvider, String authSubject);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}

