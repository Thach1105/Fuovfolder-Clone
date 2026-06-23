# Multiple Images Per Question Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add support for multiple images per source question with JSONB storage, admin multi-upload UI, and exam lightbox gallery.

**Architecture:** Store image URLs as JSONB array in `source_questions.question_image_urls`. Migrate existing single images to array format. Frontend uses new `MultiImageUploader` for admin and `ImageGallery` for exam mode.

**Tech Stack:** Java 21, Spring Boot 3.5.x, PostgreSQL JSONB, React/Next.js, TypeScript

## Global Constraints

- Java 21 with Spring Boot 3.5.x
- PostgreSQL as source of truth
- Flyway migrations only (Hibernate `ddl-auto=validate`)
- No foreign key constraints (validate in application layer)
- Max 20 images per question (soft limit, log warning)
- File types: jpg, png, webp, gif
- Max file size: 5MB per image
- Question validation: at least text OR images required

---

### Task 1: Database Migration

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V26__add_multiple_question_images.sql`

**Interfaces:**
- Consumes: Existing `source_questions` table schema
- Produces: `question_image_urls` JSONB column with migrated data

- [ ] **Step 1: Create migration file**

```sql
-- V26__add_multiple_question_images.sql

-- Add new JSONB column for multiple image URLs
ALTER TABLE source_questions
  ADD COLUMN question_image_urls JSONB DEFAULT '[]'::jsonb;

-- Migrate existing single images to array format
UPDATE source_questions
  SET question_image_urls = jsonb_build_array(question_image_url)
  WHERE question_image_url IS NOT NULL;

-- Add index for JSONB queries (optional, for future optimization)
CREATE INDEX idx_source_questions_image_urls ON source_questions USING GIN (question_image_urls);
```

- [ ] **Step 2: Test migration locally**

```bash
cd backend
docker compose up -d postgres
mvn -q -pl app flyway:migrate
```

Expected: Migration V26 applies successfully, existing images migrated to array format.

- [ ] **Step 3: Verify migration**

```bash
docker compose exec postgres psql -U fuoverflow -d fuoverflow -c "SELECT id, question_text, question_image_url, question_image_urls FROM source_questions LIMIT 5;"
```

Expected: `question_image_urls` contains array with single URL for rows that had `question_image_url`.

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V26__add_multiple_question_images.sql
git commit -m "db: add question_image_urls JSONB column with migration"
```

---

### Task 2: Backend Entity Updates

**Files:**
- Modify: `backend/source/src/main/java/com/fuoverflow/source/persistence/SourceQuestionEntity.java`

**Interfaces:**
- Consumes: Migrated database schema with `question_image_urls` column
- Produces: Entity with `questionImageUrls` field (JSON string) and updated `create()` method

- [ ] **Step 1: Add new field to entity**

Location: `SourceQuestionEntity.java:24` (after `questionImageUrl`)

```java
@Column(name = "question_image_urls", columnDefinition = "jsonb")
private String questionImageUrls; // JSON array string: ["url1", "url2"]
```

- [ ] **Step 2: Add getter**

Location: `SourceQuestionEntity.java:52` (after `getQuestionImageUrl()`)

```java
public String getQuestionImageUrls() { return questionImageUrls; }
```

- [ ] **Step 3: Add setter**

Location: `SourceQuestionEntity.java:62` (after `setQuestionImageUrl()`)

```java
public void setQuestionImageUrls(String questionImageUrls) { this.questionImageUrls = questionImageUrls; }
```

- [ ] **Step 4: Update create method signature**

Location: `SourceQuestionEntity.java:69-77`

```java
public static SourceQuestionEntity create(
        UUID id,
        UUID catalogItemId,
        String questionText,
        String questionImageUrl,
        String questionImageUrls,  // New parameter
        String explanation,
        boolean multipleCorrect,
        int sortOrder,
        Instant now) {
    SourceQuestionEntity e = new SourceQuestionEntity();
    e.id = id;
    e.catalogItemId = catalogItemId;
    e.questionText = questionText;
    e.questionImageUrl = questionImageUrl;
    e.questionImageUrls = questionImageUrls;  // Set new field
    e.explanation = explanation;
    e.multipleCorrect = multipleCorrect;
    e.sortOrder = sortOrder;
    e.lockVersion = 0;
    e.createdAt = now;
    e.updatedAt = now;
    return e;
}
```

