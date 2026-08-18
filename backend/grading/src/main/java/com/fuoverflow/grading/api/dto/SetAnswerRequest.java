package com.fuoverflow.grading.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SetAnswerRequest(@NotEmpty List<Long> qaids) {
}
