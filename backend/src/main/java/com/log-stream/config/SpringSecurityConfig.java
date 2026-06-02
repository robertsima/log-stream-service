package com.example_project_name.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {
    //This file supports two config styles for trusted JWT issuers:
    //1) A list of issuer URIs configured via spring.security.oauth2.resourceserver.jwt.issuer-uris
    //2) A single Keycloak issuer constructed from keycloak.auth-server-url and keycloak
    // ===================================================================
    //      spring:
    //        security:
    //          oauth2:
    //            resourceserver:
    //              jwt:
    //                issuer-uris:
    //                  - http://localhost:8080/realms/my-realm
    // ==================================================================
    //      keycloak:
    //        auth-server-url: http://localhost:8080
    //        realm: my-realm

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // no csrf because this is a stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Define authorization rules for endpoints and roles here. Adjust as needed.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/public/**",
                "/error",
                "/swagger-ui/**", //swagger endpoints public for testing - consider securing or removing in production
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/test",
                "/v3/api-docs.yaml").permitAll() // open endpoints
                .requestMatchers("/secured/admin").hasRole("ADMIN") //keycloak roles may need a jwt converter
                .requestMatchers("/secured/user").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated())

            // validate incoming bearer tokens as JWTs.
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(Environment environment) {
        // can use multiple trusted issuers configured via spring.security.oauth2.resourceserver.jwt.issuer-uris
        List<String> issuerUris = Binder.get(environment)
                .bind("spring.security.oauth2.resourceserver.jwt.issuer-uris", Bindable.listOf(String.class))
                .orElse(List.of());

        // alternatively a single Keycloak issuer defined via keycloak.* properties.
        String keycloakAuthServerUrl = Binder.get(environment)
                .bind("keycloak.auth-server-url", String.class)
                .orElse(null);
        String keycloakRealm = Binder.get(environment)
                .bind("keycloak.realm", String.class)
                .orElse(null);

        if (keycloakAuthServerUrl != null && keycloakRealm != null) {
            String keycloakIssuer = buildKeycloakIssuer(keycloakAuthServerUrl, keycloakRealm);
            if (issuerUris.stream().noneMatch(uri -> normalizeIssuerUri(uri).equals(normalizeIssuerUri(keycloakIssuer)))) {
                issuerUris = Stream.concat(issuerUris.stream(), Stream.of(keycloakIssuer)).toList();
            }
        }

        if (issuerUris.isEmpty()) { //no issuers
            throw new IllegalStateException("Missing issuer URIs configuration and no Keycloak issuer configured");
        }

        // stop eagerly creating remote `JwtDecoder` instances (which perform network calls) during
        // bean creation because placeholder or unreachable issuer URIs will
        // otherwise fail application startup with errors like "Bad authority".
        var allowedIssuers = issuerUris.stream()
                .map(this::normalizeIssuerUri)
                .collect(Collectors.toUnmodifiableSet());

        // Cache decoders created on-demand to avoid repeated remote metadata fetches.
        var decoderCache = new java.util.concurrent.ConcurrentHashMap<String, JwtDecoder>();

        return token -> {
            String issuer = getIssuer(token);
            String norm = normalizeIssuerUri(issuer);
            if (!allowedIssuers.contains(norm)) {
                throw new BadJwtException("Untrusted issuer: " + issuer);
            }

            try {
                // Lazily create (and cache) a decoder for this issuer.
                JwtDecoder decoder = decoderCache.computeIfAbsent(norm, JwtDecoders::fromIssuerLocation);
                return decoder.decode(token);
            } catch (Exception ex) {
                // Surface a clearer error that the decoder could not be created/used.
                throw new BadJwtException("Failed to validate token for issuer " + issuer + ": " + ex.getMessage(), ex);
            }
        };
    }


    //Helper method to construct Keycloak issuer URI from auth server URL and realm
    private String buildKeycloakIssuer(String authServerUrl, String realm) {
        String normalized = authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1) : authServerUrl;
        return normalized + "/realms/" + realm;
    }

    //Helper method to extract issuer without decoding entire jwt to determine which decoder to use
    private String getIssuer(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new BadJwtException("Invalid JWT format");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode json = new ObjectMapper().readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode issuer = json.get("iss");
            if (issuer == null || issuer.textValue() == null || issuer.textValue().isEmpty()) {
                throw new BadJwtException("Missing issuer claim");
            }
            return issuer.textValue();
            } catch (BadJwtException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BadJwtException("Unable to parse JWT issuer", ex);
}
    }

    //Helper merthod to normalize issuer URIs for consistent comparison 
    private String normalizeIssuerUri(String issuer) {
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }
}
