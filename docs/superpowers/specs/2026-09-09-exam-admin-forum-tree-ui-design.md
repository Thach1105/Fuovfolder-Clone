# Giao diện quản lý môn thi FE/PE dạng cây danh mục (forum tree)

- **Ngày**: 2026-09-09
- **Trạng thái**: Đã tự quyết định phạm vi, không qua vòng hỏi-đáp (theo yêu cầu của người dùng: "không cần hỏi tôi bất cứ việc gì, hãy tự quyết định"). Tài liệu này ghi lại quyết định để tham chiếu và review sau.
- **Phân loại**: Architectural (thêm cột DB, mở rộng API, tái cấu trúc một trang admin).

## 1. Bối cảnh và vấn đề

Trang quản lý môn thi hiện tại (`Fuexam-admin/app/exam/subjects/page.tsx`) hiển thị danh sách môn dạng bảng phẳng (`<Table>`), không nhóm theo bất kỳ tiêu chí nào ngoài `sortOrder`/`title`. Khi số môn tăng lên, admin khó tìm nhanh một môn cụ thể hoặc biết môn nào vừa có đề mới cần duyệt.

Người dùng cung cấp ảnh chụp trang chủ fuoverflow.com — khối "TÀI LIỆU CÁC MÔN HỌC" — làm tham chiếu giao diện mong muốn: các môn được nhóm theo "Kỳ" (Kỳ 0, 1, 2, 3...), mỗi nhóm có icon/badge số kỳ, danh sách mã môn dạng link cuộn ngang, hai cột số liệu (CHỦ ĐỀ / BÀI VIẾT), và một khối "hoạt động gần nhất" (avatar, badge loại đề, tiêu đề, thời gian, người đăng).

**Đã xác minh, không suy đoán** (qua đọc code thật):
- Không có bất kỳ cột hay bảng mapping nào trong repo hiện lưu "Kỳ học" theo nghĩa chương trình đào tạo (Kỳ 0/1/2/3...). `exam_subjects.category_slug` đang tồn tại nhưng dùng cho mục đích khác (gợi ý "related subjects" cùng category), giá trị mặc định UI là chuỗi tự do `"on-thi"`, không phải danh sách kỳ cố định.
- `exam_papers.term` là học kỳ theo lịch (`SU26` = Summer 2026, suy từ `ExamCode` qua regex), khác hoàn toàn với "Kỳ 0/1/2/3" theo thứ tự chương trình học mà ảnh tham chiếu thể hiện (ví dụ `CEA201`, `ECR201` đều thuộc "Kỳ 1" trong ảnh dù mã số là 2xx — đây là dữ liệu chương trình đào tạo do con người biết, không suy ra được từ mã môn bằng thuật toán).
- Do đó "Kỳ" trong ảnh tham chiếu là dữ liệu do quản trị viên forum gốc nhập tay, **không thể** derive tự động từ subject code trong hệ thống này. Quyết định: thêm một cột mới để admin gán tay.

## 2. Quyết định thiết kế (đã tự chọn, không hỏi lại)

### 2.1 Dữ liệu: thêm `curriculum_term` vào `exam_subjects`

- Cột mới: `curriculum_term smallint NULL`, ràng buộc `CHECK (curriculum_term IS NULL OR curriculum_term BETWEEN 0 AND 9)`.
- `NULL` = "Chưa rõ kỳ" — nhóm mặc định, hiển thị đầu tiên (giống mục "Tổng hợp - Chưa rõ kỳ" trong ảnh). Toàn bộ môn hiện có sẽ ở nhóm này cho tới khi admin gán kỳ thủ công — không backfill suy đoán, vì suy đoán sai sẽ gây hiểu nhầm cho admin về mức độ khó/thứ tự học.
- **Vì sao không tái dùng `category_slug`**: hai khái niệm ngữ nghĩa khác nhau (category dùng cho gợi ý môn liên quan; kỳ dùng để nhóm duyệt). Tái dùng sẽ làm hỏng tính năng "related subjects" đang chạy dựa trên category_slug tự do.
- **Vì sao `smallint` thay vì text tự do**: cho phép validate phạm vi (0-9, khớp số kỳ tối đa quan sát được trong chương trình FPT), sort tự nhiên, và render badge màu theo số mà không cần bảng mapping.

