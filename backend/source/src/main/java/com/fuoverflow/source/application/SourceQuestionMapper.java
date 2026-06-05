package com.fuoverflow.source.application;

import com.fuoverflow.source.api.dto.AdminQuestionOptionResponse;
import com.fuoverflow.source.api.dto.AdminQuestionResponse;
import com.fuoverflow.source.api.dto.PublicQuestionOptionResponse;
import com.fuoverflow.source.api.dto.PublicQuestionResponse;
import com.fuoverflow.source.persistence.SourceQuestionEntity;
import com.fuoverflow.source.persistence.SourceQuestionOptionEntity;

import java.util.List;

public final class SourceQuestionMapper {
    private SourceQuestionMapper() {
    }

    public static AdminQuestionResponse toAdmin(
            SourceQuestionEntity question,
            List<SourceQuestionOptionEntity> options) {
        return new AdminQuestionResponse(
                question.getId(),
                question.getCatalogItemId(),
                question.getQuestionText(),
                question.getQuestionImageUrl(),
                question.getExplanation(),
                question.isMultipleCorrect(),
                question.getSortOrder(),
                options.stream().map(SourceQuestionMapper::toAdminOption).toList(),
                question.getCreatedAt(),
                question.getUpdatedAt());
    }

    public static AdminQuestionOptionResponse toAdminOption(SourceQuestionOptionEntity option) {
        return new AdminQuestionOptionResponse(
                option.getId(),
                option.getOptionText(),
                option.getOptionImageUrl(),
                option.isCorrect(),
                option.getSortOrder());
    }

    public static PublicQuestionResponse toPublic(
            SourceQuestionEntity question,
            List<SourceQuestionOptionEntity> options) {
        return new PublicQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getQuestionImageUrl(),
                question.getExplanation(),
                question.isMultipleCorrect(),
                question.getSortOrder(),
                options.stream().map(SourceQuestionMapper::toPublicOption).toList());
    }

    public static PublicQuestionOptionResponse toPublicOption(SourceQuestionOptionEntity option) {
        return new PublicQuestionOptionResponse(
                option.getId(),
                option.getOptionText(),
                option.getOptionImageUrl(),
                option.isCorrect());
    }
}
