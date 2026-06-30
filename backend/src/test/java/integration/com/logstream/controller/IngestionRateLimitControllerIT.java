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
import org.springframework.test.context.TestPropertySource;

import integration.com.logstream.PostgresBaseIT;

@TestPropertySource(properties = {
        "app.rate-limit.ingestion-requests-per-minute=2"
})
class IngestionRateLimitControllerIT extends PostgresBaseIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void ingestLogEvent_shouldReturn429AfterPerTokenLimit() {
        restTemplate.postForEntity(
                "/api/v1/users",
                Map.of("email", "ingest-rate@example.com", "username", "ingestrate"),
                Map.class
        );

        ResponseEntity<Map> appResponse = restTemplate.postForEntity(
                "/api/v1/apps",
                Map.of("ownerEmail", "ingest-rate@example.com", "name", "ingest-rate-service"),
                Map.class
        );
        String appId = String.valueOf(appResponse.getBody().get("id"));

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                "/api/v1/apps/" + appId + "/tokens",
                Map.of("name", "rate-token"),
                Map.class
        );
        String rawToken = String.valueOf(tokenResponse.getBody().get("token"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Ingestion-Token", rawToken);

        // Limit is 2 per minute: first two are accepted.
        for (int i = 0; i < 2; i++) {
            assertThat(sendLog(headers).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        // Third within the same window is rejected.
        ResponseEntity<Map> overLimit = sendLogForBody(headers);
        assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(overLimit.getBody().get("code")).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    private ResponseEntity<Void> sendLog(HttpHeaders headers) {
        return restTemplate.postForEntity("/api/v1/log-events", logRequest(headers), Void.class);
    }

    private ResponseEntity<Map> sendLogForBody(HttpHeaders headers) {
        return restTemplate.postForEntity("/api/v1/log-events", logRequest(headers), Map.class);
    }

    private HttpEntity<Map<String, Object>> logRequest(HttpHeaders headers) {
        return new HttpEntity<>(
                Map.of(
                        "id", "event-" + System.nanoTime(),
                        "level", "ERROR",
                        "message", "Rate limit test",
                        "occurredAt", OffsetDateTime.now().toString(),
                        "logger", "RateLimitTest"
                ),
                headers
        );
    }
}
