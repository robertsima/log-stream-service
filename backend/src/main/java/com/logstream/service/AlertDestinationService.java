package com.logstream.service;

import java.util.List;
import java.util.UUID;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.generated.model.LogEventRequest;

public interface AlertDestinationService {
    AlertDestinationResponse create(UUID appId, CreateAlertDestinationRequest request);

    List<AlertDestinationResponse> findByApp(UUID appId);

    void delete(UUID appId, UUID destinationId);

    void test(UUID appId, UUID destinationId);

    void sendAnalyzedAlert(
            UUID appId,
            UUID destinationId,
            String fingerprint,
            List<LogEventRequest> events,
            String analysis);

    void sendAnalyzedAlert(
            UUID appId,
            UUID destinationId,
            AlertTrigger alertTrigger,
            String analysis);
}
