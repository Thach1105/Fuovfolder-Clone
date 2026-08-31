package com.fuoverflow.grading.api.dto;

/** @param result APPLIED, SUGGESTED or SKIPPED */
public record ApplyAnswersOutcome(long qid, String result, String reason) {
}
