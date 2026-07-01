package com.logstream.service;

import org.springframework.stereotype.Service;

import com.logstream.domain.entity.Users;
import com.logstream.domain.mapper.UserMapper;
import com.logstream.domain.repository.UserRepository;
import com.logstream.exception.UnauthorizedException;
import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;
import com.logstream.security.CurrentUserProvider;
import com.logstream.security.ManagementPrincipal;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public UserServiceImpl(UserRepository userRepository, CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        return userRepository.findByEmail(createUserRequest.getEmail())
                .map(UserMapper::toResponse)
                .orElseGet(() -> createNewUser(createUserRequest));
    }

    @Override
    public UserResponse getCurrentUser() {
        ManagementPrincipal principal = currentUserProvider.getPrincipal()
                .orElseThrow(() -> new UnauthorizedException("Sign in to manage PrairieLog resources."));
        return UserMapper.toResponse(getOrCreate(principal));
    }

    public Users getOrCreate(ManagementPrincipal principal) {
        if (principal.subject() != null && !principal.subject().isBlank()) {
            return userRepository.findByAuthProviderAndAuthSubject(principal.provider(), principal.subject())
                    .orElseGet(() -> findByEmailOrCreate(principal));
        }
        return findByEmailOrCreate(principal);
    }

    private Users findByEmailOrCreate(ManagementPrincipal principal) {
        return userRepository.findByEmail(principal.email())
                .map(existing -> updateAuthIdentity(existing, principal))
                .orElseGet(() -> createFromPrincipal(principal));
    }

    private Users updateAuthIdentity(Users user, ManagementPrincipal principal) {
        boolean changed = false;
        if (user.getAuthProvider() == null || user.getAuthProvider().isBlank()) {
            user.setAuthProvider(principal.provider());
            changed = true;
        }
        if ((user.getAuthSubject() == null || user.getAuthSubject().isBlank())
                && principal.subject() != null && !principal.subject().isBlank()) {
            user.setAuthSubject(principal.subject());
            changed = true;
        }
        return changed ? userRepository.save(user) : user;
    }

    private Users createFromPrincipal(ManagementPrincipal principal) {
        Users user = new Users();
        user.setId(java.util.UUID.randomUUID());
        user.setEmail(principal.email());
        user.setUsername(buildUsername(principal));
        user.setRole("USER");
        user.setAuthProvider(principal.provider());
        user.setAuthSubject(principal.subject());
        user.setCreatedAt(java.time.OffsetDateTime.now());
        return userRepository.save(user);
    }

    private UserResponse createNewUser(CreateUserRequest createUserRequest) {
        if (userRepository.existsByUsername(createUserRequest.getUsername())) {
            throw new IllegalStateException("Username already exists");
        }

        Users user = UserMapper.toEntity(UserMapper.toDto(createUserRequest));
        user.setRole("USER");
        Users saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    private String buildUsername(ManagementPrincipal principal) {
        String base = principal.email().split("@")[0].replaceAll("[^A-Za-z0-9_\\-]", "");
        if (base.length() < 3) {
            base = "user";
        }
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix;
            suffix++;
        }
        return username;
    }
}


