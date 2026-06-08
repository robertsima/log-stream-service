package com.logstream.service;

import com.logstream.entity.AlertDestination;
import com.logstream.model.AlertBucket;

public interface AlertSenderService {
    public void sendTest(AlertDestination destination);

    public void sendAggregatedAlert(AlertDestination destination, AlertBucket bucket);
    
}
