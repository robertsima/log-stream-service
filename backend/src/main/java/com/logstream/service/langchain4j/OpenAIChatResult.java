package com.logstream.service.langchain4j;

public record OpenAIChatResult(
        String text,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
