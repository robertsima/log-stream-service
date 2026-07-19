package com.logstream.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.AlertDestinationsApi;
import com.logstream.generated.model.AlertDestinationResponse;
import com.logstream.generated.model.CreateAlertDestinationRequest;
import com.logstream.service.AlertDestinationService;

@RestController
public class AlertDestinationsController implements AlertDestinationsApi {

    private final AlertDestinationService alertDestinationService;

    public AlertDestinationsController(AlertDestinationService alertDestinationService) {
        this.alertDestinationService = alertDestinationService;
    }

    @Override
    public ResponseEntity<AlertDestinationResponse> createAlertDestination(
            UUID appId,
            CreateAlertDestinationRequest createAlertDestinationRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(alertDestinationService.create(appId, createAlertDestinationRequest));
    }

    @Override
    public ResponseEntity<List<AlertDestinationResponse>> getAlertDestinations(UUID appId) {
        return ResponseEntity.ok(alertDestinationService.findByApp(appId));
    }

    @Override
    public ResponseEntity<Void> deleteAlertDestination(UUID appId, UUID destinationId) {
        alertDestinationService.delete(appId, destinationId);
        return ResponseEntity.noContent().build();
    }
}