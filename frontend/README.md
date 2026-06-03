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

Auth dùng HttpOnly cookie (`fuoverflow_at`, `fuoverflow_rt`) với `credentials: include`.
