package com.fuoverflow.source.api.dto;

public record MediaUploadResponse(
        String objectKey,
        String publicUrl
) {
}
