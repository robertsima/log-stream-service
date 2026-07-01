package com.logstream.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.logstream.domain.entity.App;
import com.logstream.domain.entity.Users;
import com.logstream.domain.mapper.AppMapper;
import com.logstream.domain.repository.AppRepository;
import com.logstream.domain.repository.UserRepository;
import com.logstream.exception.ForbiddenException;
import com.logstream.exception.QuotaExceededException;
import com.logstream.exception.UnauthorizedException;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.security.CurrentUserProvider;

@Service
public class AppServiceImpl implements AppService {

    private AppRepository appRepository;
    private UserRepository userRepository;
    private UserServiceImpl userService;
    private CurrentUserProvider currentUserProvider;
    private boolean authEnabled;
    private int maxAppsPerUser = 10;

    @Autowired
    public AppServiceImpl(
            AppRepository appRepository,
            UserRepository userRepository,
            UserServiceImpl userService,
            CurrentUserProvider currentUserProvider,
            @Value("${app.security.auth-enabled:false}") boolean authEnabled,
            @Value("${app.quotas.max-apps-per-user:10}") int maxAppsPerUser) {
        this.appRepository = appRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
        this.authEnabled = authEnabled;
        this.maxAppsPerUser = maxAppsPerUser;
    }

    public AppServiceImpl(AppRepository appRepository, UserRepository userRepository) {
        this(appRepository, userRepository, null, null, false, 10);
    }

    public AppServiceImpl() {
    }

    @Override
    public AppResponse createApp(CreateAppRequest createAppRequest) {
        Users owner = resolveOwner(createAppRequest);

        if (appRepository.countByOwnerUserIdAndDeletedAtIsNull(owner.getId()) >= maxAppsPerUser) {
            throw new QuotaExceededException("A user cannot own more than " + maxAppsPerUser + " active apps.");
        }

        return appRepository.findByOwnerUserAndName(owner, createAppRequest.getName())
                .map(AppMapper::toResponse)
                .orElseGet(() -> {
                    App app = AppMapper.toEntity(AppMapper.toDto(createAppRequest, owner), owner);
                    App saved = appRepository.save(app);
                    return AppMapper.toResponse(saved);
                });
    }

    @Override
    public AppResponse getAppById(UUID appId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new NoSuchElementException("App not found"));
        requireOwner(app);
        return AppMapper.toResponse(AppMapper.toDto(app));
    }

    @Override
    public List<AppResponse> getAppsByOwnerEmail(String ownerEmail) {
        Users principalOwner = resolveCurrentOwnerOrNull();
        if (principalOwner != null) {
            Users owner = principalOwner;
            if (ownerEmail != null && !ownerEmail.isBlank() && !owner.getEmail().equalsIgnoreCase(ownerEmail)) {
                throw new ForbiddenException("You can only list apps for your signed-in user.");
            }
            return appRepository.findByOwnerUserId(owner.getId()).stream()
                    .map(AppMapper::toDto)
                    .map(AppMapper::toResponse)
                    .collect(Collectors.toList());
        }
        return appRepository.findByOwnerUserEmail(ownerEmail).stream()
                .map(AppMapper::toDto)
                .map(AppMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void requireOwner(UUID appId) {
        Users owner = resolveCurrentOwnerOrNull();
        if (owner == null && !authEnabled) {
            return;
        }
        if (owner == null) {
            owner = currentOwner();
        }
        if (!appRepository.existsByIdAndOwnerUserId(appId, owner.getId())) {
            throw new ForbiddenException("You can only manage apps you own.");
        }
    }

    private void requireOwner(App app) {
        Users owner = resolveCurrentOwnerOrNull();
        if (owner == null && !authEnabled) {
            return;
        }
        if (owner == null) {
            owner = currentOwner();
        }
        if (!owner.getId().equals(app.getOwnerUser().getId())) {
            throw new ForbiddenException("You can only manage apps you own.");
        }
    }

    private Users resolveOwner(CreateAppRequest createAppRequest) {
        return currentUserProvider.getPrincipal()
                .map(userService::getOrCreate)
                .orElseGet(() -> resolveLegacyOwner(createAppRequest));
    }

    private Users resolveLegacyOwner(CreateAppRequest createAppRequest) {
        if (authEnabled) {
            throw new UnauthorizedException("Sign in to manage PrairieLog resources.");
        }
        if (createAppRequest.getOwnerEmail() == null || createAppRequest.getOwnerEmail().isBlank()) {
            throw new NoSuchElementException("Owner user not found");
        }
        return userRepository.findByEmail(createAppRequest.getOwnerEmail())
                .orElseThrow(() -> new NoSuchElementException("Owner user not found"));
    }

    private Users currentOwner() {
        return currentUserProvider.getPrincipal()
                .map(userService::getOrCreate)
                .orElseThrow(() -> new UnauthorizedException("Sign in to manage PrairieLog resources."));
    }

    private Users resolveCurrentOwnerOrNull() {
        return currentUserProvider.getPrincipal()
                .map(userService::getOrCreate)
                .orElse(null);
    }
}
