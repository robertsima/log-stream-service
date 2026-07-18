package com.logstream.domain.model;

public record OpenAIChatResult(
        String text,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
