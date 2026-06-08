package com.logstream.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.logstream")
@EnableJpaRepositories(basePackages = "com.logstream.repository")
@EntityScan(basePackages = "com.logstream.entity")
public class LogStreamService {

	public static void main(String[] args) {
		SpringApplication.run(LogStreamService.class, args);
	}

}
