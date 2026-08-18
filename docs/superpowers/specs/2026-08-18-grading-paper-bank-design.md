# Bank đề thi nội bộ + chấm điểm `.dat` tự chủ

Ngày: 2026-08-18
Trạng thái: đã duyệt, chờ implementation plan

## 1. Bối cảnh

`CheckScoreService` hiện tại nhận file `.dat` của sinh viên rồi **proxy sang `api.ask-4-help.com`**
bằng `X-Authorize-Key` do admin cấu hình, trừ 29.000 điểm, hỗ trợ voucher, và trả về
`{totalQuestions, score, subject, correctAnswers, charged}`.

Mục tiêu: **đem việc chấm điểm về tự làm**, bỏ phụ thuộc bên thứ ba, dựa trên bank đề do admin
tự nhập từ payload JSON của hệ thống thi.

Ba yêu cầu gốc:

1. Hàm chuyển JSON payload thành đề thi có cấu trúc.
2. Chỉ admin nhập JSON; nếu JSON chưa có đáp án thì phải hoàn thiện đáp án mới được lưu. Lưu vào
   bảng riêng, chỉ dùng để so nội bộ.
3. Sinh viên nộp `.dat`, hệ thống decode, so với đề trùng 100% đã lưu, chấm điểm và trả điểm về.

## 2. Phát hiện từ dữ liệu thật

Những điều dưới đây đã kiểm chứng trên dữ liệu thật, không phải suy đoán. Chúng định hình thiết kế.

### 2.1 Payload JSON không chứa đáp án

`QuestionAnswers[]` chỉ có `QID`, `QAID`, `Text`. Không có `IsCorrect`, không có `correctAnswer`.
Field đáp án duy nhất tồn tại trong họ payload này là `MatchQuestions[].Solution`, và nó bị mask
thành `#;#;#;#;#;#;#;#;#;#`. Với đề dạng ảnh, `Text` của mọi lựa chọn đều là chuỗi rỗng.

Kết luận: đáp án **bắt buộc** phải do admin nhập tay. Không có đường nào lấy tự động từ payload.

### 2.2 Bốn payload không cùng một format

4 row trong `public_api_payloads` là 2 đề, mỗi đề POST trùng 2 lần (giống nhau từng byte).

| | TEST_EOS_Client_278333 | CSP201m_SU26_FE_315379 |
|---|---|---|
| Top-level key | 20 | 18 |
| Key riêng | `TestType`, `EssayQuestion` | — |
| Section có dữ liệu | cả 5 + Essay | chỉ `GrammarQuestions` |
| `QType` | 1, 2, 5, 6, và **absent** | chỉ 1 |
| `ImageData` | 0/15 | 50/50 |
| Option có `Text` | 13/15 | 0/50 |

Khác biệt cấu trúc, không chỉ nội dung:

- **Key optional biến mất hẳn**, không phải `null`. Parser phải dùng `.get()`.
- **Field set của node câu hỏi khác nhau theo section**: `ImageSize` chỉ có ở CSP201m; `Lock` có ở
  FillBlank/IndicateM nhưng không có ở Grammar của TEST_EOS; `PassageQuestions` thiếu cả
  `CourseId` **và** `QType`; `MatchQuestions` dùng `MID`/`ColumnA`/`ColumnB`/`Solution`, không có
  `QuestionAnswers`.
- **Hai chế độ nội dung loại trừ nhau**: một đề đặt nội dung trong `Text`, đề kia trong `ImageData`.

`QType` không map 1-1 với section. Trong cùng `FillBlankQuestions` có hai cú pháp chỗ trống:

```
QType=6  "... bị (###) hoặc (###) ... là (###)% và phải đóng (###)%"
         4 marker (###)         ↔ 4 QuestionAnswers   → gõ tự do
QType=5  "... trong thời hạn (~7~6~5~4) ngày ... sau (~4~5~6~7) ngày"
         2 marker (~a~b~c~d)    ↔ 2 QuestionAnswers   → dropdown, lựa chọn nhúng trong Text
```

