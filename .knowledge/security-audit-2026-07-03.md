# Báo Cáo Kiểm Tra Bảo Mật FuOverflow

**Ngày**: 2026-07-03  
**Phạm vi**: Đánh giá bảo mật cho môi trường production — bảo vệ dữ liệu chống đánh cắp qua request, trình duyệt dev tools (F12), và các vector tấn công từ FE đến server.

---

## Những Điểm Đã Làm Tốt

- Ký JWT bằng RS256 với cặp khóa RSA (thuật toán đúng đắn)
- Refresh token lưu dưới dạng hash HMAC-SHA256 có pepper — không bao giờ lưu token gốc
- Refresh token xoay vòng (rotation) với phát hiện tái sử dụng theo family (revokeFamily)
- Cookie flags: HttpOnly, SameSite=Lax, Secure (profile prod)
- Token KHÔNG trả về trong response body — chỉ trả metadata trong `AuthTokenResponse`
- Mã hóa mật khẩu: BCrypt qua Spring Security `DelegatingPasswordEncoder`
- Thu hồi session khi đổi mật khẩu (`revokeAllExcept()`) và reset (`revokeAllByUserId()`)
- Tạo token ngẫu nhiên 256-bit bằng `SecureRandom` cho refresh, xác minh email, reset mật khẩu
- Xác minh chữ ký webhook PayOS qua `payOS.webhooks().verify(body)`
- CSRF tắt kết hợp SameSite=Lax cookies (chấp nhận được cho stateless API)

---

## CRITICAL — Phải Sửa Ngay Lập Tức

### ~~C1. Khóa riêng RSA (private key) đã bị commit vào lịch sử git~~ → Hạ xuống LOW

- **File**: `backend/app/src/main/resources/keys/dev-private.pem`, `backend/auth/src/test/resources/keys/test-private.pem`
- **Trạng thái**: **Không ảnh hưởng production** — production đã dùng key riêng mount từ bên ngoài, không dùng dev key trong repo.
- **Vấn đề còn lại**: Dev key vẫn nằm trong git history. Nếu repo bị public hoặc bị truy cập trái phép, dev key bị lộ (chỉ ảnh hưởng môi trường local/dev).
- **Cách sửa** (khi có thời gian):
  1. `git rm --cached backend/app/src/main/resources/keys/dev-private.pem backend/app/src/main/resources/keys/dev-public.pem`
  2. Cân nhắc dùng `git filter-repo` hoặc BFG để xóa khỏi lịch sử nếu repo từng public.

### C2. Mật khẩu Coursera trả về dạng plaintext cho admin API

- **File**: `backend/coursera/src/main/java/com/fuoverflow/coursera/api/dto/AdminRequestDetailResponse.java` dòng 16
- **File**: `backend/coursera/src/main/java/com/fuoverflow/coursera/application/CourseraRequestAdminService.java` dòng 125
- **Vấn đề**: Endpoint chi tiết request admin giải mã `passwordCiphertext` và trả về mật khẩu Coursera gốc dưới dạng `courseraPassword` trong JSON response. Mật khẩu plaintext bị lưu trong browser history, hiển thị trong tab Network của F12, bị ghi log bởi proxy/CDN.
- **Tác động**: Lộ mật khẩu dạng plaintext. Bất kỳ admin nào cũng xem được mật khẩu Coursera của user.
- **Cách sửa**: Xóa `courseraPassword` khỏi `AdminRequestDetailResponse`. Nếu admin cần dùng credential, cung cấp qua action riêng biệt có ghi audit log, hoặc che dấu (chỉ hiện vài ký tự đầu/cuối).

