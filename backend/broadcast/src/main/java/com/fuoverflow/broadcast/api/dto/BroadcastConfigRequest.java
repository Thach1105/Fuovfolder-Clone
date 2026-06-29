package com.fuoverflow.broadcast.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record BroadcastConfigRequest(
        @NotNull Map<String, Object> config,
        boolean enabled
) {}
