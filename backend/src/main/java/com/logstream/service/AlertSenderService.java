package com.logstream.service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertTrigger;
import com.logstream.generated.model.AlertAnalysisResponse;
import com.logstream.service.alerting.AlertBucket;

public interface AlertSenderService {
    void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis);
//    public void sendTest(AlertDestination destination);
//
//    public void sendAggregatedAlert(AlertDestination destination, AlertTrigger bucket);

//    public void sendAnalyzedAlert(AlertDestination destination, AlertTrigger bucket, String analysis);
//
//    void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis);
}
