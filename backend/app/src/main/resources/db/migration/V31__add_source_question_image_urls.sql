-- Add question_image_urls column to source_questions for multiple image support
ALTER TABLE source_questions ADD COLUMN IF NOT EXISTS question_image_urls jsonb;