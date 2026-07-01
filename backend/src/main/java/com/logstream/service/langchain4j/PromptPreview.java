package com.logstream.service.langchain4j;

public record PromptPreview(
        String prompt,
        int promptCharCount,
        int estimatedPromptTokens) {
}
