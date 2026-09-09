-- V55__exam_subject_curriculum_term.sql
-- Admin-assigned curriculum term ("Kỳ 0".."Kỳ 9") used to group subjects in the
-- admin browser. NULL means "chưa rõ kỳ" (unassigned) and is the default for
-- every existing row — there is no reliable way to derive this from subject
-- code alone (course numbering does not match FPT's curriculum term order),
-- so it is deliberately never backfilled.
ALTER TABLE exam_subjects
    ADD COLUMN curriculum_term integer NULL;

ALTER TABLE exam_subjects
    ADD CONSTRAINT exam_subjects_curriculum_term_range
        CHECK (curriculum_term IS NULL OR curriculum_term BETWEEN 0 AND 9);
