-- FuOverflow-like database schema
-- Created: 2026-06-02
-- Database: PostgreSQL
-- Note: No foreign key constraints by design. Relationships are validated in application code.

create extension if not exists pgcrypto;

-- =========================
-- USERS / AUTH
-- =========================

-- Bảng người dùng: lưu thông tin tài khoản, hồ sơ cá nhân và trạng thái hoạt động
create table users (
    id uuid primary key,
    username varchar(64) not null, -- Tên đăng nhập duy nhất
    email varchar(255) not null, -- Email duy nhất
    password_hash text null, -- Hash mật khẩu, null nếu đăng nhập hoàn toàn qua OAuth
    display_name varchar(120) not null, -- Tên hiển thị trên giao diện
    avatar_url text null, -- Đường dẫn ảnh đại diện
    bio text null, -- Giới thiệu bản thân
    locale varchar(20) default 'vi-VN', -- Ngôn ngữ ưu tiên
    timezone varchar(64) default 'Asia/Ho_Chi_Minh', -- Múi giờ của user
    status varchar(32) not null default 'active', -- Trạng thái (active, pending, banned, deleted)
    email_verified_at timestamptz null, -- Thời điểm xác thực email
    last_seen_at timestamptz null, -- Lần cuối cùng hoạt động
    lock_version int not null default 0, -- Dùng cho Optimistic Locking (tránh ghi đè dữ liệu đồng thời)
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null -- Thời điểm xóa mềm (soft delete)
);

create unique index ux_users_username_live on users (lower(username)) where deleted_at is null;
create unique index ux_users_email_live on users (lower(email)) where deleted_at is null;
create index ix_users_status on users(status);
create index ix_users_last_seen on users(last_seen_at desc);

-- Bảng phiên đăng nhập: quản lý token session, thiết bị truy cập và thời gian hết hạn
create table user_sessions (
    id uuid primary key,
    user_id uuid not null, -- ID người dùng sở hữu phiên
    token_hash varchar(255) not null, -- Hash của refresh token (không lưu token thô)
    ip_address inet null, -- Địa chỉ IP của client khi đăng nhập
    user_agent text null, -- Thông tin trình duyệt/thiết bị
    expires_at timestamptz not null, -- Thời điểm phiên hết hạn
    revoked_at timestamptz null, -- Thời điểm phiên bị thu hồi/đăng xuất sớm
    created_at timestamptz not null default now()
);

create unique index ux_user_sessions_token_hash on user_sessions(token_hash);
create index ix_user_sessions_user on user_sessions(user_id);
create index ix_user_sessions_expires on user_sessions(expires_at);

-- Bảng liên kết OAuth: lưu thông tin kết nối các tài khoản bên thứ 3 (Google, Github...)
create table user_oauth_accounts (
    id uuid primary key,
    user_id uuid not null, -- ID người dùng liên kết
    provider varchar(32) not null, -- Tên nhà cung cấp (google, github...)
    provider_user_id varchar(255) not null, -- ID người dùng phía nhà cung cấp OAuth
    email varchar(255) null, -- Email từ nhà cung cấp OAuth
    created_at timestamptz not null default now()
);

create unique index ux_oauth_provider_user on user_oauth_accounts(provider, provider_user_id);
create index ix_oauth_user on user_oauth_accounts(user_id);

-- =========================
-- ROLES / PERMISSIONS
-- =========================

-- Bảng vai trò (roles): định nghĩa các nhóm quyền hệ thống hoặc quyền theo cấp bậc diễn đàn/khóa học
create table roles (
    id uuid primary key,
    slug varchar(64) not null, -- Định danh vai trò duy nhất (vd: admin, moderator)
    name varchar(120) not null, -- Tên hiển thị của vai trò
    scope varchar(32) not null, -- Phạm vi tác dụng (global, forum, course)
    permissions_json jsonb not null default '{}', -- Các quyền cụ thể lưu dạng JSON
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint roles_scope_check check (scope in ('global','forum','course'))
);

create unique index ux_roles_slug on roles(slug);

