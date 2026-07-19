package com.logstream.service;

import tools.jackson.databind.JsonNode;
import com.logstream.controller.dto.AppTokenDTO;
import com.logstream.domain.model.LogEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.kafka.toggle.enabled", havingValue = "true")
public class KafkaProducerService {

    public static final String CENTRAL_LOG_TOPIC = "central-log-events";

    private final AppTokenService appTokenService;
    private final KafkaTemplate<String, LogEvent> kafkaTemplate;

    public KafkaProducerService(AppTokenService appTokenService, KafkaTemplate<String, LogEvent> kafkaTemplate) {
        this.appTokenService = appTokenService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Validates the ingestion token synchronously, then queues the raw event on the
     * central log topic. Returns the generated message key used for tracing.
     */
    public UUID ingestLogEvent(JsonNode json, String rawToken) {
        AppTokenDTO appToken = appTokenService.validateAndRefreshToken(rawToken); // auth stays
        UUID messageId = UUID.randomUUID();

        LogEvent record = new LogEvent(appToken.getAppId(), appToken.getAppName(), Instant.now(), json);
        kafkaTemplate.send(CENTRAL_LOG_TOPIC, messageId.toString(), record);
        return messageId;
    }

    //at present no batch kafka
}