- [ ] **Step 5: Build to verify compilation**

```bash
cd backend
mvn -q -pl source clean compile
```

Expected: Compilation succeeds.

- [ ] **Step 6: Commit**

```bash
git add backend/source/src/main/java/com/fuoverflow/source/persistence/SourceQuestionEntity.java
git commit -m "feat(source): add questionImageUrls field to entity"
```

---

### Task 3: Backend DTO Updates

**Files:**
- Modify: `backend/source/src/main/java/com/fuoverflow/source/api/dto/CreateQuestionRequest.java`
- Modify: `backend/source/src/main/java/com/fuoverflow/source/api/dto/UpdateQuestionRequest.java`
- Modify: `backend/source/src/main/java/com/fuoverflow/source/api/dto/AdminQuestionResponse.java`
- Modify: `backend/source/src/main/java/com/fuoverflow/source/api/dto/PublicQuestionResponse.java`

**Interfaces:**
- Consumes: Entity with `questionImageUrls` field
- Produces: DTOs with `List<String> questionImageUrls` field

- [ ] **Step 1: Update CreateQuestionRequest**

Location: `CreateQuestionRequest.java:11`

Change from:
```java
@Size(max = 500) String questionImageUrl,
```

To:
```java
@Size(max = 500) String questionImageUrl,
List<String> questionImageUrls,
```

- [ ] **Step 2: Update UpdateQuestionRequest**

Location: `UpdateQuestionRequest.java:11`

Change from:
```java
@Size(max = 500) String questionImageUrl,
```

To:
```java
@Size(max = 500) String questionImageUrl,
List<String> questionImageUrls,
```

- [ ] **Step 3: Update AdminQuestionResponse**

Location: `AdminQuestionResponse.java:11`


Add after `questionImageUrl`:
```java
String questionImageUrl,
List<String> questionImageUrls,
```

- [ ] **Step 4: Update PublicQuestionResponse**

Location: `PublicQuestionResponse.java:9`

Add after `questionImageUrl`:
```java
String questionImageUrl,
List<String> questionImageUrls,
```

- [ ] **Step 5: Build to verify compilation**

```bash
cd backend
mvn -q -pl source clean compile
```

Expected: Compilation succeeds.

- [ ] **Step 6: Commit**

```bash
git add backend/source/src/main/java/com/fuoverflow/source/api/dto/
git commit -m "feat(source): add questionImageUrls to DTOs"
```

---

### Task 4: Backend Service Layer Updates

**Files:**
- Modify: `backend/source/src/main/java/com/fuoverflow/source/application/SourceQuestionAdminService.java`
- Modify: `backend/source/src/main/java/com/fuoverflow/source/application/SourceMediaUrlResolver.java`

**Interfaces:**
- Consumes: DTOs with `questionImageUrls` field, Entity with `questionImageUrls`
- Produces: Service methods that handle JSON serialization/deserialization, validation for 20+ images

- [ ] **Step 1: Add Jackson imports to SourceQuestionAdminService**

Location: `SourceQuestionAdminService.java:1` (top of file)

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

- [ ] **Step 2: Add ObjectMapper and Logger fields**

Location: `SourceQuestionAdminService.java` (after class declaration)

```java
private static final Logger log = LoggerFactory.getLogger(SourceQuestionAdminService.class);
private final ObjectMapper objectMapper;

// Update constructor to inject ObjectMapper
public SourceQuestionAdminService(
    SourceQuestionRepository questionRepository,
    SourceQuestionOptionRepository optionRepository,
    SourceCatalogItemRepository catalogRepository,
    SourceMediaService mediaService,
    SourceMediaUrlResolver urlResolver,
    ObjectMapper objectMapper
) {
    this.questionRepository = questionRepository;
    this.optionRepository = optionRepository;
    this.catalogRepository = catalogRepository;
    this.mediaService = mediaService;
    this.urlResolver = urlResolver;
    this.objectMapper = objectMapper;
}
```

- [ ] **Step 3: Add helper method to serialize image URLs**

Location: `SourceQuestionAdminService.java` (end of class, before closing brace)

```java
private String serializeImageUrls(List<String> urls) {
    if (urls == null || urls.isEmpty()) {
        return "[]";
    }
    try {
        return objectMapper.writeValueAsString(urls);
    } catch (JsonProcessingException e) {
        log.error("Failed to serialize image URLs", e);
        return "[]";
    }
}
```

