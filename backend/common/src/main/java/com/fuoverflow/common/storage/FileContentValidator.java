package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileContentValidator {
    private static final Set<String> OFFICE_EXTENSIONS = Set.of(".docx", ".pptx");
    private static final int ZIP_HEADER_READ = 8192;

    private FileContentValidator() {
    }

    public static void validate(MultipartFile file, FileKind kind, UploadProperties properties) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_EMPTY", "Uploaded file is empty");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        long maxBytes = kind == FileKind.IMAGE ? properties.imageMaxBytes() : properties.documentMaxBytes();
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("FILE_TOO_LARGE", "File exceeds maximum allowed size");
        }
        String declaredType = normalizeContentType(file.getContentType());
        if (kind == FileKind.IMAGE) {
            validateImage(file, declaredType, properties);
        } else {
            validateDocument(file, filename, declaredType, properties);
        }
    }

    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.bin";
        }
        String name = originalFilename.replace('\0', ' ').trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank()) {
            return "upload.bin";
        }
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static void validateImage(MultipartFile file, String declaredType, UploadProperties properties) {
        if (declaredType == null || !properties.allowedImageTypes().contains(declaredType)) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Only PNG, JPEG, WebP, and GIF images are allowed");
        }
        byte[] header = readHeader(file, 12);
        if (!matchesImageMagic(header, declaredType)) {
            throw new BadRequestException("FILE_TYPE_INVALID", "File content does not match declared image type");
        }
    }

    private static void validateDocument(
            MultipartFile file,
            String filename,
            String declaredType,
            UploadProperties properties) {
        if (declaredType == null || !properties.allowedDocumentTypes().contains(declaredType)) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Only PDF, DOCX, and PPTX documents are allowed");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (declaredType.equals("application/pdf")) {
            if (!lowerName.endsWith(".pdf")) {
                throw new BadRequestException("FILE_TYPE_INVALID", "PDF files must use .pdf extension");
            }
            byte[] header = readHeader(file, 5);
            if (!startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
                throw new BadRequestException("FILE_TYPE_INVALID", "File content is not a valid PDF");
            }
            return;
        }
        if (declaredType.endsWith("wordprocessingml.document")) {
            if (!lowerName.endsWith(".docx")) {
                throw new BadRequestException("FILE_TYPE_INVALID", "Word files must use .docx extension");
            }
            validateOfficeOpenXml(file, ".docx");
            return;
        }
        if (declaredType.endsWith("presentationml.presentation")) {
            if (!lowerName.endsWith(".pptx")) {
                throw new BadRequestException("FILE_TYPE_INVALID", "PowerPoint files must use .pptx extension");
            }
            validateOfficeOpenXml(file, ".pptx");
            return;
        }
        throw new BadRequestException("FILE_TYPE_INVALID", "Unsupported document type");
    }

    private static void validateOfficeOpenXml(MultipartFile file, String expectedExtension) {
        if (!OFFICE_EXTENSIONS.contains(expectedExtension)) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Unsupported Office document");
        }
        byte[] header = readHeader(file, 4);
        if (!startsWith(header, new byte[] {0x50, 0x4B, 0x03, 0x04})) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Office document must be a valid ZIP archive");
        }
        boolean hasContentTypes = false;
        try (InputStream raw = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > 256) {
                    throw new BadRequestException("FILE_TYPE_INVALID", "Office document archive is too complex");
                }
                if ("[Content_Types].xml".equals(entry.getName())) {
                    hasContentTypes = true;
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Unable to read Office document archive");
        }
        if (!hasContentTypes) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Invalid Office Open XML document");
        }
    }

    private static boolean matchesImageMagic(byte[] header, String contentType) {
        return switch (contentType) {
            case "image/png" -> startsWith(header, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
            case "image/jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            case "image/gif" -> startsWith(header, "GIF8".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> header.length >= 12
                    && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && startsWith(slice(header, 8, 4), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private static byte[] readHeader(MultipartFile file, int length) {
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[length];
            int read = input.readNBytes(buffer, 0, length);
            if (read <= 0) {
                throw new BadRequestException("FILE_EMPTY", "Uploaded file is empty");
            }
            if (read < length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buffer, 0, trimmed, 0, read);
                return trimmed;
            }
            return buffer;
        } catch (IOException ex) {
            throw new BadRequestException("FILE_READ_FAILED", "Unable to read uploaded file");
        }
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(source, offset, copy, 0, length);
        return copy;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    public static String extensionFor(String contentType, String filename) {
        String normalized = normalizeContentType(contentType);
        if ("image/png".equals(normalized)) {
            return ".png";
        }
        if ("image/webp".equals(normalized)) {
            return ".webp";
        }
        if ("image/gif".equals(normalized)) {
            return ".gif";
        }
        if ("image/jpeg".equals(normalized)) {
            return ".jpg";
        }
        if ("application/pdf".equals(normalized)) {
            return ".pdf";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx")) {
            return ".docx";
        }
        if (lower.endsWith(".pptx")) {
            return ".pptx";
        }
        return ".bin";
    }
}
