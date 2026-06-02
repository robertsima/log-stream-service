package com.example_project_name.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example_project_name.model.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
    List<Users> findByName(String name);
}

