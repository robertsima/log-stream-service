package com.logstream.service.langchain4j;

import com.logstream.domain.model.AlertBucket;

public interface AlertAnalysisService {
    AlertAnalysisOutcome analyzeAlertBucket(AlertBucket alertBucket);

    PromptPreview previewPrompt(AlertBucket alertBucket);

    void setSystemPrompt(String systemPrompt);

    void setUserPrompt(String userPrompt);
}
