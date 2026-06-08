package com.logstream.webhooks;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.logstream.entity.AlertDestination;

@Service
public class SlackWebhookSender {

    private final RestClient restClient;

    public SlackWebhookSender(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public void sendTest(AlertDestination destination) {
        Map<String, Object> payload = Map.of(
                "text",
                """
                :white_check_mark: *Log Stream Service Test Alert*
                Your Slack alert destination is working.

                *Destination:* %s
                *Type:* %s
                """.formatted(destination.getName(), destination.getDestinationType().name())
        );

        restClient.post()
                .uri(destination.getWebhookUrl())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
