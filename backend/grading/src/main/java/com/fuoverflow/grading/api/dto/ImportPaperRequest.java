package com.fuoverflow.grading.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * @param confirmedFingerprint the fingerprint returned by {@code /preview}; required on import so a
 *                             paper can only be saved by a caller that has seen what it is saving.
 */
public record ImportPaperRequest(JsonNode payload, UUID payloadId, String confirmedFingerprint) {
}
