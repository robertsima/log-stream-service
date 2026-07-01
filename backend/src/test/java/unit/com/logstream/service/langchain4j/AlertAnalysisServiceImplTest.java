package unit.com.logstream.service.langchain4j;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.logstream.domain.model.AlertBucket;
import com.logstream.generated.model.LogEventRequest;
import com.logstream.generated.model.LogLevel;
import com.logstream.service.langchain4j.AlertAnalysisServiceImpl;
import com.logstream.service.langchain4j.OpenAIChatResult;
import com.logstream.service.langchain4j.OpenAIModel;

class AlertAnalysisServiceImplTest {

    @Test
    void analyzeAlertBucket_shouldIncludeBoundedContextAroundErrors() {
        RecordingOpenAIModel openAIModel = new RecordingOpenAIModel();
        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "bucket-1");
        bucket.add(event("before-0", LogLevel.DEBUG));
        bucket.add(event("before-1", LogLevel.INFO));
        bucket.add(event("before-2", LogLevel.INFO));
        bucket.add(event("before-3", LogLevel.DEBUG));
        bucket.add(event("before-4", LogLevel.INFO));
        bucket.add(event("before-5", LogLevel.INFO));
        bucket.add(event("before-6", LogLevel.DEBUG));
        bucket.add(event("before-7", LogLevel.INFO));
        bucket.add(event("before-8", LogLevel.INFO));
        bucket.add(event("before-9", LogLevel.DEBUG));
        bucket.add(event("before-10", LogLevel.INFO));
        bucket.add(event("before-11", LogLevel.INFO));
        bucket.add(event("boom", LogLevel.ERROR));
        bucket.add(event("after-1", LogLevel.INFO));
        bucket.add(event("after-2", LogLevel.INFO));
        bucket.add(event("after-3", LogLevel.INFO));
        bucket.add(event("after-4", LogLevel.INFO));
        bucket.add(event("after-5", LogLevel.INFO));
        bucket.add(event("after-6", LogLevel.INFO));

        service.analyzeAlertBucket(bucket);

        assertThat(openAIModel.lastUserPrompt())
                .contains("before-10")
                .contains("before-11")
                .contains("ERR Service: boom")
                .contains("after-1")
                .doesNotContain("before-0")
                .doesNotContain("before-7")
                .doesNotContain("after-2")
                .doesNotContain("DEBUG");
    }

    @Test
    void analyzeAlertBucket_shouldCondenseLongErrorMessages() {
        RecordingOpenAIModel openAIModel = new RecordingOpenAIModel();
        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "bucket-2");
        bucket.add(event(
                "first line\n"
                        + "com.example.BoomException: second line\n"
                        + "\tat com.example.alpha.AlphaService.run(AlphaService.java:10)\n"
                        + "\tat com.example.beta.BetaService.call(BetaService.java:20)\n"
                        + "\tat org.springframework.Foo.bar(Foo.java:99)",
                LogLevel.ERROR));

        service.analyzeAlertBucket(bucket);

        assertThat(openAIModel.lastUserPrompt())
                .contains("first line")
                .contains("BoomException")
                .contains("AlphaService.run:10")
                .contains("BetaService.call:20")
                .doesNotContain("org.springframework");
    }

    @Test
    void analyzeAlertBucket_shouldDedupeRepeatedErrors() {
        RecordingOpenAIModel openAIModel = new RecordingOpenAIModel();
        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(openAIModel);

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "bucket-3");
        bucket.add(event("same failure", LogLevel.ERROR));
        bucket.add(event("same failure", LogLevel.ERROR));
        bucket.add(event("same failure", LogLevel.ERROR));

        service.analyzeAlertBucket(bucket);

        assertThat(openAIModel.lastUserPrompt())
                .contains("errors=3")
                .contains("ERR Service: same failure")
                .contains("ERR (+2 similar)")
                .doesNotContain("ERR Service: same failure\nERR Service: same failure");
    }

    @Test
    void previewPrompt_shouldRenderThePromptWithoutCallingTheModel() {
        AlertAnalysisServiceImpl service = new AlertAnalysisServiceImpl(new RecordingOpenAIModel());

        AlertBucket bucket = new AlertBucket(UUID.randomUUID(), "bucket-preview");
        bucket.add(event("boom", LogLevel.ERROR));

        String preview = service.previewPrompt(bucket).prompt();

        assertThat(preview)
                .contains("--- system ---")
                .contains("--- user ---")
                .contains("ERR Service: boom");
    }

    private LogEventRequest event(String message, LogLevel level) {
        return new LogEventRequest()
                .id(UUID.randomUUID().toString())
                .level(level)
                .message(message)
                .logger("com.example.Service")
                .occurredAt(OffsetDateTime.now());
    }

    private static class RecordingOpenAIModel extends OpenAIModel {
        private String lastSystemPrompt;
        private String lastUserPrompt;

        private RecordingOpenAIModel() {
            super("test-key");
        }

        @Override
        public OpenAIChatResult chatForAnalysis(String systemPrompt, String userPrompt) {
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            return new OpenAIChatResult(
                    "{\"confidence\":\"high\",\"rootCause\":\"boom\",\"affectedComponents\":[\"Service\"],\"urgency\":\"high\",\"remediation\":[\"fix it\"]}",
                    10,
                    5,
                    15);
        }

        @Override
        public OpenAIChatResult chat(String systemPrompt, String userPrompt) {
            return chatForAnalysis(systemPrompt, userPrompt);
        }

        private String lastUserPrompt() {
            return lastUserPrompt;
        }
    }
}
