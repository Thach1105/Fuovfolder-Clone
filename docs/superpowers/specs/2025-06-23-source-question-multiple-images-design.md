# Multiple Images Per Question - Design Spec

**Date:** 2025-06-23
**Status:** Approved
**Author:** Claude + User

## Summary

Add support for multiple images per source question. Images are displayed as thumbnails in exam mode, expandable via lightbox. Admin panel allows uploading, reordering, and deleting multiple images.

## Current State

- **Backend:** `question_image_url` is a single `VARCHAR(500)` column
- **Frontend Admin:** Single `ImageUploader` component
- **Frontend Exam:** Single image display

## Requirements

1. Support multiple images per question (no hard limit, backend warns at 20+)
2. Preserve upload order (array order)
3. Admin can upload multiple files at once
4. Admin can drag-to-reorder images
5. Admin can delete individual images or all
6. Exam mode shows thumbnail grid with lightbox

## Architecture Decision

**Chosen Approach: JSONB Column**

Store image URLs as JSONB array in `source_questions.question_image_urls`.

**Trade-offs:**
- ✅ Simple migration (add column, migrate data)
- ✅ No join tables or cascade logic
- ✅ Works with existing monolith pattern
- ❌ Cannot index individual URLs easily (not needed)

## Design

### 1. Backend Changes

#### 1.1 Database Migration

```sql
-- V{version}__add_multiple_question_images.sql
ALTER TABLE source_questions
  ADD COLUMN question_image_urls JSONB DEFAULT '[]'::jsonb;

-- Migrate existing single images
UPDATE source_questions
  SET question_image_urls = jsonb_build_array(question_image_url)
  WHERE question_image_url IS NOT NULL;
```

Later migration (optional, after frontend transition):
```sql
ALTER TABLE source_questions DROP COLUMN question_image_url;
```

#### 1.2 Entity (`SourceQuestionEntity.java`)

```java
@Column(name = "question_image_urls", columnDefinition = "jsonb")
private String questionImageUrls; // JSON array string: ["url1", "url2"]

// Update create method signature
public static SourceQuestionEntity create(
    UUID id,
    UUID catalogItemId,
    String questionText,
    String questionImageUrls,  // Changed from single URL
    String explanation,
    boolean multipleCorrect,
    int sortOrder,
    Instant now
) { ... }
```

#### 1.3 DTOs

```java
// CreateQuestionRequest.java, UpdateQuestionRequest.java
public record CreateQuestionRequest(
    String questionText,
    List<String> questionImageUrls,  // Changed: List<String>
    String explanation,
    boolean multipleCorrect,
    @NotEmpty @Valid List<QuestionOptionRequest> options
) { }

// AdminQuestionResponse.java, PublicQuestionResponse.java
public record AdminQuestionResponse(
    String id,
    String questionText,
    List<String> questionImageUrls,  // Changed: List<String>
    String explanation,
    boolean multipleCorrect,
    List<AdminQuestionOptionResponse> options,
    int sortOrder
) { }
```

#### 1.4 Service Layer (`SourceQuestionAdminService.java`)

- Parse JSON array on read/write
- Validate array size (warn if > 20)
- Handle null/empty arrays

#### 1.5 Media Resolver (`SourceMediaUrlResolver.java`)

- Resolve all URLs in array for public view
- Return empty list if null/empty

### 2. Frontend Admin Changes

#### 2.1 API Types (`Fuexam-admin/types/api.ts`)

```typescript
type QuestionBody = {
  questionText?: string;
  questionImageUrls?: string[];
  explanation?: string;
  options: QuestionOptionBody[];
};

type AdminQuestion = {
  id: string;
  questionText: string | null;
  questionImageUrls: string[] | null;
  explanation: string | null;
  multipleCorrect: boolean;
  options: AdminQuestionOption[];
  sortOrder: number;
};
```

#### 2.2 New Component: `MultiImageUploader`

Location: `Fuexam-admin/components/admin/MultiImageUploader.tsx`

