package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContentValidatorTest {
    private final UploadProperties properties = new UploadProperties(
            5L * 1024 * 1024,
            20L * 1024 * 1024,
            50L * 1024 * 1024,
            30,
            null,
            null,
            null);

    @Test
    void rejectsFakePdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                "hello".getBytes());
        assertThrows(BadRequestException.class, () -> FileContentValidator.validate(file, FileKind.DOCUMENT, properties));
    }

    @Test
    void rejectsOversizeImage() {
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.png",
                "image/png",
                pngHeader) {
            @Override
            public long getSize() {
                return 6L * 1024 * 1024;
            }
        };
        assertThrows(BadRequestException.class, () -> FileContentValidator.validate(file, FileKind.IMAGE, properties));
    }

    @Test
    void rejectsWrongMagicBytesForPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                "not-a-png".getBytes());
        assertThrows(BadRequestException.class, () -> FileContentValidator.validate(file, FileKind.IMAGE, properties));
    }

    @Test
    void acceptsValidZipArchive() {
        byte[] zipHeader = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resources.zip",
                "application/zip",
                zipHeader);
        FileContentValidator.validate(file, FileKind.ARCHIVE, properties);
    }

    @Test
    void rejectsArchiveWithWrongMagic() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resources.zip",
                "application/zip",
                "not-a-zip".getBytes());
        assertThrows(BadRequestException.class, () -> FileContentValidator.validate(file, FileKind.ARCHIVE, properties));
    }

    @Test
    void rejectsArchiveWithWrongExtension() {
        byte[] zipHeader = new byte[] {0x50, 0x4B, 0x03, 0x04};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resources.rar",
                "application/zip",
                zipHeader);
        assertThrows(BadRequestException.class, () -> FileContentValidator.validate(file, FileKind.ARCHIVE, properties));
    }
}