`QType` đã gặp: `1` MCQ, `2` indicate mistake, `5` fill-blank dropdown, `6` fill-blank tự do,
**absent** = câu trong passage. Thiếu `3`, `4` — gần chắc tồn tại nhưng chưa capture được.

Hệ quả: validator phải switch theo **cặp `(section, QType)`**, và phải parse chuỗi `Text` bằng
regex để lấy số chỗ trống. Số `QuestionAnswers` khớp số marker, dùng được để cross-check.

### 2.3 File `.dat` chỉ chứa ID, không chứa nội dung đề

Sau khi giải mã DES và parse NRBF, gốc là `QuestionLib.SubmitPaper`:

```
LoginId          "vietpqse192550"        mã đăng nhập sinh viên
SPaper._examCode "SCM302_SU26_FE_553972"
SPaper._grammar._items[]  50 câu QuestionLib.Entity.Question
    _qid            1972478849
    _text           null      ← KHÔNG có nội dung câu hỏi
    _imageData      null      ← KHÔNG có ảnh
    _questionAnswers._items[]
        _qaid       467097660
        _text       null
        _selected   true      ← ĐÁP ÁN SINH VIÊN CHỌN
        _chosen     false     ← luôn false, KHÔNG phải cờ đáp án đúng
byteImage        32.131 byte JPEG 350x500 — ảnh chân dung sinh viên
_pwd             tên máy, MAC, IP, danh sách process
_actionLog       log từng cú click, kèm IP + MAC + timestamp
```

Đã kiểm: `_selected=True` đúng 1 lựa chọn trên cả 50 câu; `_chosen` toàn `False`;
`_text`/`_imageData` toàn `None`.

### 2.4 Thứ tự `_items` là thứ tự hiển thị của riêng sinh viên đó

`_actionLog` ghi từng cú click theo format `<section> | <chữ cái> <True/False> | <QID>`:

```
11:04:02 | MultipleChoices | C False | 1972478849   ← bỏ chọn C
11:04:02 | MultipleChoices | A True  | 1972478849   ← chọn A
```

Replay toàn bộ log rồi so với cờ `_selected`: **khớp 50/50**, không lệch câu nào. Suy ra thứ tự
`_questionAnswers._items` chính là thứ tự hiển thị (A=0, B=1, C=2, D=3), và `_actionLog` là nguồn
kiểm chứng độc lập.

### 2.5 QID/QAID của đề thật được sinh ngẫu nhiên

| | QID / QAID | chapterId |
|---|---|---|
| SCM302 (thật) | `1553282 … 2104117901` rải khắp dải 32-bit | `10995–11003` liên tiếp |
| CSP201m (thật) | `62487086 … 2144468678` rải ngẫu nhiên | `5963–5981` liên tiếp |
| TEST_EOS (demo) | `15000–15013`, QAID `64851+` nhỏ liên tiếp | `689–693` |

`chapterId` là id thật trong DB nên nhỏ và liên tiếp. QID/QAID của đề thật thì rải ngẫu nhiên,
còn đề demo lại nhỏ liên tiếp → QID/QAID của đề thi thật **được sinh ngẫu nhiên lúc tạo đề**.

### 2.6 Payload đến tay client đã bị shuffle

Câu Reading đầu tiên của TEST_EOS:

```
vị trí A  QAID=64887  'Tất cả các lựa chọn trên'   ← QAID lớn nhất nhưng ở đầu
vị trí B  QAID=64883  'Trung thực'
vị trí C  QAID=64882  'Minh bạch'
```

QAID của TEST_EOS cấp liên tiếp theo thứ tự soạn đề, nên "Tất cả các lựa chọn trên" — vốn nằm
cuối — bị đẩy lên vị trí A là bằng chứng thứ tự đã đảo.

**Không** kết luận CSP201m có shuffle hay không: QAID của nó là int ngẫu nhiên nên việc 49/50 câu
"không tăng dần" là đương nhiên, không phải bằng chứng.

