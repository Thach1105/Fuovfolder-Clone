package com.fuoverflow.grading.api.dto;

import java.util.List;

public record ApplyAnswersResponse(
        int applied,
        int suggested,
        int skipped,
        List<Long> stillUnansweredQids,
        List<ApplyAnswersOutcome> outcomes
) {
}
