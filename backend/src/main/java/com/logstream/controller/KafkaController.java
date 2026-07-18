package com.logstream.controller;

import tools.jackson.databind.JsonNode;
import com.logstream.exception.KafkaUnavailableException;
import com.logstream.generated.api.KafkaApi;
import com.logstream.generated.model.KafkaBatchPublishResponse;
import com.logstream.generated.model.KafkaPublishResponse;
import com.logstream.generated.model.KafkaStatusResponse;
import com.logstream.service.KafkaProducerService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class KafkaController implements KafkaApi {

    private static final List<String> TOPICS = List.of("central-log-events", "alert-messages");

    /**
     * KafkaProducerService only exists when spring.kafka.toggle.enabled=true, so it
     * is resolved lazily; a missing bean maps to the documented 503.
     */
    private final ObjectProvider<KafkaProducerService> kafkaProducerService;

    public KafkaController(ObjectProvider<KafkaProducerService> kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public ResponseEntity<KafkaStatusResponse> getKafkaStatus() {
        boolean enabled = kafkaProducerService.getIfAvailable() != null;
        return ResponseEntity.ok(new KafkaStatusResponse(enabled, TOPICS));
    }

    @Override
    public ResponseEntity<KafkaBatchPublishResponse> ingestLogEventBatchViaKafka(String xIngestionToken, List<JsonNode> requestBody) {
        //at present no batch kafka
        return KafkaApi.super.ingestLogEventBatchViaKafka(xIngestionToken, requestBody);
    }

    @Override
    public ResponseEntity<KafkaPublishResponse> ingestLogEventViaKafka(String xIngestionToken, JsonNode requestBody) {
        KafkaProducerService producer = kafkaProducerService.getIfAvailable();
        if (producer == null) {
            throw new KafkaUnavailableException("Kafka integration is disabled.");
        }

        UUID messageId = producer.ingestLogEvent(requestBody, trimmed(xIngestionToken));
        return ResponseEntity.accepted()
                .body(new KafkaPublishResponse(KafkaProducerService.CENTRAL_LOG_TOPIC, messageId, OffsetDateTime.now()));
    }

    private String trimmed(String token) {
        return token == null ? null : token.trim();
    }
}
