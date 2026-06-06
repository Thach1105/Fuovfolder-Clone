package com.fuoverflow.reaction.api.dto;

public record ReactionStatusResponse(
        String type,
        long count,
        boolean reacted
) {
}
