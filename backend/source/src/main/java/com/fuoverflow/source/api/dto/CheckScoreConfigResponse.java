package com.fuoverflow.source.api.dto;

public record CheckScoreConfigResponse(
        boolean configured,
        boolean cookieConfigured,
        String checkScoreUrl
) {
}
