package com.logstream.service;

import java.util.List;
import java.util.UUID;

import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;

public interface AppService {
    AppResponse createApp(CreateAppRequest createAppRequest);
    AppResponse getAppById(UUID appId);
    List<AppResponse> getAppsByOwnerEmail(String ownerEmail);
}
