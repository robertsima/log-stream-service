package com.logstream.domain.model;

public record PromptPreview(
        String prompt,
        int promptCharCount,
        int estimatedPromptTokens) {
}
