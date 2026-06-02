package com.logstream.mapper;

import com.logstream.controller.dto.UserDTO;
import com.logstream.entity.Users;
import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserDTO toDto(CreateUserRequest request) {
        UserDTO dto = new UserDTO();
        dto.setId(java.util.UUID.randomUUID());
        dto.setEmail(request.getEmail());
        dto.setUsername(request.getUsername());
        dto.setCreatedAt(java.time.OffsetDateTime.now());
        return dto;
    }

    public static Users toEntity(UserDTO dto) {
        Users user = new Users();
        user.setId(dto.getId());
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setCreatedAt(dto.getCreatedAt());
        user.setUpdatedAt(dto.getUpdatedAt());
        user.setDeletedAt(dto.getDeletedAt());
        return user;
    }

    public static UserResponse toResponse(Users user) {
        UserResponse response = new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getCreatedAt());
        if (user.getUpdatedAt() != null) {
            response.updatedAt(user.getUpdatedAt());
        }
        return response;
    }
}
