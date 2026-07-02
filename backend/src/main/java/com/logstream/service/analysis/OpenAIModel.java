package com.logstream.service.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;

@Component
public class OpenAIModel {
    private static final Logger log = LoggerFactory.getLogger(OpenAIModel.class);
    private static final int MAX_COMPLETION_TOKENS = 800;

    private final String apiKey;
    private final OpenAiChatModel analysisModel;

    public OpenAIModel() {
        this(System.getenv("OPENAI_API_KEY"));
    }

    public OpenAIModel(String apiKey) {
        this.apiKey = apiKey;
        this.analysisModel = OpenAiChatModel.builder()
                .apiKey(this.apiKey)
                .modelName("gpt-5-nano")
                .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                .temperature(1.0)
                .responseFormat("json_object")
                .reasoningEffort("minimal")
                .build();
    }

    public OpenAIChatResult chatForAnalysis(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("OpenAI API key is not configured.");
            return new OpenAIChatResult("OpenAI API key is not configured.", null, null, null);
        }

        log.debug(
                "OpenAI analysis request (system={} chars, user={} chars)",
                systemPrompt == null ? 0 : systemPrompt.length(),
                userPrompt == null ? 0 : userPrompt.length());
        log.debug("OpenAI system prompt:\n{}", systemPrompt);
        log.debug("OpenAI user prompt:\n{}", userPrompt);

        ChatResponse response = analysisModel.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt));
        return toChatResult(response);
    }

    private OpenAIChatResult toChatResult(ChatResponse response) {
        String text = response.aiMessage().text();
        if (text == null || text.isBlank()) {
            log.warn("OpenAI response had no content (finishReason={}); the completion token budget may be too "
                    + "low for this model's reasoning overhead.", response.finishReason());
            text = "Analysis unavailable: the model returned no content.";
        }
        TokenUsage usage = response.tokenUsage();

        Integer promptTokens = usage == null ? null : usage.inputTokenCount();
        Integer completionTokens = usage == null ? null : usage.outputTokenCount();
        Integer totalTokens = usage == null ? null : usage.totalTokenCount();

        log.info(
                "OpenAI token usage: prompt={} completion={} total={}",
                promptTokens,
                completionTokens,
                totalTokens);
        log.debug("OpenAI model chat response (length={}): {}", text == null ? 0 : text.length(), text);

        return new OpenAIChatResult(text, promptTokens, completionTokens, totalTokens);
    }

    public String formatPreview(String systemPrompt, String userPrompt) {
        return "--- system ---\n"
                + nullToEmpty(systemPrompt)
                + "\n\n--- user ---\n"
                + nullToEmpty(userPrompt);
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
