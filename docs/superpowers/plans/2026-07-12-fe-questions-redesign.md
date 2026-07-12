# FE Questions Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert FE Questions from structured MCQ model to image-post model with blur-gated preview images, rate limiting, and paired blur file management.

**Architecture:** Drop `exam_fe_options` table and MCQ fields. Add server-side blur generation at upload time (Java AWT). Backend returns `type: "full"` or `type: "blur"` image items per question — non-members get blur signed URLs for locked images, members get full signed URLs. Rate limit media endpoint at 60 req/min/IP.

**Tech Stack:** Java 21, Spring Boot 3.5.x, PostgreSQL (Flyway), Next.js (TypeScript), Tailwind CSS.

## Global Constraints

- Java 21, Spring Boot 3.5.x, Maven multi-module monolith.
- Hibernate `ddl-auto = validate` — all schema changes via Flyway migrations only.
- No DB foreign keys — cross-table refs validated in app layer via guards/services.
- Soft-delete where schema has `deleted_at`.
- HttpOnly/Secure cookies for auth; signed URLs (HMAC-SHA256) for media.
- No new infrastructure (no CDN, no external image service).
- `java.awt` for blur generation — no new dependencies beyond what JDK provides.

---

### Task 1: Flyway Migration — Drop MCQ, Add Blur Support

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V45__fe_questions_drop_mcq_add_blur.sql`

**Interfaces:**
- Consumes: nothing
- Produces: Updated schema — `exam_fe_options` dropped; `exam_fe_questions` loses `multiple_correct` and `explanation`, gains `question_blur_urls`; `exam_subjects.fe_preview_count` renamed to `fe_preview_image_count` with default 2.

- [ ] **Step 1: Write the migration file**

```sql
-- V45: FE Questions redesign — drop MCQ structure, add blur image support.
-- FE Questions become image-based posts (title + images + comments).
-- Blur thumbnails are generated server-side and stored parallel to originals.

-- 1. Drop the options table (hard-deleted during question updates anyway)
DROP TABLE IF EXISTS exam_fe_options;

-- 2. Remove MCQ-specific columns from questions
ALTER TABLE exam_fe_questions DROP COLUMN IF EXISTS multiple_correct;
ALTER TABLE exam_fe_questions DROP COLUMN IF EXISTS explanation;

-- 3. Add blur image tracking (parallel array to question_image_urls)
ALTER TABLE exam_fe_questions
    ADD COLUMN question_blur_urls jsonb NOT NULL DEFAULT '[]';

-- 4. Rename fe_preview_count -> fe_preview_image_count (now means per-post image count)
ALTER TABLE exam_subjects
    RENAME COLUMN fe_preview_count TO fe_preview_image_count;

ALTER TABLE exam_subjects
    DROP CONSTRAINT IF EXISTS exam_subjects_preview_count_nonneg;

ALTER TABLE exam_subjects
    ADD CONSTRAINT exam_subjects_preview_image_count_nonneg
        CHECK (fe_preview_image_count >= 0);

ALTER TABLE exam_subjects
    ALTER COLUMN fe_preview_image_count SET DEFAULT 2;

-- 5. Drop the trigger on the now-deleted options table
DROP TRIGGER IF EXISTS trg_exam_fe_options_updated_at ON exam_fe_options;
```

- [ ] **Step 2: Verify migration compiles by running Flyway validate**

Run: `cd backend && mvn -q flyway:validate -pl app 2>&1 || echo "Expected: validation may fail until entities are updated"`

Expected: Migration file recognized by Flyway. Validation may fail because entities still reference dropped columns — that's expected and will be fixed in Task 2.

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V45__fe_questions_drop_mcq_add_blur.sql
git commit -m "feat(exam): migration to drop MCQ structure and add blur image support"
```

---

### Task 2: Update Backend Entities and Repositories — Remove MCQ, Add Blur

**Files:**
- Delete: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeOptionEntity.java`
- Delete: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeOptionRepository.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeQuestionEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeQuestionRepository.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`

**Interfaces:**
- Consumes: Task 1 migration (schema changes)
- Produces:
  - `ExamFeQuestionEntity`: fields `questionBlurUrls` (String, jsonb), no more `explanation`/`multipleCorrect`. Factory: `create(UUID id, UUID subjectId, String questionText, String questionImageUrls, String questionBlurUrls, int sortOrder, Instant now)`.
  - `ExamSubjectEntity`: field renamed `fePreviewCount` → `fePreviewImageCount`, getter `getFePreviewImageCount()`, setter `setFePreviewImageCount(int)`. Factory param renamed.
  - `ExamFeOptionEntity` and `ExamFeOptionRepository` deleted.
  - `ExamFeQuestionRepository`: unchanged query methods (the `findSubjectIdByQuestionImageUrlsContaining` stays for media ownership check).

- [ ] **Step 1: Delete ExamFeOptionEntity.java**

Delete file: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeOptionEntity.java`

- [ ] **Step 2: Delete ExamFeOptionRepository.java**

Delete file: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeOptionRepository.java`

- [ ] **Step 3: Update ExamFeQuestionEntity.java — remove MCQ fields, add blurUrls**

Replace the entire file content of `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeQuestionEntity.java`:

```java
package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_fe_questions")
public class ExamFeQuestionEntity {
    @Id
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_image_urls", columnDefinition = "jsonb")
    private String questionImageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_blur_urls", columnDefinition = "jsonb")
    private String questionBlurUrls;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getSubjectId() { return subjectId; }
    public String getQuestionText() { return questionText; }
    public String getQuestionImageUrls() { return questionImageUrls; }
    public String getQuestionBlurUrls() { return questionBlurUrls; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionImageUrls(String questionImageUrls) { this.questionImageUrls = questionImageUrls; }
    public void setQuestionBlurUrls(String questionBlurUrls) { this.questionBlurUrls = questionBlurUrls; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ExamFeQuestionEntity create(
            UUID id, UUID subjectId, String questionText,
            String questionImageUrls, String questionBlurUrls,
            int sortOrder, Instant now) {
        ExamFeQuestionEntity e = new ExamFeQuestionEntity();
        e.id = id;
        e.subjectId = subjectId;
        e.questionText = questionText;
        e.questionImageUrls = questionImageUrls;
        e.questionBlurUrls = questionBlurUrls;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
```

