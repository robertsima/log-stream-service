package com.logstream.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.logstream.generated.model.LogEventRequest;

public class BucketFingerprint {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");

    private final String value;

    private BucketFingerprint(String value) {
        this.value = value;
    }

    public static BucketFingerprint from(AlertBucket alertBucket) {
        StringBuilder builder = new StringBuilder();
        builder.append(alertBucket.getAppId()).append('|');

        for (LogEventRequest event : alertBucket.getEvents()) {
            builder.append(normalize(event.getMessage())).append('|');
            builder.append(normalize(event.getLogger())).append('|');
            builder.append(normalize(event.getTraceId())).append('|');
            builder.append(normalize(event.getSpanId())).append('|');
        }

        return new BucketFingerprint(sha256(builder.toString()));
    }

    public String value() {
        return value;
    }

    private static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input.toLowerCase(Locale.ROOT);
        normalized = NUMBER.matcher(normalized).replaceAll("{number}");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash fingerprint", ex);
        }
    }
}