Hệ quả bắt buộc: **chấm theo `QAID`, tuyệt đối không theo vị trí A/B/C/D.**

### 2.7 Câu hỏi mở chưa trả lời được

`553972` trong `SCM302_SU26_FE_553972` là số của **ca thi** (nhiều SV dùng chung) hay của **từng
lượt thi** (mỗi SV một mã)? Không kết luận được từ một file `.dat`.

Test dứt điểm: 2 file `.dat` của 2 sinh viên khác nhau, cùng môn cùng kỳ. Cùng `examCode` và cùng
tập QID → đề dùng chung.

**Thiết kế không phụ thuộc câu trả lời này** nhờ khớp theo fingerprint (mục 3.2).

## 3. Quyết định kiến trúc

### 3.1 Module mới `backend/grading`

Sở hữu bank đề + decoder + grader. `source` gọi sang qua `GradingPort` đặt trong `common`, theo
đúng tiền lệ `com.fuoverflow.common.voucher.VoucherRedemptionPort`.

Lý do chọn module riêng thay vì nhét vào `source` hay `exam`: bảng đáp án là dữ liệu nội bộ không
bao giờ được lộ ra API công khai. Có module riêng thì việc "không endpoint nào trả đáp án ra" là
điều kiểm tra được bằng cách đọc một thư mục.

### 3.2 Khớp bài theo fingerprint

```
fingerprint = sha256( examCode + "|" + canonical(sorted[(QID, sorted[QAID...])]) )
```

Chuẩn hoá không phụ thuộc thứ tự (vì đề bị shuffle), phủ cả QID lẫn QAID. Khớp fingerprint nghĩa
là bộ đáp án theo QAID chắc chắn áp được — đúng nghĩa "trùng 100%".

Đúng cho cả hai trường hợp ở mục 2.7: đề dùng chung thì một lần import phủ cả ca thi; đề riêng
từng SV thì chỉ bài đó chấm được, còn lại từ chối và không trừ điểm.

### 3.3 Java thuần, không thêm Python service

`DatDecoder.java` chỉ dùng `javax.crypto` + `DataInputStream`, không dependency ngoài JDK. Thêm
Python service nghĩa là thêm một runtime, một deploy, một lớp auth nội bộ, và phải **viết lại
parser NRBF bằng Python** (không có thư viện sẵn dùng được). Java vẫn phải giữ fingerprint, chấm
điểm và trừ điểm vì chúng cần cùng transaction với DB. Nên Python chỉ sở hữu đúng phần decode —
tức viết lại cái đã chạy được, đổi lấy một điểm lỗi mới.

### 3.4 Lưu hash nội dung từ ngày đầu, chưa dùng để chấm

Chấm vẫn theo fingerprint id. Nhưng mỗi câu lưu thêm `content_sha256` (+ `image_sha256`) và mỗi
lựa chọn lưu `option_sha256`.

Lý do: id ngẫu nhiên theo từng đề nghĩa là đáp án **không chuyển được** sang đề khác. Hash nội
dung là cây cầu duy nhất giữa "chấm được một đề" và "chấm được cả môn" — dùng làm khoá dedupe ở
cấp câu để sau này tái dùng đáp án đã xác nhận. Cột hash là thứ rẻ nhất trong toàn bộ thiết kế.

### 3.5 Không khớp thì từ chối, không trừ điểm

Không fallback sang ask-4-help. Bank chưa có đề → trả lỗi rõ ràng, ví không bị trừ.

## 4. Mô hình dữ liệu

Migration `V51__grading_paper_bank.sql`. Không FK theo quy ước repo; ref chéo do service kiểm.

