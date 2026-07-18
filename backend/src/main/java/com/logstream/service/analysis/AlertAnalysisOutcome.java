package com.logstream.service.analysis;

import com.logstream.domain.model.OpenAIChatResult;

public record AlertAnalysisOutcome(
        String analysis,
        String analysisJson,
        boolean cached,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {

    public static AlertAnalysisOutcome cached(String analysis) {
        return new AlertAnalysisOutcome(analysis, null, true, null, null, null);
    }

    public static AlertAnalysisOutcome fromChat(OpenAIChatResult chatResult) {
        String rawJson = chatResult.text();
        String formatted = AlertAnalysisFormatter.formatModelResponse(rawJson);
        return new AlertAnalysisOutcome(
                formatted,
                rawJson,
                false,
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                chatResult.totalTokens());
    }
}