Migration: `backend/app/src/main/resources/db/migration/V55__exam_subject_curriculum_term.sql` (số kế tiếp sau `V54`, đã kiểm tra danh sách migration thật).

### 2.2 API: mở rộng, không tạo endpoint song song

- `CreateSubjectRequest` / `UpdateSubjectRequest` (`backend/exam/.../api/dto/`): thêm `Integer curriculumTerm` với `@Min(0) @Max(9)`, nullable.
- `AdminSubjectResponse`: thêm `Integer curriculumTerm`, `int fePaperCount`, `int pePaperCount` (đếm **tất cả trạng thái**, kể cả nháp — dùng `paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull` đã có sẵn, không cần thêm query mới), và `AdminSubjectResponse.LatestPaperSummary latestPaper` (nested record: `examCode, paperType, status, createdAt`), nullable khi môn chưa có đề nào.
- **Vì sao mở rộng response hiện có thay vì thêm endpoint `/dashboard` riêng**: `ExamSubjectAdminService.listAll()` đã tính aggregate theo từng subject trong vòng lặp (pattern N+1 hiện có, không phải aggregate hàng loạt) — mở rộng cùng vòng lặp đó là thay đổi nhỏ nhất, một nguồn dữ liệu duy nhất, không phải đồng bộ hai endpoint. Trang CRUD môn thi hiện tại (bảng cũ nếu cần) vẫn dùng chung response này, không có rủi ro tương thích ngược vì chỉ thêm field.
- "Hoạt động gần nhất" lấy theo **đề tạo gần đây nhất** (mọi trạng thái) trong subject đó, không phải theo bình luận — vì mục tiêu chính là admin thấy môn nào vừa có đề mới cần xem/duyệt, khớp với luồng làm việc thật (webhook ingest → admin duyệt) đã có trong hệ thống.
- **Không thêm permission mới**: field mới đi theo đúng permission `exam.subject.admin:read/create/update` đã gate các endpoint này.

### 2.3 Giao diện: tái cấu trúc phần danh sách của trang môn thi hiện có

Không tạo trang mới. Sửa `Fuexam-admin/app/exam/subjects/page.tsx`:

- **Giữ nguyên**: form tạo/sửa môn (thêm 1 dropdown "Kỳ học": Chưa rõ kỳ, Kỳ 0…Kỳ 9), luồng xoá, phân quyền `canWrite`/`canDelete`, ô tìm kiếm `search` đã có.
- **Thay phần bảng phẳng** bằng cây nhóm theo `curriculumTerm`:
  - Nhóm `null` trước tiên, nhãn "Tổng hợp - Chưa rõ kỳ", icon tài liệu, màu trung tính.
  - Sau đó `0..9` tăng dần, nhãn "Kỳ {n}", badge tròn màu số tuần hoàn qua bảng màu cố định (amber/blue/rose/emerald/violet/cyan/…), lặp nếu hết màu.
  - Trong mỗi nhóm: giữ thứ tự `sortOrder` rồi `title` (đúng thứ tự `listAll()` trả về, không cần logic sort mới).
  - Mỗi môn hiển thị dạng chip link (icon thư mục nhỏ + mã môn), bấm vào điều hướng tới `/exam/subjects/{id}/papers` (trang quản lý nội dung đã có sẵn, không đổi). Chip có icon bút chì nhỏ để mở form sửa môn đó (dùng lại form hiện có).
  - Hai cột số liệu bên phải mỗi nhóm (tổng theo nhóm, cộng dồn từ các môn trong nhóm): "Câu hỏi" (`feQuestionCount + pePaperCount`, tương đương "CHỦ ĐỀ") và "Đề thi" (`fePaperCount + pePaperCount`, tương đương "BÀI VIẾT").
  - Khối "hoạt động gần nhất" mỗi nhóm: lấy `latestPaper` mới nhất (theo `createdAt`) trong số các môn thuộc nhóm đó, tính ở client (không cần endpoint tổng hợp riêng). Badge màu theo `paperType` (FE = amber, PE = rose) + `examCode` + `formatDateTime(createdAt)` (dùng `lib/format-datetime.ts` có sẵn, không viết lại logic thời gian tương đối).
  - Ô tìm kiếm hiện có: lọc chip theo `code`/`title` chứa từ khoá (client-side, giữ đúng UX đã có ở trang public `Fuexam/app/(app)/exam/page.tsx`), ẩn nhóm không còn chip nào khớp.
  - Một nút thu gọn/mở rộng toàn bộ cây (không thu gọn từng nhóm riêng — YAGNI, không cần lưu trạng thái qua các lần tải trang).