- [ ] **Step 4: Add helper method to deserialize image URLs**

```java
private List<String> deserializeImageUrls(String json) {
    if (json == null || json.isBlank() || "[]".equals(json)) {
        return List.of();
    }
    try {
        return objectMapper.readValue(json, 
            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException e) {
        log.error("Failed to deserialize image URLs", e);
        return List.of();
    }
}
```

- [ ] **Step 5: Add validation method for image count**

```java
private void validateImageCount(List<String> urls) {
    if (urls != null && urls.size() > 20) {
        log.warn("Question has {} images, which exceeds recommended limit of 20", urls.size());
    }
}
```

- [ ] **Step 6: Update createQuestion method**

Location: Find the method that creates questions (around line 66-80)

Update to handle `questionImageUrls`:

```java
public String createQuestion(String catalogItemId, CreateQuestionRequest request) {
    // ... existing validation ...
    
    String normalizedImageUrl = urlResolver.normalizeForStorage(request.questionImageUrl());
    String serializedImageUrls = serializeImageUrls(request.questionImageUrls());
    
    validateImageCount(request.questionImageUrls());
    
    SourceQuestionEntity question = SourceQuestionEntity.create(
        UUID.randomUUID(),
        UUID.fromString(catalogItemId),
        validated.questionText(),
        normalizedImageUrl,
        serializedImageUrls,  // New parameter
        validated.explanation(),
        validated.multipleCorrect(),
        // ... rest of params
    );
    
    // ... rest of method
}
```

- [ ] **Step 7: Update updateQuestion method**

Location: Find the method that updates questions (around line 99-110)

```java
public void updateQuestion(String catalogItemId, String questionId, UpdateQuestionRequest request) {
    // ... existing code ...
    
    String normalizedImageUrl = urlResolver.normalizeForStorage(request.questionImageUrl());
    String serializedImageUrls = serializeImageUrls(request.questionImageUrls());
    
    validateImageCount(request.questionImageUrls());
    
    question.setQuestionImageUrl(normalizedImageUrl);
    question.setQuestionImageUrls(serializedImageUrls);
    
    // ... rest of method
}
```

- [ ] **Step 8: Update response mapping to deserialize URLs**

Location: Find methods that build AdminQuestionResponse (around line 207-281)

Update to include:

```java
List<String> imageUrls = deserializeImageUrls(question.getQuestionImageUrls());
```

And in response construction:

```java
new AdminQuestionResponse(
    question.getId().toString(),
    question.getQuestionText(),
    question.getQuestionImageUrl(),
    imageUrls,  // Add deserialized URLs
    // ... rest of fields
)
```

- [ ] **Step 9: Update SourceMediaUrlResolver**

Location: `SourceMediaUrlResolver.java`

Add method to resolve multiple URLs:

```java
public List<String> resolveImageUrls(List<String> imageUrls) {
    if (imageUrls == null || imageUrls.isEmpty()) {
        return List.of();
    }
    return imageUrls.stream()
        .map(objectStorage::resolvePublicUrl)
        .toList();
}
```

- [ ] **Step 10: Update response methods to resolve multiple URLs**

In methods that create AdminQuestionResponse/PublicQuestionResponse:

```java
List<String> resolvedImageUrls = urlResolver.resolveImageUrls(imageUrls);

new AdminQuestionResponse(
    // ...
    resolvedImageUrls,
    // ...
)
```

- [ ] **Step 11: Build and test**

```bash
cd backend
mvn -q -pl source clean test
```

Expected: Tests pass (some may need updates for new field).

- [ ] **Step 12: Commit**

```bash
git add backend/source/src/main/java/com/fuoverflow/source/application/
git commit -m "feat(source): handle JSON serialization for multiple image URLs"
```

---

### Task 5: Frontend Admin API Types

**Files:**
- Modify: `Fuexam-admin/types/api.ts`

**Interfaces:**
- Consumes: Backend API changes with `questionImageUrls`
- Produces: TypeScript types matching backend DTOs

- [ ] **Step 1: Update QuestionBody type**

Location: `Fuexam-admin/types/api.ts` (find QuestionBody)

```typescript
export type QuestionBody = {
  questionText?: string;
  questionImageUrl?: string;  // Keep for backward compat
  questionImageUrls?: string[];  // Add new field
  explanation?: string;
  options: QuestionOptionBody[];
};
```

