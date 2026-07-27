package com.fuoverflow.source.api.dto;

public record CheckScoreResponse(
        String totalQuestions,
        String score,
        String subject,
        String correctAnswers,
        String charged
) {
}
