-- FE question view count
ALTER TABLE exam_fe_questions
    ADD COLUMN view_count bigint NOT NULL DEFAULT 0;

-- Denormalized like count on comments
ALTER TABLE exam_comments
    ADD COLUMN like_count int NOT NULL DEFAULT 0;

-- Comment likes tracking
CREATE TABLE exam_comment_likes (
    id         uuid PRIMARY KEY,
    comment_id uuid NOT NULL,
    user_id    uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_exam_comment_likes_user_comment
        UNIQUE (comment_id, user_id)
);

CREATE INDEX ix_exam_comment_likes_comment
    ON exam_comment_likes (comment_id);