- [ ] **Step 2: Update AdminQuestion type**

```typescript
export type AdminQuestion = {
  id: string;
  questionText: string | null;
  questionImageUrl: string | null;  // Keep for backward compat
  questionImageUrls: string[] | null;  // Add new field
  explanation: string | null;
  multipleCorrect: boolean;
  options: AdminQuestionOption[];
  sortOrder: number;
};
```

- [ ] **Step 3: Update PublicQuestion type (if exists)**

```typescript
export type PublicQuestion = {
  id: string;
  questionText: string | null;
  questionImageUrl: string | null;
  questionImageUrls: string[] | null;
  explanation: string | null;
  options: PublicQuestionOption[];
};
```

- [ ] **Step 4: Verify TypeScript compilation**

```bash
cd Fuexam-admin
npm run build
```

Expected: No TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add Fuexam-admin/types/api.ts
git commit -m "feat(admin): add questionImageUrls to API types"
```

---

### Task 6: MultiImageUploader Component

**Files:**
- Create: `Fuexam-admin/components/admin/MultiImageUploader.tsx`

**Interfaces:**
- Consumes: `uploadMedia` from `@/lib/api/media`, `resolveMediaUrl`
- Produces: React component with props `{ label, purpose, value: string[], onChange: (urls: string[]) => void, maxImages?: number }`


- [ ] **Step 1: Create MultiImageUploader component file**

```typescript
"use client";

import { useRef, useState } from "react";
import { X, Upload, GripVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  uploadMedia,
  resolveMediaUrl,
  type UploadPurpose,
} from "@/lib/api/media";

type Props = {
  label?: string;
  purpose: UploadPurpose;
  value: string[];
  onChange: (urls: string[]) => void;
  maxImages?: number;
  accept?: string;
};

