package com.logstream.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.logstream.entity.AlertDestination;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.repository.AlertDestinationRepository;

@Service
public class AlertDestinationServiceImpl implements AlertDestinationService {

    private final AlertDestinationRepository repository;
    private final AlertSenderService alertSenderService;

    public AlertDestinationServiceImpl(AlertDestinationRepository repository, AlertSenderService alertSenderService) {
        this.repository = repository;
        this.alertSenderService = alertSenderService;
    }

    @Override
    public AlertDestinationResponse create(UUID appId, CreateAlertDestinationRequest request) {
        AlertDestination destination = new AlertDestination();
        destination.setAppId(appId);
        destination.setName(request.getName());
        destination.setWebhookUrl(request.getWebhookUrl());
        destination.setDestinationType(
                com.logstream.generated.model.AlertDestinationType.fromValue(request.getType().getValue())
        );

        AlertDestination saved = repository.save(destination);
        return toResponse(saved);
    }

    @Override
    public List<AlertDestinationResponse> findByApp(UUID appId) {
        return repository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID appId, UUID destinationId) {
        AlertDestination destination = repository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId)
                .orElseThrow(() -> new IllegalArgumentException("Alert destination not found"));

        destination.softDelete();
        repository.save(destination);
    }

    @Override
    public void test(UUID appId, UUID destinationId) {
        AlertDestination destination = repository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId)
                .orElseThrow(() -> new IllegalArgumentException("Alert destination not found"));

        System.out.println("Testing alert destination: " + destination.getId());

        alertSenderService.sendTest(destination);
    }

    private AlertDestinationResponse toResponse(AlertDestination destination) {
        return new AlertDestinationResponse()
                .id(destination.getId())
                .appId(destination.getAppId())
                .type(destination.getDestinationType())
                .name(destination.getName())
                .enabled(destination.getEnabled())
                .createdAt(destination.getCreatedAt())
                .updatedAt(destination.getUpdatedAt());
    }
}