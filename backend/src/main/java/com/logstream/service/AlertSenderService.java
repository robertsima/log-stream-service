package com.logstream.service;

import com.logstream.domain.entity.AlertDestination;
import com.logstream.domain.model.AlertGroupSummary;
import com.logstream.generated.model.AlertAnalysisResponse;

public interface AlertSenderService {
    void sendAnalyzedAlert(AlertDestination destination, AlertAnalysisResponse analysis, AlertGroupSummary summary);
}