- [ ] **Step 4: Update ExamSubjectEntity.java — rename fePreviewCount to fePreviewImageCount**

In `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`:

1. Rename the field:
   - `@Column(name = "fe_preview_count", ...)` → `@Column(name = "fe_preview_image_count", ...)`
   - `private int fePreviewCount;` → `private int fePreviewImageCount;`
2. Rename getter: `getFePreviewCount()` → `getFePreviewImageCount()`
3. Rename setter: `setFePreviewCount(int)` → `setFePreviewImageCount(int)`
4. Rename factory param: `fePreviewCount` → `fePreviewImageCount` (and `e.fePreviewCount = ...` → `e.fePreviewImageCount = ...`)

- [ ] **Step 5: Verify compilation**

Run: `cd backend && mvn -q compile -pl exam -am 2>&1 | tail -20`

Expected: Compilation errors from service classes that still reference deleted types — that's expected and fixed in Task 3. The entity/repository layer itself should compile clean.

- [ ] **Step 6: Commit**

```bash
git add -A backend/exam/src/main/java/com/fuoverflow/exam/persistence/
git commit -m "refactor(exam): remove MCQ entities, add blur field to FE question entity"
```

---

### Task 3: Add Blur Generation to ObjectStorage and ExamMediaService

**Files:**
- Modify: `backend/common/src/main/java/com/fuoverflow/common/storage/ObjectStorage.java` — add `storeBytes(byte[], String objectKey, String contentType)` method
- Modify: `backend/common/src/main/java/com/fuoverflow/common/storage/LocalObjectStorage.java` — implement `storeBytes`
- Modify: `backend/common/src/main/java/com/fuoverflow/common/storage/S3CompatibleObjectStorage.java` — implement `storeBytes`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/BlurImageGenerator.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamMediaService.java` — add `uploadWithBlur`, `deletePaired`
- Create: `backend/exam/src/test/java/com/fuoverflow/exam/application/BlurImageGeneratorTest.java`

**Interfaces:**
- Consumes: `ObjectStorage.openStream(key)`, `ObjectStorage.delete(key)`
- Produces:
  - `ObjectStorage.storeBytes(byte[] data, String objectKey, String contentType)` — stores raw bytes at given key
  - `BlurImageGenerator.generateBlur(InputStream originalImage, String contentType)` → `byte[]` — returns blurred JPEG bytes (~100px wide, Gaussian blur)
  - `ExamMediaService.uploadWithBlur(MultipartFile file, UploadPurpose purpose, UUID adminUserId)` → `BlurUploadResult(String objectKey, String blurObjectKey, String publicUrl)` — uploads original + generates & stores blur
  - `ExamMediaService.deletePaired(String objectKey, String blurObjectKey)` — deletes both original and blur files
  - `ExamMediaService.deletePairedAll(List<String> objectKeys, List<String> blurKeys)` — bulk paired delete

- [ ] **Step 1: Add storeBytes to ObjectStorage interface**

In `backend/common/src/main/java/com/fuoverflow/common/storage/ObjectStorage.java`, add:

```java
void storeBytes(byte[] data, String objectKey, String contentType);
```

- [ ] **Step 2: Implement storeBytes in LocalObjectStorage**

In `backend/common/src/main/java/com/fuoverflow/common/storage/LocalObjectStorage.java`, add:

```java
@Override
public void storeBytes(byte[] data, String objectKey, String contentType) {
    Path target = resolvePhysicalPath(objectKey);
    try {
        Files.createDirectories(target.getParent());
        Files.write(target, data);
    } catch (IOException ex) {
        throw new BadRequestException("FILE_STORE_FAILED", "Failed to store file");
    }
}
```

- [ ] **Step 3: Implement storeBytes in S3CompatibleObjectStorage**

In `backend/common/src/main/java/com/fuoverflow/common/storage/S3CompatibleObjectStorage.java`, add:

```java
@Override
public void storeBytes(byte[] data, String objectKey, String contentType) {
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentType)
            .build();
    client.putObject(request, RequestBody.fromBytes(data));
}
```

(Check existing S3 imports — `PutObjectRequest`, `RequestBody` should already be imported. Verify by reading the file first.)

- [ ] **Step 4: Create BlurImageGenerator**

Create `backend/exam/src/main/java/com/fuoverflow/exam/application/BlurImageGenerator.java`:

```java
package com.fuoverflow.exam.application;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Component
class BlurImageGenerator {
    private static final int TARGET_WIDTH = 100;
    private static final int BLUR_RADIUS = 10;

    byte[] generateBlur(InputStream originalImage) throws IOException {
        BufferedImage source = ImageIO.read(originalImage);
        if (source == null) {
            throw new IOException("Unable to decode image");
        }

        int targetHeight = Math.max(1,
                (int) Math.round((double) source.getHeight() / source.getWidth() * TARGET_WIDTH));
        BufferedImage resized = new BufferedImage(TARGET_WIDTH, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, TARGET_WIDTH, targetHeight, null);
        g.dispose();

        BufferedImage blurred = applyGaussianBlur(resized, BLUR_RADIUS);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(blurred, "jpeg", out);
        return out.toByteArray();
    }

