package com.fuoverflow.source.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.source.api.dto.AdminQuestionOptionResponse;
import com.fuoverflow.source.api.dto.AdminQuestionResponse;
import com.fuoverflow.source.api.dto.PublicQuestionOptionResponse;
import com.fuoverflow.source.api.dto.PublicQuestionResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceMediaUrlResolver {
    private final ObjectStorage objectStorage;

    public SourceMediaUrlResolver(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public String normalizeForStorage(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return null;
        }
        return objectStorage.normalizeToObjectKey(imageReference);
    }

    public AdminQuestionResponse resolveAdmin(AdminQuestionResponse response) {
        return new AdminQuestionResponse(
                response.id(),
                response.catalogItemId(),
                response.questionText(),
                objectStorage.resolvePublicUrl(response.questionImageUrl()),
                response.explanation(),
                response.multipleCorrect(),
                response.sortOrder(),
                resolveAdminOptions(response.options()),
                response.createdAt(),
                response.updatedAt());
    }

    public PublicQuestionResponse resolvePublic(PublicQuestionResponse response) {
        return new PublicQuestionResponse(
                response.id(),
                response.questionText(),
                objectStorage.resolvePublicUrl(response.questionImageUrl()),
                response.explanation(),
                response.multipleCorrect(),
                response.sortOrder(),
                resolvePublicOptions(response.options()));
    }

    private List<AdminQuestionOptionResponse> resolveAdminOptions(List<AdminQuestionOptionResponse> options) {
        return options.stream()
                .map(option -> new AdminQuestionOptionResponse(
                        option.id(),
                        option.optionText(),
                        objectStorage.resolvePublicUrl(option.optionImageUrl()),
                        option.isCorrect(),
                        option.sortOrder()))
                .toList();
    }

    private List<PublicQuestionOptionResponse> resolvePublicOptions(List<PublicQuestionOptionResponse> options) {
        return options.stream()
                .map(option -> new PublicQuestionOptionResponse(
                        option.id(),
                        option.optionText(),
                        objectStorage.resolvePublicUrl(option.optionImageUrl()),
                        option.isCorrect()))
                .toList();
    }
}
