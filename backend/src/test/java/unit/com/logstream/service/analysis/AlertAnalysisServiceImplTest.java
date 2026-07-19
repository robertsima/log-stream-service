//package unit.com.logstream.service.analysis;
//
//import java.time.OffsetDateTime;
//import java.util.Map;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import org.junit.jupiter.api.Test;
//
//import com.logstream.service.alerting.AlertTrigger;
//import com.logstream.generated.model.LogEventRequest;
//import com.logstream.generated.model.LogLevel;
//import com.logstream.service.analysis.AlertAnalysisServiceImpl;
//import com.logstream.domain.model.OpenAIChatResult;
//import com.logstream.config.OpenAIModelConfig;
//
//class AlertAnalysisServiceImplTest {
//
//    @Test
//    void analyzeAlertTrigger_shouldIncludeBoundedContextAroundErrors() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-1");
//        bucket.add(event("before-0", LogLevel.DEBUG));
//        bucket.add(event("before-1", LogLevel.INFO));
//        bucket.add(event("before-2", LogLevel.INFO));
//        bucket.add(event("before-3", LogLevel.DEBUG));
//        bucket.add(event("before-4", LogLevel.INFO));
//        bucket.add(event("before-5", LogLevel.INFO));
//        bucket.add(event("before-6", LogLevel.DEBUG));
//        bucket.add(event("before-7", LogLevel.INFO));
//        bucket.add(event("before-8", LogLevel.INFO));
//        bucket.add(event("before-9", LogLevel.DEBUG));
//        bucket.add(event("before-10", LogLevel.INFO));
//        bucket.add(event("before-11", LogLevel.INFO));
//        bucket.add(event("boom", LogLevel.ERROR));
//        bucket.add(event("after-1", LogLevel.INFO));
//        bucket.add(event("after-2", LogLevel.INFO));
//        bucket.add(event("after-3", LogLevel.INFO));
//        bucket.add(event("after-4", LogLevel.INFO));
//        bucket.add(event("after-5", LogLevel.INFO));
//        bucket.add(event("after-6", LogLevel.INFO));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("before-11")
//                .contains("before-10")
//                .contains("before-8")
//                .contains("before-7")
//                .contains("before-5")
//                .contains("ERR Service: boom")
//                .contains("after-1")
//                .contains("after-2")
//                .doesNotContain("before-9")
//                .doesNotContain("before-6")
//                .doesNotContain("before-4")
//                .doesNotContain("before-3")
//                .doesNotContain("before-2")
//                .doesNotContain("before-0")
//                .doesNotContain("after-3")
//                .doesNotContain("DEBUG");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldCondenseLongErrorMessages() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-2");
//        bucket.add(event(
//                "first line\n"
//                        + "com.example.BoomException: second line\n"
//                        + "\tat com.example.alpha.AlphaService.run(AlphaService.java:10)\n"
//                        + "\tat com.example.beta.BetaService.call(BetaService.java:20)\n"
//                        + "\tat org.springframework.Foo.bar(Foo.java:99)",
//                LogLevel.ERROR));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("first line")
//                .contains("BoomException")
//                .contains("AlphaService.run:10")
//                .contains("BetaService.call:20")
//                .doesNotContain("org.springframework");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldKeepContextMessagesUpToTheNewCharLimit() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        String longContext = "c".repeat(250);
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-context");
//        bucket.add(event(longContext, LogLevel.INFO));
//        bucket.add(event("boom", LogLevel.ERROR));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt()).contains(longContext);
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldKeepErrorMessagesUpToTheNewCharLimit() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        String longSummary = "e".repeat(650);
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-error");
//        bucket.add(event(longSummary, LogLevel.ERROR));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt()).contains(longSummary);
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldDedupeRepeatedErrors() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-3");
//        bucket.add(event("same failure", LogLevel.ERROR));
//        bucket.add(event("same failure", LogLevel.ERROR));
//        bucket.add(event("same failure", LogLevel.ERROR));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("errors=3")
//                .contains("ERR Service: same failure")
//                .contains("ERR (+2 similar)")
//                .doesNotContain("ERR Service: same failure\nERR Service: same failure");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldFallBackToMetadataStackWhenMessageHasNoFrames() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-4");
//        LogEventRequest jsError = new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.ERROR)
//                .message("Cannot read properties of undefined (reading 'foo')")
//                .logger("react-client")
//                .occurredAt(OffsetDateTime.now())
//                .metadata(Map.of(
//                        "errorName", "TypeError",
//                        "stack", "TypeError: Cannot read properties of undefined (reading 'foo')\n"
//                                + "    at Object.onClick (https://app.example.com/static/js/main.js:2:145823)\n"
//                                + "    at HTMLUnknownElement.callCallback (https://app.example.com/static/js/main.js:2:37518)"
//                ));
//        bucket.add(jsError);
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("Cannot read properties of undefined (reading 'foo')")
//                .contains("TypeError")
//                .contains("Object.onClick (main.js:2)")
//                .contains("HTMLUnknownElement.callCallback (main.js:2)");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldIncludeSpanWhenErrorsOccurOverTime() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        OffsetDateTime start = OffsetDateTime.now().minusMinutes(5);
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-5");
//        bucket.add(new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.ERROR)
//                .message("boom")
//                .logger("com.example.Service")
//                .occurredAt(start));
//        bucket.add(new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.ERROR)
//                .message("boom")
//                .logger("com.example.Service")
//                .occurredAt(start.plusMinutes(3)));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt()).contains("span=3m");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldIncludeCompactBucketAndMetadataHints() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-context-rich");
//        bucket.setAppName("Checkout API");
//        bucket.add(new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.WARN)
//                .message("Payment provider latency high")
//                .logger("com.example.checkout.PaymentClient")
//                .traceId("trace-rich")
//                .spanId("span-warn")
//                .occurredAt(OffsetDateTime.parse("2026-07-02T12:00:00Z"))
//                .metadata(Map.of(
//                        "endpoint", "/api/v1/checkout",
//                        "orderId", "ord_123",
//                        "region", "us-east-1")));
//        bucket.add(new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(LogLevel.ERROR)
//                .message("Payment authorization failed")
//                .logger("com.example.checkout.PaymentClient")
//                .traceId("trace-rich")
//                .spanId("span-error")
//                .occurredAt(OffsetDateTime.parse("2026-07-02T12:00:01Z"))
//                .metadata(Map.of(
//                        "endpoint", "/api/v1/checkout",
//                        "statusCode", 502,
//                        "orderId", "ord_123",
//                        "host", "checkout-7f8d")));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("bucket app=Checkout API fingerprint=bucket-context-rich")
//                .contains("totalEvents=2 selectedEvents=2")
//                .contains("warnings=1 trace=trace-rich")
//                .contains("CTX PaymentClient: Payment provider latency high")
//                .contains("endpoint=/api/v1/checkout")
//                .contains("orderId=ord_123")
//                .contains("statusCode=502")
//                .contains("span=span-error");
//    }
//
//    @Test
//    void analyzeAlertTrigger_shouldIncludeUsefulDebugContextWithoutIncludingAllDebugNoise() {
//        RecordingOpenAIModelConfig openAIModel = new RecordingOpenAIModelConfig();
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-debug");
//        bucket.add(event("irrelevant debug detail", LogLevel.DEBUG));
//        bucket.add(event("request endpoint=/checkout orderId=ord_999", LogLevel.DEBUG));
//        bucket.add(event("boom", LogLevel.ERROR));
//
//        service.analyzeAlertTrigger(bucket);
//
//        assertThat(openAIModel.lastUserPrompt())
//                .contains("request endpoint=/checkout orderId=ord_999")
//                .doesNotContain("irrelevant debug detail");
//    }
//
//    @Test
//    void previewPrompt_shouldRenderThePromptWithoutCallingTheModel() {
//        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(new RecordingOpenAIModelConfig());
//
//        AlertTrigger bucket = new AlertTrigger(UUID.randomUUID(), "bucket-preview");
//        bucket.add(event("boom", LogLevel.ERROR));
//
//        String preview = service.previewPrompt(bucket).prompt();
//
//        assertThat(preview)
//                .contains("--- system ---")
//                .contains("--- user ---")
//                .contains("ERR Service: boom");
//    }
//
//    private LogEventRequest event(String message, LogLevel level) {
//        return new LogEventRequest()
//                .id(UUID.randomUUID().toString())
//                .level(level)
//                .message(message)
//                .logger("com.example.Service")
//                .occurredAt(OffsetDateTime.now());
//    }
//
//    private static class RecordingOpenAIModelConfig extends OpenAIModelConfig {
//        private String lastSystemPrompt;
//        private String lastUserPrompt;
//
//        private RecordingOpenAIModelConfig() {
//            super("test-key");
//        }
//
//        @Override
//        public OpenAIChatResult chatForAnalysis(String systemPrompt, String userPrompt) {
//            this.lastSystemPrompt = systemPrompt;
//            this.lastUserPrompt = userPrompt;
//            return new OpenAIChatResult(
//                    "{\"confidence\":\"high\",\"rootCause\":\"boom\",\"affectedComponents\":[\"Service\"],\"urgency\":\"high\",\"remediation\":[\"fix it\"]}",
//                    10,
//                    5,
//                    15);
//        }
//
//        private String lastUserPrompt() {
//            return lastUserPrompt;
//        }
//    }
//}