### C3. Endpoint introspect token không yêu cầu xác thực và truy cập công khai — ĐÃ SỬA

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`
- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`
- **Vấn đề gốc**: `/api/v1/auth/introspect` nằm trong `permitAll()`. Bất kỳ ai có thể dò thông tin session/user/role.
- **Đã sửa**: Bỏ khỏi `permitAll()`, thêm `@RequirePermission("auth.token:introspect")`. Chỉ SUPER_ADMIN mới có thể truy cập (SUPER_ADMIN bypass mọi permission check). Sau này khi phát triển module đăng nhập microservice sẽ mở rộng quyền phù hợp.

---

## HIGH — Sửa Trong Tuần Này

### H1. Không cấu hình security response headers — ĐÃ SỬA

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`
- **Đã sửa**: Thêm cấu hình headers vào `SecurityFilterChain`:
  - `Strict-Transport-Security`: max-age 1 năm, includeSubDomains (chống SSL stripping)
  - `Content-Security-Policy`: giới hạn nguồn tải tài nguyên (chống XSS)
  - `Referrer-Policy`: STRICT_ORIGIN_WHEN_CROSS_ORIGIN
  - `Permissions-Policy`: tắt geolocation, camera, microphone

### H2. Không có rate limiting trên các endpoint xác thực

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`
- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/application/AuthService.java` dòng 53-67
- **Vấn đề**: Các endpoint `login`, `register`, `refresh`, và `forgot-password` không có rate limiting. `ResendRateLimiter` chỉ áp dụng cho gửi lại email. Không có giới hạn theo IP hoặc theo tài khoản trên endpoint xác thực.
- **Tác động**: Tấn công credential stuffing (thử mật khẩu hàng loạt), brute-force đăng nhập, spam đăng ký.
- **Cách sửa**: Thêm rate limiting theo IP (Bucket4j hoặc Redis sliding window) cho `/api/v1/auth/login`, `/register`, `/password/forgot`, và `/refresh`.

### H3. Log webhook ghi toàn bộ nội dung body chứa dữ liệu thanh toán — ĐÃ SỬA

- **File**: `backend/payment/src/main/java/com/fuoverflow/payment/api/PayOSWebhookController.java`
- **Đã sửa**: Xóa `body` khỏi log nhận webhook (chỉ giữ `bodyLength`). Xóa `reference`, `amount`, `accountNumber` khỏi log xác minh (chỉ giữ `orderCode` + `code`). Xóa `body` khỏi log lỗi chữ ký không hợp lệ.

### H4. CORS `allowedHeaders: *` quá rộng — ĐÃ SỬA

- **File**: `backend/common/src/main/java/com/fuoverflow/common/config/WebCorsConfig.java`
- **Đã sửa**: Giới hạn `allowedHeaders` chỉ còn: `Authorization, Content-Type, X-Requested-With, Accept, Origin`.

### H5. Các secret được hardcode với giá trị mặc định trong config đã commit — KHÔNG ẢNH HƯỞNG PRODUCTION

- **Trạng thái**: **Production đã sử dụng biến môi trường riêng**, không dùng giá trị mặc định trong source code.
- **Vấn đề còn lại**: Giá trị mặc định vẫn nằm trong `application.yml` và `application-local.yml`. Nếu deploy mới quên set biến môi trường, giá trị mặc định sẽ được dùng.
- **Khuyến nghị** (khi có thời gian): Xóa giá trị mặc định cho các secret nhạy cảm, fail fast khi startup nếu thiếu.

### H6. `sessionId` nội bộ bị lộ trong response đăng nhập/refresh — ĐÃ SỬA

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/AuthTokenResponse.java`
- **Đã sửa**: Xóa `sessionId` khỏi `AuthTokenResponse`. Cập nhật `AuthService.bundle()` và `OAuthSessionIssuer`. Session ID vẫn embedded trong JWT claims (`sid`) cho nội bộ.

### H7. CSRF bị tắt với `secure: false` mặc định — ĐÃ SỬA

- **File**: `backend/app/src/main/resources/application.yml`, `backend/app/src/main/resources/application-local.yml`
- **Đã sửa**: Đổi mặc định `secure` thành `true` trong `application.yml`. Thêm override `secure: false` trong `application-local.yml` cho phát triển local (HTTP).

