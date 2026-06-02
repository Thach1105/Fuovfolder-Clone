-- FuOverflow-like database schema
-- Created: 2026-06-02
-- Database: PostgreSQL
-- Note: No foreign key constraints by design. Relationships are validated in application code.

create extension if not exists pgcrypto;

-- =========================
-- USERS / AUTH
-- =========================

create table users (
    id uuid primary key,
    username varchar(64) not null,
    email varchar(255) not null,
    password_hash text null,
    display_name varchar(120) not null,
    avatar_url text null,
    bio text null,
    locale varchar(20) default 'vi-VN',
    timezone varchar(64) default 'Asia/Ho_Chi_Minh',
    status varchar(32) not null default 'active',
    email_verified_at timestamptz null,
    last_seen_at timestamptz null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint users_status_check check (status in ('active','pending','banned','deleted'))
);

create unique index ux_users_username_live on users (lower(username)) where deleted_at is null;
create unique index ux_users_email_live on users (lower(email)) where deleted_at is null;
create index ix_users_status on users(status);
create index ix_users_last_seen on users(last_seen_at desc);

create table user_sessions (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    ip_address inet null,
    user_agent text null,
    expires_at timestamptz not null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now()
);

create unique index ux_user_sessions_token_hash on user_sessions(token_hash);
create index ix_user_sessions_user on user_sessions(user_id);
create index ix_user_sessions_expires on user_sessions(expires_at);

create table user_oauth_accounts (
    id uuid primary key,
    user_id uuid not null,
    provider varchar(32) not null,
    provider_user_id varchar(255) not null,
    email varchar(255) null,
    created_at timestamptz not null default now()
);

create unique index ux_oauth_provider_user on user_oauth_accounts(provider, provider_user_id);
create index ix_oauth_user on user_oauth_accounts(user_id);

-- =========================
-- ROLES / PERMISSIONS
-- =========================

create table roles (
    id uuid primary key,
    slug varchar(64) not null,
    name varchar(120) not null,
    scope varchar(32) not null,
    permissions_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint roles_scope_check check (scope in ('global','forum','course'))
);

create unique index ux_roles_slug on roles(slug);

create table role_assignments (
    id uuid primary key,
    user_id uuid not null,
    role_id uuid not null,
    scope_type varchar(32) not null,
    scope_id uuid null,
    assigned_by_user_id uuid null,
    starts_at timestamptz not null default now(),
    ends_at timestamptz null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint role_assignments_scope_check check (scope_type in ('global','forum','course'))
);

create unique index ux_role_assignment_active
on role_assignments(user_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid))
where revoked_at is null;

create index ix_role_assignment_user on role_assignments(user_id, ends_at);
create index ix_role_assignment_scope on role_assignments(scope_type, scope_id);

-- =========================
-- FORUM STRUCTURE
-- =========================

create table forums (
    id uuid primary key,
    slug varchar(120) not null,
    title varchar(255) not null,
    description text null,
    visibility varchar(32) not null default 'public',
    sort_order int not null default 0,
    created_by_user_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint forums_visibility_check check (visibility in ('public','members','private'))
);

create unique index ux_forums_slug_live on forums(slug) where deleted_at is null;
create index ix_forums_sort on forums(sort_order);

create table categories (
    id uuid primary key,
    forum_id uuid not null,
    slug varchar(120) not null,
    title varchar(255) not null,
    description text null,
    sort_order int not null default 0,
    visibility varchar(32) not null default 'public',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint categories_visibility_check check (visibility in ('public','members','private'))
);

create unique index ux_categories_forum_slug_live on categories(forum_id, slug) where deleted_at is null;
create index ix_categories_forum_sort on categories(forum_id, sort_order);

-- =========================
-- THREADS / POSTS
-- =========================

