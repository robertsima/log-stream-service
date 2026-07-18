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
import com.logstream.domain.model.OpenAIChatResult;
import com.logstream.domain.model.PromptPreview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.logstream.service.alerting.AlertBucket;
import com.logstream.service.alerting.AlertTimeWindow;
import com.logstream.service.alerting.BucketFingerprint;
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
    private final Map<String, CachedAnalysis> recentAnalyses = new ConcurrentHashMap<>();

    private String systemPrompt =
            "You are a senior SRE and your boss is watching your screen. Return JSON only with this schema:\n"
            + "{\n"
            + "  \"confidence\": \"Low|Medium|High\",\n"
            + "  \"rootCause\": \"1-2 sentences citing exception, logger, and failing operation\",\n"
            + "  \"affectedComponents\": [\"service, endpoint, or integration names\"],\n"
            + "  \"urgency\": \"Low|Medium|High\",\n"
            + "  \"remediation\": [\"specific technical steps: code paths, SQL, config keys, CLI, or rollback\"]\n"
            + "}\n"
            + "confidence = how certain the root cause is from the logs. Use 2-4 remediation steps. "
            + "Use bucket headers, trace/span ids, timestamps, logger names, and metadata hints as evidence. "
            + "No markdown, preamble, or extra keys. No generic advice.";
    private String userPrompt = "";

    public AlertAnalysisServiceImpl(OpenAIModelConfig openAIModelConfig) {
        this.openAIModelConfig = openAIModelConfig;
    }

    @Override
    public AlertAnalysisOutcome analyzeAlertBucket(AlertBucket alertBucket) {
        String fingerprint = fingerprint(alertBucket);
        CachedAnalysis cached = recentAnalyses.get(fingerprint);
        if (cached != null && cached.isFresh()) {
            return AlertAnalysisOutcome.cached(cached.response());
        }

        AlertAnalysisOutcome outcome = callModel(alertBucket);
        recentAnalyses.put(fingerprint, new CachedAnalysis(outcome.analysis()));
        return outcome;
    }

    @Override
    public PromptPreview previewPrompt(AlertBucket alertBucket) {
        String userContent = buildUserPrompt(alertBucket);
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

    private AlertAnalysisOutcome callModel(AlertBucket alertBucket) {
        String userContent = buildUserPrompt(alertBucket);
        log.debug("Built AlertAnalysis user prompt for fingerprint {}:\n{}", fingerprint(alertBucket), userContent);
        OpenAIChatResult response = openAIModelConfig.chatForAnalysis(systemPrompt, userContent);
        log.debug("AlertAnalysis service received response:\n{}", response.text());
        return AlertAnalysisOutcome.fromChat(response);
    }

    private String buildUserPrompt(AlertBucket alertBucket) {
        List<LogEventRequest> events = alertBucket.getEvents();
        if (events.isEmpty()) {
            return userPrompt == null ? "" : userPrompt;
        }

        List<LogEventRequest> selected = selectPromptEvents(events);
        StringBuilder builder = new StringBuilder();

        if (userPrompt != null && !userPrompt.isBlank()) {
            builder.append(userPrompt.trim()).append('\n');
        }

        appendHeader(builder, alertBucket, events, selected);

        String lastErrorKey = null;
        int duplicateErrors = 0;

        for (LogEventRequest event : selected) {
            if (isErrorEvent(event)) {
                String errorKey = errorKey(event);
                if (errorKey.equals(lastErrorKey)) {
                    duplicateErrors++;
                    continue;
                }
                if (duplicateErrors > 0) {
                    builder.append("ERR (+").append(duplicateErrors).append(" similar)\n");
                    duplicateErrors = 0;
                }
                lastErrorKey = errorKey;
                builder.append("ERR ")
                        .append(shortLogger(event.getLogger()))
                        .append(": ")
                        .append(formatErrorMessage(event))
                        .append(formatEventHints(event))
                        .append('\n');
                continue;
            }

            builder.append("CTX ")
                    .append(shortLogger(event.getLogger()))
                    .append(": ")
                    .append(formatContextMessage(event.getMessage()))
                    .append(formatEventHints(event))
                    .append('\n');
        }

        if (duplicateErrors > 0) {
            builder.append("ERR (+").append(duplicateErrors).append(" similar)\n");
        }

        return builder.toString().stripTrailing();
    }

    private void appendHeader(StringBuilder builder,
            AlertBucket alertBucket,
            List<LogEventRequest> allEvents,
            List<LogEventRequest> selectedEvents) {
        String traceId = selectedEvents.stream()
                .map(LogEventRequest::getTraceId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);

        List<LogEventRequest> errorEvents = selectedEvents.stream().filter(this::isErrorEvent).toList();
        long warnings = selectedEvents.stream()
                .filter(event -> event.getLevel() != null && "WARN".equalsIgnoreCase(event.getLevel().getValue()))
                .count();
        String primaryLogger = errorEvents.stream()
                .map(LogEventRequest::getLogger)
                .filter(logger -> logger != null && !logger.isBlank())
                .map(this::shortLogger)
                .findFirst()
                .orElse("?");

        builder.append("bucket app=").append(nonBlank(alertBucket.getAppName(), String.valueOf(alertBucket.getAppId())))
                .append(" fingerprint=").append(nonBlank(alertBucket.getFingerprint(), "?"))
                .append(" totalEvents=").append(allEvents.size())
                .append(" selectedEvents=").append(selectedEvents.size())
                .append('\n');

        builder.append("logger=").append(primaryLogger)
                .append(" errors=").append(errorEvents.size())
                .append(" warnings=").append(warnings);
        if (traceId != null) {
            builder.append(" trace=").append(traceId);
        }

        String span = AlertTimeWindow.from(errorEvents).spanFormatted();
        if (span != null) {
            builder.append(" span=").append(span);
        }
        builder.append('\n');
    }

    private List<LogEventRequest> selectPromptEvents(List<LogEventRequest> events) {
        Set<Integer> include = new LinkedHashSet<>();

        for (int i = 0; i < events.size(); i++) {
            if (!isErrorEvent(events.get(i))) {
                continue;
            }

            include.add(i);
            includeContextBefore(events, i, include);
            includeContextAfter(events, i, include);
        }

        if (include.isEmpty()) {
            return new ArrayList<>(events);
        }

        List<LogEventRequest> selected = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (include.contains(i)) {
                selected.add(events.get(i));
            }
        }
        return selected;
    }

    private void includeContextBefore(List<LogEventRequest> events, int errorIndex, Set<Integer> include) {
        int added = 0;
        for (int i = errorIndex - 1; i >= 0 && added < MAX_CONTEXT_BEFORE; i--) {
            if (!isUsefulContext(events.get(i))) {
                continue;
            }
            include.add(i);
            added++;
        }
    }

    private void includeContextAfter(List<LogEventRequest> events, int errorIndex, Set<Integer> include) {
        int added = 0;
        for (int i = errorIndex + 1; i < events.size() && added < MAX_CONTEXT_AFTER; i++) {
            if (!isUsefulContext(events.get(i))) {
                continue;
            }
            include.add(i);
            added++;
        }
    }

    private boolean isUsefulContext(LogEventRequest event) {
        if (event.getLevel() == null) {
            return false;
        }
        String level = event.getLevel().getValue();
        if ("INFO".equalsIgnoreCase(level) || "WARN".equalsIgnoreCase(level)) {
            return true;
        }
        if (!"DEBUG".equalsIgnoreCase(level)) {
            return false;
        }
        String message = event.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("endpoint")
                || lower.contains("request")
                || lower.contains("order")
                || lower.contains("user")
                || lower.contains("payment")
                || lower.contains("retry")
                || lower.contains("timeout")
                || lower.contains("status");
    }

    private boolean isErrorEvent(LogEventRequest event) {
        return event.getLevel() != null && "ERROR".equalsIgnoreCase(event.getLevel().getValue());
    }

    private String errorKey(LogEventRequest event) {
        return shortLogger(event.getLogger()) + "|" + normalizeErrorSummary(event.getMessage());
    }

    private String normalizeErrorSummary(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.lines()
                .findFirst()
                .orElse("")
                .toLowerCase()
                .replaceAll("\\b\\d+\\b", "{n}")
                .replaceAll("\\s+", " ")
                .trim();
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

    private String formatContextMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String singleLine = message.replace("\r\n", "\n").replace('\r', '\n').lines()
                .findFirst()
                .orElse("")
                .trim();
        if (singleLine.length() > MAX_CONTEXT_CHARS) {
            return singleLine.substring(0, MAX_CONTEXT_CHARS).trim() + "…";
        }
        return singleLine;
    }

    private String formatEventHints(LogEventRequest event) {
        List<String> hints = new ArrayList<>();
        if (event.getOccurredAt() != null) {
            hints.add("at=" + event.getOccurredAt());
        }
        if (event.getSpanId() != null && !event.getSpanId().isBlank()) {
            hints.add("span=" + event.getSpanId());
        }
        hints.addAll(metadataHints(event.getMetadata()));
        if (hints.isEmpty()) {
            return "";
        }
        return " [" + String.join(" ", hints) + "]";
    }

    private List<String> metadataHints(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }

        List<String> hints = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (String key : PRIORITY_METADATA_KEYS) {
            addMetadataHint(metadata, key, hints, used);
        }
        for (String key : metadata.keySet()) {
            if (hints.size() >= MAX_METADATA_FIELDS) {
                break;
            }
            addMetadataHint(metadata, key, hints, used);
        }
        return hints;
    }

    private void addMetadataHint(Map<String, Object> metadata, String key, List<String> hints, Set<String> used) {
        if (hints.size() >= MAX_METADATA_FIELDS || used.contains(key) || !metadata.containsKey(key)) {
            return;
        }
        Object value = metadata.get(key);
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return;
        }
        String text = String.valueOf(value).replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            return;
        }
        if (text.length() > MAX_METADATA_VALUE_CHARS) {
            text = text.substring(0, MAX_METADATA_VALUE_CHARS).trim() + "…";
        }
        hints.add(key + "=" + text);
        used.add(key);
    }

    private String formatErrorMessage(LogEventRequest event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }

        String[] lines = message.replace("\r\n", "\n").replace('\r', '\n').split("\\n");
        String summary = lines[0].trim();
        String exception = "";
        List<String> frames = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (exception.isEmpty() && (line.contains("Exception") || line.contains("Error:"))) {
                exception = line;
                continue;
            }
            Matcher matcher = STACK_FRAME.matcher(lines[i]);
            if (matcher.find() && frames.size() < MAX_STACK_FRAMES) {
                frames.add(shortClass(matcher.group(1)) + "." + matcher.group(2) + ":" + matcher.group(4));
            }
        }

        if (frames.isEmpty()) {
            exception = fallbackExceptionAndFrames(event.getMetadata(), exception, frames);
        }

        StringBuilder condensed = new StringBuilder(summary);
        if (!exception.isEmpty()) {
            condensed.append(" | ").append(exception);
        }
        if (!frames.isEmpty()) {
            condensed.append(" | at ").append(String.join(" → ", frames));
        }

        String result = condensed.toString().replaceAll("\\s+", " ").trim();
        if (result.length() > MAX_ERROR_CHARS) {
            return result.substring(0, MAX_ERROR_CHARS).trim() + "…";
        }
        return result;
    }

    /**
     * Client SDKs (React/Angular) carry the JS stack trace in metadata.stack rather than
     * embedding it in message, since message is meant to stay a short human summary.
     */
    private String fallbackExceptionAndFrames(Map<String, Object> metadata, String exception, List<String> frames) {
        if (metadata == null) {
            return exception;
        }

        String result = exception;
        Object errorName = metadata.get("errorName");
        if (result.isEmpty() && errorName != null && !String.valueOf(errorName).isBlank()) {
            result = String.valueOf(errorName);
        }

        Object stack = metadata.get("stack");
        if (stack == null) {
            return result;
        }

        String[] stackLines = String.valueOf(stack).replace("\r\n", "\n").replace('\r', '\n').split("\\n");
        if (result.isEmpty() && stackLines.length > 0) {
            String first = stackLines[0].trim();
            if (!first.isEmpty() && !first.startsWith("at ")) {
                result = first;
            }
        }

        for (String line : stackLines) {
            if (frames.size() >= MAX_STACK_FRAMES) {
                break;
            }
            Matcher matcher = STACK_FRAME_GENERIC.matcher(line.trim());
            if (matcher.matches()) {
                frames.add(genericFrame(matcher));
            }
        }

        return result;
    }

    private String genericFrame(Matcher matcher) {
        String function = matcher.group(1);
        String file = shortFile(matcher.group(2));
        String line = matcher.group(3);
        return (function == null || function.isBlank())
                ? file + ":" + line
                : function + " (" + file + ":" + line + ")";
    }

    private String shortFile(String path) {
        if (path == null) {
            return "?";
        }
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 && lastSlash < path.length() - 1 ? path.substring(lastSlash + 1) : path;
    }

    private String shortClass(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private String fingerprint(AlertBucket alertBucket) {
        return BucketFingerprint.from(alertBucket).value();
    }

    private record CachedAnalysis(String response, long createdAtMillis) {
        private CachedAnalysis(String response) {
            this(response, System.currentTimeMillis());
        }

        private boolean isFresh() {
            return System.currentTimeMillis() - createdAtMillis < 15 * 60 * 1000L;
        }
    }
}
