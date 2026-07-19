package com.logstream.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * One alert-worthy event, or a group of similar ones coalesced by
 * {@link com.logstream.service.AlertContextProcessor}. occurrenceCount == 1 for an
 * ungrouped error; > 1 means triggeringEvent is the representative (first) occurrence
 * of occurrenceCount similar errors observed between windowStartMs and windowEndMs.
 */
public record AlertTrigger(
        UUID appId,
        LogEvent triggeringEvent,
        List<LogEvent> context,
        int occurrenceCount,
        String signatureLabel,
        long windowStartMs,
        long windowEndMs
) {}
