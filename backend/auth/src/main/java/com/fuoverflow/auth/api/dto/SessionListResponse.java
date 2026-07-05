package com.fuoverflow.auth.api.dto;

import java.util.List;

public record SessionListResponse(
        List<SessionResponse> sessions,
        int maxDevices,
        String deviceLimitSource
) {}