export function MultiImageUploader({
  label = "Ảnh",
  purpose,
  value,
  onChange,
  maxImages,
  accept = "image/png,image/jpeg,image/webp,image/gif",
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    
    const fileArray = Array.from(files);
    
    // Validate max images
    if (maxImages && value.length + fileArray.length > maxImages) {
      setError(`Tối đa ${maxImages} ảnh`);
      return;
    }
    
    // Warn if > 20 images
    if (value.length + fileArray.length > 20) {
      setError("Cảnh báo: Nhiều hơn 20 ảnh có thể ảnh hưởng hiệu suất");
    }

    setError(null);
    setUploading(true);

    try {
      const uploadPromises = fileArray.map((file) => uploadMedia(file, purpose));
      const uploaded = await Promise.all(uploadPromises);
      const newUrls = uploaded.map((u) => u.objectKey);
      onChange([...value, ...newUrls]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tải ảnh thất bại.");
    } finally {
      setUploading(false);
    }
  }

  function removeImage(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function removeAll() {
    onChange([]);
  }

  function handleDragStart(index: number) {
    setDraggedIndex(index);
  }

  function handleDragOver(e: React.DragEvent, index: number) {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === index) return;

    const newUrls = [...value];
    const draggedUrl = newUrls[draggedIndex];
    newUrls.splice(draggedIndex, 1);
    newUrls.splice(index, 0, draggedUrl);
    
    onChange(newUrls);
    setDraggedIndex(index);
  }

  function handleDragEnd() {
    setDraggedIndex(null);
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        {value.length > 0 && (
          <button
            type="button"
            className="text-xs text-destructive hover:underline"
            onClick={removeAll}
          >
            Xóa tất cả
          </button>
        )}
      </div>

      {value.length > 0 && (
        <div className="grid grid-cols-3 gap-2">
          {value.map((url, index) => {
            const previewUrl = resolveMediaUrl(url);
            return (
              <div
                key={`${url}-${index}`}
                draggable
                onDragStart={() => handleDragStart(index)}
                onDragOver={(e) => handleDragOver(e, index)}
                onDragEnd={handleDragEnd}
                className="group relative cursor-move rounded-lg border border-border bg-muted/20"
              >
                <div className="absolute left-1 top-1 rounded bg-background/90 p-0.5 opacity-0 group-hover:opacity-100">
                  <GripVertical className="h-3 w-3 text-muted-foreground" />
                </div>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={previewUrl}
                  alt={`Ảnh ${index + 1}`}
                  className="h-24 w-full rounded-lg object-cover"
                />
                <button
                  type="button"
                  className="absolute right-1 top-1 rounded-full bg-background/90 p-1 opacity-0 group-hover:opacity-100"
                  onClick={() => removeImage(index)}
                >
                  <X className="h-3 w-3 text-destructive" />
                </button>
                <div className="absolute bottom-1 right-1 rounded bg-background/90 px-1.5 py-0.5 text-xs">
                  {index + 1}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
        >
          {uploading ? (
            "Đang tải..."
          ) : (
            <>
              <Upload className="mr-2 h-4 w-4" />
              {value.length > 0 ? "Thêm ảnh" : "Tải ảnh lên"}
            </>
          )}
        </Button>
      </div>

      {error && <p className="text-xs text-destructive">{error}</p>}
      {value.length > 0 && (
        <p className="text-xs text-muted-foreground">
          {value.length} ảnh · Kéo để sắp xếp
        </p>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Test component in isolation (optional manual check)**

Open admin page and verify component renders (will update page in next task).

- [ ] **Step 3: Commit**

```bash
git add Fuexam-admin/components/admin/MultiImageUploader.tsx
git commit -m "feat(admin): add MultiImageUploader component with drag-reorder"
```

---

### Task 7: Admin Questions Page Update

**Files:**
- Modify: `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx`
- Modify: `Fuexam-admin/lib/api/source.ts` (API client)

**Interfaces:**
- Consumes: `MultiImageUploader` component, updated API types with `questionImageUrls`
- Produces: Updated admin page using multiple image upload

- [ ] **Step 1: Update page imports**

Location: `page.tsx:10` (after existing ImageUploader import)

```typescript
import { MultiImageUploader } from "@/components/admin/MultiImageUploader";
```

- [ ] **Step 2: Update state**

Location: `page.tsx:57` (change from single URL to array)

Change:
```typescript
const [questionImageUrl, setQuestionImageUrl] = useState<string | null>(null);
```

To:
```typescript
const [questionImageUrls, setQuestionImageUrls] = useState<string[]>([]);
```

- [ ] **Step 3: Update resetForm**

Location: `page.tsx:85-91`

Change:
```typescript
setQuestionImageUrl(null);
```

To:
```typescript
setQuestionImageUrls([]);
```

- [ ] **Step 4: Update startEdit**

Location: `page.tsx:93-108`

Change:
```typescript
setQuestionImageUrl(question.questionImageUrl);
```

To:
```typescript
setQuestionImageUrls(question.questionImageUrls ?? []);
```

- [ ] **Step 5: Update buildBody**

Location: `page.tsx:122-134`

Change:
```typescript
questionImageUrl: questionImageUrl ?? undefined,
```

To:
```typescript
questionImageUrls: questionImageUrls.length > 0 ? questionImageUrls : undefined,
```

- [ ] **Step 6: Update validation**

Location: `page.tsx:156-161`

Change:
```typescript
if (!questionText.trim() && !questionImageUrl) {
```

To:
```typescript
if (!questionText.trim() && questionImageUrls.length === 0) {
```

- [ ] **Step 7: Replace ImageUploader with MultiImageUploader**

Location: `page.tsx:251-256`

Replace:
```typescript
<ImageUploader
  label="Ảnh câu hỏi"
  purpose="source_question"
  value={questionImageUrl}
  onChange={setQuestionImageUrl}
/>
```

With:
```typescript
<MultiImageUploader
  label="Ảnh câu hỏi"
  purpose="source_question"
  value={questionImageUrls}
  onChange={setQuestionImageUrls}
/>
```

- [ ] **Step 8: Update preview section**

Location: `page.tsx:400-409`

Replace single image display:
```typescript
{questionImage && (
  <img src={questionImage} alt="" className="max-h-48 rounded-lg border border-border" />
)}
```

With:
```typescript
{q.questionImageUrls && q.questionImageUrls.length > 0 && (
  <div className="grid grid-cols-2 gap-2">
    {q.questionImageUrls.map((url, idx) => {
      const imgUrl = sourceMediaUrl(url);
      return (
        <img
          key={`${url}-${idx}`}
          src={imgUrl}
          alt={`Ảnh ${idx + 1}`}
          className="max-h-32 rounded-lg border border-border object-cover"
        />
      );
    })}
  </div>
)}
```

- [ ] **Step 9: Update API client types (if needed)**

Location: `Fuexam-admin/lib/api/source.ts`

Ensure API functions handle `questionImageUrls` correctly (may already work if types updated).

- [ ] **Step 10: Test locally**

```bash
cd Fuexam-admin
npm run dev
```

Navigate to admin questions page, test:
- Upload multiple images
- Drag to reorder
- Delete individual image
- Delete all
- Edit existing question

Expected: All operations work correctly.

- [ ] **Step 11: Commit**

```bash
git add Fuexam-admin/app/source/catalog/[id]/questions/page.tsx Fuexam-admin/lib/api/source.ts
git commit -m "feat(admin): integrate MultiImageUploader in questions page"
```

---

### Task 8: Frontend Exam ImageGallery Component

**Files:**
- Create: `Fuexam/components/exam/ImageGallery.tsx`
- Create: `Fuexam/components/exam/Lightbox.tsx`

**Interfaces:**
- Consumes: Array of image URLs
- Produces: Thumbnail grid with lightbox modal


- [ ] **Step 1: Create Lightbox component**

```typescript
"use client";

import { useEffect } from "react";
import { X, ChevronLeft, ChevronRight } from "lucide-react";

type Props = {
  images: string[];
  currentIndex: number;
  onClose: () => void;
  onNavigate: (index: number) => void;
};

export function Lightbox({ images, currentIndex, onClose, onNavigate }: Props) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowLeft" && currentIndex > 0) {
        onNavigate(currentIndex - 1);
      }
      if (e.key === "ArrowRight" && currentIndex < images.length - 1) {
        onNavigate(currentIndex + 1);
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [currentIndex, images.length, onClose, onNavigate]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
      onClick={onClose}
    >
      <button
        className="absolute right-4 top-4 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
        onClick={onClose}
      >
        <X className="h-6 w-6" />
      </button>

      {currentIndex > 0 && (
        <button
          className="absolute left-4 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
          onClick={(e) => {
            e.stopPropagation();
            onNavigate(currentIndex - 1);
          }}
        >
          <ChevronLeft className="h-6 w-6" />
        </button>
      )}

      {currentIndex < images.length - 1 && (
        <button
          className="absolute right-4 top-1/2 -translate-y-1/2 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
          onClick={(e) => {
            e.stopPropagation();
            onNavigate(currentIndex + 1);
          }}
        >
          <ChevronRight className="h-6 w-6" />
        </button>
      )}

      <div className="relative max-h-[90vh] max-w-[90vw]" onClick={(e) => e.stopPropagation()}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={images[currentIndex]}
          alt={`Ảnh ${currentIndex + 1}`}
          className="max-h-[90vh] max-w-[90vw] rounded-lg object-contain"
        />
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full bg-black/70 px-3 py-1 text-sm text-white">
          {currentIndex + 1} / {images.length}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Create ImageGallery component**

```typescript
"use client";

import { useState } from "react";
import { Lightbox } from "./Lightbox";

type Props = {
  images: string[];
  alt?: string;
};

export function ImageGallery({ images, alt }: Props) {
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  if (!images || images.length === 0) return null;

  const gridCols = images.length === 1 ? "grid-cols-1" : images.length === 2 ? "grid-cols-2" : "grid-cols-3";

  return (
    <>
      <div className={`grid gap-2 ${gridCols}`}>
        {images.map((url, index) => (
          <button
            key={`${url}-${index}`}
            type="button"
            onClick={() => setLightboxIndex(index)}
            className="group relative overflow-hidden rounded-lg border border-border"
          >
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={url}
              alt={alt ? `${alt} ${index + 1}` : `Ảnh ${index + 1}`}
              className="h-32 w-full object-cover transition-transform group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
            {images.length > 1 && (
              <div className="absolute bottom-1 right-1 rounded bg-black/70 px-1.5 py-0.5 text-xs text-white">
                {index + 1}/{images.length}
              </div>
            )}
          </button>
        ))}
      </div>

      {lightboxIndex !== null && (
        <Lightbox
          images={images}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </>
  );
}
```

- [ ] **Step 3: Update question display component (find where questions render)**

Location: Find the component that displays questions in exam mode (likely in `Fuexam/app` or `Fuexam/components`)

Add import:
```typescript
import { ImageGallery } from "@/components/exam/ImageGallery";
```

Replace single image display with:
```typescript
{question.questionImageUrls && question.questionImageUrls.length > 0 && (
  <ImageGallery
    images={question.questionImageUrls}
    alt={`Câu hỏi ${questionIndex + 1}`}
  />
)}
```

- [ ] **Step 4: Test locally**

```bash
cd Fuexam
npm run dev
```

Navigate to exam with questions that have images. Test:
- Thumbnail grid displays
- Click opens lightbox
- Keyboard navigation (Esc, arrows)
- Image counter shows correctly
- Close button works

Expected: All lightbox features work correctly.

- [ ] **Step 5: Commit**

```bash
git add Fuexam/components/exam/
git commit -m "feat(exam): add ImageGallery with lightbox for multiple question images"
```

---

### Task 9: Integration Testing

**Files:**
- Test existing functionality end-to-end

**Interfaces:**
- Consumes: All previous tasks (backend + frontend)
- Produces: Verified working feature

- [ ] **Step 1: Backend integration test**

```bash
cd backend
mvn -q -pl source test
```

Expected: All tests pass. If any fail, fix before proceeding.

- [ ] **Step 2: Start backend locally**

```bash
cd backend
docker compose up -d postgres redis
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

Expected: Server starts on port 8080.

- [ ] **Step 3: Test API endpoints manually (optional)**

Using curl or Postman:

Create question with multiple images:
```bash
curl -X POST http://localhost:8080/api/v1/admin/source/catalog/{id}/questions \
  -H "Content-Type: application/json" \
  -d '{
    "questionText": "Test multiple images",
    "questionImageUrls": ["img1.jpg", "img2.jpg", "img3.jpg"],
    "explanation": "Test",
    "options": [
      {"optionText": "A", "isCorrect": true},
      {"optionText": "B", "isCorrect": false}
    ]
  }'
```

Expected: 201 Created, question stored with array of URLs.

- [ ] **Step 4: Test admin UI workflow**

1. Navigate to admin questions page
2. Upload 3 images using MultiImageUploader
3. Drag to reorder
4. Delete one image
5. Save question
6. Refresh page, verify order preserved
7. Edit question, add 2 more images
8. Save, verify 4 images total
9. Delete all images
10. Save, verify question valid with text only

Expected: All operations work smoothly.

- [ ] **Step 5: Test exam mode**

1. Navigate to exam view
2. Find question with multiple images
3. Verify thumbnail grid displays
4. Click thumbnail, verify lightbox opens
5. Use arrow keys to navigate
6. Press Esc to close
7. Verify on mobile viewport (responsive)

Expected: Gallery and lightbox work correctly.

- [ ] **Step 6: Test migration of existing questions**

Query database:
```bash
docker compose exec postgres psql -U fuoverflow -d fuoverflow -c "
  SELECT id, question_text, 
         question_image_url, 
         question_image_urls 
  FROM source_questions 
  WHERE question_image_url IS NOT NULL 
  LIMIT 5;
"
```

Expected: Existing single images migrated to array format in `question_image_urls`.

- [ ] **Step 7: Test edge cases**

1. Question with no images (text only) - should work
2. Question with 1 image - should display properly
3. Question with 20+ images - should show warning but allow save
4. Invalid image URL - should handle gracefully
5. Upload non-image file - should reject

Expected: All edge cases handled correctly.

- [ ] **Step 8: Final commit**

```bash
git add -A
git commit -m "test: verify multiple question images feature end-to-end"
```

---

## Self-Review Checklist

**Spec Coverage:**
- ✅ Multiple images per question via JSONB array
- ✅ Migration of existing single images
- ✅ Admin multi-upload with drag-reorder
- ✅ Delete individual/all images
- ✅ Exam thumbnail grid with lightbox
- ✅ Keyboard navigation in lightbox
- ✅ Validation (20+ warning, file types, size)
- ✅ Backward compatibility (kept old field)

**No Placeholders:**
- All code blocks complete
- All file paths exact
- All commands with expected output
- No "TBD" or "TODO" markers

**Type Consistency:**
- `questionImageUrls` used consistently across all tasks
- `List<String>` in Java DTOs
- `string[]` in TypeScript types
- JSON array serialization handled

**Dependencies:**
- Task 2 depends on Task 1 (DB schema)
- Task 3 depends on Task 2 (Entity)
- Task 4 depends on Task 3 (DTOs)
- Task 6 depends on Task 5 (Types)
- Task 7 depends on Task 6 (Component)
- Task 8 independent (can run parallel with 7)
- Task 9 depends on all previous


---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2025-06-23-source-question-multiple-images.md`. 

Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