    private static BufferedImage applyGaussianBlur(BufferedImage image, int radius) {
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float sigma = radius / 3.0f;
        float sum = 0;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                float value = (float) Math.exp(-(x * x + y * y) / (2 * sigma * sigma));
                data[(y + radius) * size + (x + radius)] = value;
                sum += value;
            }
        }
        for (int i = 0; i < data.length; i++) {
            data[i] /= sum;
        }
        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }
}
```

- [ ] **Step 5: Write BlurImageGenerator test**

Create `backend/exam/src/test/java/com/fuoverflow/exam/application/BlurImageGeneratorTest.java`:

```java
package com.fuoverflow.exam.application;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlurImageGeneratorTest {
    private final BlurImageGenerator generator = new BlurImageGenerator();

    @Test
    void generateBlur_producesSmallJpeg() throws IOException {
        BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] blurBytes = generator.generateBlur(new ByteArrayInputStream(sourceBytes.toByteArray()));

        assertNotNull(blurBytes);
        assertTrue(blurBytes.length > 0);
        assertTrue(blurBytes.length < sourceBytes.size());

        BufferedImage blurImage = ImageIO.read(new ByteArrayInputStream(blurBytes));
        assertNotNull(blurImage);
        assertEquals(100, blurImage.getWidth());
        assertEquals(75, blurImage.getHeight());
    }

    @Test
    void generateBlur_handlesPortraitImage() throws IOException {
        BufferedImage source = new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] blurBytes = generator.generateBlur(new ByteArrayInputStream(sourceBytes.toByteArray()));

        BufferedImage blurImage = ImageIO.read(new ByteArrayInputStream(blurBytes));
        assertEquals(100, blurImage.getWidth());
        assertEquals(200, blurImage.getHeight());
    }
}
```

- [ ] **Step 6: Run BlurImageGenerator test**

Run: `cd backend && mvn -q test -pl exam -Dtest=BlurImageGeneratorTest -am 2>&1 | tail -10`

Expected: 2 tests pass.

- [ ] **Step 7: Update ExamMediaService — add uploadWithBlur and deletePaired**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamMediaService.java`:

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ExamMediaService {
    private static final Logger log = LoggerFactory.getLogger(ExamMediaService.class);

    private final UploadService uploadService;
    private final ObjectStorage objectStorage;
    private final BlurImageGenerator blurGenerator;

    public ExamMediaService(UploadService uploadService, ObjectStorage objectStorage,
                            BlurImageGenerator blurGenerator) {
        this.uploadService = uploadService;
        this.objectStorage = objectStorage;
        this.blurGenerator = blurGenerator;
    }

    public StoredObject upload(MultipartFile file, UploadPurpose purpose, UUID adminUserId) {
        UploadResponse uploaded = uploadService.upload(file, purpose, adminUserId,
                SecurityContextHolder.getContext().getAuthentication());
        return new StoredObject(uploaded.objectKey(), uploaded.publicUrl());
    }

    public BlurUploadResult uploadWithBlur(MultipartFile file, UploadPurpose purpose, UUID adminUserId) {
        StoredObject stored = upload(file, purpose, adminUserId);
        String blurKey = deriveBlurKey(stored.objectKey());
        try (InputStream stream = objectStorage.openStream(stored.objectKey())) {
            byte[] blurBytes = blurGenerator.generateBlur(stream);
            objectStorage.storeBytes(blurBytes, blurKey, "image/jpeg");
        } catch (Exception e) {
            log.warn("Failed to generate blur for {}: {}", stored.objectKey(), e.getMessage());
        }
        return new BlurUploadResult(stored.objectKey(), blurKey, stored.publicUrl());
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

    public void deletePaired(String objectKey, String blurKey) {
        deleteStoredReference(objectKey);
        if (blurKey != null) {
            try {
                objectStorage.delete(blurKey);
            } catch (Exception e) {
                log.warn("Failed to delete blur file {}: {}", blurKey, e.getMessage());
            }
        }
    }

    public void deletePairedAll(List<String> objectKeys, List<String> blurKeys) {
        deleteStoredReferences(objectKeys);
        if (blurKeys == null) return;
        for (String blurKey : blurKeys) {
            if (blurKey != null) {
                try {
                    objectStorage.delete(blurKey);
                } catch (Exception e) {
                    log.warn("Failed to delete blur file {}: {}", blurKey, e.getMessage());
                }
            }
        }
    }

    static String deriveBlurKey(String objectKey) {
        int lastDot = objectKey.lastIndexOf('.');
        if (lastDot > 0) {
            return objectKey.substring(0, lastDot) + "-blur.jpg";
        }
        return objectKey + "-blur.jpg";
    }

    public record BlurUploadResult(String objectKey, String blurObjectKey, String publicUrl) {}
}
```

- [ ] **Step 8: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/storage/ObjectStorage.java \
      backend/common/src/main/java/com/fuoverflow/common/storage/LocalObjectStorage.java \
      backend/common/src/main/java/com/fuoverflow/common/storage/S3CompatibleObjectStorage.java \
      backend/exam/src/main/java/com/fuoverflow/exam/application/BlurImageGenerator.java \
      backend/exam/src/test/java/com/fuoverflow/exam/application/BlurImageGeneratorTest.java \
      backend/exam/src/main/java/com/fuoverflow/exam/application/ExamMediaService.java
git commit -m "feat(exam): add blur image generation and paired storage/delete"
```

---

### Task 4: Update DTOs — Remove MCQ, Add Image Items

**Files:**
- Delete: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/FeOptionRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateFeQuestionRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateFeQuestionRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminFeQuestionResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicFeQuestionResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicFeQuestionListResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/MediaUploadResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectDetailResponse.java`

**Interfaces:**
- Consumes: Task 2 entity changes
- Produces:
  - `CreateFeQuestionRequest(String questionText, List<String> questionImageUrls, List<String> questionBlurUrls, Integer sortOrder)` — no options
  - `UpdateFeQuestionRequest(String questionText, List<String> questionImageUrls, List<String> questionBlurUrls, Integer sortOrder)` — no options
  - `AdminFeQuestionResponse(UUID id, UUID subjectId, String questionText, List<String> questionImageUrls, List<String> questionBlurUrls, int sortOrder, Instant createdAt, Instant updatedAt)` — no options, no explanation, no multipleCorrect
  - `PublicFeQuestionResponse(UUID id, String questionText, int totalImageCount, List<PublicImageItem> images, int sortOrder, int commentCount, Instant createdAt)` — new structure with image items
  - `PublicFeQuestionResponse.PublicImageItem(int index, String url, String type)` — nested record, type = "full" | "blur"
  - `PublicFeQuestionListResponse(boolean locked, int totalCount, int previewImageCount, List<PublicFeQuestionResponse> questions)` — renamed previewCount → previewImageCount
  - `MediaUploadResponse(String objectKey, String blurObjectKey, String publicUrl)` — added blurObjectKey
  - `AdminSubjectResponse`: `fePreviewCount` → `fePreviewImageCount`
  - `PublicSubjectDetailResponse`: `fePreviewCount` → `fePreviewImageCount`

- [ ] **Step 1: Delete FeOptionRequest.java**

Delete file: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/FeOptionRequest.java`

- [ ] **Step 2: Rewrite CreateFeQuestionRequest**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateFeQuestionRequest.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.util.List;

public record CreateFeQuestionRequest(
        String questionText,
        List<String> questionImageUrls,
        List<String> questionBlurUrls,
        Integer sortOrder
) {
}
```

- [ ] **Step 3: Rewrite UpdateFeQuestionRequest**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateFeQuestionRequest.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.util.List;

public record UpdateFeQuestionRequest(
        String questionText,
        List<String> questionImageUrls,
        List<String> questionBlurUrls,
        Integer sortOrder
) {
}
```

- [ ] **Step 4: Rewrite AdminFeQuestionResponse**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminFeQuestionResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFeQuestionResponse(
        UUID id,
        UUID subjectId,
        String questionText,
        List<String> questionImageUrls,
        List<String> questionBlurUrls,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
```

- [ ] **Step 5: Rewrite PublicFeQuestionResponse**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicFeQuestionResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicFeQuestionResponse(
        UUID id,
        String questionText,
        int totalImageCount,
        List<PublicImageItem> images,
        int sortOrder,
        int commentCount,
        Instant createdAt
) {
    public record PublicImageItem(
            int index,
            String url,
            String type
    ) {
    }
}
```

- [ ] **Step 6: Update PublicFeQuestionListResponse**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicFeQuestionListResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.util.List;

public record PublicFeQuestionListResponse(
        boolean locked,
        int totalCount,
        int previewImageCount,
        List<PublicFeQuestionResponse> questions
) {
}
```

- [ ] **Step 7: Update MediaUploadResponse**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/MediaUploadResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

public record MediaUploadResponse(
        String objectKey,
        String blurObjectKey,
        String publicUrl
) {
}
```

- [ ] **Step 8: Update AdminSubjectResponse — rename fePreviewCount**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubjectResponse(
        UUID id,
        String code,
        String title,
        String description,
        String coverImageUrl,
        String cardColor,
        String categorySlug,
        int fePreviewImageCount,
        long viewCount,
        boolean active,
        int sortOrder,
        int feQuestionCount,
        int pePaperCount,
        Instant createdAt,
        Instant updatedAt
) {
}
```

- [ ] **Step 9: Update PublicSubjectDetailResponse — rename fePreviewCount**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectDetailResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.util.UUID;

public record PublicSubjectDetailResponse(
        UUID id,
        String code,
        String title,
        String description,
        String categorySlug,
        String cardColor,
        String coverImageUrl,
        long viewCount,
        int feQuestionCount,
        int pePaperCount,
        int fePreviewImageCount,
        boolean hasActiveMembership
) {
}
```

- [ ] **Step 10: Update CreateSubjectRequest and UpdateSubjectRequest — rename fePreviewCount**

In `CreateSubjectRequest.java`: rename `fePreviewCount` → `fePreviewImageCount` (field + annotation).

In `UpdateSubjectRequest.java`: rename `fePreviewCount` → `fePreviewImageCount` (field + annotation).

- [ ] **Step 11: Update ExamProperties — rename defaultFePreviewCount**

In `backend/exam/src/main/java/com/fuoverflow/exam/config/ExamProperties.java`:

Rename field `defaultFePreviewCount` → `defaultFePreviewImageCount` and the method `defaultFePreviewCountOrDefault()` → `defaultFePreviewImageCountOrDefault()`.

- [ ] **Step 12: Commit**

```bash
git add -A backend/exam/src/main/java/com/fuoverflow/exam/api/dto/ \
      backend/exam/src/main/java/com/fuoverflow/exam/config/ExamProperties.java
git commit -m "refactor(exam): update DTOs — remove MCQ fields, add image items and blur keys"
```

---

### Task 5: Rewrite ExamFeQuestionAdminService — Remove Options Logic, Add Blur Management

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamFeQuestionAdminService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`

**Interfaces:**
- Consumes: Task 2 entities (no options), Task 3 `ExamMediaService.deletePairedAll`, Task 4 DTOs
- Produces:
  - `list(UUID subjectId)` → `List<AdminFeQuestionResponse>` (no options in response)
  - `get(UUID subjectId, UUID questionId)` → `AdminFeQuestionResponse`
  - `create(UUID subjectId, CreateFeQuestionRequest)` → `AdminFeQuestionResponse`
  - `update(UUID subjectId, UUID questionId, UpdateFeQuestionRequest)` → `AdminFeQuestionResponse`
  - `delete(UUID subjectId, UUID questionId)` — deletes original + blur files
  - `reorder(UUID subjectId, ReorderRequest)` — unchanged

- [ ] **Step 1: Rewrite ExamFeQuestionAdminService**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamFeQuestionAdminService.java`:

```java
package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminFeQuestionResponse;
import com.fuoverflow.exam.api.dto.CreateFeQuestionRequest;
import com.fuoverflow.exam.api.dto.ReorderRequest;
import com.fuoverflow.exam.api.dto.UpdateFeQuestionRequest;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExamFeQuestionAdminService {
    private final ExamFeQuestionRepository questionRepository;
    private final ExamSubjectRepository subjectRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamFeQuestionAdminService(
            ExamFeQuestionRepository questionRepository,
            ExamSubjectRepository subjectRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminFeQuestionResponse> list(UUID subjectId) {
        requireSubject(subjectId);
        return questionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId).stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminFeQuestionResponse get(UUID subjectId, UUID questionId) {
        return toAdmin(requireQuestion(subjectId, questionId));
    }

    @Transactional
    public AdminFeQuestionResponse create(UUID subjectId, CreateFeQuestionRequest request) {
        requireSubject(subjectId);
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.questionBlurUrls());

        Instant now = Instant.now();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : (int) questionRepository.countBySubjectIdAndDeletedAtIsNull(subjectId);

        UUID questionId = UUID.randomUUID();
        ExamFeQuestionEntity question = ExamFeQuestionEntity.create(
                questionId,
                subjectId,
                validated.questionText(),
                ExamJsonUtil.serialize(objectMapper, validated.imageKeys()),
                ExamJsonUtil.serialize(objectMapper, validated.blurKeys()),
                sortOrder,
                now);
        questionRepository.save(question);
        mediaService.markLinkedAll(validated.imageKeys());
        return get(subjectId, questionId);
    }

    @Transactional
    public AdminFeQuestionResponse update(UUID subjectId, UUID questionId, UpdateFeQuestionRequest request) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.questionBlurUrls());

        List<String> oldImageKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls());
        List<String> oldBlurKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls());
        cleanupReplacedImages(oldImageKeys, oldBlurKeys, validated.imageKeys(), validated.blurKeys());

        Instant now = Instant.now();
        question.setQuestionText(validated.questionText());
        question.setQuestionImageUrls(ExamJsonUtil.serialize(objectMapper, validated.imageKeys()));
        question.setQuestionBlurUrls(ExamJsonUtil.serialize(objectMapper, validated.blurKeys()));
        if (request.sortOrder() != null) {
            question.setSortOrder(request.sortOrder());
        }
        question.setUpdatedAt(now);
        questionRepository.save(question);
        mediaService.markLinkedAll(validated.imageKeys());
        return get(subjectId, questionId);
    }

    @Transactional
    public void delete(UUID subjectId, UUID questionId) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls());
        mediaService.deletePairedAll(imageKeys, blurKeys);

        Instant now = Instant.now();
        question.setDeletedAt(now);
        question.setUpdatedAt(now);
        questionRepository.save(question);
    }

    @Transactional
    public void reorder(UUID subjectId, ReorderRequest request) {
        requireSubject(subjectId);
        List<ExamFeQuestionEntity> questions =
                questionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId);
        Set<UUID> existingIds = new HashSet<>();
        questions.forEach(q -> existingIds.add(q.getId()));
        if (request.ids().size() != existingIds.size() || !existingIds.containsAll(request.ids())) {
            throw new BadRequestException("INVALID_REORDER", "Question ID list must match all questions for this subject");
        }
        Instant now = Instant.now();
        int order = 0;
        for (UUID id : request.ids()) {
            ExamFeQuestionEntity q = questions.stream().filter(x -> x.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new NotFoundException("EXAM_FE_QUESTION_NOT_FOUND", "Question not found"));
            q.setSortOrder(order++);
            q.setUpdatedAt(now);
            questionRepository.save(q);
        }
    }

    private void cleanupReplacedImages(
            List<String> oldImageKeys, List<String> oldBlurKeys,
            List<String> newImageKeys, List<String> newBlurKeys) {
        Set<String> retained = new HashSet<>(newImageKeys != null ? newImageKeys : List.of());
        if (oldImageKeys != null) {
            for (int i = 0; i < oldImageKeys.size(); i++) {
                String oldKey = oldImageKeys.get(i);
                if (oldKey != null && !retained.contains(oldKey)) {
                    String oldBlur = (oldBlurKeys != null && i < oldBlurKeys.size()) ? oldBlurKeys.get(i) : null;
                    mediaService.deletePaired(oldKey, oldBlur);
                }
            }
        }
    }

    private Validated validate(String questionText, List<String> imageUrls, List<String> blurUrls) {
        String normalizedText = blankToNull(questionText);
        List<String> normalizedImages = new ArrayList<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                String key = urlResolver.normalizeForStorage(url);
                if (key != null) {
                    normalizedImages.add(key);
                }
            }
        }
        if (normalizedText == null && normalizedImages.isEmpty()) {
            throw new BadRequestException("QUESTION_EMPTY", "Question must have text or at least one image");
        }
        List<String> normalizedBlurs = new ArrayList<>();
        if (blurUrls != null) {
            for (String url : blurUrls) {
                String key = urlResolver.normalizeForStorage(url);
                normalizedBlurs.add(key);
            }
        }
        return new Validated(normalizedText, normalizedImages, normalizedBlurs);
    }

    private AdminFeQuestionResponse toAdmin(ExamFeQuestionEntity q) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionBlurUrls());
        return new AdminFeQuestionResponse(
                q.getId(),
                q.getSubjectId(),
                q.getQuestionText(),
                urlResolver.plainAll(imageKeys),
                urlResolver.plainAll(blurKeys),
                q.getSortOrder(),
                q.getCreatedAt(),
                q.getUpdatedAt());
    }

    private void requireSubject(UUID subjectId) {
        if (subjectRepository.findByIdAndDeletedAtIsNull(subjectId).isEmpty()) {
            throw new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found");
        }
    }

    private ExamFeQuestionEntity requireQuestion(UUID subjectId, UUID questionId) {
        return questionRepository.findByIdAndSubjectIdAndDeletedAtIsNull(questionId, subjectId)
                .orElseThrow(() -> new NotFoundException("EXAM_FE_QUESTION_NOT_FOUND", "Question not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Validated(String questionText, List<String> imageKeys, List<String> blurKeys) {
    }
}
```

- [ ] **Step 2: Update ExamSubjectAdminService — rename fePreviewCount references**

In `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`:

1. In `create()`: change `request.fePreviewCount()` → `request.fePreviewImageCount()`, `properties.defaultFePreviewCountOrDefault()` → `properties.defaultFePreviewImageCountOrDefault()`, `entity.setFePreviewCount(...)` calls implied by the `ExamSubjectEntity.create()` param → already done in entity rename.
2. In `update()`: change `request.fePreviewCount()` → `request.fePreviewImageCount()`, `entity.setFePreviewCount(...)` → `entity.setFePreviewImageCount(...)`.
3. In `toAdmin()`: change `e.getFePreviewCount()` → `e.getFePreviewImageCount()`.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamFeQuestionAdminService.java \
      backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java
git commit -m "refactor(exam): rewrite admin service — remove options, add blur key management"
```

---

### Task 6: Rewrite ExamCatalogQueryService — Image Gating with Blur

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`

**Interfaces:**
- Consumes: Task 2 entities, Task 4 DTOs (`PublicFeQuestionResponse`, `PublicFeQuestionResponse.PublicImageItem`, `PublicFeQuestionListResponse`), `ExamCommentRepository.countBySubjectTypeAndSubjectIdAndDeletedAtIsNull`
- Produces:
  - `listFeQuestions(String idOrCode, UUID userId)` → `PublicFeQuestionListResponse` — all posts visible; images gated per `fePreviewImageCount`. Member: all images type "full". Non-member: first N images type "full", rest type "blur" with blur signed URLs.

- [ ] **Step 1: Rewrite ExamCatalogQueryService**

Replace `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`:

```java
package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.api.dto.PublicPeItemResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExamCatalogQueryService {
    private final ExamSubjectRepository subjectRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamCommentRepository commentRepository;
    private final ExamAccessGuard accessGuard;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamCatalogQueryService(
            ExamSubjectRepository subjectRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamCommentRepository commentRepository,
            ExamAccessGuard accessGuard,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.commentRepository = commentRepository;
        this.accessGuard = accessGuard;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PublicSubjectCardResponse> listActive() {
        return subjectRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(this::toCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicSubjectDetailResponse getDetail(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        boolean member = accessGuard.hasActiveMembership(userId);
        return new PublicSubjectDetailResponse(
                subject.getId(),
                subject.getCode(),
                subject.getTitle(),
                subject.getDescription(),
                subject.getCategorySlug(),
                subject.getCardColor(),
                urlResolver.signed(subject.getCoverImageUrl()),
                subject.getViewCount(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(subject.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(subject.getId()),
                subject.getFePreviewImageCount(),
                member);
    }

    @Transactional(readOnly = true)
    public PublicFeQuestionListResponse listFeQuestions(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        boolean member = accessGuard.hasActiveMembership(userId);
        List<ExamFeQuestionEntity> all =
                feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subject.getId());
        int total = all.size();
        int previewImageCount = Math.max(0, subject.getFePreviewImageCount());

        List<PublicFeQuestionResponse> questions = all.stream()
                .map(q -> toPublicQuestion(q, member, previewImageCount))
                .toList();
        return new PublicFeQuestionListResponse(!member, total, previewImageCount, questions);
    }

    @Transactional(readOnly = true)
    public List<PublicPeItemResponse> listPeItems(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        accessGuard.requireActiveMembership(userId);
        return peItemRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subject.getId()).stream()
                .map(this::toPublicPeItem)
                .toList();
    }

    private PublicSubjectCardResponse toCard(ExamSubjectEntity s) {
        return new PublicSubjectCardResponse(
                s.getId(),
                s.getCode(),
                s.getTitle(),
                s.getCategorySlug(),
                s.getCardColor(),
                urlResolver.signed(s.getCoverImageUrl()),
                s.getViewCount(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(s.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(s.getId()));
    }

    private PublicFeQuestionResponse toPublicQuestion(
            ExamFeQuestionEntity q, boolean isMember, int previewImageCount) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionBlurUrls());
        int totalImages = imageKeys.size();

        List<PublicFeQuestionResponse.PublicImageItem> images = new ArrayList<>();
        for (int i = 0; i < totalImages; i++) {
            if (isMember || i < previewImageCount) {
                images.add(new PublicFeQuestionResponse.PublicImageItem(
                        i, urlResolver.signed(imageKeys.get(i)), "full"));
            } else {
                String blurKey = (i < blurKeys.size()) ? blurKeys.get(i) : null;
                String blurUrl = (blurKey != null) ? urlResolver.signed(blurKey) : null;
                images.add(new PublicFeQuestionResponse.PublicImageItem(i, blurUrl, "blur"));
            }
        }

        int commentCount = (int) commentRepository.countBySubjectTypeAndSubjectIdAndDeletedAtIsNull(
                "fe_question", q.getId());

        return new PublicFeQuestionResponse(
                q.getId(),
                q.getQuestionText(),
                totalImages,
                images,
                q.getSortOrder(),
                commentCount,
                q.getCreatedAt());
    }

    private PublicPeItemResponse toPublicPeItem(ExamPeItemEntity item) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls());
        List<PublicPeItemResponse.PublicPeResourceResponse> resources =
                peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).stream()
                        .map(this::toPublicResource)
                        .toList();
        return new PublicPeItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                urlResolver.signedAll(imageKeys),
                item.getSortOrder(),
                resources);
    }

    private PublicPeItemResponse.PublicPeResourceResponse toPublicResource(ExamPeResourceEntity r) {
        return new PublicPeItemResponse.PublicPeResourceResponse(
                r.getId(),
                r.getFolderLabel(),
                r.getOriginalFilename(),
                r.getMimeType(),
                r.getSizeBytes(),
                r.getSortOrder(),
                "/api/v1/exam/pe/resources/" + r.getId() + "/download");
    }

    ExamSubjectEntity resolveActive(String idOrCode) {
        return tryParseUuid(idOrCode)
                .flatMap(subjectRepository::findByIdAndDeletedAtIsNull)
                .or(() -> subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(idOrCode.trim()))
                .filter(ExamSubjectEntity::isActive)
                .orElseThrow(() -> new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found"));
    }

    private static Optional<UUID> tryParseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java
git commit -m "feat(exam): rewrite catalog query — image gating with blur for non-members"
```

---

### Task 7: Update ExamAdminController and ExamMediaController — Blur Upload + Rate Limit

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamAdminController.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamMediaController.java`

**Interfaces:**
- Consumes: Task 3 `ExamMediaService.uploadWithBlur`, `ExamMediaService.BlurUploadResult`; Task 4 `MediaUploadResponse(objectKey, blurObjectKey, publicUrl)`
- Produces:
  - Admin upload endpoint returns `MediaUploadResponse` with `blurObjectKey` for FE images
  - Media serve endpoint has rate limiting (60 req/min/IP) via simple in-memory counter
  - `ExamMediaController` removes `ExamFeOptionRepository` dependency

- [ ] **Step 1: Update ExamAdminController — upload with blur for FE images**

In `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamAdminController.java`, update the `uploadMedia` method:

```java
@PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@RequirePermission("exam.media.admin:create")
public ApiResponse<MediaUploadResponse> uploadMedia(
        Authentication authentication,
        @RequestParam("purpose") String purpose,
        @RequestPart("file") MultipartFile file) {
    UUID adminUserId = UUID.fromString(authentication.getName());
    UploadPurpose uploadPurpose = resolvePurpose(purpose);
    if (uploadPurpose == UploadPurpose.EXAM_FE_IMAGE) {
        ExamMediaService.BlurUploadResult result = mediaService.uploadWithBlur(file, uploadPurpose, adminUserId);
        return ApiResponse.ok(new MediaUploadResponse(result.objectKey(), result.blurObjectKey(), result.publicUrl()));
    }
    StoredObject stored = mediaService.upload(file, uploadPurpose, adminUserId);
    return ApiResponse.ok(new MediaUploadResponse(stored.objectKey(), null, stored.publicUrl()));
}
```

- [ ] **Step 2: Update ExamMediaController — remove ExamFeOptionRepository, add rate limiting**

In `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamMediaController.java`:

1. Remove `ExamFeOptionRepository` from constructor and field.
2. In `requireImageIsExamOwned`: remove the `feOptionRepository.findSubjectIdByOptionImageUrl(objectKey).isPresent()` check.
3. Add a simple IP-based rate limiter using `ConcurrentHashMap`:

```java
private final ConcurrentHashMap<String, long[]> rateLimitMap = new ConcurrentHashMap<>();
private static final int MAX_REQUESTS_PER_MINUTE = 60;

private void checkRateLimit(HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    long now = System.currentTimeMillis();
    long windowStart = now - 60_000;
    long[] timestamps = rateLimitMap.compute(ip, (k, existing) -> {
        if (existing == null) return new long[]{now, 1};
        if (existing[0] < windowStart) return new long[]{now, 1};
        existing[1]++;
        return existing;
    });
    if (timestamps[1] > MAX_REQUESTS_PER_MINUTE) {
        throw new com.fuoverflow.common.exception.TooManyRequestsException(
                "RATE_LIMIT_EXCEEDED", "Too many requests. Try again later.");
    }
}
```

Add `HttpServletRequest request` parameter to `serveMedia` and call `checkRateLimit(request)` at the start.

Note: If `TooManyRequestsException` doesn't exist in the common module, create a simple one extending `RuntimeException` with `@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)`. Check the existing exception classes in `backend/common/src/main/java/com/fuoverflow/common/exception/` first and follow the pattern.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/ExamAdminController.java \
      backend/exam/src/main/java/com/fuoverflow/exam/api/ExamMediaController.java
git commit -m "feat(exam): blur upload for FE images, rate limit on media endpoint"
```

---

### Task 8: Update Tests

**Files:**
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCommentServiceTest.java`
- Delete or update any test that references `ExamFeOptionRepository` or MCQ fields

**Interfaces:**
- Consumes: All prior task changes
- Produces: Passing test suite

- [ ] **Step 1: Rewrite ExamCatalogQueryServiceTest**

Replace `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`:

```java
package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCatalogQueryServiceTest {
    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ExamCommentRepository commentRepository;
    @Mock private ExamAccessGuard accessGuard;
    @Mock private ExamMediaUrlResolver urlResolver;

    private ExamCatalogQueryService service;
    private UUID userId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        service = new ExamCatalogQueryService(
                subjectRepository, feQuestionRepository,
                peItemRepository, peResourceRepository, commentRepository,
                accessGuard, urlResolver, new ObjectMapper());
        userId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        lenient().when(urlResolver.signed(any())).thenAnswer(inv -> "signed:" + inv.getArgument(0));
        lenient().when(urlResolver.signedAll(any())).thenReturn(List.of());
        lenient().when(commentRepository.countBySubjectTypeAndSubjectIdAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(0L);
    }

    @Test
    void nonMember_getsAllPosts_withBlurredImages() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(3, 5));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(3, res.totalCount());
        assertEquals(2, res.previewImageCount());
        assertEquals(3, res.questions().size());

        PublicFeQuestionResponse q = res.questions().get(0);
        assertEquals(5, q.totalImageCount());
        assertEquals(5, q.images().size());
        assertEquals("full", q.images().get(0).type());
        assertEquals("full", q.images().get(1).type());
        assertEquals("blur", q.images().get(2).type());
        assertEquals("blur", q.images().get(3).type());
        assertEquals("blur", q.images().get(4).type());
    }

    @Test
    void member_getsAllFullImages() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(true);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(2, 4));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertFalse(res.locked());
        assertEquals(2, res.questions().size());
        for (PublicFeQuestionResponse q : res.questions()) {
            assertTrue(q.images().stream().allMatch(img -> "full".equals(img.type())));
        }
    }

    @Test
    void nonMember_fewImagesThanPreview_allFull() {
        ExamSubjectEntity subject = subject(5);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(1, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        PublicFeQuestionResponse q = res.questions().get(0);
        assertTrue(q.images().stream().allMatch(img -> "full".equals(img.type())));
    }

    @Test
    void listPeItems_requiresMembership() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        org.mockito.Mockito.doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);

        assertThrows(ForbiddenException.class, () -> service.listPeItems("MLN111", userId));
    }

    private ExamSubjectEntity subject(int previewImageCount) {
        return ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null,
                previewImageCount, true, 0, Instant.now());
    }

    private List<ExamFeQuestionEntity> questionsWithImages(int n, int imagesPerQuestion) {
        List<ExamFeQuestionEntity> list = new ArrayList<>();
        ObjectMapper om = new ObjectMapper();
        Instant now = Instant.now();
        for (int i = 0; i < n; i++) {
            List<String> imageKeys = new ArrayList<>();
            List<String> blurKeys = new ArrayList<>();
            for (int j = 0; j < imagesPerQuestion; j++) {
                imageKeys.add("exam/fe/img" + i + "_" + j + ".png");
                blurKeys.add("exam/fe/img" + i + "_" + j + "-blur.jpg");
            }
            String imageJson = ExamJsonUtil.serialize(om, imageKeys);
            String blurJson = ExamJsonUtil.serialize(om, blurKeys);
            list.add(ExamFeQuestionEntity.create(
                    UUID.randomUUID(), subjectId, "Q" + i, imageJson, blurJson, i, now));
        }
        return list;
    }
}
```

- [ ] **Step 2: Update ExamCommentServiceTest if it references ExamFeOptionRepository**

Check if `ExamCommentServiceTest` imports or references `ExamFeOptionRepository`. If not, no changes needed. If it does, remove those references.

- [ ] **Step 3: Run all exam tests**

Run: `cd backend && mvn -q test -pl exam -am 2>&1 | tail -20`

Expected: All tests pass.

- [ ] **Step 4: Compile and verify the full backend**

Run: `cd backend && mvn -q -DskipTests package 2>&1 | tail -10`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A backend/exam/src/test/
git commit -m "test(exam): update tests for FE questions redesign"
```

