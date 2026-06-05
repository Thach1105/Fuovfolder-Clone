-- Source question bank: multiple-choice questions with optional images per question/option.

create table source_questions (
    id uuid primary key,
    catalog_item_id uuid not null,
    question_text text null,
    question_image_url varchar(500) null,
    explanation text null,
    multiple_correct boolean not null default false,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint source_questions_has_content
        check (question_text is not null or question_image_url is not null)
);

create index ix_source_questions_item_sort
    on source_questions (catalog_item_id, sort_order)
    where deleted_at is null;

create table source_question_options (
    id uuid primary key,
    question_id uuid not null,
    option_text text null,
    option_image_url varchar(500) null,
    is_correct boolean not null default false,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint source_question_options_has_content
        check (option_text is not null or option_image_url is not null)
);

create index ix_source_question_options_question
    on source_question_options (question_id, sort_order);

create trigger trg_source_questions_updated_at
    before update on source_questions
    for each row execute function set_updated_at();

create trigger trg_source_question_options_updated_at
    before update on source_question_options
    for each row execute function set_updated_at();
