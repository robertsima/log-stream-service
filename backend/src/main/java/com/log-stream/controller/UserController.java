package com.example_project_name.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example_project_name.controller.dto.UserDTO;
import com.example_project_name.generated.api.UsersApi;
import com.example_project_name.service.UserService;


@RestController
public class UserController implements UsersApi { //uses the generated openAPI interface 

    UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public List<UserDTO> getMethodName(@RequestParam String name) {
        List<UserDTO> users = userService.getUsersByName(name); // just an example endpoint for testing 
        return users;
    }
    
}