```typescript
interface Props {
  label: string;
  purpose: string;
  value: string[];
  onChange: (urls: string[]) => void;
  maxImages?: number;
}

Features:
- Select multiple files (input multiple)
- Drag & drop zone
- Grid preview with thumbnails
- Drag handles to reorder
- Delete button per image
- "Delete All" button
- Warning when > 20 images
- File type validation (jpg, png, webp, gif)
- File size validation (5MB max per file)
```

#### 2.3 Admin Page Update

File: `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx`

```typescript
// State change
const [questionImageUrls, setQuestionImageUrls] = useState<string[]>([]);

// Form section
<MultiImageUploader
  label="Ảnh câu hỏi"
  purpose="source_question"
  value={questionImageUrls}
  onChange={setQuestionImageUrls}
/>

// buildBody() update
questionImageUrls: questionImageUrls.length > 0 ? questionImageUrls : undefined,

// Preview update - show thumbnail grid
// Click thumbnail to view full size
```

### 3. Exam Mode Changes

#### 3.1 New Component: `ImageGallery`

Location: Shared components or question-display module

```typescript
interface Props {
  images: string[];
  alt?: string;
}

Features:
- Grid layout (2-3 columns based on count)
- Thumbnail ~120px height
- Click → Lightbox modal
- Lightbox: full size, navigation arrows, keyboard support
- Image counter: "1/3"
```

#### 3.2 Public API Response

```json
{
  "id": "uuid",
  "questionText": "...",
  "questionImageUrls": [
    "https://cdn.example.com/img1.jpg",
    "https://cdn.example.com/img2.jpg"
  ],
  "options": [...]
}
```

## Validation Rules

| Rule | Value |
|------|-------|
| Max images per question | 20 (soft, warning) |
| File types | jpg, png, webp, gif |
| Max file size | 5MB per image |
| Question validation | At least text OR images required |

## Migration Strategy

### Phase 1: Backend Setup
1. Add `question_image_urls` column
2. Run migration script for existing data
3. Update entity for both fields (backward compat)
4. Update DTOs to use array

### Phase 2: Admin UI
5. Create `MultiImageUploader` component
6. Update admin questions page
7. Test CRUD operations

### Phase 3: Exam UI
8. Create `ImageGallery` component
9. Update question display components
10. Test exam mode

### Phase 4: Cleanup (Later)
11. Drop `question_image_url` column after verification

## Files to Change

### Backend
- `backend/source/src/main/java/com/fuoverflow/source/persistence/SourceQuestionEntity.java`
- `backend/source/src/main/java/com/fuoverflow/source/api/dto/CreateQuestionRequest.java`
- `backend/source/src/main/java/com/fuoverflow/source/api/dto/UpdateQuestionRequest.java`
- `backend/source/src/main/java/com/fuoverflow/source/api/dto/AdminQuestionResponse.java`
- `backend/source/src/main/java/com/fuoverflow/source/api/dto/PublicQuestionResponse.java`
- `backend/source/src/main/java/com/fuoverflow/source/application/SourceQuestionAdminService.java`
- `backend/source/src/main/java/com/fuoverflow/source/application/SourceMediaUrlResolver.java`
- `backend/app/src/main/resources/db/migration/V{version}__add_multiple_question_images.sql`

### Frontend Admin
- `Fuexam-admin/types/api.ts`
- `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx`
- `Fuexam-admin/components/admin/MultiImageUploader.tsx` (new)
- `Fuexam-admin/lib/api/source.ts` (API client updates)

### Frontend Exam
- `Fuexam/components/{question-display}/ImageGallery.tsx` (new)
- Update question display component to use ImageGallery

## Testing Checklist

- [ ] Bulk upload files preserves order
- [ ] Drag-to-reorder works and saves
- [ ] Delete single image works
- [ ] Delete all images works
- [ ] Existing questions with one image migrate correctly
- [ ] Exam mode shows thumbnail grid
- [ ] Lightbox opens, navigates, closes correctly
- [ ] Keyboard navigation in lightbox (Esc, arrows)
- [ ] Validation warnings for > 20 images
- [ ] File type and size validation
- [ ] API serialization/deserialization of JSON array