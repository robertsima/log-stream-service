package com.logstream.service.analysis;

import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.OpenAIChatResult;

public record AlertAnalysisOutcome(
        String appId,
        String analysis,
        String analysisJson,
        boolean cached,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int occurrenceCount,
        String appName,
        String signatureLabel,
        long windowSeconds) {

    public static AlertAnalysisOutcome cached(String analysis) {
        return new AlertAnalysisOutcome(null, analysis, null, true, null, null, null, 1, null, null, 0);
    }

    /**
     * occurrenceCount/appName/signatureLabel/windowSeconds ride along purely for the
     * webhook senders' "N similar errors" header (see AlertGroupSummary); they don't
     * affect the OpenAI call itself.
     */
    public static AlertAnalysisOutcome fromChat(OpenAIChatResult chatResult, AlertTrigger alert) {
        String rawJson = chatResult.text();
        String formatted = AlertAnalysisFormatter.formatModelResponse(rawJson);
        String appId = alert.appId() == null ? null : alert.appId().toString();
        String appName = alert.triggeringEvent() == null ? null : alert.triggeringEvent().appName();
        // Ceil ms→s so sub-second bursts are not reported as 0; floor at 1s for headers.
        long elapsedMs = Math.max(0L, alert.windowEndMs() - alert.windowStartMs());
        long windowSeconds = Math.max(1L, (elapsedMs + 999L) / 1000L);
        return new AlertAnalysisOutcome(
                appId,
                formatted,
                rawJson,
                false,
                chatResult.promptTokens(),
                chatResult.completionTokens(),
                chatResult.totalTokens(),
                Math.max(1, alert.occurrenceCount()),
                appName,
                alert.signatureLabel(),
                windowSeconds);
    }
}