---

## MEDIUM — Sửa Sớm

### M1. `UserEntity` không có `@JsonIgnore` trên các trường nhạy cảm

- **File**: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`
- **Vấn đề**: Các getter công khai cho `passwordHash` (dòng 172), `normalizedEmail` (dòng 171), `usernameNormalized` (dòng 169), `rolesJson` (dòng 179) không có `@JsonIgnore`. Nếu bất kỳ đoạn code nào vô tình serialize entity (error handler, debug endpoint, thay đổi code tương lai), hash mật khẩu sẽ bị lộ.
- **Tác động**: Một lần serialize vô tình → lộ toàn bộ hash mật khẩu.
- **Cách sửa**: Thêm `@JsonIgnore` trên `getPasswordHash()`, `getNormalizedEmail()`, `getUsernameNormalized()`, và `getRolesJson()` như biện pháp phòng thủ nhiều lớp (defense-in-depth).

### M2. DTO `AuthUserView` mang `passwordHash` qua ranh giới module

- **File**: `backend/user/src/main/java/com/fuoverflow/user/api/dto/AuthUserView.java` dòng 13
- **File**: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java` dòng 26
- **Vấn đề**: `AuthUserView` là record công khai trong package `api.dto` chứa `passwordHash`. Record không thể dùng `@JsonIgnore` mà không có custom serializer. Nếu bất kỳ code path nào serialize nó (logging, error dump), hash mật khẩu bị lộ.
- **Tác động**: Rủi ro serialize/logging vô tình qua ranh giới module.
- **Cách sửa**: Tách thành `AuthUserView` (không có hash, dùng chung) + `AuthCredentials` (package-private, chỉ dùng cho xác minh mật khẩu).

