package com.logstream.webhooks;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.entity.AlertDestination;

@Service
public class DiscordWebhookSender {

    private final RestClient restClient;

    public DiscordWebhookSender(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public void sendTest(AlertDestination destination) {
        System.out.println("Sending Discord test alert to destination: " + destination.getId());
        System.out.println("Destination type: " + destination.getDestinationType());
        System.out.println("Webhook URL starts with: " + mask(destination.getWebhookUrl()));

        Map<String, Object> payload = Map.of(
                "content", "Log Stream Service test alert is working."
        );

        ResponseEntity<Void> response = restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        System.out.println("Discord webhook response status: " + response.getStatusCode());
    }

    private String mask(String url) {
        if (url == null || url.length() < 30) {
            return String.valueOf(url);
        }

        return url.substring(0, 30) + "...";
    }
}
