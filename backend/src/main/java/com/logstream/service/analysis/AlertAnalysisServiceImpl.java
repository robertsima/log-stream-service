package com.logstream.service.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.logstream.config.OpenAIModelConfig;
import com.logstream.domain.model.AlertTrigger;
import com.logstream.domain.model.LogEvent;
import com.logstream.domain.model.OpenAIChatResult;
import com.logstream.domain.model.PromptPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import com.logstream.generated.model.LogEventRequest;

@Service
public class AlertAnalysisServiceImpl implements AlertAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisServiceImpl.class);
    private static final int MAX_CONTEXT_BEFORE = 5;
    private static final int MAX_CONTEXT_AFTER = 2;
    private static final int MAX_CONTEXT_CHARS = 300;
    private static final int MAX_ERROR_CHARS = 700;
    private static final int MAX_METADATA_VALUE_CHARS = 120;
    private static final int MAX_METADATA_FIELDS = 6;
    private static final int MAX_STACK_FRAMES = 2;
    private static final List<String> PRIORITY_METADATA_KEYS = List.of(
            "endpoint", "path", "method", "status", "statusCode", "userId", "tenantId",
            "orderId", "requestId", "operation", "component", "service", "host", "region");
    private static final Pattern STACK_FRAME = Pattern.compile("\\s*at\\s+([\\w.$]+)\\.([\\w$]+)\\(([\\w.$]+):(\\d+)\\)");
    private static final Pattern STACK_FRAME_GENERIC =
            Pattern.compile("\\s*at\\s+(?:(.+?)\\s*\\()?([^()\\s]+?):(\\d+)(?::\\d+)?\\)?\\s*$");

    private final OpenAIModelConfig openAIModelConfig;
