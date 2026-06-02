package com.logstream.mapper;

import java.time.OffsetDateTime;

import com.logstream.controller.dto.AppDTO;
import com.logstream.entity.App;
import com.logstream.entity.Users;
import com.logstream.generated.model.AppResponse;
import com.logstream.generated.model.CreateAppRequest;

public final class AppMapper {

    private AppMapper() {
    }

    public static AppDTO toDto(CreateAppRequest request, Users owner) {
        AppDTO dto = new AppDTO();
        dto.setId(java.util.UUID.randomUUID());
        dto.setOwnerUserId(owner.getId());
        dto.setName(request.getName());
        dto.setDescription(request.getDescription());
        dto.setIsActive(true);
        dto.setCreatedAt(OffsetDateTime.now());
        return dto;
    }

    public static AppDTO toDto(App app) {
        AppDTO dto = new AppDTO();
        dto.setId(app.getId());
        dto.setOwnerUserId(app.getOwnerUser().getId());
        dto.setName(app.getName());
        dto.setDescription(app.getDescription());
        dto.setIsActive(app.getIsActive());
        dto.setCreatedAt(app.getCreatedAt());
        dto.setUpdatedAt(app.getUpdatedAt());
        dto.setDeletedAt(app.getDeletedAt());
        return dto;
    }

    public static App toEntity(AppDTO dto, Users owner) {
        App app = new App();
        app.setId(dto.getId());
        app.setOwnerUser(owner);
        app.setName(dto.getName());
        app.setDescription(dto.getDescription());
        app.setIsActive(dto.getIsActive());
        app.setCreatedAt(dto.getCreatedAt());
        app.setUpdatedAt(dto.getUpdatedAt());
        app.setDeletedAt(dto.getDeletedAt());
        return app;
    }

    public static AppResponse toResponse(App app) {
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

    public static AppResponse toResponse(AppDTO dto) {
        AppResponse response = new AppResponse(dto.getId(), dto.getOwnerUserId(), dto.getName(), dto.getCreatedAt())
                .isActive(dto.getIsActive());

        if (dto.getDescription() != null) {
            response.description(dto.getDescription());
        }
        if (dto.getUpdatedAt() != null) {
            response.updatedAt(dto.getUpdatedAt());
        }
        return response;
    }
}
