package com.logstream.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.model.App;
import com.logstream.model.Users;
import com.logstream.repository.AppRepository;
import com.logstream.repository.UserRepository;

@Service
public class AppServiceImpl implements AppService {

    private final AppRepository appRepository;
    private final UserRepository userRepository;

    public AppServiceImpl(AppRepository appRepository, UserRepository userRepository) {
        this.appRepository = appRepository;
        this.userRepository = userRepository;
    }

    @Override
    public AppResponse createApp(CreateAppRequest createAppRequest) {
        Users owner = userRepository.findByEmail(createAppRequest.getOwnerEmail())
                .orElseThrow(() -> new NoSuchElementException("Owner user not found"));

        appRepository.findByOwnerUserAndName(owner, createAppRequest.getName()).ifPresent(existing -> {
            throw new IllegalStateException("App already exists for this owner");
        });

        App app = new App();
        app.setId(UUID.randomUUID());
        app.setOwnerUser(owner);
        app.setName(createAppRequest.getName());
        app.setDescription(createAppRequest.getDescription().isPresent() ? createAppRequest.getDescription().get() : null);
        app.setIsActive(true);
        app.setCreatedAt(OffsetDateTime.now());

        App saved = appRepository.save(app);
        return toResponse(saved);
    }

    @Override
    public AppResponse getAppById(UUID appId) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new NoSuchElementException("App not found"));
        return toResponse(app);
    }

    @Override
    public List<AppResponse> getAppsByOwnerEmail(String ownerEmail) {
        return appRepository.findByOwnerUserEmail(ownerEmail).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AppResponse toResponse(App app) {
        AppResponse response = new AppResponse(app.getId(), app.getOwnerUser().getId(), app.getName(), app.getCreatedAt())
                .isActive(app.getIsActive());

        if (app.getDescription() != null) {
            response.description(app.getDescription());
        }
        if (app.getUpdatedAt() != null) {
            response.updatedAt(app.getUpdatedAt());
        }
        return response;
    }
}
