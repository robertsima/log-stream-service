package com.logstream.service;

import java.util.List;
import java.util.UUID;

import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;

public interface AlertDestinationService {
    AlertDestinationResponse create(UUID appId, CreateAlertDestinationRequest request);

    List<AlertDestinationResponse> findByApp(UUID appId);

    void delete(UUID appId, UUID destinationId);

    void test(UUID appId, UUID destinationId);
}