-- Bảng phân quyền: gán các vai trò từ bảng roles cho user
create table role_assignments (
    id uuid primary key,
    user_id uuid not null, -- ID người dùng được cấp quyền
    role_id uuid not null, -- ID vai trò được cấp
    scope_type varchar(32) not null, -- Phạm vi (global, forum, course)
    scope_id uuid null, -- ID của diễn đàn/khóa học cụ thể (null nếu scope là global)
    assigned_by_user_id uuid null, -- Người thực hiện cấp quyền
    starts_at timestamptz not null default now(), -- Thời điểm bắt đầu có hiệu lực
    ends_at timestamptz null, -- Thời điểm hết hạn (nếu quyền cấp tạm thời)
    revoked_at timestamptz null, -- Thời điểm bị thu hồi sớm
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

-- Bảng diễn đàn chính (Forum): tổ chức cộng đồng lớn nhất, chứa nhiều Categories
create table forums (
    id uuid primary key,
    slug varchar(120) not null, -- URL slug
    title varchar(255) not null, -- Tiêu đề diễn đàn
    description text null, -- Mô tả diễn đàn
    visibility varchar(32) not null default 'public', -- Phạm vi hiển thị (public, members, private)
    sort_order int not null default 0, -- Thứ tự ưu tiên hiển thị
    created_by_user_id uuid null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null, -- Thời điểm xóa mềm
    constraint forums_visibility_check check (visibility in ('public','members','private'))
);

create unique index ux_forums_slug_live on forums(slug) where deleted_at is null;
create index ix_forums_sort on forums(sort_order);

-- Bảng danh mục: phân loại nhỏ hơn bên trong mỗi diễn đàn, chứa các threads
create table categories (
    id uuid primary key,
    forum_id uuid not null, -- Diễn đàn chứa danh mục này
    slug varchar(120) not null, -- URL slug
    title varchar(255) not null,
    description text null,
    sort_order int not null default 0, -- Thứ tự hiển thị trong diễn đàn
    visibility varchar(32) not null default 'public', -- Phạm vi hiển thị
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

-- Bảng chủ đề (Thread): bài đăng chính trong diễn đàn, chứa các bài trả lời (posts)
create table threads (
    id uuid primary key,
    forum_id uuid not null, -- Diễn đàn chứa thread
    category_id uuid not null, -- Danh mục chứa thread
    author_user_id uuid not null, -- Người tạo thread
    title varchar(300) not null,
    slug varchar(350) not null, -- URL slug
    status varchar(32) not null default 'open', -- Trạng thái (open, locked, archived, hidden, deleted)
    pinned_at timestamptz null, -- Thời điểm ghim lên đầu (null = không ghim)
    locked_at timestamptz null, -- Thời điểm khóa bài (không cho reply thêm)
    last_post_id uuid null, -- ID bài reply gần nhất (dùng hiển thị nhanh)
    last_post_at timestamptz not null default now(), -- Thời điểm reply gần nhất (dùng sắp xếp theo hoạt động)
    reply_count int not null default 0, -- Bộ đếm denormalize: số reply
    view_count bigint not null default 0, -- Bộ đếm denormalize: lượt xem
    reaction_count int not null default 0, -- Bộ đếm denormalize: tổng reaction
    search_vector tsvector null, -- Vector tìm kiếm full-text PostgreSQL
    lock_version int not null default 0, -- Optimistic locking
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

-- Bảng bài viết (Post): câu trả lời/bình luận trong mỗi thread, hỗ trợ reply lồng nhau
create table posts (
    id uuid primary key,
    thread_id uuid not null, -- Thread chứa bài viết
    author_user_id uuid not null, -- Người viết
    parent_post_id uuid null, -- Bài viết cha (null nếu là reply trực tiếp, có giá trị nếu reply lồng)
    body_md text not null, -- Nội dung dạng Markdown (gốc do user nhập)
    body_html text not null, -- Nội dung dạng HTML (render từ Markdown, dùng hiển thị)
    status varchar(32) not null default 'visible', -- Trạng thái (visible, hidden, deleted, flagged)
    edit_count int not null default 0, -- Số lần chỉnh sửa
    edit_version int not null default 1, -- Phiên bản chỉnh sửa hiện tại
    reaction_count int not null default 0, -- Bộ đếm denormalize: reaction
    last_edited_at timestamptz null, -- Lần chỉnh sửa cuối cùng
    search_vector tsvector null, -- Vector tìm kiếm full-text
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint posts_status_check check (status in ('visible','hidden','deleted','flagged'))
);

create index ix_posts_thread_created on posts(thread_id, created_at);
create index ix_posts_author_created on posts(author_user_id, created_at desc);
create index ix_posts_status_created on posts(status, created_at desc);
create index ix_posts_search on posts using gin(search_vector);

-- Bảng cảm xúc bài viết: mỗi user chỉ được react 1 lần cho mỗi loại trên mỗi post
create table post_reactions (
    id uuid primary key,
    post_id uuid not null, -- Bài viết được react
    user_id uuid not null, -- Người react
    reaction_type varchar(32) not null, -- Loại cảm xúc (like, love, helpful,...)
    created_at timestamptz not null default now()
);

create unique index ux_post_reaction_once on post_reactions(post_id, user_id, reaction_type);
create index ix_post_reactions_post_type on post_reactions(post_id, reaction_type);
create index ix_post_reactions_user on post_reactions(user_id, created_at desc);

-- Bảng bookmark chủ đề: user lưu lại thread để xem sau
create table thread_bookmarks (
    id uuid primary key,
    thread_id uuid not null,
    user_id uuid not null,
    created_at timestamptz not null default now()
);

create unique index ux_thread_bookmark on thread_bookmarks(thread_id, user_id);
create index ix_thread_bookmarks_user on thread_bookmarks(user_id, created_at desc);

-- Bảng thẻ (Tags): từ khóa gắn vào thread để phân loại nội dung, hỗ trợ tìm kiếm
create table tags (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(120) not null,
    description text null,
    usage_count int not null default 0, -- Bộ đếm denormalize: số thread đang dùng tag này
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create unique index ux_tags_slug_live on tags(slug) where deleted_at is null;
create index ix_tags_usage on tags(usage_count desc);

-- Bảng liên kết thread - tag (quan hệ many-to-many)
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

-- Bảng báo cáo nội dung xấu (Content Flag): quản lý các khiếu nại từ user gửi lên ban quản trị
create table content_flags (
    id uuid primary key,
    reporter_user_id uuid not null, -- Người báo cáo
    target_type varchar(32) not null, -- Loại nội dung bị báo cáo (thread, post, material, course_review, user)
    target_id uuid not null, -- ID của nội dung tương ứng target_type
    reason varchar(120) not null, -- Lý do báo cáo (spam, toxic, plagiarism,...)
    note text null, -- Ghi chú chi tiết từ người báo cáo
    status varchar(32) not null default 'open', -- Trạng thái xử lý (open, reviewing, resolved, rejected)
    resolver_user_id uuid null, -- Người điều hành (moderator/admin) xử lý báo cáo
    resolved_at timestamptz null, -- Thời điểm xử lý xong
    created_at timestamptz not null default now(),
    constraint content_flags_target_check check (target_type in ('thread','post','material','course_review','user')),
    constraint content_flags_status_check check (status in ('open','reviewing','resolved','rejected'))
);

create index ix_content_flags_target on content_flags(target_type, target_id);
create index ix_content_flags_status_created on content_flags(status, created_at);

-- Bảng nhật ký kiểm duyệt: ghi lại các hành động hành chính của admin/mod (khoá thread, ẩn post, ban user...)
create table moderation_actions (
    id uuid primary key,
    actor_user_id uuid not null, -- Người thực hiện (admin/mod)
    action varchar(64) not null, -- Hành động (ban_user, delete_post, lock_thread,...)
    target_type varchar(32) not null, -- Loại đối tượng bị xử lý (user, thread, post,...)
    target_id uuid not null, -- ID đối tượng bị xử lý
    reason text null, -- Lý do kiểm duyệt
    metadata_json jsonb not null default '{}', -- Dữ liệu cấu trúc đính kèm hành động
    created_at timestamptz not null default now()
);

create index ix_moderation_target on moderation_actions(target_type, target_id, created_at desc);
create index ix_moderation_actor on moderation_actions(actor_user_id, created_at desc);

-- =========================
-- COURSES
-- =========================

-- Bảng khóa học: thông tin khóa học do giảng viên biên soạn
create table courses (
    id uuid primary key,
    slug varchar(160) not null, -- URL slug khóa học
    title varchar(255) not null,
    subtitle varchar(500) null, -- Phụ đề giới thiệu ngắn
    description_md text null, -- Mô tả đầy đủ (Markdown)
    description_html text null, -- Mô tả đầy đủ (HTML dùng để render)
    level varchar(32) not null default 'beginner', -- Cấp độ (beginner, intermediate, advanced)
    visibility varchar(32) not null default 'draft', -- Chế độ xem (draft, private, published, archived)
    owner_user_id uuid not null, -- Chủ sở hữu khóa học (người tạo đầu tiên)
    price_cents int not null default 0, -- Giá bán bằng cents (tránh sai số số thập phân)
    currency char(3) not null default 'VND', -- Đơn vị tiền tệ
    rating_avg numeric(3,2) not null default 0, -- Điểm đánh giá trung bình
    rating_count int not null default 0, -- Số lượt đánh giá
    published_at timestamptz null, -- Thời điểm xuất bản công khai
    lock_version int not null default 0, -- Optimistic locking
    search_vector tsvector null, -- Vector tìm kiếm full-text
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

-- Bảng giảng viên khóa học: quản lý danh sách và quyền hạn của nhóm giảng dạy trong khóa học
create table course_instructors (
    id uuid primary key,
    course_id uuid not null, -- ID khóa học
    user_id uuid not null, -- ID giảng viên
    role varchar(32) not null default 'instructor', -- Vai trò giảng dạy (owner, instructor, assistant)
    created_at timestamptz not null default now(),
    constraint course_instructors_role_check check (role in ('owner','instructor','assistant'))
);

create unique index ux_course_instructor on course_instructors(course_id, user_id);
create index ix_course_instructors_user on course_instructors(user_id);

-- Bảng chương/phần học: Gom nhóm các bài học trong một khóa học
create table course_sections (
    id uuid primary key,
    course_id uuid not null, -- Khóa học chứa chương này
    title varchar(255) not null,
    description text null,
    sort_order int not null default 0, -- Thứ tự sắp xếp chương trong khóa học
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null
);

create unique index ux_course_sections_order_live on course_sections(course_id, sort_order) where deleted_at is null;
create index ix_course_sections_course on course_sections(course_id, sort_order);

-- Bảng bài học: chi tiết bài học thuộc chương học, có thể là video, bài viết, quiz...
create table course_lessons (
    id uuid primary key,
    course_id uuid not null,
    section_id uuid not null, -- Chương chứa bài học
    slug varchar(160) not null, -- URL slug
    title varchar(255) not null,
    summary text null, -- Tóm tắt nội dung
    lesson_type varchar(32) not null, -- Loại bài học (video, article, quiz, assignment, live)
    body_md text null, -- Nội dung bài viết (Markdown, nếu type là article)
    body_html text null, -- Nội dung bài viết (HTML, dùng render)
    duration_seconds int null, -- Thời lượng bài học (giây, hữu ích với video)
    is_preview boolean not null default false, -- Cho phép xem thử miễn phí không cần đăng ký khóa học
    sort_order int not null default 0, -- Thứ tự sắp xếp trong chương
    status varchar(32) not null default 'draft', -- Trạng thái (draft, published, archived)
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

-- Bảng tệp tin tải lên: quản lý các file vật lý đã upload lên máy chủ lưu trữ (local/cloud)
create table uploaded_files (
    id uuid primary key,
    owner_user_id uuid not null, -- Người tải lên
    original_filename varchar(500) not null, -- Tên file gốc của người dùng
    storage_path text not null, -- Đường dẫn lưu trữ vật lý trên server (hoặc key trên cloud storage)
    public_url text null, -- URL truy cập trực tiếp nếu là file public
    file_category varchar(32) not null, -- Nhóm file (avatar, image, video, audio, attachment, log, other)
    mime_type varchar(120) not null, -- Loại định dạng tệp (ví dụ: image/png, application/pdf)
    size_bytes bigint not null, -- Dung lượng file tính bằng bytes
    checksum_sha256 char(64) null, -- Mã hash SHA-256 dùng để kiểm tra tính toàn vẹn và chống trùng lặp file
    visibility varchar(32) not null default 'private', -- Quyền truy cập tệp (private, protected, public)
    status varchar(32) not null default 'active', -- Trạng thái (active, quarantined - cách ly do nghi ngờ virus, deleted)
    created_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint uploaded_files_category_check check (file_category in ('avatar','image','video','audio','attachment','log','other')),
    constraint uploaded_files_visibility_check check (visibility in ('private','protected','public')),
    constraint uploaded_files_status_check check (status in ('active','quarantined','deleted'))
);

create index ix_uploaded_files_owner on uploaded_files(owner_user_id, created_at desc);
create index ix_uploaded_files_category on uploaded_files(file_category, created_at desc);
create index ix_uploaded_files_checksum on uploaded_files(checksum_sha256);

-- Bảng học liệu (Material): tài liệu học tập bổ sung trong khóa học hoặc bài học (file, link, embed...)
create table materials (
    id uuid primary key,
    owner_user_id uuid not null, -- Người tạo học liệu
    course_id uuid null, -- Liên kết với khóa học (null nếu là tài liệu độc lập)
    lesson_id uuid null, -- Liên kết với bài học cụ thể
    uploaded_file_id uuid null, -- Liên kết tới bảng uploaded_files (nếu là tài liệu dạng file tải lên)
    code varchar(64) null, -- Mã định danh học liệu (dành cho quản lý nội bộ)
    title varchar(255) not null,
    description text null,
    material_type varchar(32) not null, -- Loại học liệu (file, link, video, embed, text, question_bank)
    storage_path text null, -- Sao chép đường dẫn lưu trữ để truy vấn nhanh
    external_url text null, -- URL ngoài (nếu là dạng link/embed)
    mime_type varchar(120) null,
    size_bytes bigint null,
    checksum_sha256 char(64) null,
    visibility varchar(32) not null default 'private', -- Phạm vi truy cập (private, enrolled, members, public, paid)
    price_points int not null default 0, -- Giá trị quy đổi bằng điểm tích lũy (nếu cần dùng điểm mua tài liệu)
    version int not null default 1, -- Phiên bản tài liệu
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

-- Bảng lịch sử phiên bản học liệu: lưu lại các file cũ của học liệu khi được cập nhật
create table material_versions (
    id uuid primary key,
    material_id uuid not null, -- ID học liệu gốc
    version int not null, -- Số thứ tự phiên bản (1, 2, 3...)
    uploaded_file_id uuid null, -- File đính kèm của phiên bản này
    storage_path text null,
    checksum_sha256 char(64) null,
    size_bytes bigint null,
    created_by_user_id uuid not null, -- Người cập nhật phiên bản
    created_at timestamptz not null default now()
);

create unique index ux_material_version on material_versions(material_id, version);

-- =========================
-- ENROLLMENT / PROGRESS
-- =========================

-- Bảng đăng ký khóa học: ghi nhận quyền tham gia khóa học của học viên
create table course_enrollments (
    id uuid primary key,
    course_id uuid not null,
    user_id uuid not null, -- Học viên
    source varchar(32) not null, -- Nguồn gốc đăng ký (free, purchase - mua, invite - mời, membership - đặc quyền thành viên, admin - admin add vào)
    status varchar(32) not null default 'active', -- Trạng thái đăng ký
    enrolled_at timestamptz not null default now(), -- Thời điểm bắt đầu tham gia
    completed_at timestamptz null, -- Thời điểm hoàn thành khóa học
    expires_at timestamptz null, -- Thời điểm hết hạn truy cập khóa học (null = truy cập vĩnh viễn)
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

-- Bảng tiến độ học tập: theo dõi trạng thái xem/hoàn thành từng bài học của học viên
create table lesson_progress (
    id uuid primary key,
    course_id uuid not null,
    lesson_id uuid not null,
    user_id uuid not null,
    status varchar(32) not null default 'not_started', -- Trạng thái (chưa học, đang học, hoàn thành)
    progress_percent int not null default 0, -- Phần trăm đã hoàn thành (0-100), áp dụng cho video đang xem dở
    started_at timestamptz null, -- Thời điểm mở bài học lần đầu
    completed_at timestamptz null, -- Thời điểm học xong
    updated_at timestamptz not null default now(),
    constraint lesson_progress_status_check check (status in ('not_started','in_progress','completed')),
    constraint lesson_progress_percent_check check (progress_percent >= 0 and progress_percent <= 100)
);

create unique index ux_lesson_progress_user on lesson_progress(lesson_id, user_id);
create index ix_lesson_progress_user_course on lesson_progress(user_id, course_id, status);

-- =========================
-- QUIZ
-- =========================

-- Bảng bài trắc nghiệm (Quiz): gắn với bài học để kiểm tra kiến thức
create table quizzes (
    id uuid primary key,
    lesson_id uuid not null, -- Bài học chứa quiz (bài học có loại là quiz)
    title varchar(255) not null,
    pass_score_percent int not null default 60, -- Phần trăm điểm tối thiểu để qua bài (ví dụ 60%)
    max_attempts int null, -- Số lần làm tối đa (null = không giới hạn)
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint quizzes_pass_score_check check (pass_score_percent >= 0 and pass_score_percent <= 100)
);

create index ix_quizzes_lesson on quizzes(lesson_id);

-- Bảng câu hỏi: danh sách câu hỏi trong mỗi bài trắc nghiệm
create table quiz_questions (
    id uuid primary key,
    quiz_id uuid not null, -- Bài trắc nghiệm chứa câu hỏi này
    question_type varchar(32) not null, -- Loại câu hỏi (single - chọn một, multiple - chọn nhiều, text - tự luận/điền từ)
    prompt_md text not null, -- Đề bài dạng Markdown
    prompt_html text not null, -- Đề bài dạng HTML render sẵn
    points int not null default 1, -- Số điểm đạt được nếu làm đúng câu này
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint quiz_questions_type_check check (question_type in ('single','multiple','text'))
);

create index ix_quiz_questions_quiz_order on quiz_questions(quiz_id, sort_order);

-- Bảng đáp án: các phương án lựa chọn cho câu hỏi trắc nghiệm
create table quiz_answers (
    id uuid primary key,
    question_id uuid not null, -- Câu hỏi chứa đáp án này
    answer_text text not null, -- Nội dung đáp án
    is_correct boolean not null default false, -- Đánh dấu đáp án đúng
    sort_order int not null default 0
);

create index ix_quiz_answers_question_order on quiz_answers(question_id, sort_order);

-- Bảng lượt làm bài: ghi nhận chi tiết mỗi lần làm bài trắc nghiệm của học viên
create table quiz_attempts (
    id uuid primary key,
    quiz_id uuid not null,
    lesson_id uuid not null,
    course_id uuid not null,
    user_id uuid not null, -- Người thực hiện làm bài
    score_percent int null, -- Kết quả đạt được (phần trăm điểm số)
    passed boolean null, -- Kết quả Đạt/Không đạt
    started_at timestamptz not null default now(), -- Thời điểm bắt đầu làm
    submitted_at timestamptz null, -- Thời điểm nộp bài
    answers_json jsonb not null default '{}' -- Chi tiết các câu trả lời do user chọn/nhập (lưu dạng JSON để đối soát)
);

create index ix_quiz_attempts_user_quiz on quiz_attempts(user_id, quiz_id, submitted_at desc);
create index ix_quiz_attempts_course_user on quiz_attempts(course_id, user_id, submitted_at desc);

-- =========================
-- MEMBERSHIP / ENTITLEMENTS
-- =========================

-- Bảng gói thành viên: định nghĩa các gói dịch vụ trả phí định kỳ (Vip, Gold...)
create table membership_plans (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(255) not null,
    description text null,
    price_cents int not null default 0, -- Giá gói bằng cents (để xử lý tiền tệ chính xác)
    currency char(3) not null default 'VND',
    billing_interval varchar(32) not null, -- Chu kỳ thanh toán (month - tháng, year - năm, lifetime - trọn đời)
    status varchar(32) not null default 'active', -- Trạng thái gói (active, inactive, archived)
    features_json jsonb not null default '{}', -- Các đặc quyền đặc lợi đi kèm gói lưu dạng JSON
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint membership_interval_check check (billing_interval in ('month','year','lifetime')),
    constraint membership_plan_status_check check (status in ('active','inactive','archived'))
);

create unique index ux_membership_plans_slug on membership_plans(slug);
create index ix_membership_plans_status on membership_plans(status);

-- Bảng đăng ký gói thành viên: ghi nhận tình trạng đăng ký gói trả phí của user
create table memberships (
    id uuid primary key,
    user_id uuid not null,
    plan_id uuid not null, -- Gói đăng ký
    status varchar(32) not null, -- Trạng thái (trialing - dùng thử, active - đang hoạt động, past_due - quá hạn thanh toán, cancelled - đã hủy, expired - đã hết hạn)
    starts_at timestamptz not null, -- Thời điểm gói bắt đầu có hiệu lực
    current_period_start timestamptz null, -- Thời điểm bắt đầu chu kỳ thanh toán hiện tại
    current_period_end timestamptz null, -- Thời điểm kết thúc chu kỳ thanh toán hiện tại (cần thanh toán tiếp trước mốc này)
    cancel_at timestamptz null, -- Mốc thời gian được lên lịch để tự động hủy (ví dụ: hủy vào cuối chu kỳ)
    cancelled_at timestamptz null, -- Thời điểm thực tế user nhấn hủy đăng ký
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint memberships_status_check check (status in ('trialing','active','past_due','cancelled','expired'))
);

create index ix_memberships_user_status on memberships(user_id, status);
create index ix_memberships_plan_status on memberships(plan_id, status);
create index ix_memberships_period_end on memberships(current_period_end);

-- Bảng sự kiện membership: ghi log lịch sử các thay đổi liên quan đến gói (đăng ký mới, gia hạn, thanh toán thất bại...)
create table membership_events (
    id uuid primary key,
    membership_id uuid not null,
    user_id uuid not null,
    event_type varchar(64) not null, -- Loại sự kiện (created, renewed, payment_failed, cancelled...)
    payload_json jsonb not null default '{}', -- Thông tin chi tiết đi kèm sự kiện
    occurred_at timestamptz not null, -- Thời điểm sự kiện phát sinh thực tế
    created_at timestamptz not null default now()
);

create index ix_membership_events_membership on membership_events(membership_id, occurred_at);
create index ix_membership_events_user on membership_events(user_id, occurred_at);

-- Bảng quyền truy cập (Entitlement): quản lý trung tâm quyền truy cập nội dung (khóa học, tài liệu, forum) bất kể nguồn gốc
create table entitlements (
    id uuid primary key,
    user_id uuid null,
    membership_id uuid null, -- Quyền được cấp từ gói thành viên nào (nếu có)
    course_id uuid null, -- Quyền truy cập khóa học nào (nếu có)
    material_id uuid null, -- Quyền truy cập tài liệu nào (nếu có)
    forum_id uuid null, -- Quyền truy cập diễn đàn nào (nếu có)
    entitlement_type varchar(32) not null, -- Loại quyền (course_access, material_access, forum_access, badge_access)
    starts_at timestamptz not null default now(), -- Bắt đầu có quyền
    ends_at timestamptz null, -- Hết quyền truy cập (null = vĩnh viễn)
    source_type varchar(32) not null, -- Nguồn gốc cấp quyền (membership, purchase, admin, award, free)
    source_id uuid null, -- ID nguồn gốc (order_id, membership_id,...)
    created_at timestamptz not null default now(),
    revoked_at timestamptz null, -- Thời điểm bị thu hồi quyền (nếu bị hủy gói/hoàn tiền)
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

-- Bảng đơn hàng: ghi nhận giao dịch mua hàng của user
create table orders (
    id uuid primary key,
    user_id uuid not null,
    status varchar(32) not null default 'pending', -- Trạng thái đơn (pending, paid, failed, refunded, cancelled)
    subtotal_cents int not null default 0, -- Tổng giá trước chiết khấu/thuế
    discount_cents int not null default 0, -- Số tiền giảm giá
    tax_cents int not null default 0, -- Thuế
    total_cents int not null default 0, -- Tổng tiền cuối cùng phải thanh toán
    currency char(3) not null default 'VND',
    provider varchar(64) null, -- Cổng thanh toán sử dụng (vnpay, momo, stripe...)
    provider_order_id varchar(255) null, -- ID đơn hàng phía cổng thanh toán
    idempotency_key varchar(255) null, -- Mã chống trùng lặp đơn hàng (ngừa tạo đơn trùng khi gọi API nhiều lần)
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint orders_status_check check (status in ('pending','paid','failed','refunded','cancelled'))
);

create unique index ux_orders_provider_id on orders(provider, provider_order_id) where provider_order_id is not null;
create unique index ux_orders_idempotency on orders(idempotency_key) where idempotency_key is not null;
create index ix_orders_user_created on orders(user_id, created_at desc);
create index ix_orders_status_created on orders(status, created_at desc);

-- Bảng chi tiết đơn hàng: mỗi dòng là một mục hàng trong đơn (1 đơn có thể mua nhiều khóa/gói)
create table order_items (
    id uuid primary key,
    order_id uuid not null,
    item_type varchar(32) not null, -- Loại sản phẩm (course, membership, material)
    item_id uuid not null, -- ID khóa học/gói/tài liệu
    quantity int not null default 1,
    unit_price_cents int not null, -- Đơn giá
    total_cents int not null, -- Thành tiền (quantity × unit_price)
    metadata_json jsonb not null default '{}', -- Thông tin bổ sung (tên sản phẩm tại thời điểm mua, ...)
    constraint order_items_type_check check (item_type in ('course','membership','material'))
);

create index ix_order_items_order on order_items(order_id);
create index ix_order_items_item on order_items(item_type, item_id);

-- Bảng thanh toán: ghi nhận giao dịch thực tế với cổng thanh toán cho mỗi đơn hàng
create table payments (
    id uuid primary key,
    order_id uuid not null, -- Đơn hàng
    user_id uuid not null,
    provider varchar(64) not null, -- Cổng thanh toán (vnpay, momo...)
    provider_payment_id varchar(255) not null, -- ID giao dịch phía cổng thanh toán
    amount_cents int not null, -- Số tiền đã thanh toán
    currency char(3) not null default 'VND',
    status varchar(32) not null, -- Trạng thái (pending, paid, failed, refunded)
    paid_at timestamptz null, -- Thời điểm thanh toán thành công
    created_at timestamptz not null default now(),
    constraint payments_status_check check (status in ('pending','paid','failed','refunded'))
);

create unique index ux_payments_provider_payment on payments(provider, provider_payment_id);
create index ix_payments_order on payments(order_id);
create index ix_payments_user_created on payments(user_id, created_at desc);

-- Bảng sự kiện webhook thanh toán: lưu tất cả webhook callbacks từ cổng thanh toán gửi tới server
create table payment_webhook_events (
    id uuid primary key,
    provider varchar(64) not null,
    provider_event_id varchar(255) not null, -- ID sự kiện phía cổng thanh toán (dùng chống xử lý trùng lặp)
    event_type varchar(120) not null, -- Loại sự kiện (payment.success, payment.failed, refund.created...)
    payload_json jsonb not null, -- Toàn bộ nội dung webhook gốc từ cổng thanh toán
    signature_valid boolean not null default false, -- Xác thực chữ ký webhook có hợp lệ
    processed_at timestamptz null, -- Thời điểm đã xử lý xong
    created_at timestamptz not null default now()
);

create unique index ux_payment_webhook_provider_event on payment_webhook_events(provider, provider_event_id);
create index ix_payment_webhook_processed on payment_webhook_events(processed_at);

-- =========================
-- AWARDS / POINTS
-- =========================

-- Bảng định nghĩa giải thưởng/huy chương: khai báo các loại thành tích có thể cấp cho user
create table award_definitions (
    id uuid primary key,
    slug varchar(120) not null,
    name varchar(255) not null,
    description text null,
    icon_url text null, -- Ảnh/icon đại diện giải thưởng
    award_type varchar(32) not null, -- Loại (badge - huy hiệu, certificate - chứng chỉ, points - điểm, trophy - cúp)
    criteria_json jsonb not null default '{}', -- Điều kiện đạt giải thưởng (lưu JSON)
    is_active boolean not null default true, -- Có đang còn được sử dụng không
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint award_type_check check (award_type in ('badge','certificate','points','trophy'))
);

create unique index ux_award_definitions_slug_live on award_definitions(slug) where deleted_at is null;
create index ix_award_definitions_active on award_definitions(is_active);

-- Bảng giải thưởng của user: ghi nhận các giải thưởng/huy hiệu đã trao cho người dùng
create table user_awards (
    id uuid primary key,
    user_id uuid not null,
    award_definition_id uuid not null, -- Giải thưởng được trao
    awarded_by_user_id uuid null, -- Người trao thưởng (null nếu hệ thống tự cấp)
    source_type varchar(32) not null, -- Nguồn gốc đạt giải thưởng (course - học xong khóa, lesson, forum, membership, manual - trao tay, material)
    source_id uuid null, -- ID nguồn gốc (ví dụ: course_id)
    evidence_json jsonb not null default '{}', -- Minh chứng đạt giải (điểm số, ngày học xong...)
    awarded_at timestamptz not null default now(),
    revoked_at timestamptz null, -- Thời điểm bị thu hồi thưởng (nếu có)
    revoke_reason text null, -- Lý do thu hồi
    constraint user_awards_source_check check (source_type in ('course','lesson','forum','membership','manual','material'))
);

create unique index ux_user_award_active
on user_awards(user_id, award_definition_id, source_type, coalesce(source_id, '00000000-0000-0000-0000-000000000000'::uuid))
where revoked_at is null;

create index ix_user_awards_user on user_awards(user_id, awarded_at desc);
create index ix_user_awards_definition on user_awards(award_definition_id, awarded_at desc);

-- Bảng sổ cái điểm số (Points Ledger): ghi nhận mọi biến động cộng/trừ điểm tích lũy của user
create table points_ledger (
    id uuid primary key,
    user_id uuid not null,
    delta int not null, -- Lượng thay đổi điểm số (số dương = cộng điểm, số âm = trừ điểm)
    reason varchar(255) not null, -- Lý do biến động điểm (ví dụ: write_post, buy_material, admin_tweak...)
    source_type varchar(32) not null, -- Loại đối tượng liên quan (forum, material, payment...)
    source_id uuid null, -- ID đối tượng tương ứng
    created_at timestamptz not null default now(),
    constraint points_delta_nonzero check (delta <> 0)
);

create index ix_points_ledger_user on points_ledger(user_id, created_at desc);
create index ix_points_ledger_source on points_ledger(source_type, source_id);

-- Bảng chứng chỉ: cấp khi học viên hoàn thành khóa học
create table certificates (
    id uuid primary key,
    user_id uuid not null,
    course_id uuid null, -- Khóa học cấp chứng chỉ
    award_definition_id uuid null, -- Liên kết với định nghĩa giải thưởng tương ứng
    certificate_no varchar(120) not null, -- Mã số chứng chỉ duy nhất
    verification_code varchar(120) not null, -- Mã xác thực trực tuyến để kiểm tra thật giả
    issued_at timestamptz not null default now(), -- Ngày cấp
    revoked_at timestamptz null, -- Ngày thu hồi (nếu bị hủy bỏ)
    metadata_json jsonb not null default '{}'
);

create unique index ux_certificates_no on certificates(certificate_no);
create unique index ux_certificates_verify on certificates(verification_code);
create index ix_certificates_user on certificates(user_id, issued_at desc);
create index ix_certificates_course on certificates(course_id);

-- =========================
-- NOTIFICATIONS
-- =========================

-- Bảng thông báo: chứa thông báo đẩy tới người dùng
create table notifications (
    id uuid primary key,
    user_id uuid not null, -- Người nhận thông báo
    type varchar(64) not null, -- Loại thông báo (new_reply, course_published, point_awarded...)
    title varchar(255) not null,
    body text null, -- Nội dung ngắn của thông báo
    data_json jsonb not null default '{}', -- Các dữ liệu đính kèm phục vụ việc ấn vào thông báo sẽ mở ra màn hình nào
    read_at timestamptz null, -- Thời điểm user đã đọc (null = chưa đọc)
    created_at timestamptz not null default now()
);

create index ix_notifications_user_unread_created on notifications(user_id, read_at, created_at desc);

-- Bảng tùy chọn thông báo: người dùng cài đặt muốn nhận/từ chối loại thông báo nào qua kênh nào
create table notification_preferences (
    id uuid primary key,
    user_id uuid not null,
    channel varchar(32) not null, -- Kênh nhận (web, email, push - thông báo điện thoại di động)
    type varchar(64) not null, -- Loại sự kiện (như type ở bảng notifications)
    enabled boolean not null default true, -- Có đồng ý nhận không (true = bật, false = tắt)
    updated_at timestamptz not null default now(),
    constraint notification_channel_check check (channel in ('web','email','push'))
);

create unique index ux_notification_pref on notification_preferences(user_id, channel, type);

-- =========================
-- SEARCH / OUTBOX / AUDIT
-- =========================

-- Bảng công việc lập chỉ mục tìm kiếm (Search Index Job): hàng đợi để worker đồng bộ dữ liệu vào search_vector
create table search_index_jobs (
    id uuid primary key,
    entity_type varchar(32) not null, -- Loại thực thể (thread, post, course, material)
    entity_id uuid not null, -- ID thực thể cần lập chỉ mục
    operation varchar(32) not null, -- Hoạt động (upsert - cập nhật/thêm mới, delete - xóa khỏi index)
    status varchar(32) not null default 'pending', -- Trạng thái (pending, processing, done, failed)
    attempts int not null default 0, -- Số lần thử đồng bộ
    available_at timestamptz not null default now(), -- Lên lịch thời gian chạy
    created_at timestamptz not null default now(),
    processed_at timestamptz null, -- Thời gian hoàn tất đồng bộ
    constraint search_jobs_operation_check check (operation in ('upsert','delete')),
    constraint search_jobs_status_check check (status in ('pending','processing','done','failed'))
);

create index ix_search_jobs_pending on search_index_jobs(status, available_at);
create index ix_search_jobs_entity on search_index_jobs(entity_type, entity_id);

-- Bảng outbox events: hàng đợi sự kiện domain để xử lý bất đồng bộ (pattern Transactional Outbox)
create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(64) not null, -- Loại aggregate gốc phát sinh sự kiện (User, Course, Thread...)
    aggregate_id uuid not null, -- ID của aggregate đó
    event_type varchar(120) not null, -- Loại sự kiện (UserRegistered, CoursePublished, ThreadCreated...)
    payload_json jsonb not null default '{}', -- Dữ liệu chi tiết sự kiện
    status varchar(32) not null default 'pending', -- Trạng thái xử lý (pending, processing, done, failed)
    attempts int not null default 0, -- Số lần thử xử lý
    available_at timestamptz not null default now(), -- Thời điểm sẵn sàng để xử lý (hỗ trợ retry với delay)
    created_at timestamptz not null default now(),
    processed_at timestamptz null, -- Thời điểm xử lý xong
    constraint outbox_status_check check (status in ('pending','processing','done','failed'))
);

create index ix_outbox_pending on outbox_events(status, available_at);
create index ix_outbox_aggregate on outbox_events(aggregate_type, aggregate_id, created_at desc);

-- Bảng nhật ký kiểm toán (Audit Log): ghi lại tất cả hành động quan trọng trên hệ thống để truy vết
create table audit_log (
    id uuid primary key,
    actor_user_id uuid null, -- Người thực hiện hành động (null nếu là hệ thống)
    action varchar(120) not null, -- Hành động (create_user, update_course, delete_post...)
    entity_type varchar(64) not null, -- Loại đối tượng bị tác động
    entity_id uuid not null, -- ID đối tượng
    before_json jsonb null, -- Snapshot trạng thái trước khi thay đổi
    after_json jsonb null, -- Snapshot trạng thái sau khi thay đổi
    ip_address inet null, -- Địa chỉ IP người thực hiện
    user_agent text null, -- Thông tin trình duyệt/thiết bị
    created_at timestamptz not null default now()
);

create index ix_audit_entity on audit_log(entity_type, entity_id, created_at desc);
create index ix_audit_actor on audit_log(actor_user_id, created_at desc);

-- =========================
-- OPTIONAL ORPHAN SCAN RESULTS
-- =========================

-- Bảng kết quả quét orphan: ghi nhận các record tham chiếu đến ID không tồn tại (do không có FK constraint)
create table orphan_scan_results (
    id uuid primary key,
    source_table varchar(120) not null, -- Bảng chứa tham chiếu
    source_column varchar(120) not null, -- Cột chứa foreign key
    source_id uuid not null, -- Giá trị ID của record bị orphan
    expected_table varchar(120) not null, -- Bảng đích mà nó đáng lẽ phải tham chiếu đến
    expected_id uuid not null, -- Giá trị ID không tìm thấy ở bảng đích
    status varchar(32) not null default 'open', -- Trạng thái (open - chưa xử lý, ignored - bỏ qua, fixed - đã sửa)
    detected_at timestamptz not null default now(), -- Thời điểm phát hiện
    resolved_at timestamptz null, -- Thời điểm đã giải quyết
    note text null, -- Ghi chú xử lý
    constraint orphan_scan_status_check check (status in ('open','ignored','fixed'))
);

create index ix_orphan_scan_open on orphan_scan_results(status, detected_at desc);
create index ix_orphan_scan_source on orphan_scan_results(source_table, source_id);

-- =========================
-- UPDATED_AT TRIGGER
-- =========================
-- Function tự động cập nhật cột updated_at mỗi khi có UPDATE trên bảng
-- Được gắn vào các bảng có cột updated_at để tự động ghi timestamp

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
