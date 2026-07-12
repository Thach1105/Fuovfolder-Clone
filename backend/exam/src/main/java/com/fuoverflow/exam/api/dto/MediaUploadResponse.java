package com.fuoverflow.exam.api.dto;

public record MediaUploadResponse(
        String objectKey,
        String blurObjectKey,
        String publicUrl
) {
}
