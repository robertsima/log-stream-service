package com.logstream.service.alerting;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MessageNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");

    private MessageNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input.toLowerCase(Locale.ROOT);
        normalized = NUMBER.matcher(normalized).replaceAll("{number}");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }
}
