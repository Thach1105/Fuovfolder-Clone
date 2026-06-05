package com.fuoverflow.source.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SourceMediaService {
    private static final String QUESTION_IMAGE_FOLDER = "source/questions";

    private final ObjectStorage objectStorage;

    public SourceMediaService(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public StoredObject uploadQuestionImage(MultipartFile file) {
        return objectStorage.storeImage(file, QUESTION_IMAGE_FOLDER);
    }

    public void deleteStoredReference(String objectKeyOrLegacyReference) {
        objectStorage.delete(objectKeyOrLegacyReference);
    }
}
