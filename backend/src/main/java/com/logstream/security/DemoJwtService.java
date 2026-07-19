package com.logstream.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DemoJwtService {

    private static final String HMAC = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public DemoJwtService(@Value("${app.security.demo-jwt-secret:}") String configuredSecret) {
        this.objectMapper = JsonMapper.builder().build();
        this.secret = configuredSecret == null || configuredSecret.isBlank()
                ? generateEphemeralSecret()
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String createToken(String email) {
        try {
            Instant now = Instant.now();
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> claims = Map.of(
                    "iss", "prairielog-demo",
                    "sub", email,
                    "email", email,
                    "name", "PrairieLog Demo",
                    "provider", "demo",
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(3600).getEpochSecond());

            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedClaims = encode(objectMapper.writeValueAsBytes(claims));
            String signingInput = encodedHeader + "." + encodedClaims;
            return signingInput + "." + encode(sign(signingInput));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create demo token", ex);
        }
    }

    public ManagementPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = encode(sign(signingInput)).getBytes(StandardCharsets.UTF_8);
            byte[] actual = parts[2].getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }

            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    });

            if (!"prairielog-demo".equals(claims.get("iss"))) {
                return null;
            }

            Number exp = (Number) claims.get("exp");
            if (exp == null || exp.longValue() <= Instant.now().getEpochSecond()) {
                return null;
            }

            String email = (String) claims.get("email");
            String subject = (String) claims.get("sub");
            String name = (String) claims.get("name");
            if (email == null || email.isBlank()) {
                return null;
            }
            return new ManagementPrincipal(email, subject, name, "demo");
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(secret, HMAC));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] generateEphemeralSecret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return value;
    }
}
