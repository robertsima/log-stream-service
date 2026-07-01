package com.logstream.service.langchain4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.logstream.domain.model.AlertBucket;
import com.logstream.domain.model.BucketFingerprint;
import com.logstream.generated.model.LogEventRequest;

@Service
public class AlertAnalysisServiceImpl implements AlertAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AlertAnalysisServiceImpl.class);
    private static final int MAX_CONTEXT_BEFORE = 2;
    private static final int MAX_CONTEXT_AFTER = 1;
    private static final int MAX_CONTEXT_CHARS = 120;
    private static final int MAX_ERROR_CHARS = 280;
    private static final int MAX_STACK_FRAMES = 2;
    private static final Pattern STACK_FRAME = Pattern.compile("\\s*at\\s+([\\w.$]+)\\.([\\w$]+)\\(([\\w.$]+):(\\d+)\\)");

    private final OpenAIModel openAIModel;
    private final Map<String, CachedAnalysis> recentAnalyses = new ConcurrentHashMap<>();

    private String systemPrompt =
            "You are a senior SRE. Return JSON only with this schema:\n"
            + "{\n"
            + "  \"confidence\": \"low|medium|high\",\n"
            + "  \"rootCause\": \"1-2 sentences citing exception, logger, and failing operation\",\n"
            + "  \"affectedComponents\": [\"service, endpoint, or integration names\"],\n"
            + "  \"urgency\": \"low|medium|high\",\n"
            + "  \"remediation\": [\"specific technical steps: code paths, SQL, config keys, CLI, or rollback\"]\n"
            + "}\n"
            + "confidence = how certain the root cause is from the logs. Use 2-4 remediation steps. "
            + "No markdown, preamble, or extra keys. No generic advice.";
    private String userPrompt = "";

    public AlertAnalysisServiceImpl(OpenAIModel openAIModel) {
        this.openAIModel = openAIModel;
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
        String prompt = openAIModel.formatPreview(systemPrompt, userContent);
        int charCount = prompt.length();
        return new PromptPreview(prompt, charCount, OpenAIModel.estimateTokens(prompt));
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
        OpenAIChatResult response = openAIModel.chatForAnalysis(systemPrompt, userContent);
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

        appendHeader(builder, selected);

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
                        .append(formatErrorMessage(event.getMessage()))
                        .append('\n');
                continue;
            }

            builder.append("CTX ")
                    .append(shortLogger(event.getLogger()))
                    .append(": ")
                    .append(formatContextMessage(event.getMessage()))
                    .append('\n');
        }

        if (duplicateErrors > 0) {
            builder.append("ERR (+").append(duplicateErrors).append(" similar)\n");
        }

        return builder.toString().stripTrailing();
    }

    private void appendHeader(StringBuilder builder, List<LogEventRequest> events) {
        String traceId = events.stream()
                .map(LogEventRequest::getTraceId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);

        long errorCount = events.stream().filter(this::isErrorEvent).count();
        String primaryLogger = events.stream()
                .filter(this::isErrorEvent)
                .map(LogEventRequest::getLogger)
                .filter(logger -> logger != null && !logger.isBlank())
                .map(this::shortLogger)
                .findFirst()
                .orElse("?");

        builder.append("logger=").append(primaryLogger)
                .append(" errors=").append(errorCount);
        if (traceId != null) {
            builder.append(" trace=").append(traceId);
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
        return "INFO".equalsIgnoreCase(level) || "WARN".equalsIgnoreCase(level);
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

    private String formatErrorMessage(String message) {
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

    private String shortClass(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
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
