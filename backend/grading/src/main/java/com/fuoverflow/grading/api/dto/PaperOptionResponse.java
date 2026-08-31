package com.fuoverflow.grading.api.dto;

/** Admin-only view. {@code isCorrect} must never appear in a DTO served outside this module. */
public record PaperOptionResponse(long qaid, int optionIndex, String text, boolean isCorrect) {
}
