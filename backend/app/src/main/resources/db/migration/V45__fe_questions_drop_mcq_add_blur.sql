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
