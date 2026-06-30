# Voucher System Design

**Date:** 2026-07-01
**Status:** Approved

## Overview

Voucher/coupon system cho FuOverflow: admin tạo mã voucher giảm giá points cho các giao dịch mua bằng points (source purchase, membership subscribe, coursera request). User nhập mã khi checkout để nhận giảm giá.

## Requirements

### Functional
- Admin tạo/sửa/bật tắt voucher codes
- Hai dạng discount: percentage (%) và fixed (trừ số points cố định)
- Voucher có số lượng giới hạn, xử lý concurrent redemption an toàn
- Mỗi voucher có thể áp dụng cho 1 hoặc nhiều loại giao dịch (source, membership, coursera)
- User nhập mã voucher khi checkout, chỉ 1 voucher per transaction
- Preview trước khi commit checkout

### Điều kiện voucher (6 loại)
1. **Đơn hàng tối thiểu** (min_order_points) — voucher chỉ áp dụng khi đơn >= N points
2. **Giảm tối đa** (max_discount_points) — cap cho voucher %, VD: giảm 20% nhưng tối đa 1000 points
3. **Giới hạn mỗi user** (max_usage_per_user) — mỗi user dùng tối đa N lần
4. **Thời hạn voucher** (starts_at → ends_at) — chỉ valid trong khoảng thời gian
5. **Yêu cầu membership** (required_membership_slugs) — chỉ user có membership cụ thể
6. **Chỉ cho user cụ thể** (voucher_user_assignments) — admin assign cho list user

### Concurrency
- Atomic UPDATE: `UPDATE vouchers SET used_count = used_count + 1 WHERE id = ? AND used_count < max_usage AND active = true`
- Affected rows = 0 → voucher đã hết hoặc bị deactivate

## Architecture

### Approach: Module riêng `backend/voucher/`
- Đúng pattern modular monolith hiện tại
- Voucher logic tập trung, các module source/course/membership gọi `VoucherService`
- Follow pattern giống membership, deposit modules

## Database Schema

### Bảng `vouchers`

| Column | Type | Description |
|--------|------|-------------|
| `id` | uuid PK | |
| `code` | varchar(50) UNIQUE | Mã voucher (VD: `SALE20`), uppercase |
| `description` | text | Mô tả cho admin |
| `discount_type` | varchar(16) | `percentage` hoặc `fixed` |
| `discount_value` | int | Giá trị giảm (20 = 20% hoặc 500 = 500 points) |
| `max_discount_points` | int NULL | Cap giảm tối đa cho voucher % (NULL = không giới hạn) |
| `min_order_points` | int default 0 | Đơn hàng tối thiểu |
| `max_usage` | int | Tổng số lượt dùng tối đa |
| `used_count` | int default 0 | Số lượt đã dùng (atomic update) |
| `max_usage_per_user` | int default 1 | Mỗi user dùng tối đa N lần |
| `applicable_types` | varchar(255) | Comma-separated: `source,membership,coursera` |
| `required_membership_slugs` | varchar(255) NULL | Slug membership yêu cầu (NULL = all) |
| `starts_at` | timestamptz | Bắt đầu hiệu lực |
| `ends_at` | timestamptz | Hết hạn |
| `active` | boolean default true | Admin toggle on/off |
| `created_by` | uuid | Admin tạo |
| `created_at` | timestamptz default now() | |
| `updated_at` | timestamptz default now() | |

Check constraint: `discount_type IN ('percentage', 'fixed')`.
Partial unique index: `CREATE UNIQUE INDEX ON vouchers (upper(code)) WHERE active = true`.

### Bảng `voucher_user_assignments`

| Column | Type | Description |
|--------|------|-------------|
| `id` | uuid PK | |
| `voucher_id` | uuid | Ref vouchers |
| `user_id` | uuid | User được assign |
| `created_at` | timestamptz default now() | |

Unique index trên `(voucher_id, user_id)`.

### Bảng `voucher_redemptions`

