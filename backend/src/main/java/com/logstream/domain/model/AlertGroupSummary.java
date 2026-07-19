package com.logstream.domain.model;

/**
 * Grouping metadata for a delivered alert, carried alongside the OpenAPI-generated
 * {@link com.logstream.generated.model.AlertAnalysisResponse} so webhook senders can render a
 * "N similar errors" header without changing the contract-bound analysis response shape.
 */
public record AlertGroupSummary(String appName, String signatureLabel, int occurrenceCount, long windowSeconds) {

    public boolean isGrouped() {
        return occurrenceCount > 1;
    }
}
