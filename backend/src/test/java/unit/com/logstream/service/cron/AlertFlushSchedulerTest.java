//package unit.com.logstream.service.cron;
//
//import java.time.OffsetDateTime;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.Mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.verifyNoInteractions;
//import static org.mockito.Mockito.when;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import com.logstream.domain.entity.AlertDestination;
//import com.logstream.domain.repository.AlertDestinationRepository;
//import com.logstream.generated.model.AlertDestinationType;
//import com.logstream.generated.model.LogEventRequest;
//import com.logstream.generated.model.LogLevel;
//import com.logstream.service.AlertAggregationService;
//import com.logstream.service.AlertSenderService;
//import com.logstream.service.alerting.AlertBucket;
//import com.logstream.service.analysis.AlertAnalysisOutcome;
//import com.logstream.service.analysis.AlertAnalysisService;
//import com.logstream.service.cron.AlertFlushScheduler;
//
//@ExtendWith(MockitoExtension.class)
//class AlertFlushSchedulerTest {
//
//    @Mock
//    private AlertAggregationService aggregationService;
//
//    @Mock
//    private AlertDestinationRepository destinationRepository;
//
//    @Mock
//    private AlertSenderService alertSenderService;
//
//    @Mock
//    private AlertAnalysisService alertAnalysisService;
//
//    private AlertFlushScheduler scheduler;
//    private UUID appId;
//    private AlertBucket bucket;
//    private AlertDestination slackDestination;
//
//    @BeforeEach
//    void setUp() {
//        scheduler = new AlertFlushScheduler(
//                aggregationService,
//                destinationRepository,
//                alertSenderService,
//                alertAnalysisService,
//                true);
//        appId = UUID.randomUUID();
//        bucket = bucket(appId);
//        slackDestination = destination(AlertDestinationType.SLACK);
//    }
//
//    @Test
//    void flush_shouldAnalyzeBucketAndSendAnalyzedAlertByDefault() {
//        when(aggregationService.drainBuckets()).thenReturn(Map.of("bucket", bucket));
//        when(destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
//                .thenReturn(List.of(slackDestination));
//        when(alertAnalysisService.analyzeAlertTrigger(bucket))
//                .thenReturn(new AlertAnalysisOutcome("analysis text", "{}", false, 10, 5, 15));
//
//        scheduler.flush();
//
//        verify(alertAnalysisService).analyzeAlertTrigger(bucket);
//        verify(alertSenderService).sendAnalyzedAlert(slackDestination, bucket, "analysis text");
//        verify(alertSenderService, never()).sendAggregatedAlert(slackDestination, bucket);
//    }
//
//    @Test
//    void flush_shouldAnalyzeOnceAndReuseAnalysisForAllDestinations() {
//        AlertDestination discordDestination = destination(AlertDestinationType.DISCORD);
//        when(aggregationService.drainBuckets()).thenReturn(Map.of("bucket", bucket));
//        when(destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
//                .thenReturn(List.of(slackDestination, discordDestination));
//        when(alertAnalysisService.analyzeAlertTrigger(bucket))
//                .thenReturn(new AlertAnalysisOutcome("shared analysis", "{}", false, 10, 5, 15));
//
//        scheduler.flush();
//
//        verify(alertAnalysisService).analyzeAlertTrigger(bucket);
//        verify(alertSenderService).sendAnalyzedAlert(slackDestination, bucket, "shared analysis");
//        verify(alertSenderService).sendAnalyzedAlert(discordDestination, bucket, "shared analysis");
//    }
//
//    @Test
//    void flush_shouldSkipAnalysisWhenNoDestinationsAreEnabled() {
//        when(aggregationService.drainBuckets()).thenReturn(Map.of("bucket", bucket));
//        when(destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId)).thenReturn(List.of());
//
//        scheduler.flush();
//
//        verifyNoInteractions(alertAnalysisService, alertSenderService);
//    }
//
//    @Test
//    void flush_shouldFallBackToAggregatedAlertWhenAnalysisFails() {
//        when(aggregationService.drainBuckets()).thenReturn(Map.of("bucket", bucket));
//        when(destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
//                .thenReturn(List.of(slackDestination));
//        when(alertAnalysisService.analyzeAlertTrigger(bucket)).thenThrow(new RuntimeException("model unavailable"));
//
//        scheduler.flush();
//
//        verify(alertSenderService).sendAggregatedAlert(slackDestination, bucket);
//        verify(alertSenderService, never()).sendAnalyzedAlert(slackDestination, bucket, "model unavailable");
//    }
//
//    @Test
//    void flush_shouldFallBackToAggregatedAlertWhenAnalysisIsUnavailable() {
//        when(aggregationService.drainBuckets()).thenReturn(Map.of("bucket", bucket));
//        when(destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(appId))
//                .thenReturn(List.of(slackDestination));
//        when(alertAnalysisService.analyzeAlertTrigger(bucket))
//                .thenReturn(new AlertAnalysisOutcome(
//                        "OpenAI API key is not configured.",
//                        null,
//                        false,
//                        null,
//                        null,
//                        null));
//
//        scheduler.flush();
//
//        verify(alertSenderService).sendAggregatedAlert(slackDestination, bucket);
//        verify(alertSenderService, never())
//                .sendAnalyzedAlert(slackDestination, bucket, "OpenAI API key is not configured.");
//    }
//
//    private AlertBucket bucket(UUID appId) {
//        AlertBucket alertBucket = new AlertBucket(appId, "fingerprint");
//        alertBucket.setAppName("Checkout Service");
//        alertBucket.add(new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.ERROR)
//                .message("payment failed")
//                .logger("com.example.CheckoutService")
//                .occurredAt(OffsetDateTime.now()));
//        return alertBucket;
//    }
//
//    private AlertDestination destination(AlertDestinationType type) {
//        AlertDestination destination = new AlertDestination();
//        destination.setId(UUID.randomUUID());
//        destination.setAppId(appId);
//        destination.setDestinationType(type);
//        destination.setEnabled(true);
//        return destination;
//    }
//}