| Column | Type | Description |
|--------|------|-------------|
| `id` | uuid PK | |
| `voucher_id` | uuid | |
| `user_id` | uuid | |
| `transaction_type` | varchar(32) | `source`, `membership`, `coursera` |
| `transaction_id` | uuid | ID của purchase/subscription/request |
| `original_points` | int | Giá gốc |
| `discount_points` | int | Số points được giảm |
| `final_points` | int | Số points thực trả |
| `created_at` | timestamptz default now() | |

Index trên `(voucher_id, user_id)` cho query đếm per-user usage.

## Backend Module Structure

```
backend/voucher/
  src/main/java/com/fuoverflow/voucher/
    VoucherModule.java
    api/
      VoucherAdminController.java
      VoucherController.java           # User preview endpoint
      dto/
        CreateVoucherRequest.java
        UpdateVoucherRequest.java
        VoucherResponse.java
        AssignUsersRequest.java
        VoucherRedemptionResponse.java
        VoucherPreviewRequest.java
        VoucherPreviewResponse.java
    application/
      VoucherService.java              # Validate & redeem (public API cho modules khác)
      VoucherAdminService.java         # Admin CRUD
    domain/
      VoucherDiscountResult.java       # Record kết quả tính giảm giá
    persistence/
      VoucherEntity.java
      VoucherRepository.java
      VoucherUserAssignmentEntity.java
      VoucherUserAssignmentRepository.java
      VoucherRedemptionEntity.java
      VoucherRedemptionRepository.java
```

## API Endpoints

### Admin API

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| `POST` | `/api/v1/admin/vouchers` | `voucher.admin:create` | Tạo voucher |
| `GET` | `/api/v1/admin/vouchers` | `voucher.admin:read` | List (paginated, filter) |
| `GET` | `/api/v1/admin/vouchers/{id}` | `voucher.admin:read` | Chi tiết + stats |
| `PUT` | `/api/v1/admin/vouchers/{id}` | `voucher.admin:update` | Sửa voucher |
| `PATCH` | `/api/v1/admin/vouchers/{id}/toggle` | `voucher.admin:update` | Bật/tắt active |
| `POST` | `/api/v1/admin/vouchers/{id}/assignments` | `voucher.admin:update` | Assign user list |
| `DELETE` | `/api/v1/admin/vouchers/{id}/assignments/{userId}` | `voucher.admin:update` | Remove assignment |
| `GET` | `/api/v1/admin/vouchers/{id}/redemptions` | `voucher.admin:read` | Lịch sử sử dụng |

### User API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/vouchers/preview` | Preview discount (không redeem) |

Preview request: `{ "code": "SALE20", "transactionType": "source", "originalPoints": 90000 }`
Preview response: `{ "valid": true, "discountPoints": 18000, "finalPoints": 72000, "message": "Giảm 20%, tối đa 50000 points" }`

## VoucherService — Core Logic

### Public methods

```java
// Preview: tính giảm giá, không commit
VoucherDiscountResult preview(String code, UUID userId,
    String transactionType, int originalPoints)

// Redeem: validate + atomic update used_count + tạo redemption record
VoucherDiscountResult redeem(String code, UUID userId,
    String transactionType, UUID transactionId, int originalPoints)
```

### Validation chain (cả preview và redeem)

1. Voucher tồn tại và `active = true`
2. Thời gian hiện tại nằm trong `starts_at` — `ends_at`
3. `used_count < max_usage`
4. `transactionType` nằm trong `applicable_types`
5. `originalPoints >= min_order_points`
6. User chưa vượt `max_usage_per_user` (đếm từ `voucher_redemptions`)
7. Nếu `required_membership_slugs` != null → query `memberships` table cho user có active membership với slug matching (voucher module depend vào membership module hoặc truy vấn trực tiếp qua repository)
8. Nếu có `voucher_user_assignments` rows cho voucher → check user nằm trong list (nếu không có rows → voucher mở cho tất cả)
9. Tính discount:
   - `percentage`: `originalPoints * value / 100`, cap bởi `max_discount_points`
   - `fixed`: `min(value, originalPoints)` (không giảm quá giá gốc)