---

### Task 9: Frontend — Update API Types and Admin UI

**Files:**
- Modify: `Fuexam-admin/lib/api/exam.ts` — remove option types, rename fePreviewCount, update upload response
- Modify: `Fuexam-admin/app/exam/subjects/page.tsx` — add fePreviewImageCount input
- Modify: `Fuexam/lib/api/exam.ts` — add image item types for public API

**Interfaces:**
- Consumes: Task 4 backend DTOs
- Produces:
  - Admin TS types: `AdminFeQuestion` without options/explanation/multipleCorrect, with `questionBlurUrls`; `AdminSubject.fePreviewImageCount`; `ExamMediaUploadResult` with `blurObjectKey`
  - Admin subjects page: input for `fePreviewImageCount` (number, 1-10)
  - Public TS types: `PublicImageItem`, `PublicFeQuestion` with image items

- [ ] **Step 1: Update Fuexam-admin/lib/api/exam.ts**

Remove: `AdminFeOption`, `FeOptionBody` interfaces. Remove `options` from `AdminFeQuestion` and `FeQuestionBody`. Rename `fePreviewCount` → `fePreviewImageCount` in `AdminSubject` and `AdminSubjectBody`. Add `blurObjectKey` to `ExamMediaUploadResult`. Remove `explanation` and `multipleCorrect` from `AdminFeQuestion`. Add `questionBlurUrls` to `AdminFeQuestion` and `FeQuestionBody`.

