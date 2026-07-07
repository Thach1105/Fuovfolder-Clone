-- Exam (FE/PE) service: membership-gated exam content.
--   FE = Final Exam multiple-choice questions (image + options + explanation).
--   PE = Practical Exam papers (exam images + downloadable resource files / zip folders).
-- Access is gated by an ACTIVE MEMBERSHIP (any plan), not per-item purchase.
-- Non-members can preview the first N FE questions of each subject.
-- No DB foreign keys per project decision; cross-table refs validated in the app layer.

create table exam_subjects (
    id uuid primary key,
    code varchar(64) not null,
    title varchar(500) not null,
    description text null,
    cover_image_url varchar(500) null,
    card_color varchar(32) null,
    category_slug varchar(64) null,
    fe_preview_count int not null default 3,
    view_count bigint not null default 0,
    is_active boolean not null default true,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint exam_subjects_preview_count_nonneg check (fe_preview_count >= 0)
);

create unique index ux_exam_subjects_code_live
    on exam_subjects (lower(code))
    where deleted_at is null;

create index ix_exam_subjects_active_sort
    on exam_subjects (is_active, sort_order)
    where deleted_at is null;

-- FE questions ----------------------------------------------------------------

create table exam_fe_questions (
    id uuid primary key,
    subject_id uuid not null,
    question_text text null,
    question_image_urls jsonb not null default '[]',
    explanation text null,
    multiple_correct boolean not null default false,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create index ix_exam_fe_questions_subject_sort
    on exam_fe_questions (subject_id, sort_order)
    where deleted_at is null;

create index ix_exam_fe_questions_image_urls_gin
    on exam_fe_questions using gin (question_image_urls);

create table exam_fe_options (
    id uuid primary key,
    question_id uuid not null,
    option_text text null,
    option_image_url varchar(500) null,
    is_correct boolean not null default false,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint exam_fe_options_has_content
        check (option_text is not null or option_image_url is not null)
);

create index ix_exam_fe_options_question
    on exam_fe_options (question_id, sort_order);

-- PE papers + resources -------------------------------------------------------

create table exam_pe_items (
    id uuid primary key,
    subject_id uuid not null,
    title varchar(500) not null,
    description text null,
    exam_image_urls jsonb not null default '[]',
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create index ix_exam_pe_items_subject_sort
    on exam_pe_items (subject_id, sort_order)
    where deleted_at is null;

create index ix_exam_pe_items_image_urls_gin
    on exam_pe_items using gin (exam_image_urls);

create table exam_pe_resources (
    id uuid primary key,
    pe_item_id uuid not null,
    folder_label varchar(255) null,
    object_key varchar(500) not null,
    original_filename varchar(500) not null,
    mime_type varchar(120) null,
    size_bytes bigint not null default 0,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create index ix_exam_pe_resources_item_sort
    on exam_pe_resources (pe_item_id, sort_order)
    where deleted_at is null;

create index ix_exam_pe_resources_object_key
    on exam_pe_resources (object_key)
    where deleted_at is null;

-- Comments (polymorphic; member-only at the app layer) ------------------------

create table exam_comments (
    id uuid primary key,
    subject_type varchar(16) not null,
    subject_id uuid not null,
    exam_subject_id uuid not null,
    author_user_id uuid not null,
    parent_comment_id uuid null,
    body_md text not null,
    body_html text not null,
    status varchar(16) not null default 'visible',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint exam_comments_subject_type_check
        check (subject_type in ('fe_question', 'pe_item')),
    constraint exam_comments_status_check
        check (status in ('visible', 'hidden'))
);

create index ix_exam_comments_subject
    on exam_comments (subject_type, subject_id, created_at)
    where deleted_at is null;

create index ix_exam_comments_parent
    on exam_comments (parent_comment_id)
    where deleted_at is null;

-- updated_at triggers ---------------------------------------------------------

create trigger trg_exam_subjects_updated_at
    before update on exam_subjects
    for each row execute function set_updated_at();

create trigger trg_exam_fe_questions_updated_at
    before update on exam_fe_questions
    for each row execute function set_updated_at();

create trigger trg_exam_fe_options_updated_at
    before update on exam_fe_options
    for each row execute function set_updated_at();

create trigger trg_exam_pe_items_updated_at
    before update on exam_pe_items
    for each row execute function set_updated_at();

create trigger trg_exam_comments_updated_at
    before update on exam_comments
    for each row execute function set_updated_at();
