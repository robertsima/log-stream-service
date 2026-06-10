package integration.com.logstream.controller;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import integration.com.logstream.PostgresBaseIT;

class AlertDestinationsControllerIT extends PostgresBaseIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAlertDestination_shouldReturnCreatedDestination() {
        // 1. Create user
        ResponseEntity<Map> userResponse = restTemplate.postForEntity(
                "/api/v1/users",
                Map.of(
                        "email", "robert@example.com",
                        "username", "robert"
                ),
                Map.class
        );

        assertThat(userResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 2. Create app
        ResponseEntity<Map> appResponse = restTemplate.postForEntity(
                "/api/v1/apps",
                Map.of(
                        "ownerEmail", "robert@example.com",
                        "name", "payment-service",
                        "description", "Payment logs"
                ),
                Map.class
        );

        assertThat(appResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String appId = String.valueOf(appResponse.getBody().get("id"));

        // 3. Create alert destination
        ResponseEntity<Map> destinationResponse = restTemplate.postForEntity(
                "/api/v1/apps/" + appId + "/alert-destinations",
                Map.of(
                        "type", "DISCORD",
                        "name", "dev-alerts",
                        "webhookUrl", "https://discord.com/api/webhooks/test/test"
                ),
                Map.class
        );

        assertThat(destinationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(destinationResponse.getBody().get("appId")).isEqualTo(appId);
        assertThat(destinationResponse.getBody().get("type")).isEqualTo("DISCORD");
        assertThat(destinationResponse.getBody().get("name")).isEqualTo("dev-alerts");
        assertThat(destinationResponse.getBody().get("enabled")).isEqualTo(true);
    }

    @Test
    void getAlertDestinations_shouldReturnDestinationsForApp() {
        restTemplate.postForEntity(
                "/api/v1/users",
                Map.of(
                        "email", "alice@example.com",
                        "username", "alice"
                ),
                Map.class
        );

        ResponseEntity<Map> appResponse = restTemplate.postForEntity(
                "/api/v1/apps",
                Map.of(
                        "ownerEmail", "alice@example.com",
                        "name", "inventory-service"
                ),
                Map.class
        );

        String appId = String.valueOf(appResponse.getBody().get("id"));

        restTemplate.postForEntity(
                "/api/v1/apps/" + appId + "/alert-destinations",
                Map.of(
                        "type", "SLACK",
                        "name", "slack-alerts",
                        "webhookUrl", "https://hooks.slack.com/services/test/test/test"
                ),
                Map.class
        );

        ResponseEntity<Object[]> response = restTemplate.getForEntity(
                "/api/v1/apps/" + appId + "/alert-destinations",
                Object[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}