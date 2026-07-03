package com.logstream.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.logstream.exception.ForbiddenException;
import com.logstream.generated.api.AlertAnalysisApi;
import com.logstream.generated.model.AlertAnalysisPreviewResponse;
import com.logstream.generated.model.AlertAnalysisResponse;
import com.logstream.generated.model.AlertBucketAnalysisRequest;
import com.logstream.generated.model.TokenUsage;
import com.logstream.service.analysis.AlertAnalysisService;
import com.logstream.service.analysis.AlertAnalysisOutcome;
import com.logstream.service.analysis.PromptPreview;
import com.logstream.service.alerting.AlertBucket;
import com.logstream.generated.model.LogEventRequest;

import java.util.stream.Collectors;

@RestController
public class AlertAnalysisController implements AlertAnalysisApi {

    private final AlertAnalysisService alertAnalysisService;
    private final boolean promptPreviewEnabled;

    public AlertAnalysisController(
            AlertAnalysisService alertAnalysisService,
            @Value("${alerts.analysis.prompt-preview-enabled:false}") boolean promptPreviewEnabled
    ) {
        this.alertAnalysisService = alertAnalysisService;
        this.promptPreviewEnabled = promptPreviewEnabled;
    }

    @Override
    public ResponseEntity<AlertAnalysisResponse> analyzeAlertBucket(AlertBucketAnalysisRequest alertBucketAnalysisRequest) {
        AlertBucket alertBucket = toAlertBucket(alertBucketAnalysisRequest);
        AlertAnalysisOutcome outcome = alertAnalysisService.analyzeAlertBucket(alertBucket);

        AlertAnalysisResponse response = new AlertAnalysisResponse()
                .analysis(outcome.analysis())
                .analysisJson(outcome.analysisJson())
                .cached(outcome.cached());

        if (!outcome.cached() && outcome.totalTokens() != null) {
            response.tokenUsage(new TokenUsage()
                    .promptTokens(outcome.promptTokens())
                    .completionTokens(outcome.completionTokens())
                    .totalTokens(outcome.totalTokens()));
        }

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AlertAnalysisPreviewResponse> previewAlertPrompt(AlertBucketAnalysisRequest alertBucketAnalysisRequest) {
        if (!promptPreviewEnabled) {
            throw new ForbiddenException("Alert analysis prompt preview is disabled.");
        }

        AlertBucket alertBucket = toAlertBucket(alertBucketAnalysisRequest);
        PromptPreview preview = alertAnalysisService.previewPrompt(alertBucket);

        AlertAnalysisPreviewResponse response = new AlertAnalysisPreviewResponse()
                .prompt(preview.prompt())
                .promptCharCount(preview.promptCharCount())
                .estimatedPromptTokens(preview.estimatedPromptTokens());

        return ResponseEntity.ok(response);
    }

    private AlertBucket toAlertBucket(AlertBucketAnalysisRequest request) {
        AlertBucket bucket = new AlertBucket(request.getAppId(), request.getFingerprint());
        if (request.getEvents() != null) {
            bucket.getEvents().addAll(request.getEvents().stream()
                    .map(this::copyLogEventRequest)
                    .collect(Collectors.toList()));
        }
        return bucket;
    }

    private LogEventRequest copyLogEventRequest(LogEventRequest source) {
        LogEventRequest copy = new LogEventRequest();
        copy.setId(source.getId());
        copy.setLevel(source.getLevel());
        copy.setLogger(source.getLogger());
        copy.setMessage(source.getMessage());
        copy.setOccurredAt(source.getOccurredAt());
        copy.setTraceId(source.getTraceId());
        copy.setSpanId(source.getSpanId());
        copy.setMetadata(source.getMetadata());
        return copy;
    }
}