create table threads (
    id uuid primary key,
    forum_id uuid not null,
    category_id uuid not null,
    author_user_id uuid not null,
    title varchar(300) not null,
    slug varchar(350) not null,
    status varchar(32) not null default 'open',
    pinned_at timestamptz null,
    locked_at timestamptz null,
    last_post_id uuid null,
    last_post_at timestamptz not null default now(),
    reply_count int not null default 0,
    view_count bigint not null default 0,
    reaction_count int not null default 0,
    search_vector tsvector null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint threads_status_check check (status in ('open','locked','archived','hidden','deleted'))
);

create unique index ux_threads_slug_live on threads(slug) where deleted_at is null;
create index ix_threads_category_activity on threads(category_id, pinned_at desc, last_post_at desc);
create index ix_threads_forum_activity on threads(forum_id, pinned_at desc, last_post_at desc);
create index ix_threads_author_created on threads(author_user_id, created_at desc);
create index ix_threads_status_activity on threads(status, last_post_at desc);
create index ix_threads_search on threads using gin(search_vector);

create table posts (
    id uuid primary key,
    thread_id uuid not null,
    author_user_id uuid not null,
    parent_post_id uuid null,
    body_md text not null,
    body_html text not null,
    status varchar(32) not null default 'visible',
    edit_count int not null default 0,
    edit_version int not null default 1,
    reaction_count int not null default 0,
    last_edited_at timestamptz null,
    search_vector tsvector null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint posts_status_check check (status in ('visible','hidden','deleted','flagged'))
);

create index ix_posts_thread_created on posts(thread_id, created_at);
create index ix_posts_author_created on posts(author_user_id, created_at desc);
create index ix_posts_status_created on posts(status, created_at desc);
create index ix_posts_search on posts using gin(search_vector);

create table post_reactions (
    id uuid primary key,
    post_id uuid not null,
    user_id uuid not null,
    reaction_type varchar(32) not null,
    created_at timestamptz not null default now()
);

create unique index ux_post_reaction_once on post_reactions(post_id, user_id, reaction_type);
create index ix_post_reactions_post_type on post_reactions(post_id, reaction_type);
create index ix_post_reactions_user on post_reactions(user_id, created_at desc);

create table thread_bookmarks (
    id uuid primary key,
    thread_id uuid not null,
    user_id uuid not null,
    created_at timestamptz not null default now()
);

create unique index ux_thread_bookmark on thread_bookmarks(thread_id, user_id);
create index ix_thread_bookmarks_user on thread_bookmarks(user_id, created_at desc);