### M3. Đường dẫn cookie access token `/` quá rộng

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/application/CookieService.java` dòng 12
- **Vấn đề**: Cookie access token với path `/` được gửi kèm mọi request bao gồm `/uploads/**`, `/actuator/**`, và tài nguyên tĩnh. Tăng bề mặt tấn công nếu các path đó có lỗ hổng.
- **Tác động**: Access token bị lộ trên các path không thuộc API.
- **Cách sửa**: Giới hạn đường dẫn cookie access token thành `/api/` thay vì `/`.

### M4. Chính sách mật khẩu chỉ kiểm tra độ dài (8-128 ký tự)

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/application/PasswordService.java` dòng 16-19
- **Vấn đề**: Không yêu cầu độ phức tạp (chữ hoa, số, ký tự đặc biệt). Không kiểm tra danh sách mật khẩu phổ biến/bị lộ. User có thể đặt mật khẩu `12345678` hoặc `password`.
- **Tác động**: Chấp nhận mật khẩu yếu, dễ bị tấn công từ điển.
- **Cách sửa**: Thêm yêu cầu phức tạp (chữ hoa + chữ thường + số + ký tự đặc biệt) hoặc tích hợp HaveIBeenPwned k-anonymity API / danh sách chặn top-10K mật khẩu phổ biến.

### M5. Cookie yêu cầu ủy quyền OAuth2 không được bảo vệ tính toàn vẹn

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieOAuth2AuthorizationRequestRepository.java` dòng 113-134
- **Vấn đề**: Yêu cầu ủy quyền OAuth2 (bao gồm `state`, `clientId`, `redirectUri`) được serialize dưới dạng JSON Base64 thuần trong cookie. Không được mã hóa hay ký HMAC. Kẻ tấn công có thể sửa đổi nội dung cookie (ví dụ: redirect URI) trước callback.
- **Tác động**: Có thể bị thao túng luồng OAuth2. Tham số `state` của Spring Security cung cấp một phần bảo vệ nhưng không hoàn chỉnh.
- **Cách sửa**: Ký HMAC giá trị cookie trước khi mã hóa Base64. Xác minh khi đọc.

### M6. Không kiểm tra thu hồi session theo thời gian thực trong JWT filter

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieAuthenticationFilter.java` dòng 36-56
- **Vấn đề**: Filter giải mã JWT và tin tưởng nó chỉ dựa trên chữ ký + hạn sử dụng. Không kiểm tra session (`sid` claim) đã bị thu hồi chưa. Sau khi đăng xuất hoặc đổi mật khẩu, access token hiện tại vẫn hợp lệ cho đến khi hết hạn (tối đa 10 phút với TTL mặc định).
- **Tác động**: Session đã thu hồi có khoảng trống (grace window) bằng TTL của access token.
- **Cách sửa**: Chọn một trong các phương án:
  1. Giảm TTL access token xuống 2-3 phút, hoặc
  2. Thêm kiểm tra thu hồi session nhẹ trong filter với Redis caching, hoặc
  3. Duy trì JWT denylist ngắn hạn trong Redis cho các sự kiện quan trọng (đổi mật khẩu, admin ban).

### M7. Boolean `superAdmin` bị lộ trong response profile user

- **File**: `backend/user/src/main/java/com/fuoverflow/user/api/dto/UserProfileResponse.java` dòng 22
- **Vấn đề**: Trạng thái `superAdmin` được trả về cho endpoint profile của user. Lộ mức đặc quyền trong tab Network của F12.
- **Tác động**: Thông tin hữu ích cho social engineering (lừa đảo).
- **Cách sửa**: Xóa `superAdmin` khỏi response profile hướng user nếu FE không cần để hiển thị UI. Giữ lại trong response chỉ dành cho admin.

### M8. CORS cho Actuator + lộ Prometheus

- **File**: `backend/common/src/main/java/com/fuoverflow/common/config/WebCorsConfig.java` dòng 34-35
- **File**: `backend/app/src/main/resources/application.yml` dòng 47-48
- **Vấn đề**: CORS được đăng ký trên `/actuator/**` với cùng chính sách như API (cho phép credentials). Actuator expose `health,info,prometheus`. Cấu hình sai trong tương lai có thể cho phép truy cập metrics cross-origin với credentials.
- **Tác động**: Prometheus metrics có thể lộ trạng thái nội bộ nếu vô tình cho phép truy cập.
- **Cách sửa**: Xóa `/actuator/**` khỏi CORS. Cân nhắc giới hạn actuator vào port quản lý riêng biệt.

### M9. `allowedOriginPatterns` cho phép cấu hình sai với wildcard

- **File**: `backend/common/src/main/java/com/fuoverflow/common/config/WebCorsConfig.java` dòng 25
- **Vấn đề**: `setAllowedOriginPatterns` chấp nhận glob patterns. Nếu `CORS_ALLOWED_ORIGINS` được set thành `*` hoặc `http://*.example.com`, nó cho phép matching origin rộng hơn dự định trong khi vẫn hỗ trợ credentials.
- **Tác động**: CORS mở với credentials nếu cấu hình sai với `*`.
- **Cách sửa**: Thêm validation kiểm tra không có pattern nào chứa wildcard `*` khi `allowCredentials=true`. Hoặc dùng `setAllowedOrigins` thay vì `setAllowedOriginPatterns`.

---

## LOW — Mức Thấp

### L1. `UploadRateLimiter` dùng map trong bộ nhớ — reset khi khởi động lại

- **File**: `backend/material/src/main/java/com/fuoverflow/material/application/UploadRateLimiter.java`
- **Tác động**: Bypass rate limit bằng cách tính thời gian khởi động lại hoặc trong triển khai nhiều instance.
- **Cách sửa**: Dùng rate limiting dựa trên Redis để đảm bảo nhất quán.

### L2. JWT gốc lưu trong trường `Authentication.credentials`

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieAuthenticationFilter.java` dòng 49
- **Vấn đề**: `new UsernamePasswordAuthenticationToken(jwt.getSubject(), token, authorities)` — JWT gốc làm credential. Nếu code gọi `authentication.getCredentials()` và ghi log, token bị lộ.
- **Cách sửa**: Truyền `null` thay cho credentials.

### L3. `RegisterResponse` trả về email và UUID nội bộ

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/RegisterResponse.java`
- **Tác động**: Liệt kê email qua đăng ký — kẻ tấn công có thể xác nhận email tồn tại.
- **Cách sửa**: Cân nhắc trả về thông báo chung "kiểm tra email của bạn" thay vì echo lại thông tin user.

### L4. `AdminUserSummaryResponse` bao gồm email của tất cả user

- **File**: `backend/user/src/main/java/com/fuoverflow/user/api/dto/AdminUserSummaryResponse.java` dòng 11
- **Tác động**: Lộ email hàng loạt nếu quyền admin bị cấp quá rộng.
- **Cách sửa**: Kiểm tra phân quyền `admin.user:read`. Cân nhắc che email trong danh sách (ví dụ: `t***@gmail.com`).

### L5. Record `TokenPair` có thể lộ token nếu vô tình bị serialize

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/domain/TokenPair.java`
- **Vấn đề**: Chứa `accessToken`, `refreshToken`, và `refreshTokenHash`. Nếu bất kỳ logging hoặc serialization nào chạm vào `TokenPair`, token bị lộ.
- **Cách sửa**: Thêm `@JsonIgnore` hoặc override `toString()` để che dấu các trường nhạy cảm.

### L6. Không có thuộc tính `Domain` trên cookies

- **File**: `backend/auth/src/main/java/com/fuoverflow/auth/application/CookieService.java` dòng 14
- **Tác động**: Không đáng kể — mặc định là host chính xác (an toàn mặc định). Nên ghi chú là có chủ đích.

---

## Kế Hoạch Ưu Tiên Sửa Lỗi

| Ưu tiên | Mã | Vấn đề | Ước tính |
|----------|-----|--------|----------|
| ~~Ngay lập tức~~ **Sau** | C1 | ~~Xoay vòng khóa RSA~~ Xóa dev key khỏi git history (prod không bị ảnh hưởng) | 30m |
| **Ngay lập tức** | C2 | Xóa mật khẩu Coursera plaintext khỏi response | 30m |
| **Ngay lập tức** | C3 | Bảo mật endpoint introspect | 15m |
| **Trong tuần** | H1 | Thêm security response headers | 30m |
| **Trong tuần** | H2 | Rate limiting trên endpoint xác thực | 2-4h |
| **Trong tuần** | H3 | Sửa logging webhook | 15m |
| **Trong tuần** | H4 | Giới hạn CORS allowed headers | 15m |
| **Trong tuần** | H5 | Xóa giá trị mặc định secret khỏi config | 1h |
| **Trong tuần** | H6 | Xóa sessionId khỏi response | 15m |
| **Trong tuần** | H7 | Đặt mặc định secure cookie thành true | 10m |
| **Sớm** | M1 | Thêm @JsonIgnore trên trường nhạy cảm UserEntity | 15m |
| **Sớm** | M2 | Tách AuthUserView để xóa passwordHash | 30m |
| **Sớm** | M3 | Giới hạn path cookie access token thành /api/ | 10m |
| **Sớm** | M4 | Tăng cường chính sách mật khẩu | 1h |
| **Sớm** | M5 | Ký HMAC cho cookie OAuth2 | 1h |
| **Sớm** | M6 | Thêm kiểm tra thu hồi session hoặc giảm TTL | 1-2h |
| **Sớm** | M7 | Xóa superAdmin khỏi response profile | 10m |
| **Sớm** | M8 | Xóa actuator khỏi CORS | 10m |
| **Sớm** | M9 | Kiểm tra CORS origin patterns | 15m |
| **Sau** | L1-L6 | Các bản sửa phòng thủ nhiều lớp ưu tiên thấp | 1-2h |
