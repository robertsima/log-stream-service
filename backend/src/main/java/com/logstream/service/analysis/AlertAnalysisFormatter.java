package com.logstream.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class AlertAnalysisFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AlertAnalysisFormatter() {
    }

    public static String formatModelResponse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return rawJson;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            StringBuilder builder = new StringBuilder();

            builder.append("Analysis");
            appendConfidenceUrgency(builder, root);
            appendSection(builder, "Root cause", text(root.get("rootCause")));
            appendBulletSection(builder, "Affected components", root.get("affectedComponents"));
            appendNumberedSection(builder, "Remediation", root.get("remediation"));

            String formatted = builder.toString().stripTrailing();
            return formatted.isEmpty() ? rawJson.trim() : formatted;
        } catch (Exception ex) {
            return rawJson.trim();
        }
    }

    private static void appendConfidenceUrgency(StringBuilder builder, JsonNode root) {
        String confidence = text(root.get("confidence"));
        String urgency = text(root.get("urgency"));
        if (confidence.isBlank() && urgency.isBlank()) {
            return;
        }

        builder.append('\n');
        if (!confidence.isBlank()) {
            builder.append("Confidence: ").append(formatLevel(confidence));
        }
        if (!urgency.isBlank()) {
            if (!confidence.isBlank()) {
                builder.append("  |  ");
            }
            builder.append("Urgency: ").append(formatLevel(urgency));
        }
    }

    private static String formatLevel(String value) {
        return value.trim().toUpperCase();
    }

    private static void appendSection(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("\n\n").append(label).append('\n').append(value.trim());
    }

    private static void appendBulletSection(StringBuilder builder, String label, JsonNode values) {
        if (values == null || !values.isArray() || values.isEmpty()) {
            return;
        }
        builder.append("\n\n").append(label);
        for (JsonNode value : values) {
            String item = text(value);
            if (!item.isBlank()) {
                builder.append("\n• ").append(item);
            }
        }
    }

    private static void appendNumberedSection(StringBuilder builder, String label, JsonNode values) {
        if (values == null || !values.isArray() || values.isEmpty()) {
            return;
        }
        builder.append("\n\n").append(label);
        int index = 1;
        for (JsonNode value : values) {
            String item = text(value);
            if (!item.isBlank()) {
                builder.append('\n').append(index++).append(". ").append(item);
            }
        }
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}
