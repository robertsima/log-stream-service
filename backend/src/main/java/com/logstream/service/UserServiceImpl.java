package com.logstream.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;
import com.logstream.model.Users;
import com.logstream.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        if (userRepository.existsByEmail(createUserRequest.getEmail())
                || userRepository.existsByUsername(createUserRequest.getUsername())) {
            throw new IllegalStateException("User already exists");
        }

        Users user = new Users();
        user.setId(UUID.randomUUID());
        user.setEmail(createUserRequest.getEmail());
        user.setUsername(createUserRequest.getUsername());
        user.setCreatedAt(OffsetDateTime.now());

        Users saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getEmail(), saved.getUsername(), saved.getCreatedAt());
    }
}