- [ ] **Step 2: Update Fuexam-admin/app/exam/subjects/page.tsx**

Add an input for `fePreviewImageCount` in the subject create/edit form:

```tsx
<div className="space-y-1.5">
  <label className="text-xs font-semibold text-muted-foreground">
    Số ảnh xem trước (free user)
  </label>
  <input
    type="number"
    min={1}
    max={10}
    value={form.fePreviewImageCount ?? 2}
    onChange={(e) => setForm({ ...form, fePreviewImageCount: parseInt(e.target.value) || 2 })}
    className="w-full rounded-md border px-3 py-2 text-sm"
  />
</div>
```

Also fix the table column that currently reads `item.fePaperCount` (which is undefined) to `item.feQuestionCount`.

- [ ] **Step 3: Update Fuexam/lib/api/exam.ts**

Add new types for public FE question API:

```typescript
export interface PublicImageItem {
  index: number;
  url: string | null;
  type: "full" | "blur";
}

export interface PublicFeQuestion {
  id: string;
  questionText: string | null;
  totalImageCount: number;
  images: PublicImageItem[];
  sortOrder: number;
  commentCount: number;
  createdAt: string;
}

export interface PublicFeQuestionList {
  locked: boolean;
  totalCount: number;
  previewImageCount: number;
  questions: PublicFeQuestion[];
}
```

