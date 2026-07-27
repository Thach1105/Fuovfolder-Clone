package com.fuoverflow.broadcast.api.dto;

import java.util.UUID;

public record AnnouncementActiveResponse(
        UUID id,
        String contentHtml,
        String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        int scrollSpeed,
        int stepSeconds
) {}
