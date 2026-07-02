package com.logstream.service.analysis;

import com.logstream.service.alerting.AlertBucket;

public interface AlertAnalysisService {
    AlertAnalysisOutcome analyzeAlertBucket(AlertBucket alertBucket);

    PromptPreview previewPrompt(AlertBucket alertBucket);

    void setSystemPrompt(String systemPrompt);

    void setUserPrompt(String userPrompt);
}
