package com.logstream.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    // JWT resource-server auth is optional for MVP deploy (app.security.jwt-enabled=false).
    // Set JWT_ENABLED=true and JWT_ISSUER_URIS when /secured/* routes need Keycloak JWTs.

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Autowired(required = false) JwtDecoder jwtDecoder) throws Exception {
        http
            // no csrf because this is a stateless JWT API
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Define authorization rules for endpoints and roles here. Adjust as needed.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/public/**",
                "/error",
                "/test",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/v3/api-docs/**",
                "/v3/api-docs.yaml",
                "/api/v1/**").permitAll() // swagger paths only active when SWAGGER_UI_ENABLED=true
                .requestMatchers("/secured/admin").hasRole("ADMIN")
                .requestMatchers("/secured/user").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated());

        if (jwtDecoder != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.jwt-enabled", havingValue = "true")
    public JwtDecoder jwtDecoder(Environment environment) {
        // Trusted issuers from JWT_ISSUER_URIS (comma-separated).
        String configuredIssuers = environment.getProperty("JWT_ISSUER_URIS", "");
        List<String> issuerUris = Arrays.stream(configuredIssuers.split(","))
                .map(String::trim)
                .filter(uri -> !uri.isEmpty())
                .toList();

        if (issuerUris.isEmpty()) {
            throw new IllegalStateException(
                    "JWT_ENABLED=true requires JWT_ISSUER_URIS with at least one issuer URI");
        }

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