### VoucherDiscountResult

```java
record VoucherDiscountResult(
    boolean valid,
    UUID voucherId,
    int discountPoints,
    int finalPoints,
    String message
)
```

## Integration vào Checkout Flows

Mỗi module thêm optional `voucherCode` vào request DTO:

```java
// Source
record CreatePurchaseRequest(UUID catalogItemId, @Nullable String voucherCode) {}

// Membership
record SubscribeMembershipRequest(String planSlug, @Nullable String voucherCode) {}

// Coursera
record CreateCourseraRequestBody(UUID catalogItemId, ..., @Nullable String voucherCode) {}
```

Trong service, nếu `voucherCode != null`:
1. Gọi `voucherService.redeem(code, userId, type, txId, pricePoints)`
2. Dùng `result.finalPoints()` thay vì `pricePoints` khi gọi `walletService.debit()`

Nếu `voucherCode == null` → flow giữ nguyên hiện tại, zero impact.

## Frontend — Admin Panel (Fuexam-admin)

### Sidebar
Thêm menu "Vouchers" với icon `Ticket` (Lucide), permission `voucher.admin:read`.

### Pages

```
Fuexam-admin/app/vouchers/
  page.tsx                    # List vouchers (table + filters)
  create/page.tsx             # Form tạo voucher
  [id]/page.tsx               # Detail + edit
  [id]/assignments/page.tsx   # Quản lý user assignments
  [id]/redemptions/page.tsx   # Lịch sử sử dụng
```

### List page
- Table: Code, Type, Value, Usage (used/max progress), Status, Applicable Types, Date Range
- Filters: status, discount type, applicable type
- Search by code

### Create/Edit form
- Code (uppercase auto-transform), Description, Discount Type (radio), Discount Value
- Max Discount Points (chỉ hiện khi percentage), Min Order Points
- Max Usage, Max Usage Per User
- Applicable Types (checkbox group), Required Membership (multi-select)
- Date Range (date pickers), Active toggle

### Detail page
- Stats cards: usage progress, total discount given
- Tabs: Info | Assignments | Redemptions

### Assignments page
- Table user đã assign, search + add/remove user

### Redemptions page
- Read-only table: User, Type, Original, Discount, Final, Date

## Frontend — User (Fuexam)

Ở trang checkout source/membership/coursera:
- Input "Mã voucher" + button "Áp dụng"
- Gọi `POST /api/v1/vouchers/preview` khi apply
- Hiển thị: giá gốc ~~90,000~~ → giá mới 72,000 (giảm 18,000)
- Button "Xóa" để bỏ voucher
- Submit gửi kèm `voucherCode` trong request body

## Error Cases

| Case | Response |
|------|----------|
| Mã không tồn tại | 404: "Mã voucher không hợp lệ" |
| Voucher hết hạn | 400: "Voucher đã hết hạn" |
| Voucher chưa bắt đầu | 400: "Voucher chưa có hiệu lực" |
| Hết số lượng | 400: "Voucher đã hết lượt sử dụng" |
| User đã dùng hết lượt | 400: "Bạn đã sử dụng hết lượt cho voucher này" |
| Đơn chưa đủ min | 400: "Đơn hàng cần tối thiểu N points" |
| Loại giao dịch không phù hợp | 400: "Voucher không áp dụng cho loại giao dịch này" |
| Membership không đủ điều kiện | 400: "Voucher yêu cầu membership [X]" |
| User không trong danh sách | 403: "Voucher này không dành cho bạn" |
| Race condition (atomic update = 0) | 409: "Voucher vừa hết, vui lòng thử voucher khác" |

## Permissions

Thêm vào RBAC permission catalog:
- `voucher.admin:read`
- `voucher.admin:create`
- `voucher.admin:update`
- `voucher.admin:delete`
