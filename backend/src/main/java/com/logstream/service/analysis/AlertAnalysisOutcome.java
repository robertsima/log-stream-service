package com.logstream.service.analysis;

import com.logstream.domain.model.OpenAIChatResult;

public record AlertAnalysisOutcome(
        String appId,
        String analysis,
        String analysisJson,
        boolean cached,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {

    public static AlertAnalysisOutcome cached(String analysis) {
        return new AlertAnalysisOutcome(null, analysis, null, true, null, null, null);
    }

    public static AlertAnalysisOutcome fromChat(OpenAIChatResult chatResult, String appId) {
        String rawJson = chatResult.text();
        String formatted = AlertAnalysisFormatter.formatModelResponse(rawJson);
        return new AlertAnalysisOutcome(
                appId,
                formatted,
                rawJson,
                false,
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                chatResult.totalTokens());
    }
}