Add API function:

```typescript
export function listFeQuestions(idOrCode: string) {
  return apiFetch<PublicFeQuestionList>(
    `${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}/fe`,
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add Fuexam-admin/lib/api/exam.ts Fuexam-admin/app/exam/subjects/page.tsx Fuexam/lib/api/exam.ts
git commit -m "feat(frontend): update exam API types and admin UI for FE questions redesign"
```

---

### Task 10: Frontend — Public Exam Page Blur/Lock UI

**Files:**
- Modify: `Fuexam/app/(app)/exam/[code]/page.tsx` — render FE questions as image posts with blur gating

**Interfaces:**
- Consumes: Task 9 types (`PublicFeQuestion`, `PublicImageItem`, `PublicFeQuestionList`, `listFeQuestions`)
- Produces: Updated `/exam/[code]` page with:
  - FE post cards showing image grids with full/blur rendering
  - Blur images: blurred thumbnail + lock overlay + "Mua membership" CTA
  - Lightbox navigates only through `type: "full"` images
  - Comment count badge per post
  - Collapsible `ExamCommentThread` per post

- [ ] **Step 1: Add the FE questions section to the exam detail page**

In `Fuexam/app/(app)/exam/[code]/page.tsx`:

1. Import `listFeQuestions` and the new types from `@/lib/api/exam`.
2. Add state for FE questions: `const [feData, setFeData] = useState<PublicFeQuestionList | null>(null)`.
3. Fetch FE questions when subject loads: `listFeQuestions(code).then(setFeData)`.
4. Create a `FePostCard` component that renders each question:
   - Title (if `questionText` present)
   - Image grid using the `images` array:
     - `type: "full"`: `<ImageWithWatermark>` clickable → Lightbox
     - `type: "blur"`: `<img>` with blur URL + dark overlay + Lock icon + "Mua membership để xem" text. Click → redirect to `/membership`
   - Comment count badge: `{q.commentCount} bình luận`
   - Collapsible `ExamCommentThread` with `paperId={q.id}` (reuse existing component — the comment API uses `fe_question` subject type)

