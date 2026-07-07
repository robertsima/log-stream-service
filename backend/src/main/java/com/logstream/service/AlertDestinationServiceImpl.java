package com.logstream.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.domain.repository.AlertDestinationRepository;
import com.logstream.exception.QuotaExceededException;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.generated.model.LogEventRequest;

@Service
public class AlertDestinationServiceImpl implements AlertDestinationService {

    private final AlertDestinationRepository repository;
    private final AlertSenderService alertSenderService;
    private final AppService appService;
    private final int maxDestinationsPerApp;
    private final int maxTotalDestinationsPerApp;

    @Autowired
    public AlertDestinationServiceImpl(
            AlertDestinationRepository repository,
            AlertSenderService alertSenderService,
            AppService appService,
            @Value("${app.quotas.max-destinations-per-app:5}") int maxDestinationsPerApp,
            @Value("${app.quotas.max-total-destinations-per-app:25}") int maxTotalDestinationsPerApp) {
        this.repository = repository;
        this.alertSenderService = alertSenderService;
        this.appService = appService;
        this.maxDestinationsPerApp = maxDestinationsPerApp;
        this.maxTotalDestinationsPerApp = maxTotalDestinationsPerApp;
    }

    public AlertDestinationServiceImpl(AlertDestinationRepository repository, AlertSenderService alertSenderService) {
        this(repository, alertSenderService, null, 5, 25);
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

        // Lifetime cap including soft-deleted destinations: deleting frees an active slot
        // but must not allow unbounded row growth through create/delete churn.
        if (maxTotalDestinationsPerApp > 0
                && repository.countByAppId(appId) >= maxTotalDestinationsPerApp) {
            throw new QuotaExceededException(
                    "This app has reached its lifetime limit of " + maxTotalDestinationsPerApp
                            + " alert destinations (including deleted ones).");
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

    @Override
    public void sendAnalyzedAlert(
            UUID appId,
            UUID destinationId,
            String fingerprint,
            List<LogEventRequest> events,
            String analysis) {
        requireOwner(appId);
        AlertDestination destination = repository.findByIdAndAppIdAndDeletedAtIsNull(destinationId, appId)
                .orElseThrow(() -> new IllegalArgumentException("Alert destination not found"));

        AlertBucket bucket = new AlertBucket(appId, fingerprint);
        if (appService != null) {
            bucket.setAppName(appService.getAppById(appId).getName());
        }
        if (events != null) {
            bucket.getEvents().addAll(events);
        }

        alertSenderService.sendAnalyzedAlert(destination, bucket, analysis);
    }

    private AlertDestinationResponse toResponse(AlertDestination destination) {
        AlertDestinationResponse response = new AlertDestinationResponse();
        response.setId(destination.getId());
        response.setAppId(destination.getAppId());
        response.setType(destination.getDestinationType());
        response.setName(destination.getName());
        response.setEnabled(destination.getEnabled());
        response.setCreatedAt(destination.getCreatedAt());
        response.setUpdatedAt(destination.getUpdatedAt());
        return response;
    }

    private void requireOwner(UUID appId) {
        if (appService != null) {
            appService.requireOwner(appId);
        }
    }
}