```sql
-- 1. Đề đã import
CREATE TABLE grading_papers (
    id                uuid PRIMARY KEY,
    exam_code         varchar(120) NOT NULL,
    subject_code      varchar(40)  NOT NULL,
    fingerprint       char(64)     NOT NULL,
    question_count    int          NOT NULL,
    duration_minutes  int,
    total_mark        numeric(6,2),
    status            varchar(16)  NOT NULL,   -- DRAFT | READY
    raw_payload       jsonb        NOT NULL,
    payload_sha256    char(64)     NOT NULL,
    source_payload_id uuid,
    created_by        uuid         NOT NULL,
    published_at      timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    deleted_at        timestamptz
);
CREATE UNIQUE INDEX ux_grading_papers_fp_live
    ON grading_papers (fingerprint) WHERE deleted_at IS NULL;
CREATE INDEX ix_grading_papers_subject ON grading_papers (subject_code, created_at DESC);

-- 2. Câu hỏi trong đề
CREATE TABLE grading_paper_questions (
    id              uuid PRIMARY KEY,
    paper_id        uuid         NOT NULL,
    qid             bigint       NOT NULL,
    section         varchar(24)  NOT NULL,   -- GRAMMAR|READING|FILL_BLANK|INDICATE_MISTAKE|MATCH
    q_type          int,                     -- NULL với câu trong passage
    display_no      int          NOT NULL,   -- chỉ để tham chiếu, KHÔNG dùng chấm
    mark            numeric(6,2) NOT NULL,
    chapter_id      int,
    question_text   text,                    -- NULL nếu đề dạng ảnh
    image_sha256    char(64),
    content_sha256  char(64)     NOT NULL,
    answer_mode     varchar(12)  NOT NULL,   -- SINGLE | MULTI | TEXT
    answered        boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL
);
CREATE UNIQUE INDEX ux_gpq_paper_qid ON grading_paper_questions (paper_id, qid);
CREATE INDEX ix_gpq_content ON grading_paper_questions (content_sha256);

-- 3. Bộ đáp án — nội bộ, KHÔNG endpoint công khai nào đọc
CREATE TABLE grading_paper_answers (
    id             uuid PRIMARY KEY,
    paper_id       uuid    NOT NULL,
    question_id    uuid    NOT NULL,
    qid            bigint  NOT NULL,
    qaid           bigint  NOT NULL,
    option_index   int     NOT NULL,   -- chỉ để render UI
    option_text    text,
    option_sha256  char(64),
    is_correct     boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_gpa_paper_qaid ON grading_paper_answers (paper_id, qaid);

-- 4. Lượt chấm của sinh viên
CREATE TABLE grading_submissions (
    id              uuid PRIMARY KEY,
    user_id         uuid    NOT NULL,
    paper_id        uuid,
    exam_code       varchar(120),
    subject_code    varchar(40),
    fingerprint     char(64) NOT NULL,
    login_id        varchar(80),
    file_sha256     char(64) NOT NULL,
    status          varchar(24) NOT NULL,
        -- GRADED|PAPER_NOT_FOUND|PAPER_NOT_READY|UNSUPPORTED_SECTION|DECODE_FAILED
    integrity_flag  varchar(16),            -- OK|LOG_MISMATCH|LOG_MISSING|LOGIN_MISMATCH
    total_questions int,
    answered_count  int,
    correct_count   int,
    score           numeric(6,2),
    max_score       numeric(6,2),
    answers_json    jsonb,                  -- {qid: chosen_qaid}, để xử lý khiếu nại
    charged_points  int NOT NULL DEFAULT 0,
    discount_points int NOT NULL DEFAULT 0,
    voucher_code    varchar(60),
    created_at      timestamptz NOT NULL
);
CREATE INDEX ix_gsub_user   ON grading_submissions (user_id, created_at DESC);
CREATE INDEX ix_gsub_status ON grading_submissions (status, created_at DESC);
```

### Bốn quyết định đáng nói

**`ux_grading_papers_fp_live`** — chỉ một đề sống trên mỗi fingerprint. Đây là bất biến quan trọng
nhất: không bao giờ tồn tại hai bộ đáp án xung khắc cho cùng một đề. Nhập lại để sửa thì phải
soft-delete cái cũ trước.

