package com.logstream.service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertBucket;

public interface AlertSenderService {
    public void sendTest(AlertDestination destination);

    public void sendAggregatedAlert(AlertDestination destination, AlertBucket bucket);
    
}
