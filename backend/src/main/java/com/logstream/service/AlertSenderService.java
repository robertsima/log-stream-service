package com.logstream.service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.service.alerting.AlertBucket;

public interface AlertSenderService {
    public void sendTest(AlertDestination destination);

    public void sendAggregatedAlert(AlertDestination destination, AlertBucket bucket);

    public void sendAnalyzedAlert(AlertDestination destination, AlertBucket bucket, String analysis);
    
}
