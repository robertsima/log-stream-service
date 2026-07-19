package com.logstream.service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.repository.AlertDestinationRepository;
import com.logstream.generated.model.AlertAnalysisResponse;
import com.logstream.generated.model.TokenUsage;
import com.logstream.service.analysis.AlertAnalysisOutcome;
import com.logstream.service.analysis.AlertAnalysisService;
import com.logstream.service.analysis.AlertAnalysisServiceImpl;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class KafkaConsumerService {

    //listeners for both topics, mainly worried about central log listener for now
    //listens to the central log topic, deserializes it as a string and passes into listener
//    @KafkaListener(id = "central-log-listener", topics = "central-log-events")
//    public void centralListener(String in) {
//        System.out.println(in);
//    }
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerService.class);

    private static final int MAX_CONTEXT_LOGS = 20;

    private final KafkaTemplate<String, AlertAnalysisOutcome> kafkaTemplate;

    private final Map<UUID, Deque<AlertTrigger>> recentLogs = new ConcurrentHashMap<>();

    private final AlertAnalysisService alertAnalysisService;

    private final AlertSenderService alertSenderService;

    private final AlertDestinationService alertDestinationService;

    private final AlertDestinationRepository alertDestinationRepository;


    public KafkaConsumerService(KafkaTemplate<String, AlertAnalysisOutcome> kafkaTemplate, AlertAnalysisService alertAnalysisService,  AlertSenderService alertSenderService, AlertDestinationService alertDestinationService, AlertDestinationRepository alertDestinationRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.alertAnalysisService = alertAnalysisService;
        this.alertSenderService = alertSenderService;
        this.alertDestinationService = alertDestinationService;
        this.alertDestinationRepository = alertDestinationRepository;
    }

    //listener for alert messages sent from kafka stream, used to send an alert to model for analysis
    @KafkaListener(
            id = "alert-message-listener",
            topics = "alert-messages"
    )
    public void alertMessageListener(AlertTrigger alert) {
        System.out.println("Sending alert analysis request: " + alert.triggeringEvent().appName());
        kafkaTemplate.send("analyzed-events", alert.appId().toString(), alertAnalysisService.analyzeAlertTrigger(alert));
    }

    @KafkaListener(
            id = "analyzed-events-listener",
            topics = "analyzed-events"
    )
    public void analyzedEventsListener(AlertAnalysisOutcome outcome) {
        // deterministic bad record: retrying would never succeed, so warn and drop
        if (outcome.appId() == null || outcome.appId().isBlank()) {
            log.warn("Dropping analyzed event with missing appId; cannot resolve alert destinations");
            return;
        }

        AlertAnalysisResponse analysis = new AlertAnalysisResponse()
                .analysis(outcome.analysis())
                .analysisJson(outcome.analysisJson())
                .cached(outcome.cached());

        if (!outcome.cached() && outcome.totalTokens() != null) {
            analysis.tokenUsage(new TokenUsage()
                    .promptTokens(outcome.promptTokens())
                    .completionTokens(outcome.completionTokens())
                    .totalTokens(outcome.totalTokens()));
        }

        // repository, not the service: the response DTO deliberately omits webhook URLs,
        // and findByApp's requireOwner() would fail on a listener thread with no auth context
        List<AlertDestination> destinations = alertDestinationRepository
                .findByAppIdAndEnabledTrueAndDeletedAtIsNull(UUID.fromString(outcome.appId()));
        for (AlertDestination destination : destinations) {
            try {
                alertSenderService.sendAnalyzedAlert(destination, analysis);
            } catch (Exception e) {
                // one down webhook must not fail the whole record into the retry loop;
                // RestClient exception messages embed the full webhook URL, so log only the class
                log.warn("Failed to deliver analyzed alert to destination {} ({}): {}",
                        destination.getId(), destination.getDestinationType(), e.getClass().getSimpleName());
            }
        }
    }

}


