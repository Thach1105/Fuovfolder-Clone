package com.fuoverflow.source.api.dto;

public record CheckScoreConfigResponse(
        boolean configured,
        String checkScoreUrl
) {
}
