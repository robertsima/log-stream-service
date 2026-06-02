package com.example_project_name.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example_project_name")
@EnableJpaRepositories(basePackages = "com.example_project_name.repository")
@EntityScan(basePackages = "com.example_project_name.model")
public class MyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyServiceApplication.class, args);
	}

}
