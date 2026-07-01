package com.logstream.service.langchain4j;

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
