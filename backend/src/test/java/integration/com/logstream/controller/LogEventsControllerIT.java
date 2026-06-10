package integration.com.logstream.controller;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import integration.com.logstream.PostgresBaseIT;

class LogEventsControllerIT extends PostgresBaseIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void ingestLogEvent_shouldReturnUnauthorizedWhenTokenMissing() {
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/log-events",
                Map.of(
                        "id", "event-1",
                        "level", "ERROR",
                        "message", "Payment failed",
                        "occurredAt", OffsetDateTime.now().toString(),
                        "logger", "PaymentService"
                ),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ingestLogEvent_shouldReturnUnauthorizedWhenTokenInvalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Ingestion-Token", "lss_live_invalid");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of(
                        "id", "event-1",
                        "level", "ERROR",
                        "message", "Payment failed",
                        "occurredAt", OffsetDateTime.now().toString(),
                        "logger", "PaymentService"
                ),
                headers
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/log-events",
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void ingestLogEvent_shouldReturnAcceptedWhenTokenValid() {
        // 1. Create user
        restTemplate.postForEntity(
                "/api/v1/users",
                Map.of(
                        "email", "valid-token@example.com",
                        "username", "validtoken"
                ),
                Map.class
        );

        // 2. Create app
        ResponseEntity<Map> appResponse = restTemplate.postForEntity(
                "/api/v1/apps",
                Map.of(
                        "ownerEmail", "valid-token@example.com",
                        "name", "payment-service"
                ),
                Map.class
        );

        String appId = String.valueOf(appResponse.getBody().get("id"));

        // 3. Create token
        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "/api/v1/apps/" + appId + "/tokens",
                Map.of("name", "test-token"),
                Map.class
        );

        assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String rawToken = String.valueOf(tokenResponse.getBody().get("token"));

        // 4. Send log
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Ingestion-Token", rawToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of(
                        "id", "event-1",
                        "level", "ERROR",
                        "message", "Payment failed",
                        "occurredAt", OffsetDateTime.now().toString(),
                        "logger", "PaymentService",
                        "traceId", "trace-123"
                ),
                headers
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/log-events",
                request,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}