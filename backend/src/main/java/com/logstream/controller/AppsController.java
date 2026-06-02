package com.logstream.controller;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.AppsApi;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;
import com.logstream.service.AppService;

@RestController
public class AppsController implements AppsApi {

    private final AppService appService;

    public AppsController(AppService appService) {
        this.appService = appService;
    }

    @Override
    public ResponseEntity<AppResponse> createApp(CreateAppRequest createAppRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(appService.createApp(createAppRequest));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @Override
    public ResponseEntity<AppResponse> getAppById(UUID appId) {
        try {
            return ResponseEntity.ok(appService.getAppById(appId));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @Override
    public ResponseEntity<List<AppResponse>> getAppsByOwnerEmail(String ownerEmail) {
        return ResponseEntity.ok(appService.getAppsByOwnerEmail(ownerEmail));
    }
}
