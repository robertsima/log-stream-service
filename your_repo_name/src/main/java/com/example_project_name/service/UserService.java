package com.example_project_name.service;

import java.util.List;

import com.example_project_name.controller.dto.UserDTO;

public interface UserService { //interface for user
    public List<UserDTO> getUsersByName(String name);

}