**Ngoài phạm vi, cố ý không đụng**: `Fuexam-admin/app/exam/papers/page.tsx` (paper bank + log webhook) giữ nguyên — đây là hàng đợi duyệt đề theo từng đề riêng lẻ, không phải giao diện duyệt theo môn, không phù hợp để ép vào cây kỳ học.

## 3. Thay đổi tệp

**Backend** (`backend/`):
- `app/src/main/resources/db/migration/V55__exam_subject_curriculum_term.sql` (mới)
- `exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java` — thêm field `curriculumTerm`
- `exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java` — thêm field + validation
- `exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java` — thêm field + validation
- `exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java` — thêm 4 field (curriculumTerm, fePaperCount, pePaperCount, latestPaper) + nested record
- `exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java` — cập nhật `create`/`update`/`toAdmin`, tiêm thêm `ExamPaperRepository`
- `exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java` (mới — chưa có file test cho service này, tạo mới bao phủ create/update curriculumTerm hợp lệ/không hợp lệ và tính đúng fePaperCount/pePaperCount/latestPaper)

**Frontend** (`Fuexam-admin/`):
- `lib/api/exam.ts` — mở rộng `AdminSubject`, `AdminSubjectBody` với field mới
- `app/exam/subjects/page.tsx` — thay phần render bảng bằng cây nhóm theo kỳ, thêm dropdown kỳ học vào form

## 4. Rủi ro và cách xử lý

- **Tương thích ngược**: chỉ thêm field mới vào request/response hiện có, không đổi/xoá field cũ → không phá vỡ client khác đang gọi các endpoint này (nếu có).
- **Dữ liệu cũ toàn bộ rơi vào "Chưa rõ kỳ"**: chấp nhận được — đây là hành vi đúng, không che giấu, và admin có UI để gán kỳ ngay khi cần (dropdown trong form sửa, thao tác từng môn).
- **Không có test hiện có cho `ExamSubjectAdminService`**: bổ sung file test mới, theo tiêu chuẩn CLAUDE.md ("Add tests for service-level business rules when implementing real logic").

## 5. Xác minh trước khi coi là hoàn thành

- `mvn -q -pl exam -am test` xanh, gồm test mới.
- `npx tsc --noEmit` trong `Fuexam-admin` sạch cho các file đã sửa (không kỳ vọng sạch toàn repo — trang `subjects/[id]/papers` cũ đã biết là có vấn đề từ trước, không thuộc phạm vi việc này).
- Build backend thật (`STORAGE_PROVIDER=local`), gọi `GET /api/v1/admin/exam/subjects` xác nhận field mới xuất hiện đúng, `PATCH` một môn với `curriculumTerm` và đọc lại xác nhận lưu đúng.
- Chạy `next dev` cục bộ cho `Fuexam-admin`, mở `/exam/subjects`, xác nhận cây nhóm render đúng, gán kỳ cho 1-2 môn qua form và thấy chip nhảy đúng nhóm, tìm kiếm lọc đúng.
