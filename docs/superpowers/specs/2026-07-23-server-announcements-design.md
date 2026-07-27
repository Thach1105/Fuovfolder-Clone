# Server Announcements System Design

## Overview

Mở rộng broadcast module hiện có để hỗ trợ **server-wide announcements** — thông báo do admin tạo, hiển thị dạng marquee chạy ngang trên toàn server cho tất cả người dùng. Tách biệt hoàn toàn với hệ thống broadcast event-driven hiện tại (deposit.completed), tái sử dụng infrastructure SSE + Redis pub/sub.

## Key Decisions

| Quyết định | Chọn | Lý do |
|---|---|---|
| Rich text format | HTML (TipTap editor) | Admin quen WYSIWYG, output nhỏ gọn, frontend render trực tiếp |
| Kiểu hiển thị | 1 marquee bar cố định, xoay vòng theo priority | Đơn giản, UX nhất quán |
| Quan hệ với broadcast cũ | Tách biệt — bảng `announcements` mới | Announcement có lifecycle, scheduling, rich text — khác bản chất event-driven |
| Lifecycle management | Cron job backend (`@Scheduled` mỗi phút) | Đảm bảo thông báo xuất hiện/biến mất đúng giờ, push realtime qua SSE |
| Step interval | Frontend tự xoay vòng | Backend chỉ push khi danh sách active thay đổi, giảm tải server |
| Sync cơ chế | SSE event `announcement.sync` chứa toàn bộ danh sách active | Client mới kết nối nhận initial state, sau đó nhận realtime update |

## 1. Database Schema

### Migration: `V{next}__announcement_system.sql`

```sql
CREATE TABLE announcements (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title           varchar(200)  NOT NULL,
    content_html    text          NOT NULL,
    background_color varchar(9)   NOT NULL DEFAULT '#1e40af',
    link_url        varchar(2048),
    link_label      varchar(100),
    priority        int           NOT NULL DEFAULT 0,
    scroll_speed    int           NOT NULL DEFAULT 50,
    step_seconds    int           NOT NULL DEFAULT 300,
    status          varchar(16)   NOT NULL DEFAULT 'DRAFT',
    start_at        timestamptz   NOT NULL,
    end_at          timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_announcements_status_priority
    ON announcements(status, priority DESC);

CREATE INDEX ix_announcements_start_end
    ON announcements(start_at, end_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'EXPIRED'));

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_end_after_start
    CHECK (end_at > start_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_step_positive
    CHECK (step_seconds > 0);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_scroll_speed_positive
    CHECK (scroll_speed > 0);
```

### Migration: `V{next}__add_announcement_permissions.sql`

Seed permissions `announcement.admin:read`, `announcement.admin:write`. Grant cả hai cho ADMIN, read cho SUB_ADMIN.

### Field descriptions

- `title`: tên nội bộ cho admin quản lý, không hiển thị cho user
- `content_html`: HTML output từ TipTap (chứa inline style cho màu sắc)
- `background_color`: hex color cho nền marquee bar
- `link_url` + `link_label`: CTA link (optional)
- `priority`: số cao hơn = hiển thị trước trong vòng xoay
- `scroll_speed`: tốc độ chạy chữ (pixels/giây)
- `step_seconds`: khoảng cách giữa mỗi lần xuất hiện trong vòng xoay (giây)
- `status`: DRAFT → SCHEDULED → ACTIVE → EXPIRED
- `start_at` / `end_at`: khung thời gian hoạt động
- `created_by`: UUID của admin tạo (không FK)

## 2. Backend API

### Admin API — `AnnouncementAdminController`

Base path: `/api/v1/admin/announcements`

