package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ExamMediaService {
    private final UploadService uploadService;

    public ExamMediaService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    public StoredObject upload(MultipartFile file, UploadPurpose purpose, UUID adminUserId) {
        UploadResponse uploaded = uploadService.upload(file, purpose, adminUserId,
                SecurityContextHolder.getContext().getAuthentication());
        return new StoredObject(uploaded.objectKey(), uploaded.publicUrl());
    }

    public void markLinked(String objectKey) {
        uploadService.markLinkedByStoragePath(objectKey);
    }

    public void markLinkedAll(List<String> objectKeys) {
        if (objectKeys == null) return;
        for (String key : objectKeys) {
            markLinked(key);
        }
    }

    public void unlinkStoredReference(String objectKeyOrLegacyReference) {
        uploadService.markUnlinkedByObjectKey(objectKeyOrLegacyReference);
    }

    public void unlinkStoredReferences(List<String> references) {
        if (references == null) return;
        for (String ref : references) {
            unlinkStoredReference(ref);
        }
    }

    public void deleteStoredReference(String objectKeyOrLegacyReference) {
        uploadService.deleteByStorageReference(objectKeyOrLegacyReference);
    }

    public void deleteStoredReferences(List<String> references) {
        if (references == null) return;
        for (String ref : references) {
            deleteStoredReference(ref);
        }
    }
}