**`raw_payload jsonb`** giữ nguyên payload gốc. Bank đề tự chứa, không phụ thuộc
`public_api_payloads` còn hay bị xoá, và admin UI hiển thị ảnh câu hỏi bằng cách rút base64 ra từ
đây — không cần S3, không nhân bản file. Cái giá: ~520KB/đề. Đến khoảng 2.000 đề (~1GB) thì nên
chuyển ảnh sang S3 (hạ tầng `fuoverflow.storage.s3` đã có). Chưa làm trong phạm vi này.

**Không lưu 3 thứ PII trong `.dat`**: `byteImage` (ảnh chân dung), `_pwd` (tên máy/MAC/IP/process),
`_actionLog` (IP+MAC+timeline). Chỉ lưu kết luận `integrity_flag` rút ra từ log. Decode xong là bỏ,
không ghi ra log file.

**`login_id` thì có lưu** — cho phép phát hiện người nộp `.dat` của sinh viên khác, và nó là mã SV
chứ không phải dữ liệu nhạy cảm.

## 5. Luồng admin

Permission mới theo quy ước `<module>.<resource>.admin:<action>` đang dùng trong repo:
`grading.paper.admin:read` / `:create` / `:update` / `:delete`. Seed đủ 4 cho `ADMIN`, chỉ `:read`
cho `SUB_ADMIN`.

| Method | Endpoint | Việc |
|---|---|---|
| POST | `/api/v1/admin/grading/papers/import` | JSON thô hoặc `{payloadId}`. Validate → tạo đề `DRAFT` |
| GET | `/api/v1/admin/grading/papers` | Danh sách, filter `subject`, `status` |
| GET | `/api/v1/admin/grading/papers/{id}` | Chi tiết + `unansweredQids` |
| GET | `/api/v1/admin/grading/papers/{id}/questions/{qid}/image` | Stream PNG rút từ `raw_payload` |
| PUT | `/api/v1/admin/grading/papers/{id}/questions/{qid}/answer` | `{qaids:[...]}` → set `is_correct`, `answered=true` |
| POST | `/api/v1/admin/grading/papers/{id}/publish` | Cổng lưu. Thiếu đáp án → `422` kèm danh sách qid |
| DELETE | `/api/v1/admin/grading/papers/{id}` | Soft delete, giải phóng fingerprint |

### Cổng lưu

Yêu cầu gốc là "json chưa có đáp án thì đợi admin hoàn thành rồi mới được ấn lưu". Tách hai trạng
thái thay vì chặn lúc import:

- `import` luôn thành công, đề vào `DRAFT`. Admin cần lưu nháp để làm nhiều lượt.
- `publish` mới là "ấn lưu", từ chối nếu còn `answered=false`.
- **Chỉ đề `READY` được dùng để chấm.** Đề `DRAFT` vô hình với luồng sinh viên.

Điều kiện chặn nằm ở chỗ *dùng*, không chỉ ở chỗ *lưu*, nên đáp án nửa vời không bao giờ chấm ra
điểm sai.

### Validator lúc import

- Whitelist theo cặp `(section, QType)` đã biết ở mục 2.2. Gặp `QType` lạ hoặc field set lạ thì
  **reject cả payload** kèm mã lỗi và chỗ lệch. Không silently drop câu — đề thiếu câu mà không ai
  biết rồi chấm ra điểm sai là kịch bản tệ nhất.
- Assert số câu parse được khớp `QD` và `NoOfQuestion`.
- Dedupe theo `payload_sha256` và theo `fingerprint`.
- Nếu payload có sẵn đáp án (ví dụ `MatchQuestions.Solution` không bị mask) thì điền luôn và set
  `answered=true`. Hiện chưa payload nào có, nhưng để sẵn nhánh.

## 6. Luồng chấm điểm

### 6.1 Decoder

Tách `DatDecoder.java` thành `backend/grading/support/`, bỏ `JsonWriter` (chỉ để debug CLI):

| Class | Việc |
|---|---|
| `DatCipher` | DES/CBC/PKCS5, key = IV = `fpt-univ` |
| `NrbfReader` | Parser NRBF, **thêm hàng rào giới hạn** |
| `SubmitPaperMapper` | Cây `Map`/`List` → record `DecodedSubmission` có kiểu |

