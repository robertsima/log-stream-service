package com.logstream.service.analysis;

public record OpenAIChatResult(
        String text,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
