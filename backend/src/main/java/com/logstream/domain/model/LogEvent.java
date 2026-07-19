package com.logstream.domain.model;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record LogEvent(
        UUID appId,
        String appName,
        Instant receivedAt,
        JsonNode payload
) {}