| Method | Path | Mô tả | Permission |
|---|---|---|---|
| `GET` | `/` | Danh sách, filter theo status, phân trang | `announcement.admin:read` |
| `GET` | `/{id}` | Chi tiết 1 announcement | `announcement.admin:read` |
| `POST` | `/` | Tạo mới | `announcement.admin:write` |
| `PUT` | `/{id}` | Cập nhật | `announcement.admin:write` |
| `DELETE` | `/{id}` | Xóa (chỉ DRAFT/SCHEDULED) | `announcement.admin:write` |
| `POST` | `/{id}/activate` | Kích hoạt ngay lập tức | `announcement.admin:write` |
| `POST` | `/{id}/deactivate` | Dừng sớm → EXPIRED | `announcement.admin:write` |
| `GET` | `/{id}/preview` | Preview data giống user sẽ thấy | `announcement.admin:read` |

### User API

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/v1/broadcasts/stream` | SSE stream (hiện có), nhận thêm event `announcement.sync` |
| `GET` | `/api/v1/announcements/active` | REST fallback — danh sách active (public) |

### Request DTO

```java
public record AnnouncementRequest(
    @NotBlank String title,
    @NotBlank String contentHtml,
    @NotBlank String backgroundColor,
    String linkUrl,
    String linkLabel,
    int priority,
    @Min(10) int scrollSpeed,
    @Min(60) int stepSeconds,
    @NotNull Instant startAt,
    @NotNull Instant endAt
) {}
```

### Response DTO

```java
public record AnnouncementResponse(
    UUID id,
    String title,
    String contentHtml,
    String backgroundColor,
    String linkUrl,
    String linkLabel,
    int priority,
    int scrollSpeed,
    int stepSeconds,
    String status,
    Instant startAt,
    Instant endAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {}
```

### SSE `announcement.sync` payload

```json
{
  "eventType": "announcement.sync",
  "announcements": [
    {
      "id": "uuid",
      "contentHtml": "<span style='color:red'>SALE</span> 50%!",
      "backgroundColor": "#1e40af",
      "linkUrl": "/events/sale",
      "linkLabel": "Xem ngay",
      "priority": 10,
      "scrollSpeed": 50,
      "stepSeconds": 300
    }
  ],
  "timestamp": "2026-07-23T10:00:00Z"
}
```

User-facing payload không chứa `title`, `status`, `createdBy`, `startAt`, `endAt`.

## 3. Backend Service & Cron Job

### AnnouncementService

```
create(request, adminUserId)
  → validate end_at > start_at
  → status = start_at <= now ? ACTIVE : SCHEDULED (nếu admin chọn lên lịch) hoặc DRAFT
  → save → nếu ACTIVE → publishSync()

update(id, request)
  → DRAFT/SCHEDULED: cho phép sửa toàn bộ
  → ACTIVE: chỉ sửa content_html, background_color, link, priority, scroll_speed, step_seconds
  → save → nếu đang ACTIVE → publishSync()

delete(id)
  → chỉ DRAFT/SCHEDULED

activate(id)
  → force ACTIVE ngay, bất kể start_at → publishSync()

deactivate(id)
  → ACTIVE → EXPIRED → publishSync()

getActiveAnnouncements()
  → query status = ACTIVE, order by priority DESC

publishSync()
  → build announcement.sync payload từ getActiveAnnouncements()
  → publish qua BroadcastPublisher (Redis pub/sub hiện có)
```

### AnnouncementScheduler — `@Scheduled(fixedRate = 60_000)`

```
Mỗi phút:
1. SCHEDULED where start_at <= now() → batch update → ACTIVE → changed = true
2. ACTIVE where end_at <= now() → batch update → EXPIRED → changed = true
3. if (changed) → publishSync()
```

Worst case trễ tối đa 1 phút — chấp nhận được.

### SSE integration

```
publishSync()
  → new BroadcastMessage("announcement.sync", jsonPayload, data)
  → RedisBroadcastPublisher.publish()
  → Redis "broadcast:all"
  → RedisBroadcastListener → BroadcastEmitterPool fan-out
```

Initial state: sửa `BroadcastController.stream()` — sau khi register emitter, gửi ngay danh sách active announcements.

## 4. Admin Frontend (Fuexam-admin)

### Trang danh sách: `/announcements`

- Tabs filter: Tất cả / DRAFT / SCHEDULED / ACTIVE / EXPIRED
- Card mỗi announcement: status badge, title, thời gian, interval, actions
- Actions theo status:
  - DRAFT: Preview, Sửa, Xóa
  - SCHEDULED: Preview, Sửa, Xóa, Kích hoạt ngay
  - ACTIVE: Preview, Sửa, Dừng sớm
  - EXPIRED: Preview (chỉ xem)

### Trang tạo/sửa: `/announcements/new`, `/announcements/[id]/edit`

- Tên nội bộ (text input)
- TipTap rich text editor (extensions: Color, Bold, Italic, Underline — không heading/image/list)
- Color picker cho background
- Link URL + label (optional)
- Priority (number input)
- Tốc độ chạy (number input, px/s)
- Lặp lại mỗi (number input, đơn vị phút, convert → giây khi gọi API)
- Date-time picker cho start_at / end_at
- Live preview: marquee chạy thật với đúng tốc độ, màu nền, nội dung HTML
- 3 nút submit: Lưu nháp (DRAFT), Lên lịch (SCHEDULED), Kích hoạt ngay (ACTIVE)

### Dependencies mới

- `@tiptap/react`
- `@tiptap/starter-kit`
- `@tiptap/extension-color`
- `@tiptap/extension-text-style`

### Sidebar

Thêm link `/announcements` gated by `announcement.admin:read`, dưới mục broadcast configs hiện có.

## 5. User Frontend (Fuexam)

### Component: `AnnouncementBar`

- Fixed ở top page, trên header
- Không có announcement active → không render, không chiếm space
- Có announcement → slide-down animation xuất hiện

### Hook: `useAnnouncementStream()`

- Connect SSE tới `/api/v1/broadcasts/stream`
- Lắng nghe event type `announcement.sync`
- Nhận initial state ngay khi kết nối
- Auto reconnect (EventSource built-in)
- Lưu danh sách active announcements vào state

### Hook: `useAnnouncementRotation(announcements)`

- Sort theo priority DESC
- Hiển thị announcement priority cao nhất trước
- Mỗi cái hiển thị đủ `stepSeconds` rồi chuyển sang cái tiếp
- Hết danh sách → quay lại đầu
- Khi danh sách thay đổi (SSE sync) → reset, bắt đầu từ priority cao nhất

### Component: `MarqueeStrip`

- CSS animation `translateX(100%) → translateX(-100%)`, duration tính từ `scrollSpeed`
- Background color từ `backgroundColor`
- Content: `dangerouslySetInnerHTML` + DOMPurify sanitize
- Link: nếu có `linkUrl` → badge "Xem ngay →"
- Transition fade khi chuyển giữa các announcement

### HTML Sanitize (DOMPurify)

```js
const ALLOWED_TAGS = ['span', 'strong', 'em', 'u', 'br'];
const ALLOWED_ATTR = ['style'];
const ALLOWED_STYLES = ['color', 'background-color', 'font-weight', 'font-style', 'text-decoration'];
```

## 6. Permissions

| Permission | ADMIN | SUB_ADMIN |
|---|---|---|
| `announcement.admin:read` | yes | yes |
| `announcement.admin:write` | yes | no |

## 7. Security

- HTML sanitize bằng DOMPurify ở frontend trước khi render
- Backend không validate HTML content — trust admin input, sanitize ở output layer
- SSE endpoint public (đã được cho phép trong SecurityConfig)
- REST `/api/v1/announcements/active` cũng public
- Admin endpoints require authentication + permission check

## 8. Out of Scope (có thể mở rộng sau)

- Target audience (filter theo role/nhóm user)
- User dismiss per announcement (cần tracking per-user state)
- Advanced animation effects
- Multiple display types (banner tĩnh, toast popup)
- A/B testing announcements
- Analytics (view count, click-through rate)
