package com.logstream.service;

import com.logstream.generated.model.CreateUserRequest;
import com.logstream.generated.model.UserResponse;

public interface UserService {
    UserResponse createUser(CreateUserRequest createUserRequest);
}
