# FE Questions Redesign — Image Post Model with Blur Gating

**Date**: 2026-07-12
**Status**: Draft
**Scope**: Backend exam module, Fuexam frontend, Fuexam-admin frontend

## Problem

FE Questions in the exam module are currently modeled as structured multiple-choice questions (with `exam_fe_options` table, `isCorrect`, `explanation`, `multipleCorrect`). The actual product need is different: FE Questions are **image-based posts** where a user publishes exam photos (question sheets + answer keys), and the community discusses answers via comments.

Current issues:
1. MCQ data model is unused — frontend never renders options or answer selection UI.
2. No image blur/preview gating — access is binary (all-or-nothing via membership).
3. No anti-scraping protection beyond signed URLs and watermark.

## Design Decisions

### D1: Drop MCQ structure entirely
- Drop `exam_fe_options` table.
- Remove `multiple_correct` and `explanation` columns from `exam_fe_questions`.
- FE Question becomes: optional title (`question_text`) + array of images (`question_image_urls`) + comments.

### D2: Blur gating with configurable preview count
- Each subject has `fe_preview_image_count` (renamed from `fe_preview_count`) — number of clear images per post for free users.
- Free users see ALL posts but only the first N images are clear; remaining images show blur thumbnails.
- Members see all images in full resolution.

### D3: Server-side blur generation at upload time
- When admin uploads an exam image, backend generates a blur variant (Gaussian blur + resize to ~100px width).
- Blur file stored alongside original with `-blur` suffix (e.g., `exam/fe/abc123.jpg` → `exam/fe/abc123-blur.jpg`).
- Blur file deleted when original is deleted.
- Blur object keys stored in new `question_blur_urls` jsonb column on `exam_fe_questions`, parallel to `question_image_urls`.

### D4: Backend-gated image URLs
- Public API response includes image objects with `type: "full"` or `type: "blur"`.
- `type: "full"`: signed URL to original image.
- `type: "blur"`: signed URL to blur thumbnail.
- Original image URLs for locked images are NEVER sent to frontend — scraper cannot extract them from API responses.

### D5: Rate limiting on media endpoint
- `GET /api/v1/exam/media/{key}`: 60 requests/minute/IP.
- Returns HTTP 429 when exceeded.

---

## Data Model Changes

### Migration: Drop MCQ, add blur support

```sql
-- Drop exam_fe_options table
DROP TABLE IF EXISTS exam_fe_options;

-- Remove MCQ columns from exam_fe_questions
ALTER TABLE exam_fe_questions DROP COLUMN IF EXISTS multiple_correct;
ALTER TABLE exam_fe_questions DROP COLUMN IF EXISTS explanation;

-- Add blur image tracking
ALTER TABLE exam_fe_questions
  ADD COLUMN question_blur_urls jsonb NOT NULL DEFAULT '[]';

-- Rename fePreviewCount for clarity and update constraint
ALTER TABLE exam_subjects
  RENAME COLUMN fe_preview_count TO fe_preview_image_count;
ALTER TABLE exam_subjects
  DROP CONSTRAINT IF EXISTS exam_subjects_preview_count_nonneg;
ALTER TABLE exam_subjects
  ADD CONSTRAINT exam_subjects_preview_image_count_nonneg
    CHECK (fe_preview_image_count >= 0);

-- Update default from 3 to 2 (now means images per post, not posts)
ALTER TABLE exam_subjects
  ALTER COLUMN fe_preview_image_count SET DEFAULT 2;
```

### Updated `exam_fe_questions` schema

| Column | Type | Notes |
|--------|------|-------|
| id | uuid PK | |
| subject_id | uuid NOT NULL | |
| question_text | text, nullable | Optional title/description |
| question_image_urls | jsonb, default `'[]'` | Array of original image object keys |
| question_blur_urls | jsonb, default `'[]'` | Array of blur image object keys (parallel to image_urls) |
| sort_order | int, default 0 | |
| lock_version | int, default 0 | Optimistic locking |
| created_at / updated_at / deleted_at | timestamptz | Soft delete |

---

## Backend API

### Public: `GET /api/v1/exam/catalog/{idOrCode}/fe`

Response:
```json
{
  "locked": true,
  "totalCount": 10,
  "previewImageCount": 2,
  "questions": [
    {
      "id": "uuid",
      "questionText": "De thi Giua Ky 2024",
      "totalImageCount": 5,
      "images": [
        { "index": 0, "url": "https://.../signed-url", "type": "full" },
        { "index": 1, "url": "https://.../signed-url", "type": "full" },
        { "index": 2, "url": "https://.../blur-signed-url", "type": "blur" },
        { "index": 3, "url": "https://.../blur-signed-url", "type": "blur" },
        { "index": 4, "url": "https://.../blur-signed-url", "type": "blur" }
      ],
      "sortOrder": 0,
      "commentCount": 12,
      "createdAt": "2026-07-10T..."
    }
  ]
}
```

**Logic:**
- Member: all images `type: "full"` with signed URLs to originals.
- Non-member: first N images (per `fe_preview_image_count`) `type: "full"`, rest `type: "blur"` with signed URLs to blur files.
- All users see all posts — no post-level gating.
- `commentCount` per question fetched via count query.

