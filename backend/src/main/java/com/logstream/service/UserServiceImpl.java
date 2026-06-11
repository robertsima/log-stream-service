package com.logstream.service;

import org.springframework.stereotype.Service;

import com.logstream.entity.Users;
import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;
import com.logstream.mapper.UserMapper;
import com.logstream.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse createUser(CreateUserRequest createUserRequest) {
        return userRepository.findByEmail(createUserRequest.getEmail())
                .map(UserMapper::toResponse)
                .orElseGet(() -> createNewUser(createUserRequest));
    }

    private UserResponse createNewUser(CreateUserRequest createUserRequest) {
        if (userRepository.existsByUsername(createUserRequest.getUsername())) {
            throw new IllegalStateException("Username already exists");
        }

        Users user = UserMapper.toEntity(UserMapper.toDto(createUserRequest));
        Users saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }
}


