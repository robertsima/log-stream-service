package com.logstream.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ManagementAuthFilter extends OncePerRequestFilter {

    private final boolean authEnabled;
    private final String firebaseProjectId;
    private final DemoJwtService demoJwtService;
    private final ObjectMapper objectMapper;
    private volatile JwtDecoder firebaseDecoder;

    public ManagementAuthFilter(
            @Value("${app.security.auth-enabled:false}") boolean authEnabled,
            @Value("${app.security.firebase-project-id:}") String firebaseProjectId,
            DemoJwtService demoJwtService) {
        this.authEnabled = authEnabled;
        this.firebaseProjectId = firebaseProjectId;
        this.demoJwtService = demoJwtService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
                || path.startsWith("/api/v1/log-events")
                || path.startsWith("/api/v1/kafka")
                || path.startsWith("/api/v1/ingestion-tokens/")
                || path.equals("/api/v1/auth/demo-session");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            if (!authEnabled) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing Bearer token.");
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        ManagementPrincipal principal = demoJwtService.verify(token);
        if (principal == null) {
            principal = verifyFirebase(token);
        }

        if (principal == null) {
            if (!authEnabled) {
                filterChain.doFilter(request, response);
                return;
            }
            writeError(response, request, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Bearer token.");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private ManagementPrincipal verifyFirebase(String token) {
        if (firebaseProjectId == null || firebaseProjectId.isBlank()) {
            return null;
        }

        try {
            Jwt jwt = getFirebaseDecoder().decode(token);
            String email = jwt.getClaimAsString("email");
            String subject = jwt.getSubject();
            String name = jwt.getClaimAsString("name");
            if (email == null || email.isBlank() || subject == null || subject.isBlank()) {
                return null;
            }
            return new ManagementPrincipal(email, subject, name, "firebase");
        } catch (Exception ex) {
            return null;
        }
    }

    private JwtDecoder getFirebaseDecoder() {
        JwtDecoder decoder = firebaseDecoder;
        if (decoder == null) {
            synchronized (this) {
                decoder = firebaseDecoder;
                if (decoder == null) {
                    String issuer = "https://securetoken.google.com/" + firebaseProjectId;
                    NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
                    OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
                        if (jwt.getAudience().contains(firebaseProjectId)) {
                            return OAuth2TokenValidatorResult.success();
                        }
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token",
                                "Firebase ID token audience must match FIREBASE_PROJECT_ID.",
                                null));
                    };
                    nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(issuer),
                            audienceValidator));
                    decoder = nimbusDecoder;
                    firebaseDecoder = decoder;
                }
            }
        }
        return decoder;
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "code", code,
                "message", message,
                "path", request.getRequestURI(),
                "timestamp", OffsetDateTime.now().toString()));
    }
}