### 6.2 Hàng rào bắt buộc trước khi mở cho sinh viên upload

`NrbfReader` đọc binary do người dùng kiểm soát và có 4 chỗ cấp phát theo số đọc từ file:

- `readString()`: `byte[] buf = new byte[len]` với `len` từ LEB128 tới 2^31 — cấp phát 2GB **trước**
  khi `readFully` phát hiện EOF. Một file 200 byte làm sập JVM.
- `readArraySingleObject()`, `readArraySinglePrimitive()`, `readArraySingleString()`:
  `new ArrayList<>(length)` với `length` từ file.
- `readBinaryArray()`: `total = total * n` vẫn tràn int, và vòng `for (i<total)` không chặn.
- `readRecord → readMembers → readMemberValue → readRecord` đệ quy không giới hạn độ sâu →
  `StackOverflowError`. `resolveAll` cũng đệ quy.

Đây **không** phải lỗ RCE kiểu BinaryFormatter vì ta chỉ dựng `Map`/`List`, không instantiate type
.NET nào. Nhưng là DoS rẻ tiền hạ cả service dùng chung.

```
fuoverflow.grading.max-dat-bytes    = 8388608    # 8MB; mẫu thật 131KB
fuoverflow.grading.max-objects      = 100000
fuoverflow.grading.max-array-length = 50000
fuoverflow.grading.max-string-bytes = 1048576
fuoverflow.grading.max-depth        = 64
```

Vượt bất kỳ giới hạn → `BadRequestException("DAT_MALFORMED")`. Thêm bộ đếm byte tổng để không đọc
quá kích thước file. Đổi `resolveAll` sang vòng lặp có stack tường minh.

### 6.3 Thuật toán

```
1. bytes → DatCipher → NrbfReader → DecodedSubmission { loginId, examCode, questions[] }
2. fingerprint = sha256(examCode + "|" + canonical(sorted[(qid, sorted[qaid...])]))
3. paper = findLive(fingerprint) AND status = READY
      không có → PAPER_NOT_FOUND,  KHÔNG trừ điểm
      DRAFT    → PAPER_NOT_READY,  KHÔNG trừ điểm
4. key = { qid → set(qaid where is_correct) }
5. mỗi câu: chosen = set(qaid where _selected)
            chosen == key[qid] → += mark        (khớp tập, KHÔNG theo vị trí)
6. score = Σ mark, maxScore = paper.total_mark
7. voucher.redeem() → wallet.debit() → lưu submission → trả kết quả
```

Bước 7 nằm trong **một `@Transactional`**: trừ điểm lỗi thì không trả điểm, và không có chuyện trừ
điểm mà không trả kết quả.

Câu multi-select tính all-or-nothing, đặt sau cờ
`fuoverflow.grading.multi-select-partial-credit=false`.

### 6.4 Giới hạn: MVP chỉ chấm trắc nghiệm

File `.dat` mẫu chỉ có `MultipleChoices`. Không có mẫu cho fill-blank / matching / reading nên
không biết chắc đáp án tự luận của sinh viên nằm ở field nào (đoán là `QuestionAnswer._text`).

Đề nào có câu `FILL_BLANK`/`MATCH`/`TEXT` thì trả `UNSUPPORTED_SECTION` và không trừ điểm, thay vì
đoán rồi trả điểm sai. Mở rộng cần 1 file `.dat` của đề có các dạng đó.

### 6.5 Thay đổi ở `source`

Giữ endpoint để không phá frontend, đổi ruột:

- `POST /api/v1/check-score` — bỏ hẳn HTTP ra `api.ask-4-help.com`, gọi `GradingPort.grade(...)`.
  **Contract response giữ y nguyên** `CheckScoreResult`.
- `GET /api/v1/check-score/subjects` — đọc
  `SELECT DISTINCT subject_code FROM grading_papers WHERE status='READY' AND deleted_at IS NULL`,
  cùng shape response. Danh sách này giờ đúng sự thật.
