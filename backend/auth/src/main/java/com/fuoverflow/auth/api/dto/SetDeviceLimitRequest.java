package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SetDeviceLimitRequest(
        @Min(0) @Max(100)
        Short maxDevices
) {}
