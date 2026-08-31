-- Paper-level grouping for FE/PE content, plus the webhook inbox that feeds it.
--   FE = Final Exam question posts (image-based, one post per question).
--   PE = Practical Exam paper (exam images + downloadable resource files).
-- Before this migration FE questions and PE items hung flat off a subject, so a delivered
-- paper had nowhere to record its term, exam code or content hash. No DB foreign keys per
-- project decision; cross-table references are validated in the app layer.

create table exam_papers (
    id uuid primary key,
    subject_id uuid not null,
    paper_type varchar(8) not null,
    exam_code varchar(120) not null,
    term varchar(16) null,
    retake_label varchar(64) null,
    title varchar(500) not null,
    description text null,
    duration_minutes int null,
    total_mark numeric(6,2) null,
    declared_question_count int null,
    fingerprint varchar(64) not null,
    status varchar(16) not null default 'draft',
    ingest_source varchar(64) null,
    external_paper_id varchar(64) null,
    sort_order int not null default 0,
    view_count bigint not null default 0,
    lock_version int not null default 0,
    published_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint exam_papers_type_check check (paper_type in ('FE', 'PE')),
    constraint exam_papers_status_check check (status in ('draft', 'published'))
);

create unique index ux_exam_papers_fingerprint_live
    on exam_papers (fingerprint)
    where deleted_at is null;

create unique index ux_exam_papers_exam_code_live
    on exam_papers (lower(exam_code))
    where deleted_at is null;

create index ix_exam_papers_subject_type
    on exam_papers (subject_id, paper_type, sort_order)
    where deleted_at is null;

create index ix_exam_papers_status
    on exam_papers (status, created_at desc)
    where deleted_at is null;

create trigger trg_exam_papers_updated_at
    before update on exam_papers
    for each row execute function set_updated_at();

-- Existing hand-entered content keeps working: nullable column, then backfilled below.
alter table exam_fe_questions add column paper_id uuid null;
alter table exam_pe_items     add column paper_id uuid null;

create index ix_exam_fe_questions_paper
    on exam_fe_questions (paper_id, sort_order)
    where deleted_at is null;

create index ix_exam_pe_items_paper
    on exam_pe_items (paper_id, sort_order)
    where deleted_at is null;

-- Backfill: one legacy paper per subject per type, so the read path never has to handle
-- FE questions or PE items that belong to no paper.
insert into exam_papers (
    id, subject_id, paper_type, exam_code, title, fingerprint,
    status, ingest_source, published_at, created_at, updated_at)
select gen_random_uuid(), s.id, 'FE', upper(s.code) || '_LEGACY_FE',
       'FE - chưa phân loại',
       encode(sha256(('legacy-fe:' || s.id::text)::bytea), 'hex'),
       'published', 'admin', now(), now(), now()
from exam_subjects s
where s.deleted_at is null
  and exists (select 1 from exam_fe_questions q
              where q.subject_id = s.id and q.deleted_at is null);

insert into exam_papers (
    id, subject_id, paper_type, exam_code, title, fingerprint,
    status, ingest_source, published_at, created_at, updated_at)
select gen_random_uuid(), s.id, 'PE', upper(s.code) || '_LEGACY_PE',
       'PE - chưa phân loại',
       encode(sha256(('legacy-pe:' || s.id::text)::bytea), 'hex'),
       'published', 'admin', now(), now(), now()
from exam_subjects s
where s.deleted_at is null
  and exists (select 1 from exam_pe_items i
              where i.subject_id = s.id and i.deleted_at is null);

update exam_fe_questions q
set paper_id = p.id
from exam_papers p
where p.subject_id = q.subject_id
  and p.paper_type = 'FE'
  and p.ingest_source = 'admin'
  and q.paper_id is null;

update exam_pe_items i
set paper_id = p.id
from exam_papers p
where p.subject_id = i.subject_id
  and p.paper_type = 'PE'
  and p.ingest_source = 'admin'
  and i.paper_id is null;

-- Webhook inbox. Raw body is kept for audit and retry; the status set matches outbox_events.
create table exam_webhook_events (
    id uuid primary key,
    client_id varchar(64) not null,
    event_id varchar(120) not null,
    event_type varchar(64) not null,
    payload_json jsonb not null,
    payload_sha256 varchar(64) not null,
    signature_valid boolean not null default false,
    status varchar(16) not null default 'pending',
    attempt_count int not null default 0,
    paper_id uuid null,
    error_code varchar(64) null,
    error_message text null,
    available_at timestamptz not null default now(),
    processed_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint exam_webhook_status_check
        check (status in ('pending', 'processing', 'done', 'failed'))
);

create unique index ux_exam_webhook_events_client_event
    on exam_webhook_events (client_id, event_id);

create index ix_exam_webhook_events_pending
    on exam_webhook_events (status, available_at);
