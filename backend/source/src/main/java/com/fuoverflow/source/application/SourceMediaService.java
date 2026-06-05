package com.fuoverflow.source.application;

import com.fuoverflow.common.storage.LocalFileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SourceMediaService {
    private static final String QUESTION_IMAGE_FOLDER = "source/questions";

    private final LocalFileStorageService fileStorage;

    public SourceMediaService(LocalFileStorageService fileStorage) {
        this.fileStorage = fileStorage;
    }

    public String uploadQuestionImage(MultipartFile file) {
        return fileStorage.storeImage(file, QUESTION_IMAGE_FOLDER);
    }

    public void deleteManagedUrl(String url) {
        fileStorage.deleteIfManaged(url);
    }
}
