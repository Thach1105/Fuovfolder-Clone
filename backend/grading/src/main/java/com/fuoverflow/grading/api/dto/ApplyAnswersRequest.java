package com.fuoverflow.grading.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * @param conflictPolicy KEEP_EXISTING (default), OVERWRITE or FAIL
 * @param dryRun         true computes every outcome for review and writes nothing
 */
public record ApplyAnswersRequest(
        String examCode,
        String sourceRef,
        String conflictPolicy,
        boolean dryRun,
        @NotEmpty List<AnswerItem> items
) {
}
