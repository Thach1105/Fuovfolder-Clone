package com.fuoverflow.exam.api.dto;

public record ExamCommentLikeResponse(
        boolean liked,
        int likeCount
) {
}