create table tags (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(120) not null,
    description text null,
    usage_count int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create unique index ux_tags_slug_live on tags(slug) where deleted_at is null;
create index ix_tags_usage on tags(usage_count desc);

create table thread_tags (
    id uuid primary key,
    thread_id uuid not null,
    tag_id uuid not null,
    created_at timestamptz not null default now()
);

create unique index ux_thread_tag on thread_tags(thread_id, tag_id);
create index ix_thread_tags_tag_thread on thread_tags(tag_id, thread_id);

-- =========================
-- MODERATION
-- =========================

create table content_flags (
    id uuid primary key,
    reporter_user_id uuid not null,
    target_type varchar(32) not null,
    target_id uuid not null,
    reason varchar(120) not null,
    note text null,
    status varchar(32) not null default 'open',
    resolver_user_id uuid null,
    resolved_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint content_flags_target_check check (target_type in ('thread','post','material','course_review','user')),
    constraint content_flags_status_check check (status in ('open','reviewing','resolved','rejected'))
);

create index ix_content_flags_target on content_flags(target_type, target_id);
create index ix_content_flags_status_created on content_flags(status, created_at);

create table moderation_actions (
    id uuid primary key,
    actor_user_id uuid not null,
    action varchar(64) not null,
    target_type varchar(32) not null,
    target_id uuid not null,
    reason text null,
    metadata_json jsonb not null default '{}',
    created_at timestamptz not null default now()
);

create index ix_moderation_target on moderation_actions(target_type, target_id, created_at desc);
create index ix_moderation_actor on moderation_actions(actor_user_id, created_at desc);

-- =========================
-- COURSES
-- =========================

create table courses (
    id uuid primary key,
    slug varchar(160) not null,
    title varchar(255) not null,
    subtitle varchar(500) null,
    description_md text null,
    description_html text null,
    level varchar(32) not null default 'beginner',
    visibility varchar(32) not null default 'draft',
    owner_user_id uuid not null,
    price_cents int not null default 0,
    currency char(3) not null default 'VND',
    rating_avg numeric(3,2) not null default 0,
    rating_count int not null default 0,
    published_at timestamptz null,
    lock_version int not null default 0,
    search_vector tsvector null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint courses_level_check check (level in ('beginner','intermediate','advanced')),
    constraint courses_visibility_check check (visibility in ('draft','private','published','archived'))
);

create unique index ux_courses_slug_live on courses(slug) where deleted_at is null;
create index ix_courses_visibility_published on courses(visibility, published_at desc);
create index ix_courses_owner on courses(owner_user_id);
create index ix_courses_search on courses using gin(search_vector);

create table course_instructors (
    id uuid primary key,
    course_id uuid not null,
    user_id uuid not null,
    role varchar(32) not null default 'instructor',
    created_at timestamptz not null default now(),
    constraint course_instructors_role_check check (role in ('owner','instructor','assistant'))
);

create unique index ux_course_instructor on course_instructors(course_id, user_id);
create index ix_course_instructors_user on course_instructors(user_id);

create table course_sections (
    id uuid primary key,
    course_id uuid not null,
    title varchar(255) not null,
    description text null,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create unique index ux_course_sections_order_live on course_sections(course_id, sort_order) where deleted_at is null;
create index ix_course_sections_course on course_sections(course_id, sort_order);

create table course_lessons (
    id uuid primary key,
    course_id uuid not null,
    section_id uuid not null,
    slug varchar(160) not null,
    title varchar(255) not null,
    summary text null,
    lesson_type varchar(32) not null,
    body_md text null,
    body_html text null,
    duration_seconds int null,
    is_preview boolean not null default false,
    sort_order int not null default 0,
    status varchar(32) not null default 'draft',
    edit_version int not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint lessons_type_check check (lesson_type in ('video','article','quiz','assignment','live')),
    constraint lessons_status_check check (status in ('draft','published','archived'))
);

create unique index ux_course_lesson_slug_live on course_lessons(course_id, slug) where deleted_at is null;
create index ix_lessons_section_order on course_lessons(section_id, sort_order);
create index ix_lessons_course_status_order on course_lessons(course_id, status, sort_order);

-- =========================
-- MATERIALS / FILES
-- =========================

create table uploaded_files (
    id uuid primary key,
    owner_user_id uuid not null,
    original_filename varchar(500) not null,
    storage_path text not null,
    public_url text null,
    file_category varchar(32) not null,
    mime_type varchar(120) not null,
    size_bytes bigint not null,
    checksum_sha256 char(64) null,
    visibility varchar(32) not null default 'private',
    status varchar(32) not null default 'active',
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint uploaded_files_category_check check (file_category in ('avatar','image','video','audio','attachment','log','other')),
    constraint uploaded_files_visibility_check check (visibility in ('private','protected','public')),
    constraint uploaded_files_status_check check (status in ('active','quarantined','deleted'))
);

create index ix_uploaded_files_owner on uploaded_files(owner_user_id, created_at desc);
create index ix_uploaded_files_category on uploaded_files(file_category, created_at desc);
create index ix_uploaded_files_checksum on uploaded_files(checksum_sha256);

create table materials (
    id uuid primary key,
    owner_user_id uuid not null,
    course_id uuid null,
    lesson_id uuid null,
    uploaded_file_id uuid null,
    code varchar(64) null,
    title varchar(255) not null,
    description text null,
    material_type varchar(32) not null,
    storage_path text null,
    external_url text null,
    mime_type varchar(120) null,
    size_bytes bigint null,
    checksum_sha256 char(64) null,
    visibility varchar(32) not null default 'private',
    price_points int not null default 0,
    version int not null default 1,
    download_count int not null default 0,
    view_count bigint not null default 0,
    search_vector tsvector null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint materials_type_check check (material_type in ('file','link','video','embed','text','question_bank')),
    constraint materials_visibility_check check (visibility in ('private','enrolled','members','public','paid'))
);

create index ix_materials_owner on materials(owner_user_id);
create index ix_materials_course on materials(course_id);
create index ix_materials_lesson on materials(lesson_id);
create index ix_materials_file on materials(uploaded_file_id);
create index ix_materials_visibility_created on materials(visibility, created_at desc);
create index ix_materials_code on materials(code);
create index ix_materials_search on materials using gin(search_vector);

create table material_versions (
    id uuid primary key,
    material_id uuid not null,
    version int not null,
    uploaded_file_id uuid null,
    storage_path text null,
    checksum_sha256 char(64) null,
    size_bytes bigint null,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now()
);

create unique index ux_material_version on material_versions(material_id, version);

-- =========================
-- ENROLLMENT / PROGRESS
-- =========================

create table course_enrollments (
    id uuid primary key,
    course_id uuid not null,
    user_id uuid not null,
    source varchar(32) not null,
    status varchar(32) not null default 'active',
    enrolled_at timestamptz not null default now(),
    completed_at timestamptz null,
    expires_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint enrollments_source_check check (source in ('free','purchase','invite','membership','admin')),
    constraint enrollments_status_check check (status in ('active','completed','cancelled','expired'))
);

create unique index ux_course_enrollment_active
on course_enrollments(course_id, user_id)
where status in ('active','completed');

create index ix_enrollments_user_status on course_enrollments(user_id, status);
create index ix_enrollments_course_status on course_enrollments(course_id, status);

create table lesson_progress (
    id uuid primary key,
    course_id uuid not null,
    lesson_id uuid not null,
    user_id uuid not null,
    status varchar(32) not null default 'not_started',
    progress_percent int not null default 0,
    started_at timestamptz null,
    completed_at timestamptz null,
    updated_at timestamptz not null default now(),
    constraint lesson_progress_status_check check (status in ('not_started','in_progress','completed')),
    constraint lesson_progress_percent_check check (progress_percent >= 0 and progress_percent <= 100)
);

create unique index ux_lesson_progress_user on lesson_progress(lesson_id, user_id);
create index ix_lesson_progress_user_course on lesson_progress(user_id, course_id, status);

-- =========================
-- QUIZ
-- =========================

create table quizzes (
    id uuid primary key,
    lesson_id uuid not null,
    title varchar(255) not null,
    pass_score_percent int not null default 60,
    max_attempts int null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint quizzes_pass_score_check check (pass_score_percent >= 0 and pass_score_percent <= 100)
);

create index ix_quizzes_lesson on quizzes(lesson_id);

create table quiz_questions (
    id uuid primary key,
    quiz_id uuid not null,
    question_type varchar(32) not null,
    prompt_md text not null,
    prompt_html text not null,
    points int not null default 1,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint quiz_questions_type_check check (question_type in ('single','multiple','text'))
);

create index ix_quiz_questions_quiz_order on quiz_questions(quiz_id, sort_order);

create table quiz_answers (
    id uuid primary key,
    question_id uuid not null,
    answer_text text not null,
    is_correct boolean not null default false,
    sort_order int not null default 0
);

create index ix_quiz_answers_question_order on quiz_answers(question_id, sort_order);

create table quiz_attempts (
    id uuid primary key,
    quiz_id uuid not null,
    lesson_id uuid not null,
    course_id uuid not null,
    user_id uuid not null,
    score_percent int null,
    passed boolean null,
    started_at timestamptz not null default now(),
    submitted_at timestamptz null,
    answers_json jsonb not null default '{}'
);

create index ix_quiz_attempts_user_quiz on quiz_attempts(user_id, quiz_id, submitted_at desc);
create index ix_quiz_attempts_course_user on quiz_attempts(course_id, user_id, submitted_at desc);

-- =========================
-- MEMBERSHIP / ENTITLEMENTS
-- =========================

create table membership_plans (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(255) not null,
    description text null,
    price_cents int not null default 0,
    currency char(3) not null default 'VND',
    billing_interval varchar(32) not null,
    status varchar(32) not null default 'active',
    features_json jsonb not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint membership_interval_check check (billing_interval in ('month','year','lifetime')),
    constraint membership_plan_status_check check (status in ('active','inactive','archived'))
);

create unique index ux_membership_plans_slug on membership_plans(slug);
create index ix_membership_plans_status on membership_plans(status);

create table memberships (
    id uuid primary key,
    user_id uuid not null,
    plan_id uuid not null,
    status varchar(32) not null,
    starts_at timestamptz not null,
    current_period_start timestamptz null,
    current_period_end timestamptz null,
    cancel_at timestamptz null,
    cancelled_at timestamptz null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint memberships_status_check check (status in ('trialing','active','past_due','cancelled','expired'))
);

create index ix_memberships_user_status on memberships(user_id, status);
create index ix_memberships_plan_status on memberships(plan_id, status);
create index ix_memberships_period_end on memberships(current_period_end);

create table membership_events (
    id uuid primary key,
    membership_id uuid not null,
    user_id uuid not null,
    event_type varchar(64) not null,
    payload_json jsonb not null default '{}',
    occurred_at timestamptz not null,
    created_at timestamptz not null default now()
);

create index ix_membership_events_membership on membership_events(membership_id, occurred_at);
create index ix_membership_events_user on membership_events(user_id, occurred_at);

create table entitlements (
    id uuid primary key,
    user_id uuid null,
    membership_id uuid null,
    course_id uuid null,
    material_id uuid null,
    forum_id uuid null,
    entitlement_type varchar(32) not null,
    starts_at timestamptz not null default now(),
    ends_at timestamptz null,
    source_type varchar(32) not null,
    source_id uuid null,
    created_at timestamptz not null default now(),
    revoked_at timestamptz null,
    constraint entitlements_type_check check (entitlement_type in ('course_access','material_access','forum_access','badge_access')),
    constraint entitlements_source_check check (source_type in ('membership','purchase','admin','award','free'))
);

create index ix_entitlements_user_type on entitlements(user_id, entitlement_type, ends_at);
create index ix_entitlements_course_user on entitlements(course_id, user_id);
create index ix_entitlements_material_user on entitlements(material_id, user_id);
create index ix_entitlements_forum_user on entitlements(forum_id, user_id);
create index ix_entitlements_source on entitlements(source_type, source_id);

-- =========================
-- ORDERS / PAYMENTS
-- =========================

create table orders (
    id uuid primary key,
    user_id uuid not null,
    status varchar(32) not null default 'pending',
    subtotal_cents int not null default 0,
    discount_cents int not null default 0,
    tax_cents int not null default 0,
    total_cents int not null default 0,
    currency char(3) not null default 'VND',
    provider varchar(64) null,
    provider_order_id varchar(255) null,
    idempotency_key varchar(255) null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint orders_status_check check (status in ('pending','paid','failed','refunded','cancelled'))
);

create unique index ux_orders_provider_id on orders(provider, provider_order_id) where provider_order_id is not null;
create unique index ux_orders_idempotency on orders(idempotency_key) where idempotency_key is not null;
create index ix_orders_user_created on orders(user_id, created_at desc);
create index ix_orders_status_created on orders(status, created_at desc);

create table order_items (
    id uuid primary key,
    order_id uuid not null,
    item_type varchar(32) not null,
    item_id uuid not null,
    quantity int not null default 1,
    unit_price_cents int not null,
    total_cents int not null,
    metadata_json jsonb not null default '{}',
    constraint order_items_type_check check (item_type in ('course','membership','material'))
);

create index ix_order_items_order on order_items(order_id);
create index ix_order_items_item on order_items(item_type, item_id);

create table payments (
    id uuid primary key,
    order_id uuid not null,
    user_id uuid not null,
    provider varchar(64) not null,
    provider_payment_id varchar(255) not null,
    amount_cents int not null,
    currency char(3) not null default 'VND',
    status varchar(32) not null,
    paid_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint payments_status_check check (status in ('pending','paid','failed','refunded'))
);

create unique index ux_payments_provider_payment on payments(provider, provider_payment_id);
create index ix_payments_order on payments(order_id);
create index ix_payments_user_created on payments(user_id, created_at desc);

create table payment_webhook_events (
    id uuid primary key,
    provider varchar(64) not null,
    provider_event_id varchar(255) not null,
    event_type varchar(120) not null,
    payload_json jsonb not null,
    signature_valid boolean not null default false,
    processed_at timestamptz null,
    created_at timestamptz not null default now()
);

create unique index ux_payment_webhook_provider_event on payment_webhook_events(provider, provider_event_id);
create index ix_payment_webhook_processed on payment_webhook_events(processed_at);

-- =========================
-- AWARDS / POINTS
-- =========================

create table award_definitions (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(255) not null,
    description text null,
    icon_url text null,
    award_type varchar(32) not null,
    criteria_json jsonb not null default '{}',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint award_type_check check (award_type in ('badge','certificate','points','trophy'))
);

create unique index ux_award_definitions_slug_live on award_definitions(slug) where deleted_at is null;
create index ix_award_definitions_active on award_definitions(is_active);

create table user_awards (
    id uuid primary key,
    user_id uuid not null,
    award_definition_id uuid not null,
    awarded_by_user_id uuid null,
    source_type varchar(32) not null,
    source_id uuid null,
    evidence_json jsonb not null default '{}',
    awarded_at timestamptz not null default now(),
    revoked_at timestamptz null,
    revoke_reason text null,
    constraint user_awards_source_check check (source_type in ('course','lesson','forum','membership','manual','material'))
);

create unique index ux_user_award_active
on user_awards(user_id, award_definition_id, source_type, coalesce(source_id, '00000000-0000-0000-0000-000000000000'::uuid))
where revoked_at is null;

create index ix_user_awards_user on user_awards(user_id, awarded_at desc);
create index ix_user_awards_definition on user_awards(award_definition_id, awarded_at desc);

create table points_ledger (
    id uuid primary key,
    user_id uuid not null,
    delta int not null,
    reason varchar(255) not null,
    source_type varchar(32) not null,
    source_id uuid null,
    created_at timestamptz not null default now(),
    constraint points_delta_nonzero check (delta <> 0)
);

create index ix_points_ledger_user on points_ledger(user_id, created_at desc);
create index ix_points_ledger_source on points_ledger(source_type, source_id);

create table certificates (
    id uuid primary key,
    user_id uuid not null,
    course_id uuid null,
    award_definition_id uuid null,
    certificate_no varchar(120) not null,
    verification_code varchar(120) not null,
    issued_at timestamptz not null default now(),
    revoked_at timestamptz null,
    metadata_json jsonb not null default '{}'
);

create unique index ux_certificates_no on certificates(certificate_no);
create unique index ux_certificates_verify on certificates(verification_code);
create index ix_certificates_user on certificates(user_id, issued_at desc);
create index ix_certificates_course on certificates(course_id);

-- =========================
-- NOTIFICATIONS
-- =========================

create table notifications (
    id uuid primary key,
    user_id uuid not null,
    type varchar(64) not null,
    title varchar(255) not null,
    body text null,
    data_json jsonb not null default '{}',
    read_at timestamptz null,
    created_at timestamptz not null default now()
);

create index ix_notifications_user_unread_created on notifications(user_id, read_at, created_at desc);

create table notification_preferences (
    id uuid primary key,
    user_id uuid not null,
    channel varchar(32) not null,
    type varchar(64) not null,
    enabled boolean not null default true,
    updated_at timestamptz not null default now(),
    constraint notification_channel_check check (channel in ('web','email','push'))
);

create unique index ux_notification_pref on notification_preferences(user_id, channel, type);

-- =========================
-- SEARCH / OUTBOX / AUDIT
-- =========================

create table search_index_jobs (
    id uuid primary key,
    entity_type varchar(32) not null,
    entity_id uuid not null,
    operation varchar(32) not null,
    status varchar(32) not null default 'pending',
    attempts int not null default 0,
    available_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    processed_at timestamptz null,
    constraint search_jobs_operation_check check (operation in ('upsert','delete')),
    constraint search_jobs_status_check check (status in ('pending','processing','done','failed'))
);

create index ix_search_jobs_pending on search_index_jobs(status, available_at);
create index ix_search_jobs_entity on search_index_jobs(entity_type, entity_id);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    event_type varchar(120) not null,
    payload_json jsonb not null default '{}',
    status varchar(32) not null default 'pending',
    attempts int not null default 0,
    available_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    processed_at timestamptz null,
    constraint outbox_status_check check (status in ('pending','processing','done','failed'))
);

create index ix_outbox_pending on outbox_events(status, available_at);
create index ix_outbox_aggregate on outbox_events(aggregate_type, aggregate_id, created_at desc);

create table audit_log (
    id uuid primary key,
    actor_user_id uuid null,
    action varchar(120) not null,
    entity_type varchar(64) not null,
    entity_id uuid not null,
    before_json jsonb null,
    after_json jsonb null,
    ip_address inet null,
    user_agent text null,
    created_at timestamptz not null default now()
);

create index ix_audit_entity on audit_log(entity_type, entity_id, created_at desc);
create index ix_audit_actor on audit_log(actor_user_id, created_at desc);

-- =========================
-- OPTIONAL ORPHAN SCAN RESULTS
-- =========================

create table orphan_scan_results (
    id uuid primary key,
    source_table varchar(120) not null,
    source_column varchar(120) not null,
    source_id uuid not null,
    expected_table varchar(120) not null,
    expected_id uuid not null,
    status varchar(32) not null default 'open',
    detected_at timestamptz not null default now(),
    resolved_at timestamptz null,
    note text null,
    constraint orphan_scan_status_check check (status in ('open','ignored','fixed'))
);

create index ix_orphan_scan_open on orphan_scan_results(status, detected_at desc);
create index ix_orphan_scan_source on orphan_scan_results(source_table, source_id);

-- =========================
-- UPDATED_AT TRIGGER
-- =========================

create or replace function set_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create trigger trg_users_updated_at before update on users for each row execute function set_updated_at();
create trigger trg_roles_updated_at before update on roles for each row execute function set_updated_at();
create trigger trg_forums_updated_at before update on forums for each row execute function set_updated_at();
create trigger trg_categories_updated_at before update on categories for each row execute function set_updated_at();
create trigger trg_threads_updated_at before update on threads for each row execute function set_updated_at();
create trigger trg_posts_updated_at before update on posts for each row execute function set_updated_at();
create trigger trg_tags_updated_at before update on tags for each row execute function set_updated_at();
create trigger trg_courses_updated_at before update on courses for each row execute function set_updated_at();
create trigger trg_course_sections_updated_at before update on course_sections for each row execute function set_updated_at();
create trigger trg_course_lessons_updated_at before update on course_lessons for each row execute function set_updated_at();
create trigger trg_materials_updated_at before update on materials for each row execute function set_updated_at();
create trigger trg_course_enrollments_updated_at before update on course_enrollments for each row execute function set_updated_at();
create trigger trg_membership_plans_updated_at before update on membership_plans for each row execute function set_updated_at();
create trigger trg_memberships_updated_at before update on memberships for each row execute function set_updated_at();
create trigger trg_award_definitions_updated_at before update on award_definitions for each row execute function set_updated_at();
create trigger trg_notification_preferences_updated_at before update on notification_preferences for each row execute function set_updated_at();