- `/api/v1/admin/check-score/config` thành vô dụng. **Giữ lại, đánh dấu deprecated**, xoá ở PR sau
  để không phá `Fuexam-admin/app/check-score-config` trong cùng một lần thay đổi.
- Chuyển `29_000` hardcode ở `CheckScoreService:33` sang `fuoverflow.grading.check-score-price-points`.

## 7. Bảo mật và chống lạm dụng

**Sửa file để nâng điểm — không phải mối đe doạ.** Điểm do bộ đáp án của ta tính; sinh viên sửa
`_selected` chỉ làm ta chấm cho lựa chọn giả của họ, không có lợi gì. Khoá DES `fpt-univ` là khoá
của nhà cung cấp, không phải biên giới bảo mật.

**Dùng endpoint làm máy dò đáp án — đây là mối đe doạ thật.** Nộp cùng một đề nhiều lần, mỗi lần
đổi một lựa chọn, xem điểm thay đổi → suy ra đáp án. 50 câu × 4 lựa chọn, khoảng 150 lượt là lấy
được cả bộ. Rào chắn:

- Throttle theo `(user_id, fingerprint)`: tối đa 3 lượt/ngày.
- Cùng `file_sha256` → trả lại kết quả đã lưu, **không trừ điểm lần hai**.
- Giá 29.000 điểm/lượt là rào kinh tế chính.

**Kiểm chứng `_actionLog` (mềm).** Replay log ra đáp án cuối rồi so `_selected`. Lệch → ghi
`integrity_flag=LOG_MISMATCH` cho admin xem, **không chặn**. Lý do không chặn cứng: log chỉ dùng
66/39.592 dòng nên nghi là buffer cố định có thể tràn khi sinh viên đổi đáp án nhiều lần, chặn sẽ
oan. `login_id` khác mã SV của người đăng nhập → `LOGIN_MISMATCH`.

**Không endpoint nào ngoài module `grading` được đọc `is_correct`.** Có test khẳng định điều này.

## 8. UI

### Admin — `Fuexam-admin/app/grading-papers/`

Next.js App Router + shadcn/radix, đúng stack đang dùng.

- **`/grading-papers`** — danh sách: mã đề, môn, số câu, `DRAFT`/`READY`, số câu còn thiếu đáp án,
  ngày import.
- **`/grading-papers/import`** — dán JSON hoặc chọn từ `public_api_payloads` chưa import. Validate
  xong hiện bản xem trước rồi mới tạo `DRAFT`. Lỗi validate hiện rõ chỗ lệch.
- **`/grading-papers/[id]`** — màn nhập đáp án, quan trọng nhất:
  - Mỗi câu hiện ảnh (qua endpoint stream) hoặc text; lựa chọn hiện theo `option_index` kèm `qaid`
    nhỏ bên cạnh, admin tick đáp án đúng.
  - Câu đã chốt thu gọn; mặc định lọc "chỉ câu chưa có đáp án".
  - Bubble sheet 50 ô ở đầu trang tô theo câu đã chốt, click nhảy tới câu — tái dùng ý tưởng đã
    dựng và kiểm chứng ở `my-resource/parsed-exams/`.
  - Thanh dưới: `còn 12/50 câu` + nút **Lưu & phát hành** disable đến khi đủ. Backend vẫn trả 422
    thì hiện đúng danh sách qid thiếu (phòng hai admin làm song song).

### Sinh viên — `Fuexam/app/(app)/check-score/page.tsx`

Contract giữ nguyên nên gần như không sửa. Ba điểm thêm:

- Lỗi `PAPER_NOT_FOUND`: "Hệ thống chưa có đề của bài thi này. **Chưa trừ điểm của bạn.**" — phải
  nói rõ chưa trừ điểm.
- Lỗi riêng cho `PAPER_NOT_READY` và `UNSUPPORTED_SECTION`.
- Hiện số lượt còn lại trong ngày cho đề đó.

