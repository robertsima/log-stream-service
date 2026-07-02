package com.logstream.service.analysis;

public record PromptPreview(
        String prompt,
        int promptCharCount,
        int estimatedPromptTokens) {
}
