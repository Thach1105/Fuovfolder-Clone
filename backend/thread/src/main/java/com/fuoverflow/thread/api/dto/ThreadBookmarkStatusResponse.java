package com.fuoverflow.thread.api.dto;

import java.util.UUID;

public record ThreadBookmarkStatusResponse(UUID threadId, boolean watched) {
}
