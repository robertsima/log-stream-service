package com.logstream.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.generated.api.AuthApi;
import com.logstream.generated.model.DemoSessionRequest;
import com.logstream.generated.model.DemoSessionResponse;
import com.logstream.security.DemoJwtService;

@RestController
public class AuthController implements AuthApi {

    private final boolean demoBypassEnabled;
    private final String demoBypassEmail;
    private final DemoJwtService demoJwtService;

    public AuthController(
            @Value("${app.security.demo-bypass-enabled:false}") boolean demoBypassEnabled,
            @Value("${app.security.demo-bypass-email:admin@email.com}") String demoBypassEmail,
            DemoJwtService demoJwtService) {
        this.demoBypassEnabled = demoBypassEnabled;
        this.demoBypassEmail = demoBypassEmail;
        this.demoJwtService = demoJwtService;
    }

    @Override
    public ResponseEntity<DemoSessionResponse> createDemoSession(DemoSessionRequest demoSessionRequest) {
        if (!demoBypassEnabled
                || demoSessionRequest == null
                || demoSessionRequest.getEmail() == null
                || !demoBypassEmail.equalsIgnoreCase(demoSessionRequest.getEmail().trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DemoSessionResponse response = new DemoSessionResponse();
        response.setToken(demoJwtService.createToken(demoBypassEmail));
        response.setEmail(demoBypassEmail);
        response.setExpiresInSeconds(3600);
        return ResponseEntity.ok(response);
    }
}
