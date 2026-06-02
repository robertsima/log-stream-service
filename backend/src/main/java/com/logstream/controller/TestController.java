package com.logstream.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.service.TestService;
import com.logstream.service.TestServiceImpl;

@RestController
public class TestController {
    TestService testService = new TestServiceImpl(); //injecting the test service
    
    @GetMapping("/test")
    public String test() {
        return testService.test(); //calling the test method from the service
    }
}
