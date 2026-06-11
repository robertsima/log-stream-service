package com.logstream.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.logstream.entity.App;
import com.logstream.entity.Users;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.mapper.AppMapper;
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
        return AppMapper.toResponse(AppMapper.toDto(app));
    }

    @Override
    public List<AppResponse> getAppsByOwnerEmail(String ownerEmail) {
        return appRepository.findByOwnerUserEmail(ownerEmail).stream()
                .map(AppMapper::toDto)
                .map(AppMapper::toResponse)
                .collect(Collectors.toList());
    }
}
