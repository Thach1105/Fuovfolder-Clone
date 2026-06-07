package com.fuoverflow.source.application;

import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class SourceMediaService {
    private final UploadService uploadService;

    public SourceMediaService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    public StoredObject uploadQuestionImage(MultipartFile file, UUID adminUserId) {
        UploadResponse uploaded = uploadService.upload(file, UploadPurpose.SOURCE_QUESTION, adminUserId);
        return new StoredObject(uploaded.objectKey(), uploaded.publicUrl());
    }

    public void deleteStoredReference(String objectKeyOrLegacyReference) {
        uploadService.deleteByStorageReference(objectKeyOrLegacyReference);
    }
}