### Admin: Changes to existing endpoints

- `POST/PUT /api/v1/admin/exam/subjects/{id}/fe` — remove `options`, `explanation`, `multipleCorrect` from request/response DTOs.
- Response includes `questionBlurUrls` alongside `questionImageUrls`.

### Media upload: `POST /api/v1/admin/exam/media`

When `purpose=exam_fe_image`:
1. Save original image (existing behavior).
2. Generate blur variant: read image → resize to width 100px (maintain aspect ratio) → apply Gaussian blur (radius 10) → save as `{key}-blur.{ext}`.
3. Return response: `{ "objectKey": "...", "blurObjectKey": "...-blur.jpg", "url": "..." }`.

### Media deletion

When an FE question image is removed (update or delete):
- Delete original file (existing behavior).
- Delete corresponding blur file.
- `ExamMediaService` methods updated to handle paired delete.

### Rate limiting

- Filter-based rate limit on `GET /api/v1/exam/media/**`.
- 60 requests/minute per IP.
- Uses in-memory counter (Bucket4j or simple ConcurrentHashMap with sliding window).
- Response: HTTP 429 with `Retry-After` header.

---

## Frontend Changes

### Fuexam: Public Exam Page (`/exam/[code]`)

**Post card layout:**
- Title (if present) as card header.
- Image grid (responsive: 1-3 columns based on count).
- Full images: `ImageWithWatermark` component, clickable → opens Lightbox.
- Blur images: blur thumbnail with dark gradient overlay + lock icon + "Mua membership de xem" text. Click → toast/redirect to `/membership`.
- Comment count badge below grid.
- Collapsible `ExamCommentThread` below each post.

**Lightbox:**
- Only navigates through `type: "full"` images.
- Side panel: `ExamCommentThread` with `imageIndex` for per-image comments.
- Member: all images navigable.
- Non-member: only preview images navigable.

**Membership upsell:**
- Banner at top of page for non-members (existing `MembershipUpsell` reuse).
- Per-image lock overlay on blur thumbnails.

### Fuexam-admin: Subjects Page

- Add `fe_preview_image_count` input field (number, min 1, max 10, default 2).
- Label: "So anh xem truoc (free user)".

### Fuexam-admin: Papers Page

- No MCQ fields to remove (page already doesn't render them).
- Upload response now includes `blurObjectKey` — stored transparently.

### API client types update

Remove from public types:
- `multipleCorrect`, `explanation`, `options`, `isCorrect` fields.

Add to public types:
- `PublicImageItem: { index: number, url: string, type: "full" | "blur" }`.
- `totalImageCount: number` on question response.
- `previewImageCount: number` on list response.

Remove from admin types:
- `AdminFeOption`, `FeOptionRequest` interfaces.
- Option-related CRUD functions.

---

## Security Layers

| Layer | Protection | Against |
|-------|-----------|---------|
| Signed URLs (15min TTL) | Time-limited access | URL sharing, delayed scraping |
| Backend-gated blur | No original URL in response for locked images | API response scraping |
| Rate limit (60/min/IP) | Throttle bulk requests | Automated crawlers |
| Watermark (CSS overlay) | Visual deterrent | Screenshot sharing |
| Blur file separation | Low-res blur files useless even if leaked | Direct file access |

### NOT implementing:
- Referer check — breaks proxy/VPN users.
- Canvas fingerprinting — privacy concerns, complexity.
- Right-click disable — trivially bypassed, poor UX.

---

## Impacted Files

### Backend (exam module)
- `V45__fe_questions_redesign.sql` — new migration
- `ExamFeQuestionEntity.java` — remove MCQ fields, add blurUrls
- `ExamFeQuestionRepository.java` — update queries
- `ExamFeOptionEntity.java` — DELETE
- `ExamFeOptionRepository.java` — DELETE
- `ExamFeQuestionAdminService.java` — remove option logic, add blur management
- `ExamCatalogQueryService.java` — new image gating logic
- `ExamMediaService.java` — add blur generation, paired delete
- `ExamMediaController.java` — add rate limiting
- DTOs: update Create/Update/Response records, remove option DTOs
- `ExamAdminController.java` — simplify endpoints
- Tests: update/remove option-related tests

### Frontend (Fuexam)
- `Fuexam/lib/api/exam.ts` — update types
- `Fuexam/app/(app)/exam/[code]/page.tsx` — blur image rendering
- `Fuexam/components/exam/Lightbox.tsx` — filter navigable images

### Frontend (Fuexam-admin)
- `Fuexam-admin/lib/api/exam.ts` — remove option types
- `Fuexam-admin/app/exam/subjects/page.tsx` — add previewImageCount input
- `Fuexam-admin/app/exam/subjects/[id]/papers/page.tsx` — minor cleanup

---

## Out of Scope

- Per-post purchase (only membership-based access).
- AI-based answer extraction from images.
- Image CDN or external image processing service.
- Admin UI for structured MCQ (dropped permanently).
