//package com.logstream.controller;
//
//import com.logstream.domain.model.AlertTrigger;
//import com.logstream.generated.model.*;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.RestController;
//
//import com.logstream.exception.ForbiddenException;
//import com.logstream.service.AppService;
//import com.logstream.service.analysis.AlertAnalysisService;
//import com.logstream.service.analysis.AlertAnalysisOutcome;
//import com.logstream.domain.model.PromptPreview;
//
//import java.util.stream.Collectors;
//
//@RestController
//public class AlertAnalysisController{
//
//    private final AlertAnalysisService alertAnalysisService;
//    private final AppService appService;
//    private final boolean promptPreviewEnabled;
//
//    public AlertAnalysisController(
//            AlertAnalysisService alertAnalysisService,
//            AppService appService,
//            @Value("${alerts.analysis.prompt-preview-enabled:false}") boolean promptPreviewEnabled
//    ) {
//        this.alertAnalysisService = alertAnalysisService;
//        this.appService = appService;
//        this.promptPreviewEnabled = promptPreviewEnabled;
//    }
//
//    public ResponseEntity<AlertAnalysisResponse> analyzeAlertTrigger(AlertBucketAnalysisRequest alertAnalysisRequest) {
//        appService.requireOwner(alertAnalysisRequest.getAppId());
//        AlertTrigger alert = toAlertTrigger(alertAnalysisRequest);
//        AlertAnalysisOutcome outcome = alertAnalysisService.analyzeAlertTrigger(alert);
//
//        AlertAnalysisResponse response = new AlertAnalysisResponse()
//                .analysis(outcome.analysis())
//                .analysisJson(outcome.analysisJson())
//                .cached(outcome.cached());
//
//        if (!outcome.cached() && outcome.totalTokens() != null) {
//            response.tokenUsage(new TokenUsage()
//                    .promptTokens(outcome.promptTokens())
//                    .completionTokens(outcome.completionTokens())
//                    .totalTokens(outcome.totalTokens()));
//        }
//
//        return ResponseEntity.ok(response);
//    }
//
//    public ResponseEntity<AlertAnalysisPreviewResponse> previewAlertPrompt(AlertBucketAnalysisRequest alertAnalysisRequest) {
//        if (!promptPreviewEnabled) {
//            throw new ForbiddenException("Alert analysis prompt preview is disabled.");
//        }
//
//        appService.requireOwner(alertAnalysisRequest.getAppId());
//        AlertTrigger alert = toAlertTrigger(alertAnalysisRequest);
//        PromptPreview preview = alertAnalysisService.previewPrompt(alert);
//
//        AlertAnalysisPreviewResponse response = new AlertAnalysisPreviewResponse()
//                .prompt(preview.prompt())
//                .promptCharCount(preview.promptCharCount())
//                .estimatedPromptTokens(preview.estimatedPromptTokens());
//
//        return ResponseEntity.ok(response);
//    }
//
////    private AlertTrigger toAlertTrigger(AlertBucketAnalysisRequest request) {
////        AlertTrigger alert = new AlertTrigger(request.getAppId(), request.getEvents().t);
////        if (request.getEvents() != null) {
////            alert.context().addAll(request.getEvents().stream()
////                    .map(this::copyLogEventRequest)
////                    .collect(Collectors.toList()));
////        }
////        return alert;
////    }
//
//    private LogEventRequest copyLogEventRequest(LogEventRequest source) {
//        LogEventRequest copy = new LogEventRequest();
//        copy.setId(source.getId());
//        copy.setLevel(source.getLevel());
//        copy.setLogger(source.getLogger());
//        copy.setMessage(source.getMessage());
//        copy.setOccurredAt(source.getOccurredAt());
//        copy.setTraceId(source.getTraceId());
//        copy.setSpanId(source.getSpanId());
//        copy.setMetadata(source.getMetadata());
//        return copy;
//    }
//}
