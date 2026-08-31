package com.fuoverflow.exam.api.dto.webhook;

import java.time.Instant;

public record PaperSourcePayload(String system, String externalPaperId, Instant capturedAt) {
}
