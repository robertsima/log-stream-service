package com.example_project_name.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example_project_name.controller.dto.UserDTO;
import com.example_project_name.model.Users;
import com.example_project_name.repository.UserRepository;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    //basic method implementation for getting user by name, returns a list of UserDTOs
    @Override
    public List<UserDTO> getUsersByName(String name) {
        List<Users> users = userRepository.findByName(name);
        return users.stream().map(u -> {
            UserDTO dto = new UserDTO();
            dto.setName(u.getName());
            return dto;
        }).collect(Collectors.toList());
    }
}


