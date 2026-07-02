package com.logstream.service.cron;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.domain.repository.AlertDestinationRepository;
import com.logstream.service.AlertAggregationService;
import com.logstream.service.AlertSenderService;

@Service
public class AlertFlushScheduler {

    private final AlertAggregationService aggregationService;
    private final AlertDestinationRepository destinationRepository;
    private final AlertSenderService alertSenderService;
    private final boolean enabled;

    public AlertFlushScheduler(
            AlertAggregationService aggregationService,
            AlertDestinationRepository destinationRepository,
            AlertSenderService alertSenderService,
            @Value("${alerts.enabled:true}") boolean enabled
    ) {
        this.aggregationService = aggregationService;
        this.destinationRepository = destinationRepository;
        this.alertSenderService = alertSenderService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${alerts.aggregation-window-ms:60000}")
    public void flush() {
        if (!enabled) {
            return;
        }

        Map<String, AlertBucket> buckets = aggregationService.drainBuckets();

        for (AlertBucket bucket : buckets.values()) {
            List<AlertDestination> destinations =
                    destinationRepository.findByAppIdAndEnabledTrueAndDeletedAtIsNull(bucket.getAppId());

            for (AlertDestination destination : destinations) {
                alertSenderService.sendAggregatedAlert(destination, bucket);
            }
        }
    }
}
