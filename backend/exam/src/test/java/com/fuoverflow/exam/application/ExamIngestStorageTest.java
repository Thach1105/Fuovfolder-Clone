package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.material.domain.UploadPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExamIngestStorageTest {

    @Mock private ObjectStorage objectStorage;

    @Test
    void storesTheImageUnderThePurposeFolderAndSidecarsABlur() {
        ExamIngestStorage storage = newStorage();

        ExamIngestStorage.StoredImage stored = storage.storeImage(
                png(), "image/png", UploadPurpose.EXAM_FE_IMAGE, true);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(objectStorage, times(2)).storeBytes(any(), keys.capture(), any());
        assertTrue(stored.objectKey().startsWith("exam/fe/"), stored.objectKey());
        assertTrue(stored.objectKey().endsWith(".png"), stored.objectKey());
        assertNotNull(stored.blurObjectKey());
        assertTrue(stored.blurObjectKey().endsWith("-blur.jpg"), stored.blurObjectKey());
        assertTrue(keys.getAllValues().contains(stored.objectKey()));
        assertTrue(keys.getAllValues().contains(stored.blurObjectKey()));
    }

    @Test
    void skipsTheBlurWhenNotRequested() {
        ExamIngestStorage storage = newStorage();

        ExamIngestStorage.StoredImage stored = storage.storeImage(
                png(), "image/png", UploadPurpose.EXAM_PE_IMAGE, false);

        verify(objectStorage, times(1)).storeBytes(any(), any(), any());
        assertNull(stored.blurObjectKey());
        assertTrue(stored.objectKey().startsWith("exam/pe/"), stored.objectKey());
    }

    @Test
    void keepsTheImageWhenBlurGenerationFails() {
        ExamIngestStorage storage = newStorage();
        byte[] notAnImage = "still stored by the caller's choice".getBytes();

        ExamIngestStorage.StoredImage stored = storage.storeImage(
                notAnImage, "image/png", UploadPurpose.EXAM_FE_IMAGE, true);

        assertNotNull(stored.objectKey());
        assertNull(stored.blurObjectKey());
    }

    @Test
    void surfacesAStorageFailureForTheOriginal() {
        ExamIngestStorage storage = newStorage();
        doThrow(new RuntimeException("disk full"))
                .when(objectStorage).storeBytes(any(), any(), eq("image/png"));

        try {
            storage.storeImage(png(), "image/png", UploadPurpose.EXAM_FE_IMAGE, true);
            throw new AssertionError("expected the storage failure to propagate");
        } catch (RuntimeException e) {
            assertEquals("disk full", e.getMessage());
        }
    }

    @Test
    void storesResourcesUnderTheResourceFolderKeepingTheExtension() {
        ExamIngestStorage storage = newStorage();

        String key = storage.storeResource(new byte[]{1, 2}, "application/zip", "PE01_starter.zip");

        assertTrue(key.startsWith("exam/pe/resources/"), key);
        assertTrue(key.endsWith(".zip"), key);
        verify(objectStorage).storeBytes(any(), eq(key), eq("application/zip"));
    }

    @Test
    void fallsBackToOctetStreamWhenTheResourceMimeIsUnknown() {
        ExamIngestStorage storage = newStorage();

        String key = storage.storeResource(new byte[]{1}, null, "bundle.zip");

        verify(objectStorage).storeBytes(any(), eq(key), eq("application/octet-stream"));
    }

    private ExamIngestStorage newStorage() {
        return new ExamIngestStorage(objectStorage, new BlurImageGenerator());
    }

    private static byte[] png() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC");
    }
}
