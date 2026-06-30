package com.logstream.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.logstream.entity.AlertDestination;
import com.logstream.exception.QuotaExceededException;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.repository.AlertDestinationRepository;

@Service
public class AlertDestinationServiceImpl implements AlertDestinationService {

    private final AlertDestinationRepository repository;
    private final AlertSenderService alertSenderService;
    private final AppService appService;
    private final int maxDestinationsPerApp;

    @Autowired
    public AlertDestinationServiceImpl(
            AlertDestinationRepository repository,
            AlertSenderService alertSenderService,
            AppService appService,
            @Value("${app.quotas.max-destinations-per-app:5}") int maxDestinationsPerApp) {
        this.repository = repository;
        this.alertSenderService = alertSenderService;
        this.appService = appService;
        this.maxDestinationsPerApp = maxDestinationsPerApp;
    }

    public AlertDestinationServiceImpl(AlertDestinationRepository repository, AlertSenderService alertSenderService) {
        this(repository, alertSenderService, null, 5);
    }

    @Override
    public AlertDestinationResponse create(UUID appId, CreateAlertDestinationRequest request) {
        requireOwner(appId);

        String webhookUrl = request.getWebhookUrl();
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            AlertDestination existingDestination = repository
                    .findFirstByAppIdAndWebhookUrlAndEnabledTrueAndDeletedAtIsNullOrderByCreatedAtDesc(appId, webhookUrl)
                    .orElse(null);
            if (existingDestination != null) {
                return toResponse(existingDestination);
            }
        }

        if (maxDestinationsPerApp > 0
                && repository.countByAppIdAndDeletedAtIsNull(appId) >= maxDestinationsPerApp) {
            throw new QuotaExceededException(
                    "An app cannot have more than " + maxDestinationsPerApp + " active alert destinations.");
        }

        AlertDestination destination = new AlertDestination();
        destination.setAppId(appId);
        destination.setName(request.getName());
        destination.setWebhookUrl(webhookUrl);
        destination.setDestinationType(
                com.logstream.generated.model.AlertDestinationType.fromValue(request.getType().getValue())
        );

        AlertDestination saved = repository.save(destination);
        return toResponse(saved);
    }

    @Override
    public List<AlertDestinationResponse> findByApp(UUID appId) {
        requireOwner(appId);
        return repository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void delete(UUID appId, UUID destinationId) {
        requireOwner(appId);
        AlertDestination destination = repository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId)
                .orElseThrow(() -> new IllegalArgumentException("Alert destination not found"));

        destination.softDelete();
        repository.save(destination);
    }

    @Override
    public void test(UUID appId, UUID destinationId) {
        requireOwner(appId);
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

    private void requireOwner(UUID appId) {
        if (appService != null) {
            appService.requireOwner(appId);
        }
    }
}