**Không** thêm màn đúng/sai từng câu. Giữ `score` + `số câu đúng/tổng` như hiện tại.

## 9. Test

Ràng buộc: **không commit file `.dat` mẫu vào git** — nó chứa ảnh chân dung sinh viên, MAC, IP,
tên máy.

- `NrbfReaderTest` — dựng byte array NRBF tối thiểu bằng tay cho từng record type đang dùng.
- `NrbfLimitsTest` — file vượt 8MB, khai `length=2^31-1`, string 2GB, lồng sâu 1000 tầng → phải ném
  `DAT_MALFORMED`, **không OOM, không StackOverflow**. Đây là test bảo vệ mục 6.2.
- `PaperFingerprintTest` — đảo thứ tự câu và thứ tự lựa chọn → fingerprint không đổi; đổi một QAID
  → fingerprint đổi.
- `PaperImportValidatorTest` — chạy trên cả hai shape thật (CSP201m ảnh-only, TEST_EOS đủ 5 dạng,
  đã lược `ImageData` cho gọn); `QType` lạ → reject; số câu lệch `QD` → reject.
- `GradingServiceTest` — key và submission biết trước → điểm đúng; `PAPER_NOT_FOUND` → **verify
  `walletService` chưa hề được gọi**; multi-select thiếu một lựa chọn → 0 điểm.
- `PublishGateTest` — thiếu đáp án → 422 kèm danh sách qid.
- MVC/security — student cần `points:read`; admin cần `grading.paper.admin:*`; và một test khẳng
  định không endpoint nào ngoài `grading` trả `is_correct`.
- `OracleThrottleTest` — lượt thứ 4 trong ngày cùng `(user, fingerprint)` → từ chối; cùng
  `file_sha256` → trả cache, không trừ điểm lần hai.
- File `.dat` thật: script kiểm tra tay đặt ngoài git (`temp/`), không vào CI.

## 10. Triển khai

```bash
cd backend
# thêm <module>grading</module> vào pom.xml cha, thêm dependency ở app
mvn -q -pl grading -am test
mvn -q -pl app -am test
mvn -q -DskipTests package        # vẫn 1 jar, không runtime mới
```

- Flyway tự chạy `V51` lúc boot. Bảng thuần thêm mới nên không cần down-migration.
- `.dat` 131KB; `spring.servlet.multipart.max-file-size: 20MB` sẵn có đã phủ. Giới hạn thật là
  `max-dat-bytes=8MB` ở tầng service, và `max-objects` là cái chặn bộ nhớ thực sự.
- Rollback: revert phần đổi ruột ở `source`; bảng V51 để nguyên vô hại.
- Bật lên khi bank đề còn trống thì mọi lượt nộp đều `PAPER_NOT_FOUND` và **không trừ điểm của
  ai**, nên không gây thiệt hại.

## 11. Giới hạn đã biết

1. **Chỉ chấm trắc nghiệm** (mục 6.4). Cần 1 file `.dat` của đề fill-blank/matching để mở rộng.
2. **Chưa biết `553972` là mã ca thi hay mã lượt thi** (mục 2.7). Không chặn thiết kế, nhưng nó
   quyết định admin phải nhập bao nhiêu đề. Cần 2 file `.dat` của 2 SV cùng môn cùng kỳ.
3. **Đáp án chưa tái dùng được giữa các đề.** Hash đã lưu nhưng chưa dùng; bật lên là việc riêng.
4. **`raw_payload` phình theo số đề**, ngưỡng cần chuyển sang S3 là ~2.000 đề.
5. **`QType` 3 và 4 chưa từng thấy** — validator sẽ reject payload chứa chúng, đúng chủ ý, nhưng
   nghĩa là có thể gặp đề không import được.

## 12. Ngoài phạm vi

- Tái dùng đáp án qua hash nội dung (chỉ lưu cột, chưa dùng).
- Chuyển ảnh sang S3.
- Xoá `/api/v1/admin/check-score/config` (PR sau).
- Chấm fill-blank / matching / essay.
- OCR ảnh câu hỏi.
