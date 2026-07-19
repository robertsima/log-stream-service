//package com.logstream.service.cron;
//
//import java.util.List;
//import java.util.Map;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import com.logstream.domain.entity.AlertDestination;
//import com.logstream.service.alerting.AlertBucket;
//import com.logstream.domain.repository.AlertDestinationRepository;
//import com.logstream.service.AlertAggregationService;
//import com.logstream.service.AlertSenderService;
//import com.logstream.service.analysis.AlertAnalysisOutcome;
//import com.logstream.service.analysis.AlertAnalysisService;
//
//@Service
//public class AlertFlushScheduler {
//
//    private static final Logger log = LoggerFactory.getLogger(AlertFlushScheduler.class);
//
//    private final AlertAggregationService aggregationService;
//    private final AlertDestinationRepository destinationRepository;
//    private final AlertSenderService alertSenderService;
//    private final AlertAnalysisService alertAnalysisService;
//    private final boolean enabled;
//
//    public AlertFlushScheduler(
//            AlertAggregationService aggregationService,
//            AlertDestinationRepository destinationRepository,
//            AlertSenderService alertSenderService,
//            AlertAnalysisService alertAnalysisService,
//            @Value("${alerts.enabled:true}") boolean enabled
//    ) {
//        this.aggregationService = aggregationService;
//        this.destinationRepository = destinationRepository;
//        this.alertSenderService = alertSenderService;
//        this.alertAnalysisService = alertAnalysisService;
//        this.enabled = enabled;
//    }
//
//    @Scheduled(fixedDelayString = "${alerts.aggregation-window-ms:60000}")
//    public void flush() {
//        if (!enabled) {
//            return;
//        }
//
//        Map<String, AlertBucket> buckets = aggregationService.drainBuckets();
//
//        for (AlertBucket bucket : buckets.values()) {
//            List<AlertDestination> destinations =
//                    destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(bucket.getAppId());
//
//            if (destinations.isEmpty()) {
//                continue;
//            }
//
//            String analysis = analyze(bucket);
//            for (AlertDestination destination : destinations) {
//                if (!hasUsableAnalysis(analysis)) {
//                    alertSenderService.sendAggregatedAlert(destination, bucket);
//                    continue;
//                }
//
//                alertSenderService.sendAnalyzedAlert(destination, bucket, analysis);
//            }
//        }
//    }
//
//    private String analyze(AlertBucket bucket) {
//        try {
////            AlertAnalysisOutcome outcome = alertAnalysisService.analyzeAlertTrigger(bucket);
//            return outcome == null ? null : outcome.analysis();
//        } catch (RuntimeException ex) {
//            log.warn(
//                    "Alert analysis failed for appId={} fingerprint={}; sending regular alert.",
//                    bucket.getAppId(),
//                    bucket.getFingerprint(),
//                    ex);
//            return null;
//        }
//    }
//
//    private boolean hasUsableAnalysis(String analysis) {
//        if (analysis == null || analysis.isBlank()) {
//            return false;
//        }
//
//        return !analysis.startsWith("OpenAI API key is not configured.")
//                && !analysis.startsWith("Analysis unavailable:");
//    }
//}