5. Create a `FePostImages` component similar to existing `PaperImages` but:
   - Filter only `type: "full"` images for the Lightbox navigation
   - Render blur images as locked placeholders in the grid

- [ ] **Step 2: Update Lightbox usage to only navigate full images**

When opening the Lightbox from `FePostImages`, pass only the full-type image URLs:

```tsx
const fullImages = images.filter(img => img.type === "full");
// Lightbox images prop = fullImages.map(img => img.url)
```

Non-member clicking a blur image shows a toast or redirect to `/membership` instead of opening the Lightbox.

- [ ] **Step 3: Test in browser**

Run the dev server: `cd Fuexam && npm run dev`

Test scenarios:
- Logged out / non-member: see all FE posts, first N images clear, rest blurred with lock overlay
- Member: see all images full, Lightbox navigates through all
- Click blur image → redirect to membership page
- Comments work on each post
- Lightbox comment side panel works per image

- [ ] **Step 4: Commit**

```bash
git add Fuexam/app/\(app\)/exam/\[code\]/page.tsx
git commit -m "feat(frontend): blur gating UI for FE question images"
```

---

### Task 11: Final Verification

**Files:** None created — this is a verification task.

- [ ] **Step 1: Run full backend test suite**

Run: `cd backend && mvn -q test 2>&1 | tail -20`

Expected: All tests pass, BUILD SUCCESS.

- [ ] **Step 2: Run full backend build**

Run: `cd backend && mvn -q -DskipTests package 2>&1 | tail -10`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Start the application locally (if DB available)**

Run: `cd backend && docker compose up -d postgres redis && mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local`

Verify:
- Migration V45 applies without errors
- `GET /actuator/health` returns UP
- Admin upload with `purpose=exam_fe_image` returns `blurObjectKey`
- Public FE questions API returns `images` with `type: "full"` and `type: "blur"`
- Media rate limiting returns 429 after 60 rapid requests

- [ ] **Step 4: Commit all remaining changes**

```bash
git add -A
git commit -m "feat(exam): FE questions redesign — image posts with blur gating"
```
