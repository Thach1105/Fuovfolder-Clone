# FuOverflow Frontend

Giao diện web clone FuOverflow, gắn trực tiếp với backend Spring Boot.

## Yêu cầu

- Node.js 20+
- Backend chạy tại `http://localhost:8080`

## Cài đặt

```bash
cd frontend
cp .env.local.example .env.local
npm install
```

## Chạy dev

```bash
# Terminal 1 — Backend
cd backend
docker compose up -d postgres redis
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — Frontend
cd frontend
npm run dev
```

Mở http://localhost:3000

## Test flow trên UI

1. **Đăng ký** tại `/register` → nhận verification token (dev)
2. **Xác minh email** tại `/verify-email`
3. **Đăng nhập** tại `/login`
4. **Xem/sửa profile** tại `/profile` hoặc `/settings/profile`
5. **Đăng xuất** qua header

## API đã gắn

| Trang | API |
|-------|-----|
| Register | `POST /api/v1/auth/register` |
| Verify email | `POST /api/v1/auth/email/verify` |
| Login | `POST /api/v1/auth/login` |
| Logout | `POST /api/v1/auth/logout` |
| Profile | `GET /api/v1/users/me`, `PATCH /api/v1/users/me/profile` |
| Coursera | `/coursera`, `/coursera/orders` — catalog, tạo đơn, FUO Point |
| Admin Coursera | `/admin/coursera/catalog`, `/admin/coursera/orders` |
| Suộc | `/suoc`, `/suoc/[code]`, `/suoc/my-purchases` — catalog, mua, Suộc của tôi |
| Admin Suộc | `/admin/source/catalog`, `/admin/source/purchases` |
| Diễn đàn | `/`, `/forums`, `/forums/[slug]`, `/threads/[id]`, `/whats-new` — danh sách forum, chủ đề, bài viết |

Auth dùng HttpOnly cookie (`fuoverflow_at`, `fuoverflow_rt`) với `credentials: include`.

### Coursera test flow

1. Admin cấp điểm: `POST /api/v1/admin/users/{userId}/points/adjust` với `{"delta":500000,"reason":"dev"}`.
2. User mở `/coursera`, chọn khóa, nhập credential Coursera, thanh toán.
3. Theo dõi tại `/coursera/orders`; admin xử lý tại `/admin/coursera/orders`.

### Suộc test flow

1. Admin cấp điểm cho user (như trên).
2. Admin tạo tài liệu tại `/admin/source/catalog` (mã môn, giá, thời hạn, % trùng lặp).
3. User mở `/suoc`, vào chi tiết `/suoc/[code]`, bấm "Mua ngay" (gửi `Idempotency-Key` tự sinh).
4. User xem tại `/suoc/my-purchases` (tổng / còn hạn / hết hạn).
5. Admin theo dõi và hoàn tiền tại `/admin/source/purchases` (chỉ ADMIN mới hoàn được).
