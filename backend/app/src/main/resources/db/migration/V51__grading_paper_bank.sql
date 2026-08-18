CREATE TABLE grading_papers (
    id                uuid PRIMARY KEY,
    exam_code         varchar(120) NOT NULL,
    subject_code      varchar(40)  NOT NULL,
    fingerprint       varchar(64)     NOT NULL,
    question_count    int          NOT NULL,
    duration_minutes  int,
    total_mark        numeric(6,2),
    status            varchar(16)  NOT NULL,
    raw_payload       jsonb        NOT NULL,
    payload_sha256    varchar(64)     NOT NULL,
    source_payload_id uuid,
    created_by        uuid         NOT NULL,
    published_at      timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    deleted_at        timestamptz
);
CREATE UNIQUE INDEX ux_grading_papers_fp_live
    ON grading_papers (fingerprint) WHERE deleted_at IS NULL;
CREATE INDEX ix_grading_papers_subject ON grading_papers (subject_code, created_at DESC);

CREATE TABLE grading_paper_questions (
    id                    uuid PRIMARY KEY,
    paper_id              uuid         NOT NULL,
    qid                   bigint       NOT NULL,
    section               varchar(24)  NOT NULL,
    q_type                int,
    display_no            int          NOT NULL,
    mark                  numeric(6,2) NOT NULL,
    chapter_id            int,
    question_text         text,
    image_sha256          varchar(64),
    content_sha256        varchar(64)     NOT NULL,
    answer_mode           varchar(12)  NOT NULL,
    expected_answer_count int,
    answer_source         varchar(12),
    answer_source_ref     varchar(120),
    answered              boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL
);
CREATE UNIQUE INDEX ux_gpq_paper_qid ON grading_paper_questions (paper_id, qid);
CREATE INDEX ix_gpq_content ON grading_paper_questions (content_sha256);

CREATE TABLE grading_paper_answers (
    id            uuid PRIMARY KEY,
    paper_id      uuid    NOT NULL,
    question_id   uuid    NOT NULL,
    qid           bigint  NOT NULL,
    qaid          bigint  NOT NULL,
    option_index  int     NOT NULL,
    option_text   text,
    option_sha256 varchar(64),
    is_correct    boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_gpa_paper_qaid ON grading_paper_answers (paper_id, qaid);
CREATE INDEX ix_gpa_question ON grading_paper_answers (question_id);

CREATE TABLE grading_submissions (
    id              uuid PRIMARY KEY,
    user_id         uuid    NOT NULL,
    paper_id        uuid,
    exam_code       varchar(120),
    subject_code    varchar(40),
    fingerprint     varchar(64) NOT NULL,
    login_id        varchar(80),
    file_sha256     varchar(64) NOT NULL,
    status          varchar(24) NOT NULL,
    integrity_flag  varchar(16),
    total_questions int,
    answered_count  int,
    correct_count   int,
    score           numeric(6,2),
    max_score       numeric(6,2),
    answers_json    jsonb,
    charged_points  int NOT NULL DEFAULT 0,
    discount_points int NOT NULL DEFAULT 0,
    voucher_code    varchar(60),
    created_at      timestamptz NOT NULL
);
CREATE INDEX ix_gsub_user   ON grading_submissions (user_id, created_at DESC);
CREATE INDEX ix_gsub_status ON grading_submissions (status, created_at DESC);
