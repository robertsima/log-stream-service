package com.logstream.service.analysis;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.PromptPreview;

public interface AlertAnalysisService {
    AlertAnalysisOutcome analyzeAlertTrigger(AlertTrigger alert);

    PromptPreview previewPrompt(AlertTrigger alert);

    void setSystemPrompt(String systemPrompt);

    void setUserPrompt(String userPrompt);
}