//    private final Map<String, CachedAnalysis> recentAnalyses = new ConcurrentHashMap<>();

    private String systemPrompt =
            "You are a senior SRE writing a calm, factual triage note. Return JSON only with this schema:\n"
            + "{\n"
            + "  \"confidence\": \"Low|Medium|High\",\n"
            + "  \"rootCause\": \"1-2 sentences citing exception, logger, and failing operation\",\n"
            + "  \"affectedComponents\": [\"service, endpoint, or integration names\"],\n"
            + "  \"urgency\": \"Low|Medium|High|Critical\",\n"
            + "  \"remediation\": [\"specific technical steps: code paths, SQL, config keys, CLI, or rollback\"]\n"
            + "}\n"
            + "confidence scale (base it ONLY on evidence present in the logs):\n"
            + "- High: the logs directly identify the cause - an exception type plus a stack frame or message that names the failing code path, null value, or bad input. If the stack trace pinpoints it, say High; do not hedge to Medium.\n"
            + "- Medium: a probable cause is indicated but a key link is missing or two explanations remain plausible.\n"
            + "- Low: symptoms only; the cause is not in the logs and you are inferring.\n"
            + "urgency scale (operational impact, not how alarming the words sound):\n"
            + "- Critical: outage in progress - a core service/flow is down for all users, or data loss/corruption is occurring. Page-now territory.\n"
            + "- High: a core function is failing repeatedly or for many users; service degraded but partially working. Same-day fix.\n"
            + "- Medium: recurring error on a secondary path or affecting few users; schedule a fix.\n"
            + "- Low: isolated, transient, or self-recovered error; no user-visible impact evident.\n"
            + "Urgency calibration: most single-error alerts are Low. A single error with surrounding INFO logs showing normal traffic continuing is Low unless the error itself proves broader impact. "
            + "Reserve High/Critical for evidence of ongoing widespread failure: repeated errors, multiple affected users, or no recovery visible in the logs. "
            + "If torn between two urgency levels, always pick the lower one. Overrating urgency is a worse failure than underrating it. "
            + "Use 2-4 remediation steps. "
            + "Use trace/span ids, timestamps, logger names, and metadata hints as evidence. "
            + "No markdown, preamble, or extra keys. No generic advice.";
    private String userPrompt = "";

    public AlertAnalysisServiceImpl(OpenAIModelConfig openAIModelConfig) {
        this.openAIModelConfig = openAIModelConfig;
    }

    @Override
    public AlertAnalysisOutcome analyzeAlertTrigger(AlertTrigger alert) {

        AlertAnalysisOutcome outcome = callModel(alert);
        return outcome;
    }

    @Override
    public PromptPreview previewPrompt(AlertTrigger alert) {
        String userContent = buildUserPrompt(alert);
        String prompt = openAIModelConfig.formatPreview(systemPrompt, userContent);
        int charCount = prompt.length();
        return new PromptPreview(prompt, charCount, OpenAIModelConfig.estimateTokens(prompt));
    }

    @Override
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    @Override
    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    private AlertAnalysisOutcome callModel(AlertTrigger alert) {
        String userContent = buildUserPrompt(alert);
        log.debug("Built AlertAnalysis user prompt for app {}:\n{}", alert.appId(), userContent);
        OpenAIChatResult response = openAIModelConfig.chatForAnalysis(systemPrompt, userContent);
        log.debug("AlertAnalysis service received response:\n{}", response.text());
        return AlertAnalysisOutcome.fromChat(response, alert);
    }

    private String buildUserPrompt(AlertTrigger alert) {
        List<LogEvent> events = new ArrayList<>();
        if (alert.context() != null) {
            events.addAll(alert.context());
        }
        // context already contains the triggering event; only fall back if it's absent
        if (events.isEmpty() && alert.triggeringEvent() != null) {
            events.add(alert.triggeringEvent());
        }
        if (events.isEmpty()) {
            return userPrompt == null ? "" : userPrompt;
        }

        StringBuilder builder = new StringBuilder();

        if (userPrompt != null && !userPrompt.isBlank()) {
            builder.append(userPrompt.trim()).append('\n');
        }

        LogEvent trigger = alert.triggeringEvent() != null ? alert.triggeringEvent() : events.get(0);
        long errors = events.stream().filter(this::isErrorEvent).count();
        builder.append("alert app=").append(nonBlank(trigger.appName(), String.valueOf(alert.appId())))
                .append(" events=").append(events.size())
                .append(" errors=").append(errors)
                .append('\n');

        if (alert.occurrenceCount() > 1) {
            builder.append("Note: this alert groups ").append(alert.occurrenceCount())
                    .append(" similar '").append(nonBlank(alert.signatureLabel(), "error")).append("' occurrences ")
                    .append("observed within the alerting window; weigh repetition into urgency.\n");
        }

        for (LogEvent event : events) {
            boolean error = isErrorEvent(event);
            JsonNode payload = event.payload();
            String level = payload == null ? "" : payload.path("level").asText("");
            builder.append(nonBlank(level.toUpperCase(), error ? "ERROR" : "CTX"))
                    .append(' ')
                    .append(shortLogger(payload == null ? null : payload.path("logger").asText(null)))
                    .append(": ")
                    .append(formatMessage(payload, error))
                    .append(" [at=").append(eventTimestamp(event)).append(']')
                    .append('\n');
        }

        return builder.toString().stripTrailing();
    }

    private boolean isErrorEvent(LogEvent event) {
        JsonNode payload = event.payload();
        return payload != null
                && payload.path("level").asText("").equalsIgnoreCase("ERROR");
    }

    private String formatMessage(JsonNode payload, boolean error) {
        String message = payload == null ? "" : payload.path("message").asText("");
        String collapsed = message.replaceAll("\\s+", " ").trim();
        int max = error ? MAX_ERROR_CHARS : MAX_CONTEXT_CHARS;
        return collapsed.length() > max ? collapsed.substring(0, max).trim() + "…" : collapsed;
    }

    private String eventTimestamp(LogEvent event) {
        JsonNode payload = event.payload();
        if (payload != null) {
            String at = payload.path("occurredAt").asText("");
            if (at.isBlank()) {
                at = payload.path("timestamp").asText("");
            }
            if (!at.isBlank()) {
                return at;
            }
        }
        return event.receivedAt() == null ? "?" : event.receivedAt().toString();
    }

    private String shortLogger(String logger) {
        if (logger == null || logger.isBlank()) {
            return "?";
        }
        int lastDot = logger.lastIndexOf('.');
        return lastDot >= 0 && lastDot < logger.length() - 1
                ? logger.substring(lastDot + 1)
                : logger;
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

}
