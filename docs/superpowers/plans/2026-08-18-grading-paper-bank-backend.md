# Bank đề thi nội bộ — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin nhập payload JSON đề thi, hệ thống tách thành câu hỏi + lựa chọn, admin tick đáp án đúng, chỉ được phát hành khi đủ và đúng số đáp án.

**Architecture:** Module Maven mới `backend/grading` sở hữu bank đề. Chuỗi xử lý thuần hàm: `JsonNode → PaperPayloadNormalizer → NormalizedPaper → PaperFingerprint`, rồi service ghi xuống 3 bảng. Đáp án lưu ở bảng riêng `grading_paper_answers`, không endpoint nào ngoài module này đọc `is_correct`.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Data JPA, PostgreSQL, Flyway, Jackson, JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-08-18-grading-paper-bank-design.md`

**Phạm vi:** Đây là plan 1/3. Plan 2 = decoder `.dat` + chấm điểm. Plan 3 = UI admin + UI sinh viên. Plan này chạy độc lập được: xong là admin nhập và phát hành đề qua API.

## Global Constraints

- Java 21, Spring Boot 3.5.x, Maven multi-module monolith. Không thêm hạ tầng mới.
- Constructor injection. Records cho DTO. Không business logic trong controller.
- Migration là cách duy nhất đổi schema. `ddl-auto` giữ `validate` — tên cột trong SQL phải khớp tuyệt đối với `@Column` trong entity, lệch một chữ là app không boot.
- **Không tạo FOREIGN KEY.** Ràng buộc chéo kiểm trong service.
- Soft delete qua `deleted_at`.
- **Chấm theo `qaid`, tuyệt đối không theo vị trí A/B/C/D** — payload đã bị shuffle (spec mục 2.6).
- **`is_correct` không bao giờ ra khỏi module `grading`.** Endpoint admin được đọc; không tạo DTO công khai nào chứa nó.
- Message trả cho người dùng viết tiếng Việt. Code, tên biến, commit message viết tiếng Anh.
- Test: JUnit 5 + Mockito, không dùng DB (đúng convention repo — không có testcontainers/H2).
- Exception dùng sẵn trong `common.exception`: `BadRequestException`(400), `ConflictException`(409), `NotFoundException`(404), `ForbiddenException`, `TooManyRequestsException`(429). **Không có 422** — cổng phát hành dùng 409.

---

### Task 1: Module scaffold, migration, entities

**Files:**
- Create: `backend/grading/pom.xml`
- Modify: `backend/pom.xml` (thêm `<module>grading</module>` vào `<modules>`)
- Modify: `backend/app/pom.xml` (thêm dependency `fuoverflow-grading`)
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/GradingModule.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/PaperStatus.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/PaperSection.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/AnswerMode.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/AnswerSource.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperEntity.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperQuestionEntity.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperAnswerEntity.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperRepository.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperQuestionRepository.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/persistence/GradingPaperAnswerRepository.java`
- Create: `backend/app/src/main/resources/db/migration/V51__grading_paper_bank.sql`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/persistence/GradingPaperEntityTest.java`

**Interfaces:**
- Consumes: không.
- Produces:
  - `PaperStatus { DRAFT, READY }`
  - `PaperSection { GRAMMAR, READING, FILL_BLANK, INDICATE_MISTAKE, MATCH }`
  - `AnswerMode { SINGLE, MULTI, TEXT }`
  - `AnswerSource { MANUAL, IMPORTED, SUGGESTED }`
  - `GradingPaperEntity.draft(String examCode, String subjectCode, String fingerprint, int questionCount, Integer durationMinutes, BigDecimal totalMark, String rawPayload, String payloadSha256, UUID sourcePayloadId, UUID createdBy) -> GradingPaperEntity`
  - `GradingPaperEntity.markReady()`, `.softDelete()`, getters cho mọi cột
  - `GradingPaperQuestionEntity.of(UUID paperId, long qid, PaperSection section, Integer qType, int displayNo, BigDecimal mark, Integer chapterId, String questionText, String imageSha256, String contentSha256, AnswerMode answerMode, Integer expectedAnswerCount) -> GradingPaperQuestionEntity`
  - `GradingPaperQuestionEntity.applyAnswer(AnswerSource source, String sourceRef)` — chỉ `MANUAL`/`IMPORTED`, bật `answered=true`
  - `GradingPaperQuestionEntity.suggestAnswer(String sourceRef)` — `SUGGESTED`, giữ `answered=false`
  - `GradingPaperAnswerEntity.of(UUID paperId, UUID questionId, long qid, long qaid, int optionIndex, String optionText, String optionSha256) -> GradingPaperAnswerEntity`
  - `GradingPaperRepository.findByFingerprintAndDeletedAtIsNull(String) : Optional<GradingPaperEntity>`
  - `GradingPaperRepository.findByIdAndDeletedAtIsNull(UUID) : Optional<GradingPaperEntity>`
  - `GradingPaperQuestionRepository.findByPaperIdOrderByDisplayNoAsc(UUID) : List<...>`
  - `GradingPaperQuestionRepository.findByPaperIdAndQid(UUID, long) : Optional<...>`
  - `GradingPaperAnswerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(UUID) : List<...>`
  - `GradingPaperAnswerRepository.findByPaperIdAndQid(UUID, long) : List<...>`

- [ ] **Step 1: Tạo migration**

Create `backend/app/src/main/resources/db/migration/V51__grading_paper_bank.sql`:

```sql
CREATE TABLE grading_papers (
    id                uuid PRIMARY KEY,
    exam_code         varchar(120) NOT NULL,
    subject_code      varchar(40)  NOT NULL,
    fingerprint       char(64)     NOT NULL,
    question_count    int          NOT NULL,
    duration_minutes  int,
    total_mark        numeric(6,2),
    status            varchar(16)  NOT NULL,
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

CREATE TABLE grading_paper_questions (
    id                    uuid PRIMARY KEY,
    paper_id              uuid         NOT NULL,
    qid                   bigint       NOT NULL,
    section               varchar(24)  NOT NULL,
    q_type                int,
    display_no            int          NOT NULL,
    mark                  numeric(6,2) NOT NULL,
    chapter_id            int,
    question_text         text,
    image_sha256          char(64),
    content_sha256        char(64)     NOT NULL,
    answer_mode           varchar(12)  NOT NULL,
    expected_answer_count int,
    answer_source         varchar(12),
    answer_source_ref     varchar(120),
    answered              boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL
);
CREATE UNIQUE INDEX ux_gpq_paper_qid ON grading_paper_questions (paper_id, qid);
CREATE INDEX ix_gpq_content ON grading_paper_questions (content_sha256);

CREATE TABLE grading_paper_answers (
    id            uuid PRIMARY KEY,
    paper_id      uuid    NOT NULL,
    question_id   uuid    NOT NULL,
    qid           bigint  NOT NULL,
    qaid          bigint  NOT NULL,
    option_index  int     NOT NULL,
    option_text   text,
    option_sha256 char(64),
    is_correct    boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_gpa_paper_qaid ON grading_paper_answers (paper_id, qaid);
CREATE INDEX ix_gpa_question ON grading_paper_answers (question_id);

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
    integrity_flag  varchar(16),
    total_questions int,
    answered_count  int,
    correct_count   int,
    score           numeric(6,2),
    max_score       numeric(6,2),
    answers_json    jsonb,
    charged_points  int NOT NULL DEFAULT 0,
    discount_points int NOT NULL DEFAULT 0,
    voucher_code    varchar(60),
    created_at      timestamptz NOT NULL
);
CREATE INDEX ix_gsub_user   ON grading_submissions (user_id, created_at DESC);
CREATE INDEX ix_gsub_status ON grading_submissions (status, created_at DESC);
```

`grading_submissions` tạo ở đây nhưng entity của nó thuộc Plan 2. Bảng không có entity map tới thì `ddl-auto: validate` bỏ qua, không lỗi.

- [ ] **Step 2: Tạo pom module và đăng ký vào build**

Create `backend/grading/pom.xml` — copy nguyên `backend/exam/pom.xml` rồi đổi `<artifactId>` và `<name>` thành `fuoverflow-grading`, và **bỏ** hai dependency `fuoverflow-material`, `fuoverflow-user` (module này không cần). Giữ lại: `fuoverflow-common`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-web`, `spring-security-core`, `spring-boot-starter-test` (scope test).

Modify `backend/pom.xml` — thêm dòng `<module>grading</module>` ngay sau `<module>exam</module>`.

Modify `backend/app/pom.xml` — thêm ngay sau block `fuoverflow-exam`:

```xml
        <dependency>
            <groupId>com.fuoverflow</groupId>
            <artifactId>fuoverflow-grading</artifactId>
            <version>${project.version}</version>
        </dependency>
```

Không cần cấu hình scan gì thêm: `FuOverflowApplication` đã có `scanBasePackages`, `@EnableJpaRepositories`, `@EntityScan` đều trỏ `com.fuoverflow`.

- [ ] **Step 3: Tạo enum và module marker**

```java
// GradingModule.java
package com.fuoverflow.grading;

/** Marker type for the internal grading paper bank module package boundary. */
public final class GradingModule {
    private GradingModule() {
    }
}
```

```java
// domain/PaperStatus.java
package com.fuoverflow.grading.domain;

public enum PaperStatus { DRAFT, READY }
```

```java
// domain/PaperSection.java
package com.fuoverflow.grading.domain;

public enum PaperSection { GRAMMAR, READING, FILL_BLANK, INDICATE_MISTAKE, MATCH }
```

```java
// domain/AnswerMode.java
package com.fuoverflow.grading.domain;

public enum AnswerMode { SINGLE, MULTI, TEXT }
```

```java
// domain/AnswerSource.java
package com.fuoverflow.grading.domain;

/**
 * Nguon dap an. Chi MANUAL va IMPORTED duoc tinh la da tra loi khi phat hanh de;
 * SUGGESTED la de nghi tu file khong cung he dinh danh, phai co nguoi xac nhan.
 */
public enum AnswerSource { MANUAL, IMPORTED, SUGGESTED }
```

- [ ] **Step 4: Viết test thất bại cho entity factory**

Create `backend/grading/src/test/java/com/fuoverflow/grading/persistence/GradingPaperEntityTest.java`:

```java
package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.PaperStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GradingPaperEntityTest {

    private GradingPaperEntity newDraft() {
        return GradingPaperEntity.draft(
                "CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 50,
                60, new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    @Test
    void draftStartsInDraftStatusWithIdAndTimestamps() {
        GradingPaperEntity paper = newDraft();

        assertEquals(PaperStatus.DRAFT, paper.getStatus());
        assertNotNull(paper.getId());
        assertNotNull(paper.getCreatedAt());
        assertNotNull(paper.getUpdatedAt());
        assertNull(paper.getPublishedAt());
        assertNull(paper.getDeletedAt());
    }

    @Test
    void markReadySetsStatusAndPublishedAt() {
        GradingPaperEntity paper = newDraft();

        paper.markReady();

        assertEquals(PaperStatus.READY, paper.getStatus());
        assertNotNull(paper.getPublishedAt());
    }

    @Test
    void softDeleteStampsDeletedAt() {
        GradingPaperEntity paper = newDraft();

        paper.softDelete();

        assertNotNull(paper.getDeletedAt());
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: FAIL — compile error `cannot find symbol: class GradingPaperEntity`.

- [ ] **Step 6: Viết 3 entity và 3 repository**

`GradingPaperEntity` — tên cột phải khớp tuyệt đối V51. Theo style `ExamSubjectEntity`: `@Id private UUID id` **không** dùng `@GeneratedValue`, id gán trong factory.

```java
package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.PaperStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grading_papers")
public class GradingPaperEntity {
    @Id
    private UUID id;

    @Column(name = "exam_code", nullable = false, length = 120)
    private String examCode;

    @Column(name = "subject_code", nullable = false, length = 40)
    private String subjectCode;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_mark", precision = 6, scale = 2)
    private BigDecimal totalMark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaperStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "source_payload_id")
    private UUID sourcePayloadId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected GradingPaperEntity() {
    }

    public static GradingPaperEntity draft(String examCode, String subjectCode, String fingerprint,
                                           int questionCount, Integer durationMinutes, BigDecimal totalMark,
                                           String rawPayload, String payloadSha256, UUID sourcePayloadId,
                                           UUID createdBy) {
        Instant now = Instant.now();
        GradingPaperEntity entity = new GradingPaperEntity();
        entity.id = UUID.randomUUID();
        entity.examCode = examCode;
        entity.subjectCode = subjectCode;
        entity.fingerprint = fingerprint;
        entity.questionCount = questionCount;
        entity.durationMinutes = durationMinutes;
        entity.totalMark = totalMark;
        entity.status = PaperStatus.DRAFT;
        entity.rawPayload = rawPayload;
        entity.payloadSha256 = payloadSha256;
        entity.sourcePayloadId = sourcePayloadId;
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markReady() {
        this.status = PaperStatus.READY;
        this.publishedAt = Instant.now();
        this.updatedAt = this.publishedAt;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public UUID getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getSubjectCode() { return subjectCode; }
    public String getFingerprint() { return fingerprint; }
    public int getQuestionCount() { return questionCount; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getTotalMark() { return totalMark; }
    public PaperStatus getStatus() { return status; }
    public String getRawPayload() { return rawPayload; }
    public String getPayloadSha256() { return payloadSha256; }
    public UUID getSourcePayloadId() { return sourcePayloadId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
```

`GradingPaperQuestionEntity` — cùng khuôn. Cột: `paper_id`, `qid`, `section` (`@Enumerated(EnumType.STRING)`, length 24), `q_type` (`Integer`), `display_no`, `mark`, `chapter_id`, `question_text` (`columnDefinition = "text"`), `image_sha256`, `content_sha256`, `answer_mode` (`@Enumerated(EnumType.STRING)`, length 12), `expected_answer_count` (`Integer`), `answer_source` (`@Enumerated(EnumType.STRING)`, length 12, nullable `AnswerSource`), `answer_source_ref` (`String`, length 120), `answered` (`boolean`), `created_at`, `updated_at`. Factory `of(...)` gán `id = UUID.randomUUID()`, `answered = false`, `answerSource = null`, timestamps = now.

Hai method thay cho `markAnswered` đơn thuần:

```java
    /** MANUAL/IMPORTED: dap an tin duoc, tinh la da tra loi. */
    public void applyAnswer(AnswerSource source, String sourceRef) {
        if (source == AnswerSource.SUGGESTED) {
            throw new IllegalArgumentException("use suggestAnswer for SUGGESTED");
        }
        this.answerSource = source;
        this.answerSourceRef = sourceRef;
        this.answered = true;
        this.updatedAt = Instant.now();
    }

    /** SUGGESTED: de nghi tu nguon khac he dinh danh, KHONG tinh la da tra loi. */
    public void suggestAnswer(String sourceRef) {
        this.answerSource = AnswerSource.SUGGESTED;
        this.answerSourceRef = sourceRef;
        this.answered = false;
        this.updatedAt = Instant.now();
    }
```

`GradingPaperAnswerEntity` — cột: `paper_id`, `question_id`, `qid`, `qaid`, `option_index`, `option_text` (`columnDefinition = "text"`), `option_sha256`, `is_correct` (`boolean`), `created_at`, `updated_at`. Factory `of(...)` gán id, `isCorrect = false`, timestamps. Thêm `setCorrect(boolean value)` set `isCorrect` và `updatedAt`.

3 repository:

```java
public interface GradingPaperRepository extends JpaRepository<GradingPaperEntity, UUID> {
    Optional<GradingPaperEntity> findByFingerprintAndDeletedAtIsNull(String fingerprint);
    Optional<GradingPaperEntity> findByIdAndDeletedAtIsNull(UUID id);
    Optional<GradingPaperEntity> findByPayloadSha256AndDeletedAtIsNull(String payloadSha256);
    List<GradingPaperEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();
    List<GradingPaperEntity> findBySubjectCodeIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(String subjectCode);
}

public interface GradingPaperQuestionRepository extends JpaRepository<GradingPaperQuestionEntity, UUID> {
    List<GradingPaperQuestionEntity> findByPaperIdOrderByDisplayNoAsc(UUID paperId);
    Optional<GradingPaperQuestionEntity> findByPaperIdAndQid(UUID paperId, long qid);
}

public interface GradingPaperAnswerRepository extends JpaRepository<GradingPaperAnswerEntity, UUID> {
    List<GradingPaperAnswerEntity> findByPaperIdOrderByQidAscOptionIndexAsc(UUID paperId);
    List<GradingPaperAnswerEntity> findByPaperIdAndQid(UUID paperId, long qid);
}
```

- [ ] **Step 7: Chạy test, xác nhận PASS**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS, 3 test.

Rồi xác nhận toàn bộ build không vỡ: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS, có `grading/target/fuoverflow-grading-0.0.1-SNAPSHOT.jar`.

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/app/pom.xml backend/grading \
        backend/app/src/main/resources/db/migration/V51__grading_paper_bank.sql
git commit -m "feat(grading): scaffold module with paper bank schema and entities"
```

---

### Task 2: Payload normalizer và validator

Đây là task nhiều rủi ro nhất — 4 payload thật không cùng format (spec mục 2.2). Test phải phủ đúng hai shape thật.

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/NormalizedPaper.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/NormalizedQuestion.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/domain/NormalizedOption.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/application/PaperPayloadNormalizer.java`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/application/PaperPayloadNormalizerTest.java`

**Interfaces:**
- Consumes: `PaperSection`, `AnswerMode` (Task 1).
- Produces:
  - `record NormalizedOption(long qaid, int optionIndex, String text)`
  - `record NormalizedQuestion(long qid, PaperSection section, Integer qType, int displayNo, BigDecimal mark, Integer chapterId, String questionText, String imageBase64, AnswerMode answerMode, Integer expectedAnswerCount, List<NormalizedOption> options)`
  - `record NormalizedPaper(String examCode, String subjectCode, Integer durationMinutes, BigDecimal totalMark, int declaredQuestionCount, List<NormalizedQuestion> questions)`
  - `PaperPayloadNormalizer.normalize(JsonNode payload) : NormalizedPaper` — ném `BadRequestException` khi payload không hợp lệ.

- [ ] **Step 1: Viết test thất bại**

Create `PaperPayloadNormalizerTest.java`. Dùng JSON inline nhỏ, **không** dùng file 520KB thật.

```java
package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPayloadNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaperPayloadNormalizer normalizer = new PaperPayloadNormalizer();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Shape của CSP201m: chỉ GrammarQuestions, nội dung nằm trong ImageData, option Text rỗng. */
    private static final String IMAGE_ONLY = """
        {"ExamCode":"CSP201m_SU26_FE_315379","Duration":60,"Mark":50.0,"NoOfQuestion":2,
         "QD":{"MultipleChoices":2},
         "GrammarQuestions":[
           {"QID":111,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5963,"Text":"(Choose 1 answer) ",
            "ImageData":"aGVsbG8=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":111,"QAID":9001,"Text":""},{"QID":111,"QAID":9002,"Text":""}]},
           {"QID":222,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5964,"Text":"(Choose 2 answers)",
            "ImageData":"d29ybGQ=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":222,"QAID":9003,"Text":""},{"QID":222,"QAID":9004,"Text":""},
                               {"QID":222,"QAID":9005,"Text":""}]}],
         "ReadingQuestions":[],"MatchQuestions":[],"FillBlankQuestions":[],"IndicateMQuestions":[]}
        """;

    /** Shape của TEST_EOS: đủ section, option có Text, QD.Reading đếm ĐOẠN VĂN không đếm câu. */
    private static final String MULTI_SECTION = """
        {"ExamCode":"TEST_EOS_Client_278333","Duration":20,"Mark":28.5,"NoOfQuestion":4,
         "TestType":1,"QD":{"Reading":1,"Matching":1,"FillBlank":1,"MultipleChoices":1},
         "GrammarQuestions":[
           {"QID":15000,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":689,"Text":"(Choose 1 answer) Cau A?",
            "ImageData":"","QuestionLOs":[],
            "QuestionAnswers":[{"QID":15000,"QAID":64851,"Text":"Dung"},
                               {"QID":15000,"QAID":64852,"Text":"Sai"}]}],
         "ReadingQuestions":[
           {"PID":6,"ChapterId":690,"CourseId":"TEST_EOS","Text":"Doan van",
            "PassageQuestions":[
              {"PID":6,"QID":15010,"Mark":0.5,"ChapterId":-1,"Text":"Cau doc?","ImageData":"",
               "QuestionLOs":[],
               "QuestionAnswers":[{"QID":15010,"QAID":64883,"Text":"X"},
                                  {"QID":15010,"QAID":64884,"Text":"Y"}]}]}],
         "MatchQuestions":[
           {"MID":5,"Mark":10.0,"ChapterId":692,"CourseId":"TEST_EOS",
            "ColumnA":"1. A","ColumnB":"A. B","Solution":"#;#","QuestionLOs":[]}],
         "FillBlankQuestions":[
           {"QID":15006,"PID":-1,"QType":6,"Lock":true,"Mark":4.0,"ChapterId":691,
            "Text":"Dien (###) va (###)","ImageData":"","QuestionLOs":[],
            "QuestionAnswers":[{"QID":15006,"QAID":64870,"Text":""},
                               {"QID":15006,"QAID":64871,"Text":""}]}],
         "IndicateMQuestions":[]}
        """;

    @Test
    void normalizesImageOnlyPaper() {
        NormalizedPaper paper = normalizer.normalize(json(IMAGE_ONLY));

        assertEquals("CSP201m_SU26_FE_315379", paper.examCode());
        assertEquals("CSP201m", paper.subjectCode());
        assertEquals(60, paper.durationMinutes());
        assertEquals(2, paper.questions().size());

        NormalizedQuestion first = paper.questions().get(0);
        assertEquals(111L, first.qid());
        assertEquals(PaperSection.GRAMMAR, first.section());
        assertEquals(1, first.displayNo());
        assertEquals("aGVsbG8=", first.imageBase64());
        assertNull(first.questionText(), "boilerplate (Choose N answers) khong phai noi dung cau hoi");
        assertEquals(2, first.options().size());
        assertEquals(9001L, first.options().get(0).qaid());
        assertEquals(0, first.options().get(0).optionIndex());
    }

    @Test
    void derivesAnswerModeAndExpectedCountFromChooseMarker() {
        NormalizedPaper paper = normalizer.normalize(json(IMAGE_ONLY));

        assertEquals(AnswerMode.SINGLE, paper.questions().get(0).answerMode());
        assertEquals(1, paper.questions().get(0).expectedAnswerCount());

        assertEquals(AnswerMode.MULTI, paper.questions().get(1).answerMode());
        assertEquals(2, paper.questions().get(1).expectedAnswerCount());
    }

    @Test
    void normalizesEverySectionOfMultiSectionPaper() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        assertEquals(4, paper.questions().size());
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.GRAMMAR));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.READING));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.FILL_BLANK));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.MATCH));
    }

    @Test
    void passageQuestionHasNoQTypeAndIsNotRejected() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        NormalizedQuestion reading = paper.questions().stream()
                .filter(q -> q.section() == PaperSection.READING).findFirst().orElseThrow();
        assertNull(reading.qType());
        assertEquals(15010L, reading.qid());
    }

    @Test
    void fillBlankAndMatchAreTextMode() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        assertEquals(AnswerMode.TEXT, paper.questions().stream()
                .filter(q -> q.section() == PaperSection.FILL_BLANK).findFirst().orElseThrow().answerMode());
        assertEquals(AnswerMode.TEXT, paper.questions().stream()
                .filter(q -> q.section() == PaperSection.MATCH).findFirst().orElseThrow().answerMode());
    }

    @Test
    void rejectsUnknownQType() {
        String bad = IMAGE_ONLY.replace("\"QType\":1,\"Mark\":1.0,\"ChapterId\":5963",
                                        "\"QType\":99,\"Mark\":1.0,\"ChapterId\":5963");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_UNSUPPORTED_QTYPE", ex.code());
    }

    @Test
    void rejectsWhenQdSumDoesNotMatchNoOfQuestion() {
        String bad = IMAGE_ONLY.replace("\"NoOfQuestion\":2", "\"NoOfQuestion\":7");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_COUNT_MISMATCH", ex.code());
    }

    @Test
    void rejectsWhenSectionCountDoesNotMatchQd() {
        String bad = IMAGE_ONLY.replace("\"MultipleChoices\":2", "\"MultipleChoices\":5")
                               .replace("\"NoOfQuestion\":2", "\"NoOfQuestion\":5");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_SECTION_COUNT_MISMATCH", ex.code());
    }

    @Test
    void rejectsBlankExamCode() {
        String bad = IMAGE_ONLY.replace("\"ExamCode\":\"CSP201m_SU26_FE_315379\"", "\"ExamCode\":\"\"");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_EXAM_CODE_REQUIRED", ex.code());
    }

    @Test
    void rejectsDuplicateQid() {
        String bad = IMAGE_ONLY.replace("\"QID\":222", "\"QID\":111");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_DUPLICATE_QID", ex.code());
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: FAIL — compile error, chưa có `PaperPayloadNormalizer`.

- [ ] **Step 3: Viết 3 record domain**

```java
// domain/NormalizedOption.java
package com.fuoverflow.grading.domain;

public record NormalizedOption(long qaid, int optionIndex, String text) {
}
```

```java
// domain/NormalizedQuestion.java
package com.fuoverflow.grading.domain;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedQuestion(
        long qid,
        PaperSection section,
        Integer qType,
        int displayNo,
        BigDecimal mark,
        Integer chapterId,
        String questionText,
        String imageBase64,
        AnswerMode answerMode,
        Integer expectedAnswerCount,
        List<NormalizedOption> options
) {
}
```

```java
// domain/NormalizedPaper.java
package com.fuoverflow.grading.domain;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedPaper(
        String examCode,
        String subjectCode,
        Integer durationMinutes,
        BigDecimal totalMark,
        int declaredQuestionCount,
        List<NormalizedQuestion> questions
) {
}
```

- [ ] **Step 4: Viết normalizer**

Create `application/PaperPayloadNormalizer.java`. Luật đã kiểm chứng trên payload thật:

- Cặp `(section, QType)` được phép: `GRAMMAR`→`1`; `INDICATE_MISTAKE`→`2`; `FILL_BLANK`→`5` hoặc `6`; `READING`→không có QType; `MATCH`→không có QType. Khác → `PAYLOAD_UNSUPPORTED_QTYPE`.
- `QD` map sang section theo **đơn vị riêng**: `MultipleChoices`↔số phần tử `GrammarQuestions`; `IndicateMistake`↔`IndicateMQuestions`; `FillBlank`↔`FillBlankQuestions`; `Matching`↔`MatchQuestions`; `Reading`↔**số đoạn văn** `ReadingQuestions` (KHÔNG phải số câu trong đoạn). Đây là chỗ dễ sai nhất: TEST_EOS có `QD.Reading=2` nhưng 4 câu trong 2 đoạn.
- `sum(QD.values()) == NoOfQuestion`, khác → `PAYLOAD_COUNT_MISMATCH`.
- `Text` khớp `^\(Choose (\d+) answers?\)$` sau khi trim → coi là boilerplate: `questionText = null`, `expectedAnswerCount = N`. Nếu `Text` còn nội dung sau marker thì giữ phần còn lại làm `questionText` và vẫn lấy `N`.
- `answerMode`: `FILL_BLANK`/`MATCH` → `TEXT`; còn lại `expectedAnswerCount == 1` → `SINGLE`, `> 1` → `MULTI`, không có marker → `SINGLE`.
- `MATCH` không có `QuestionAnswers` → `options` rỗng, `qid` lấy từ `MID`.
- `displayNo` đánh số 1..n theo thứ tự duyệt: Grammar → FillBlank → IndicateM → từng đoạn Reading → Match.
- `imageBase64` = `ImageData` nếu không rỗng, else `null`.
- `subjectCode` = phần trước dấu `_` đầu tiên của `ExamCode`; nếu không có `_` thì lấy cả chuỗi.
- Trùng `qid` trong cùng payload → `PAYLOAD_DUPLICATE_QID`.
- `ExamCode` rỗng/thiếu → `PAYLOAD_EXAM_CODE_REQUIRED`.
- Mọi field optional đọc bằng `path()`/`get()` rồi kiểm null — **không** index trực tiếp, vì `TestType` và `EssayQuestion` biến mất hẳn ở payload CSP201m.

Regex dùng: `Pattern.compile("^\\(Choose\\s+(\\d+)\\s+answers?\\)\\s*", Pattern.CASE_INSENSITIVE)`.

- [ ] **Step 5: Chạy test, xác nhận PASS**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS, 10 test của normalizer + 3 test entity.

- [ ] **Step 6: Kiểm chứng trên payload thật (một lần, không vào CI)**

Đây không phải test tự động, là bước xác nhận tay để chắc normalizer chịu được dữ liệu thật:

```bash
cd backend && mvn -q -pl grading -am test -Dtest=PaperPayloadNormalizerTest
```

Sau đó dùng endpoint import ở Task 4 với hai payload thật trong `temp/public_api_payloads_202608172200.json`
(row 1 = TEST_EOS 15 câu, row 3 = CSP201m 50 câu) và xác nhận cả hai vào `DRAFT` thành công.

- [ ] **Step 7: Commit**

```bash
git add backend/grading/src/main/java/com/fuoverflow/grading/domain \
        backend/grading/src/main/java/com/fuoverflow/grading/application/PaperPayloadNormalizer.java \
        backend/grading/src/test/java/com/fuoverflow/grading/application/PaperPayloadNormalizerTest.java
git commit -m "feat(grading): normalize exam payloads across both known shapes"
```

---

### Task 3: Fingerprint và content hash

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/support/Sha256.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/support/PaperFingerprint.java`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/support/PaperFingerprintTest.java`

**Interfaces:**
- Consumes: `NormalizedPaper`, `NormalizedQuestion`, `NormalizedOption` (Task 2).
- Produces:
  - `Sha256.hex(byte[] data) : String` — 64 ký tự hex thường
  - `Sha256.hexUtf8(String text) : String`
  - `PaperFingerprint.of(NormalizedPaper paper) : String`
  - `PaperFingerprint.ofSubmission(String examCode, Map<Long, Set<Long>> qidToQaids) : String` — Plan 2 dùng để khớp từ `.dat`

- [ ] **Step 1: Viết test thất bại**

```java
package com.fuoverflow.grading.support;

import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.NormalizedOption;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaperFingerprintTest {

    private NormalizedQuestion question(long qid, long... qaids) {
        List<NormalizedOption> options = new ArrayList<>();
        for (int i = 0; i < qaids.length; i++) {
            options.add(new NormalizedOption(qaids[i], i, null));
        }
        return new NormalizedQuestion(qid, PaperSection.GRAMMAR, 1, 1, BigDecimal.ONE,
                null, null, null, AnswerMode.SINGLE, 1, options);
    }

    private NormalizedPaper paper(List<NormalizedQuestion> questions) {
        return new NormalizedPaper("SCM302_SU26_FE_553972", "SCM302", 60,
                new BigDecimal("50.00"), questions.size(), questions);
    }

    @Test
    void isStableRegardlessOfQuestionOrder() {
        List<NormalizedQuestion> a = List.of(question(1, 10, 11), question(2, 20, 21));
        List<NormalizedQuestion> b = new ArrayList<>(a);
        Collections.reverse(b);

        assertEquals(PaperFingerprint.of(paper(a)), PaperFingerprint.of(paper(b)));
    }

    @Test
    void isStableRegardlessOfOptionOrder() {
        String forward = PaperFingerprint.of(paper(List.of(question(1, 10, 11, 12))));
        String shuffled = PaperFingerprint.of(paper(List.of(question(1, 12, 10, 11))));

        assertEquals(forward, shuffled);
    }

    @Test
    void changesWhenAQaidChanges() {
        String original = PaperFingerprint.of(paper(List.of(question(1, 10, 11))));
        String altered = PaperFingerprint.of(paper(List.of(question(1, 10, 99))));

        assertNotEquals(original, altered);
    }

    @Test
    void changesWhenExamCodeChanges() {
        List<NormalizedQuestion> questions = List.of(question(1, 10, 11));
        String other = PaperFingerprint.of(new NormalizedPaper(
                "SCM302_SU26_FE_999999", "SCM302", 60, new BigDecimal("50.00"), 1, questions));

        assertNotEquals(PaperFingerprint.of(paper(questions)), other);
    }

    @Test
    void submissionFingerprintMatchesPaperFingerprint() {
        List<NormalizedQuestion> questions = List.of(question(1, 10, 11), question(2, 20, 21));
        Map<Long, Set<Long>> fromDat = new LinkedHashMap<>();
        fromDat.put(2L, Set.of(21L, 20L));
        fromDat.put(1L, Set.of(11L, 10L));

        assertEquals(PaperFingerprint.of(paper(questions)),
                PaperFingerprint.ofSubmission("SCM302_SU26_FE_553972", fromDat));
    }

    @Test
    void hexIsLowercaseSixtyFourChars() {
        String hex = Sha256.hexUtf8("abc");

        assertEquals(64, hex.length());
        assertEquals(hex.toLowerCase(), hex);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test -Dtest=PaperFingerprintTest`
Expected: FAIL — chưa có `PaperFingerprint`.

- [ ] **Step 3: Viết Sha256 và PaperFingerprint**

```java
package com.fuoverflow.grading.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256 {
    private Sha256() {
    }

    public static String hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hexUtf8(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }
}
```

`PaperFingerprint.of(paper)` dựng chuỗi canonical rồi băm:

```
examCode + "|" + join(";", for each qid ascending: qid + ":" + join(",", qaids ascending))
```

`ofSubmission(examCode, qidToQaids)` dựng **đúng cùng một chuỗi** từ map. Hai hàm phải dùng chung một private helper để không bao giờ lệch nhau — test `submissionFingerprintMatchesPaperFingerprint` khoá điều này.

Câu `MATCH` không có option: vẫn ghi `qid + ":"` với danh sách rỗng.

- [ ] **Step 4: Chạy test, xác nhận PASS**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS toàn bộ.

- [ ] **Step 5: Commit**

```bash
git add backend/grading/src/main/java/com/fuoverflow/grading/support \
        backend/grading/src/test/java/com/fuoverflow/grading/support
git commit -m "feat(grading): add order-independent paper fingerprint"
```

---

### Task 4: Import service và endpoint

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/application/PaperImportService.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/GradingPaperAdminController.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/ImportPaperRequest.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PaperSummaryResponse.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PaperPreviewResponse.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PreviewQuestion.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PreviewOption.java`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/application/PaperImportServiceTest.java`

**Interfaces:**
- Consumes: `PaperPayloadNormalizer.normalize`, `PaperFingerprint.of`, `Sha256.hex`, 3 entity factory + 3 repository (Task 1–3).
- Produces:
  - `record ImportPaperRequest(JsonNode payload, UUID payloadId, String confirmedFingerprint)`
  - `record PreviewOption(long qaid, int optionIndex, String text)`
  - `record PreviewQuestion(long qid, int displayNo, String section, String answerMode, Integer expectedAnswerCount, String questionText, String imageBase64, List<PreviewOption> options)`
  - `record PaperPreviewResponse(String examCode, String subjectCode, Integer durationMinutes, BigDecimal totalMark, int questionCount, String fingerprint, String collision, UUID collisionPaperId, int existingAnsweredCount, List<PreviewQuestion> questions, List<String> warnings)` — `collision` ∈ `NONE`, `EXISTING_DRAFT`, `EXISTING_PUBLISHED`
  - `record PaperSummaryResponse(UUID id, String examCode, String subjectCode, String status, int questionCount, int answeredCount, int unansweredCount, Instant createdAt, Instant publishedAt)`
  - `PaperImportService.preview(JsonNode payload) : PaperPreviewResponse` — **không ghi gì vào DB**
  - `PaperImportService.importPayload(UUID adminId, JsonNode payload, UUID sourcePayloadId, String confirmedFingerprint) : PaperSummaryResponse`

- [ ] **Step 1: Viết test thất bại**

```java
package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.grading.api.dto.PaperPreviewResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.support.PaperFingerprint;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperImportServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperImportService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String PAYLOAD = """
        {"ExamCode":"CSP201m_SU26_FE_315379","Duration":60,"Mark":50.0,"NoOfQuestion":1,
         "QD":{"MultipleChoices":1},
         "GrammarQuestions":[
           {"QID":111,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5963,"Text":"(Choose 1 answer)",
            "ImageData":"aGVsbG8=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":111,"QAID":9001,"Text":""},{"QID":111,"QAID":9002,"Text":""}]}],
         "ReadingQuestions":[],"MatchQuestions":[],"FillBlankQuestions":[],"IndicateMQuestions":[]}
        """;

    private JsonNode payload() {
        try {
            return mapper.readTree(PAYLOAD);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        service = new PaperImportService(paperRepository, questionRepository, answerRepository,
                new PaperPayloadNormalizer(), mapper);
    }

    @Test
    void importCreatesDraftPaperWithQuestionsAndOptions() {
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(paperRepository.findByPayloadSha256AndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(paperRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String fingerprint = service.preview(payload()).fingerprint();

        PaperSummaryResponse response =
                service.importPayload(UUID.randomUUID(), payload(), null, fingerprint);

        assertEquals("CSP201m_SU26_FE_315379", response.examCode());
        assertEquals("CSP201m", response.subjectCode());
        assertEquals("DRAFT", response.status());
        assertEquals(1, response.questionCount());
        assertEquals(0, response.answeredCount());
        assertEquals(1, response.unansweredCount());

        ArgumentCaptor<List<GradingPaperQuestionEntity>> questions = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(questions.capture());
        assertEquals(1, questions.getValue().size());
        assertEquals(111L, questions.getValue().get(0).getQid());

        ArgumentCaptor<List<GradingPaperAnswerEntity>> answers = ArgumentCaptor.forClass(List.class);
        verify(answerRepository).saveAll(answers.capture());
        assertEquals(2, answers.getValue().size());
        assertFalse(answers.getValue().get(0).isCorrect(), "dap an phai mac dinh false");
    }

    @Test
    void previewParsesWithoutTouchingTheDatabase() {
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        PaperPreviewResponse preview = service.preview(payload());

        assertEquals("CSP201m_SU26_FE_315379", preview.examCode());
        assertEquals(1, preview.questionCount());
        assertEquals("NONE", preview.collision());
        assertEquals(64, preview.fingerprint().length());
        assertEquals(1, preview.questions().size());
        assertEquals("aGVsbG8=", preview.questions().get(0).imageBase64(),
                "giao dien can anh de admin xem truoc");
        assertEquals(2, preview.questions().get(0).options().size());

        verify(paperRepository, never()).save(any());
        verify(questionRepository, never()).saveAll(any());
        verify(answerRepository, never()).saveAll(any());
    }

    @Test
    void previewReportsCollisionWithExistingDraft() {
        GradingPaperEntity existing = GradingPaperEntity.draft(
                "CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
        GradingPaperQuestionEntity answered = GradingPaperQuestionEntity.of(
                existing.getId(), 111L, PaperSection.GRAMMAR, 1, 1, BigDecimal.ONE, null,
                null, null, "c".repeat(64), AnswerMode.SINGLE, 1);
        answered.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(existing.getId())).thenReturn(List.of(answered));

        PaperPreviewResponse preview = service.preview(payload());

        assertEquals("EXISTING_DRAFT", preview.collision());
        assertEquals(existing.getId(), preview.collisionPaperId());
        assertEquals(1, preview.existingAnsweredCount(),
                "giao dien phai noi ro se giu bao nhieu dap an da tick");
    }

    @Test
    void importRejectsFingerprintThatDoesNotMatchThePayload() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, "f".repeat(64)));

        assertEquals("FINGERPRINT_MISMATCH", ex.code());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void importRequiresConfirmedFingerprint() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, null));

        assertEquals("FINGERPRINT_REQUIRED", ex.code());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void reimportOfDraftReturnsExistingPaperWithoutLosingAnswers() {
        GradingPaperEntity existing = GradingPaperEntity.draft(
                "CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
        GradingPaperQuestionEntity answered = GradingPaperQuestionEntity.of(
                existing.getId(), 111L, PaperSection.GRAMMAR, 1, 1, BigDecimal.ONE, null,
                null, null, "c".repeat(64), AnswerMode.SINGLE, 1);
        answered.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(existing.getId()))
                .thenReturn(List.of(answered));

        PaperSummaryResponse response = service.importPayload(
                UUID.randomUUID(), payload(), null, PaperFingerprint.of(
                        new PaperPayloadNormalizer().normalize(payload())));

        assertEquals(existing.getId(), response.id());
        assertEquals("DRAFT", response.status());
        assertEquals(1, response.answeredCount(), "dap an da tick phai duoc giu");
        verify(paperRepository, never()).save(any());
        verify(answerRepository, never()).saveAll(any());
    }

    @Test
    void reimportOfPublishedPaperIsRejected() {
        GradingPaperEntity published = GradingPaperEntity.draft(
                "CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
        published.markReady();
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(published));

        String fingerprint = PaperFingerprint.of(new PaperPayloadNormalizer().normalize(payload()));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, fingerprint));

        assertEquals("PAPER_ALREADY_PUBLISHED", ex.code());
        verify(paperRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test -Dtest=PaperImportServiceTest`
Expected: FAIL — chưa có `PaperImportService`.

- [ ] **Step 3: Viết service**

`PaperImportService.preview(payload)` — **chỉ đọc, không ghi**:

1. `normalized = normalizer.normalize(payload)` (mọi lỗi validate bật ra ở đây, trước khi ghi bất cứ gì)
2. `fingerprint = PaperFingerprint.of(normalized)`
3. `findByFingerprintAndDeletedAtIsNull`: không có → `collision = "NONE"`; có và `DRAFT` → `"EXISTING_DRAFT"` kèm `collisionPaperId` và `existingAnsweredCount` đếm từ `questionRepository`; có và `READY` → `"EXISTING_PUBLISHED"`
4. Map từng `NormalizedQuestion` sang `PreviewQuestion`, **giữ nguyên `imageBase64`** để giao diện vẽ được ảnh mà chưa cần lưu gì
5. `warnings`: câu không có cả `questionText` lẫn `imageBase64` → `"Câu <qid> không có nội dung"`; câu thuộc `FILL_BLANK`/`MATCH` → `"Câu <qid> dạng <section> chưa chấm điểm được"`; `expectedAnswerCount == null` → `"Câu <qid> không đọc được số đáp án cần chọn"`

`@Transactional(readOnly = true)`.

`PaperImportService.importPayload(adminId, payload, sourcePayloadId, confirmedFingerprint)`:

0. `confirmedFingerprint` null/rỗng → `BadRequestException("FINGERPRINT_REQUIRED", "Cần xem trước đề rồi mới lưu.")`
1. `NormalizedPaper normalized = normalizer.normalize(payload)`
2. `String rawJson = mapper.writeValueAsString(payload)`; `payloadSha256 = Sha256.hexUtf8(rawJson)`
3. `fingerprint = PaperFingerprint.of(normalized)`. Khác `confirmedFingerprint` → `BadRequestException("FINGERPRINT_MISMATCH", "Payload đã thay đổi so với lúc xem trước. Hãy xem lại.")`. Đây là chỗ ép ở tầng backend cái luật "phải xem trước khi lưu" — không phụ thuộc thiện chí của giao diện.
4. `findByFingerprintAndDeletedAtIsNull` có kết quả:
   - `status == READY` → `ConflictException("PAPER_ALREADY_PUBLISHED", "Đề đã phát hành. Xoá đề cũ trước khi nhập lại.")`
   - `status == DRAFT` → **trả về đề cũ, không ghi gì thêm**. Đây là điểm quan trọng: nhập lại không được làm mất đáp án admin đã tick. Dựng `PaperSummaryResponse` từ đề cũ với `answeredCount` đếm từ `questionRepository`.
5. `findByPayloadSha256AndDeletedAtIsNull` có kết quả → xử lý y như bước 4
6. `paperRepository.save(GradingPaperEntity.draft(...))`
7. Mỗi câu: `contentSha256` = `Sha256.hexUtf8(questionText)` nếu có text, ngược lại `Sha256.hex(Base64.getDecoder().decode(imageBase64))`; `imageSha256` = hash ảnh nếu có ảnh, else `null`. Câu không có cả text lẫn ảnh → `contentSha256 = Sha256.hexUtf8("qid:" + qid)` để cột `NOT NULL` luôn có giá trị.
8. `questionRepository.saveAll(...)`, `answerRepository.saveAll(...)` — `optionSha256` = hash text lựa chọn nếu có text, else `null`
9. Trả `PaperSummaryResponse` với `answeredCount = 0`, `unansweredCount = số câu`

Đánh dấu `@Transactional`. Không dùng `@Transactional(readOnly = true)`.

- [ ] **Step 4: Viết controller**

```java
package com.fuoverflow.grading.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.grading.api.dto.ImportPaperRequest;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.application.PaperImportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/grading/papers")
public class GradingPaperAdminController {

    private final PaperImportService importService;

    public GradingPaperAdminController(PaperImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/preview")
    @RequirePermission("grading.paper.admin:create")
    public ApiResponse<PaperPreviewResponse> preview(@Valid @RequestBody ImportPaperRequest request) {
        return ApiResponse.ok(importService.preview(request.payload()));
    }

    @PostMapping("/import")
    @RequirePermission("grading.paper.admin:create")
    public ApiResponse<PaperSummaryResponse> importPaper(
            Authentication authentication,
            @Valid @RequestBody ImportPaperRequest request) {
        UUID adminId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(
                importService.importPayload(adminId, request.payload(), request.payloadId(),
                        request.confirmedFingerprint()),
                "Đã lưu đề, hãy hoàn thiện đáp án trước khi phát hành");
    }
}
```

`ImportPaperRequest` là record `(JsonNode payload, UUID payloadId, String confirmedFingerprint)`. Endpoint `/preview` bỏ qua `confirmedFingerprint`. Nếu `payload` null và `payloadId` null → service ném `BadRequestException("PAYLOAD_REQUIRED", "Cần payload JSON hoặc payloadId.")`. Nhánh đọc từ `public_api_payloads` theo `payloadId` làm ở Task 6 khi đã có repository đọc bảng đó; ở task này chỉ nhận `payload` trực tiếp.

- [ ] **Step 5: Chạy test, xác nhận PASS**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS toàn bộ.

- [ ] **Step 6: Commit**

```bash
git add backend/grading/src/main/java/com/fuoverflow/grading/application/PaperImportService.java \
        backend/grading/src/main/java/com/fuoverflow/grading/api \
        backend/grading/src/test/java/com/fuoverflow/grading/application/PaperImportServiceTest.java
git commit -m "feat(grading): import exam payload into draft paper"
```

---

### Task 5: Tick đáp án và cổng phát hành

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/application/PaperAnswerService.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/SetAnswerRequest.java`
- Modify: `backend/grading/src/main/java/com/fuoverflow/grading/api/GradingPaperAdminController.java`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/application/PaperAnswerServiceTest.java`

**Interfaces:**
- Consumes: repository và entity của Task 1.
- Produces:
  - `record SetAnswerRequest(List<Long> qaids)`
  - `PaperAnswerService.setAnswer(UUID paperId, long qid, List<Long> qaids) : void`
  - `PaperAnswerService.publish(UUID paperId) : PaperSummaryResponse`
  - `PaperAnswerService.softDelete(UUID paperId) : void`

- [ ] **Step 1: Viết test thất bại**

```java
package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperAnswerServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperAnswerService service;
    private GradingPaperEntity paper;

    @BeforeEach
    void setUp() {
        service = new PaperAnswerService(paperRepository, questionRepository, answerRepository);
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    private GradingPaperQuestionEntity question(long qid, AnswerMode mode, Integer expected) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, null, null, "c".repeat(64), mode, expected);
    }

    private List<GradingPaperAnswerEntity> options(GradingPaperQuestionEntity q, long... qaids) {
        List<GradingPaperAnswerEntity> list = new java.util.ArrayList<>();
        for (int i = 0; i < qaids.length; i++) {
            list.add(GradingPaperAnswerEntity.of(paper.getId(), q.getId(), q.getQid(), qaids[i], i, null, null));
        }
        return list;
    }

    @Test
    void setAnswerMarksChosenOptionCorrectAndQuestionAnswered() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        List<GradingPaperAnswerEntity> opts = options(q, 9001, 9002);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);

        service.setAnswer(paper.getId(), 111L, List.of(9002L));

        assertFalse(opts.get(0).isCorrect());
        assertTrue(opts.get(1).isCorrect());
        assertTrue(q.isAnswered());
    }

    @Test
    void setAnswerRejectsQaidFromAnotherQuestion() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(options(q, 9001, 9002));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 111L, List.of(7777L)));

        assertEquals("QAID_NOT_IN_QUESTION", ex.code());
    }

    @Test
    void setAnswerRejectsWrongNumberOfAnswers() {
        GradingPaperQuestionEntity q = question(222, AnswerMode.MULTI, 2);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 222L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 222L)).thenReturn(options(q, 1, 2, 3));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 222L, List.of(1L, 2L, 3L)));

        assertEquals("ANSWER_COUNT_MISMATCH", ex.code());
    }

    @Test
    void setAnswerRejectsEmptySelection() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(options(q, 9001, 9002));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 111L, List.of()));

        assertEquals("ANSWER_REQUIRED", ex.code());
    }

    @Test
    void publishRejectsWhenAQuestionHasNoAnswer() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.publish(paper.getId()));

        assertEquals("PAPER_INCOMPLETE", ex.code());
        assertTrue(ex.getMessage().contains("111"), "loi phai neu ro qid con thieu");
        assertEquals(PaperStatus.DRAFT, paper.getStatus());
    }

    @Test
    void publishSucceedsWhenEveryQuestionAnswered() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        service.publish(paper.getId());

        assertEquals(PaperStatus.READY, paper.getStatus());
    }

    @Test
    void publishRejectsUnknownPaper() {
        UUID missing = UUID.randomUUID();
        when(paperRepository.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.publish(missing));
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test -Dtest=PaperAnswerServiceTest`
Expected: FAIL — chưa có `PaperAnswerService`.

- [ ] **Step 3: Viết service**

`setAnswer(paperId, qid, qaids)`:
1. Nạp paper (`findByIdAndDeletedAtIsNull`, không có → `NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề.")`)
2. Paper đã `READY` → `ConflictException("PAPER_ALREADY_PUBLISHED", "Đề đã phát hành, không sửa được đáp án.")`
3. Nạp câu (`findByPaperIdAndQid`, không có → `NotFoundException("QUESTION_NOT_FOUND", ...)`)
4. `qaids` rỗng/null → `BadRequestException("ANSWER_REQUIRED", "Chưa chọn đáp án.")`
5. Nạp toàn bộ option của câu. Có `qaid` không thuộc câu → `BadRequestException("QAID_NOT_IN_QUESTION", ...)`
6. `expectedAnswerCount != null` và `qaids.size() != expectedAnswerCount` → `BadRequestException("ANSWER_COUNT_MISMATCH", "Câu này cần chọn đúng N đáp án.")`. `expectedAnswerCount == null` thì bỏ qua bước này.
7. Với mỗi option: `setCorrect(qaids.contains(option.getQaid()))`. `saveAll`.
8. `question.applyAnswer(AnswerSource.MANUAL, null)`, `save`.

Dùng `Set` từ `qaids` để so, và loại trùng trước khi đếm — gửi `[1,1]` cho câu cần 2 đáp án phải bị chặn.

`publish(paperId)`:
1. Nạp paper, `READY` rồi → `ConflictException("PAPER_ALREADY_PUBLISHED", ...)`
2. Nạp mọi câu. Câu `answered == false` → thu vào danh sách thiếu. Câu chỉ có `answer_source == SUGGESTED` có `answered == false` nên tự động nằm trong danh sách thiếu — đúng chủ ý, gợi ý từ file không mở được cổng phát hành.
3. Danh sách thiếu không rỗng → `ConflictException("PAPER_INCOMPLETE", "Còn N câu chưa có đáp án: qid1, qid2, ...")`. Message **phải** chứa các qid.
4. `paper.markReady()`, `save`, trả `PaperSummaryResponse`.

`softDelete(paperId)`: nạp paper, `paper.softDelete()`, save. Fingerprint được giải phóng nhờ partial unique index.

- [ ] **Step 4: Thêm 3 endpoint vào controller**

```java
    @PutMapping("/{paperId}/questions/{qid}/answer")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<Void> setAnswer(
            @PathVariable UUID paperId,
            @PathVariable long qid,
            @Valid @RequestBody SetAnswerRequest request) {
        answerService.setAnswer(paperId, qid, request.qaids());
        return ApiResponse.ok(null, "Đã lưu đáp án");
    }

    @PostMapping("/{paperId}/publish")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<PaperSummaryResponse> publish(@PathVariable UUID paperId) {
        return ApiResponse.ok(answerService.publish(paperId), "Đã phát hành đề");
    }

    @DeleteMapping("/{paperId}")
    @RequirePermission("grading.paper.admin:delete")
    public ApiResponse<Void> delete(@PathVariable UUID paperId) {
        answerService.softDelete(paperId);
        return ApiResponse.ok(null, "Đã xoá đề");
    }
```

`SetAnswerRequest` là record `(@NotEmpty List<Long> qaids)`.

- [ ] **Step 5: Chạy test, xác nhận PASS**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS toàn bộ.

- [ ] **Step 6: Commit**

```bash
git add backend/grading/src/main/java/com/fuoverflow/grading \
        backend/grading/src/test/java/com/fuoverflow/grading/application/PaperAnswerServiceTest.java
git commit -m "feat(grading): set answers and gate paper publishing on completeness"
```

---

### Task 6: Endpoint đọc, ảnh câu hỏi, permission

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/application/PaperQueryService.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PaperDetailResponse.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PaperQuestionResponse.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/PaperOptionResponse.java`
- Modify: `backend/grading/src/main/java/com/fuoverflow/grading/api/GradingPaperAdminController.java`
- Create: `backend/app/src/main/resources/db/migration/V52__grading_permissions.sql`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/application/PaperQueryServiceTest.java`

**Interfaces:**
- Consumes: repository Task 1, `PaperSummaryResponse` Task 4.
- Produces:
  - `record PaperOptionResponse(long qaid, int optionIndex, String text, boolean isCorrect)`
  - `record PaperQuestionResponse(long qid, String section, Integer qType, int displayNo, BigDecimal mark, String questionText, boolean hasImage, String answerMode, Integer expectedAnswerCount, boolean answered, List<PaperOptionResponse> options)`
  - `record PaperDetailResponse(PaperSummaryResponse paper, List<Long> unansweredQids, List<PaperQuestionResponse> questions)`
  - `PaperQueryService.list(String subjectCode, String status) : List<PaperSummaryResponse>`
  - `PaperQueryService.detail(UUID paperId) : PaperDetailResponse`
  - `PaperQueryService.questionImage(UUID paperId, long qid) : byte[]`
  - `PaperQueryService.readySubjectCodes() : List<String>` — Plan 2 dùng cho `/api/v1/check-score/subjects`

- [ ] **Step 1: Viết test thất bại**

```java
package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.api.dto.PaperDetailResponse;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperQueryServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperQueryService service;
    private GradingPaperEntity paper;

    private static final String RAW = """
        {"ExamCode":"CSP201m_SU26_FE_315379","GrammarQuestions":[
          {"QID":111,"ImageData":"aGVsbG8="}]}
        """;

    @BeforeEach
    void setUp() {
        service = new PaperQueryService(paperRepository, questionRepository, answerRepository,
                new com.fasterxml.jackson.databind.ObjectMapper());
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 2, 60,
                new BigDecimal("50.00"), RAW, "b".repeat(64), null, UUID.randomUUID());
    }

    private GradingPaperQuestionEntity question(long qid, boolean answered) {
        GradingPaperQuestionEntity q = GradingPaperQuestionEntity.of(paper.getId(), qid,
                PaperSection.GRAMMAR, 1, (int) qid, BigDecimal.ONE, null, null, null,
                "c".repeat(64), AnswerMode.SINGLE, 1);
        if (answered) {
            q.applyAnswer(AnswerSource.MANUAL, null);
        }
        return q;
    }

    @Test
    void detailListsUnansweredQids() {
        GradingPaperQuestionEntity done = question(111, true);
        GradingPaperQuestionEntity todo = question(222, false);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId()))
                .thenReturn(List.of(done, todo));
        when(answerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(paper.getId()))
                .thenReturn(List.of(GradingPaperAnswerEntity.of(paper.getId(), done.getId(), 111L, 9001L, 0, "A", null)));

        PaperDetailResponse detail = service.detail(paper.getId());

        assertEquals(List.of(222L), detail.unansweredQids());
        assertEquals(1, detail.paper().answeredCount());
        assertEquals(1, detail.paper().unansweredCount());
        assertEquals(2, detail.questions().size());
    }

    @Test
    void questionImageDecodesBase64FromRawPayload() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        byte[] image = service.questionImage(paper.getId(), 111L);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), image);
    }

    @Test
    void questionImageThrowsWhenQuestionHasNoImage() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.questionImage(paper.getId(), 999L));

        assertEquals("QUESTION_IMAGE_NOT_FOUND", ex.code());
    }

    @Test
    void detailExposesIsCorrectForAdminOnly() {
        // DTO admin duoc phep chua isCorrect; test nay chot rang truong do ton tai dung ten
        GradingPaperAnswerEntity option =
                GradingPaperAnswerEntity.of(paper.getId(), UUID.randomUUID(), 111L, 9001L, 0, "A", null);
        option.setCorrect(true);
        GradingPaperQuestionEntity q = question(111, true);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));
        when(answerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(paper.getId()))
                .thenReturn(List.of(option));

        PaperDetailResponse detail = service.detail(paper.getId());

        assertTrue(detail.questions().get(0).options().get(0).isCorrect());
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test -Dtest=PaperQueryServiceTest`
Expected: FAIL — chưa có `PaperQueryService`.

- [ ] **Step 3: Viết service và DTO**

`detail(paperId)`: nạp paper + câu + option, gom option theo `qid`, dựng `PaperDetailResponse`. `hasImage` xác định bằng `imageSha256 != null`. `unansweredQids` = qid của câu `answered == false`, giữ thứ tự `displayNo`.

`questionImage(paperId, qid)`: đọc `paper.getRawPayload()` bằng `ObjectMapper`, tìm trong 5 mảng section (kể cả `ReadingQuestions[].PassageQuestions`) node có `QID == qid`, lấy `ImageData`, `Base64.getDecoder().decode(...)`. Không thấy hoặc `ImageData` rỗng → `NotFoundException("QUESTION_IMAGE_NOT_FOUND", "Câu này không có ảnh.")`.

`list(subjectCode, status)`: `subjectCode` null → `findByDeletedAtIsNullOrderByCreatedAtDesc`, ngược lại `findBySubjectCodeIgnoreCase...`. `status` không null thì lọc trong bộ nhớ theo `PaperStatus.valueOf(status)`. Mỗi paper cần `answeredCount` — đếm qua `questionRepository.findByPaperIdOrderByDisplayNoAsc`.

`readySubjectCodes()`: lọc `status == READY`, `map(getSubjectCode)`, `distinct`, `sorted`.

`@Transactional(readOnly = true)` cho cả 4 method.

- [ ] **Step 4: Thêm endpoint đọc vào controller**

```java
    @GetMapping
    @RequirePermission("grading.paper.admin:read")
    public ApiResponse<List<PaperSummaryResponse>> list(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.list(subject, status));
    }

    @GetMapping("/{paperId}")
    @RequirePermission("grading.paper.admin:read")
    public ApiResponse<PaperDetailResponse> detail(@PathVariable UUID paperId) {
        return ApiResponse.ok(queryService.detail(paperId));
    }

    @GetMapping(value = "/{paperId}/questions/{qid}/image", produces = MediaType.IMAGE_PNG_VALUE)
    @RequirePermission("grading.paper.admin:read")
    public ResponseEntity<byte[]> questionImage(@PathVariable UUID paperId, @PathVariable long qid) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(queryService.questionImage(paperId, qid));
    }
```

- [ ] **Step 5: Tạo migration permission**

Create `backend/app/src/main/resources/db/migration/V52__grading_permissions.sql`, theo đúng khuôn `V48__add_announcement_permissions.sql`:

```sql
INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('grading.paper.admin:read',   'grading', 'grading.paper.admin', 'read',   'Admin: xem bank de thi noi bo'),
  ('grading.paper.admin:create', 'grading', 'grading.paper.admin', 'create', 'Admin: nhap de thi tu payload JSON'),
  ('grading.paper.admin:update', 'grading', 'grading.paper.admin', 'update', 'Admin: tick dap an va phat hanh de'),
  ('grading.paper.admin:delete', 'grading', 'grading.paper.admin', 'delete', 'Admin: xoa de khoi bank')
ON CONFLICT (slug) DO NOTHING;

UPDATE roles
SET permissions_json = permissions_json || '["grading.paper.admin:read","grading.paper.admin:create","grading.paper.admin:update","grading.paper.admin:delete"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["grading.paper.admin:read"]'::jsonb);

UPDATE roles
SET permissions_json = permissions_json || '["grading.paper.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["grading.paper.admin:read"]'::jsonb);
```

- [ ] **Step 6: Chạy test và build đầy đủ**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS toàn bộ.

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Kiểm chứng tay với payload thật**

```bash
cd backend && docker compose up -d postgres redis
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

Đăng nhập bằng tài khoản ADMIN, lấy access token, rồi:

```bash
# import de CSP201m 50 cau tu dump that
python3 - <<'PY' > /tmp/import-body.json
import json
o=json.load(open('temp/public_api_payloads_202608172200.json',encoding='utf-8'))
print(json.dumps({"payload": json.loads(o['public_api_payloads'][2]['payload_json'])}))
PY

curl -sS -X POST http://localhost:8080/api/v1/admin/grading/papers/import \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  --data @/tmp/import-body.json | python3 -m json.tool
```

Kỳ vọng: `status: "DRAFT"`, `questionCount: 50`, `unansweredCount: 50`.
Rồi gọi `POST .../publish` ngay và kỳ vọng `409 PAPER_INCOMPLETE` có liệt kê qid.

- [ ] **Step 8: Commit**

```bash
git add backend/grading backend/app/src/main/resources/db/migration/V52__grading_permissions.sql
git commit -m "feat(grading): add paper read endpoints, question image streaming and permissions"
```

---

### Task 7: Nhập bộ đáp án rời và xác nhận gợi ý

Đây là task trả lời tình huống: admin có một file JSON **có** đáp án và một file **không có**. Hai
dialect JSON không chung field định danh nào (spec mục "Hai dạng JSON đáp án"), nên độ tin cậy phải
khác nhau.

**Files:**
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/application/PaperAnswerImportService.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/ApplyAnswersRequest.java`
- Create: `backend/grading/src/main/java/com/fuoverflow/grading/api/dto/ApplyAnswersResponse.java`
- Modify: `backend/grading/src/main/java/com/fuoverflow/grading/api/GradingPaperAdminController.java`
- Test: `backend/grading/src/test/java/com/fuoverflow/grading/application/PaperAnswerImportServiceTest.java`

**Interfaces:**
- Consumes: `PaperAnswerService.setAnswer` (Task 5) không dùng lại — service này ghi trực tiếp để đặt được `AnswerSource`. Dùng 3 repository của Task 1 và `AnswerSource` enum.
- Produces:
  - `record AnswerItem(long qid, List<Long> qaids, List<String> optionTexts, List<String> letters)`
  - `record ApplyAnswersRequest(String examCode, String sourceRef, String conflictPolicy, boolean dryRun, List<AnswerItem> items)`
  - `record ApplyAnswersOutcome(long qid, String result, String reason)` — `result` ∈ `APPLIED`, `SUGGESTED`, `SKIPPED`
  - `record ApplyAnswersResponse(int applied, int suggested, int skipped, List<Long> stillUnansweredQids, List<ApplyAnswersOutcome> outcomes)`
  - `PaperAnswerImportService.apply(UUID paperId, ApplyAnswersRequest request) : ApplyAnswersResponse`
  - `PaperAnswerImportService.confirmSuggestion(UUID paperId, long qid) : void`

- [ ] **Step 1: Viết test thất bại**

```java
package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.grading.api.dto.AnswerItem;
import com.fuoverflow.grading.api.dto.ApplyAnswersRequest;
import com.fuoverflow.grading.api.dto.ApplyAnswersResponse;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import com.fuoverflow.grading.support.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperAnswerImportServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperAnswerImportService service;
    private GradingPaperEntity paper;

    @BeforeEach
    void setUp() {
        service = new PaperAnswerImportService(paperRepository, questionRepository, answerRepository);
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    /** Cau thuan anh: question_text null, image_sha256 khac null. */
    private GradingPaperQuestionEntity imageQuestion(long qid, int expected) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, null, "img".repeat(21) + "a", "c".repeat(64),
                expected > 1 ? AnswerMode.MULTI : AnswerMode.SINGLE, expected);
    }

    /** Cau dang text: question_text co noi dung, khong co anh. */
    private GradingPaperQuestionEntity textQuestion(long qid) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, "Cau hoi text?", null, "c".repeat(64), AnswerMode.SINGLE, 1);
    }

    private List<GradingPaperAnswerEntity> options(GradingPaperQuestionEntity q, String... texts) {
        List<GradingPaperAnswerEntity> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            list.add(GradingPaperAnswerEntity.of(paper.getId(), q.getId(), q.getQid(), 9000L + i, i,
                    texts[i], texts[i] == null ? null : Sha256.hexUtf8(texts[i])));
        }
        return list;
    }

    private ApplyAnswersRequest request(AnswerItem... items) {
        return new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump-2026-08", "KEEP_EXISTING",
                false, List.of(items));
    }

    private ApplyAnswersRequest dryRun(AnswerItem... items) {
        return new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump-2026-08", "KEEP_EXISTING",
                true, List.of(items));
    }

    @Test
    void byQaidIsTrustedAndCountsAsAnswered() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.applied());
        assertEquals(0, response.suggested());
        assertTrue(opts.get(1).isCorrect());
        assertEquals(AnswerSource.IMPORTED, q.getAnswerSource());
        assertTrue(q.isAnswered());
    }

    @Test
    void byLetterOnImageQuestionIsOnlyASuggestion() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null, null, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("C"))));

        assertEquals(0, response.applied());
        assertEquals(1, response.suggested());
        assertTrue(opts.get(2).isCorrect(), "C = option_index 2");
        assertEquals(AnswerSource.SUGGESTED, q.getAnswerSource());
        assertFalse(q.isAnswered(), "goi y KHONG duoc mo cong phat hanh");
        assertEquals(List.of(111L), response.stillUnansweredQids());
    }

    @Test
    void byLetterOnTextQuestionIsRejected() {
        GradingPaperQuestionEntity q = textQuestion(111);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("C"))));

        assertEquals(0, response.applied());
        assertEquals(1, response.skipped());
        assertEquals("LETTER_NOT_ALLOWED_FOR_TEXT_QUESTION", response.outcomes().get(0).reason());
    }

    @Test
    void byLetterBeyondOptionCountIsSkipped() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(options(q, null, null));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("D"))));

        assertEquals(1, response.skipped());
        assertEquals("LETTER_OUT_OF_RANGE", response.outcomes().get(0).reason());
    }

    @Test
    void letterCountMustMatchExpectedAnswerCount() {
        GradingPaperQuestionEntity q = imageQuestion(111, 2);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(options(q, null, null, null, null));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("A"))));

        assertEquals(1, response.skipped());
        assertEquals("ANSWER_COUNT_MISMATCH", response.outcomes().get(0).reason());
    }

    @Test
    void byOptionTextMatchesOnHashAndIsTrusted() {
        GradingPaperQuestionEntity q = textQuestion(111);
        List<GradingPaperAnswerEntity> opts = options(q, "Sai", "Dung");
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, List.of("Dung"), null)));

        assertEquals(1, response.applied());
        assertTrue(opts.get(1).isCorrect());
        assertEquals(AnswerSource.IMPORTED, q.getAnswerSource());
    }

    @Test
    void keepExistingDoesNotOverwriteManualAnswer() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.skipped());
        assertEquals("ALREADY_ANSWERED_MANUALLY", response.outcomes().get(0).reason());
        assertEquals(AnswerSource.MANUAL, q.getAnswerSource());
    }

    @Test
    void unknownQidIsSkippedNotFatal() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 999L)).thenReturn(Optional.empty());
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of());

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(999L, List.of(1L), null, null)));

        assertEquals(1, response.skipped());
        assertEquals("QID_NOT_IN_PAPER", response.outcomes().get(0).reason());
    }

    @Test
    void dryRunComputesOutcomesWithoutWritingAnything() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                dryRun(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.applied(), "dry run van bao ket qua se ap duoc");
        assertFalse(opts.get(1).isCorrect(), "dry run KHONG duoc doi du lieu");
        assertFalse(q.isAnswered(), "dry run KHONG duoc doi du lieu");
        assertEquals(null, q.getAnswerSource());
        verify(answerRepository, never()).saveAll(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void examCodeMismatchIsRejectedOutright() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        ApplyAnswersRequest wrong = new ApplyAnswersRequest("SCM302_SU26_FE_553972", "dump", "KEEP_EXISTING",
                List.of(new AnswerItem(111L, List.of(9001L), null, null)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.apply(paper.getId(), wrong));

        assertEquals("EXAM_CODE_MISMATCH", ex.code());
    }

    @Test
    void confirmSuggestionPromotesItToManual() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.suggestAnswer("dump-2026-08");
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));

        service.confirmSuggestion(paper.getId(), 111L);

        assertEquals(AnswerSource.MANUAL, q.getAnswerSource());
        assertTrue(q.isAnswered());
    }

    @Test
    void confirmRejectsQuestionWithoutSuggestion() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.confirmSuggestion(paper.getId(), 111L));

        assertEquals("NO_SUGGESTION_TO_CONFIRM", ex.code());
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận FAIL**

Run: `cd backend && mvn -q -pl grading -am test -Dtest=PaperAnswerImportServiceTest`
Expected: FAIL — chưa có `PaperAnswerImportService`.

- [ ] **Step 3: Viết 3 record DTO**

```java
// api/dto/AnswerItem.java
package com.fuoverflow.grading.api.dto;

import java.util.List;

/** Dung dung MOT trong ba cach khoa: qaids, optionTexts, hoac letters. */
public record AnswerItem(long qid, List<Long> qaids, List<String> optionTexts, List<String> letters) {
}
```

```java
// api/dto/ApplyAnswersRequest.java
package com.fuoverflow.grading.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ApplyAnswersRequest(
        String examCode,
        String sourceRef,
        String conflictPolicy,
        /** true = chi tinh ket qua de admin xem truoc, KHONG ghi gi. */
        boolean dryRun,
        @NotEmpty List<AnswerItem> items
) {
}
```

```java
// api/dto/ApplyAnswersResponse.java
package com.fuoverflow.grading.api.dto;

import java.util.List;

public record ApplyAnswersOutcome(long qid, String result, String reason) {
}

// file rieng: ApplyAnswersResponse.java
public record ApplyAnswersResponse(
        int applied,
        int suggested,
        int skipped,
        List<Long> stillUnansweredQids,
        List<ApplyAnswersOutcome> outcomes
) {
}
```

`ApplyAnswersOutcome` và `ApplyAnswersResponse` là hai file riêng, mỗi record một file public.

- [ ] **Step 4: Viết service**

`apply(paperId, request)`:

1. Nạp paper. Không có → `NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề.")`
2. `status == READY` → `ConflictException("PAPER_ALREADY_PUBLISHED", "Đề đã phát hành.")`
3. `request.examCode()` không null và khác `paper.getExamCode()` → `BadRequestException("EXAM_CODE_MISMATCH", "Bộ đáp án thuộc mã đề khác.")`. Đây là kiểm tra chặn cả lô, vì nhập lệch đề là sai toàn bộ.
4. `conflictPolicy` mặc định `KEEP_EXISTING` khi null. Giá trị lạ → `BadRequestException("INVALID_CONFLICT_POLICY", ...)`.
5. Với **từng** item, không dừng cả lô khi một item lỗi — thu vào `outcomes`:
   - Câu không tồn tại → `SKIPPED` / `QID_NOT_IN_PAPER`
   - Câu đã `answered` và `answerSource == MANUAL` và policy `KEEP_EXISTING` → `SKIPPED` / `ALREADY_ANSWERED_MANUALLY`
   - Policy `FAIL` mà câu đã có đáp án → ném `ConflictException("ANSWER_CONFLICT", "Câu <qid> đã có đáp án.")`
   - Đúng một trong ba cách khoá phải có giá trị; 0 hoặc ≥2 → `SKIPPED` / `AMBIGUOUS_KEY`
   - `qaids`: qaid không thuộc câu → `SKIPPED` / `QAID_NOT_IN_QUESTION`. Hợp lệ → `setCorrect`, `applyAnswer(IMPORTED, sourceRef)`, `APPLIED`
   - `optionTexts`: khớp theo `Sha256.hexUtf8(text)` với `option_sha256`. Không khớp đủ → `SKIPPED` / `OPTION_TEXT_NOT_FOUND`. Khớp → `applyAnswer(IMPORTED, sourceRef)`, `APPLIED`
   - `letters`: câu có `questionText != null` hoặc `imageSha256 == null` → `SKIPPED` / `LETTER_NOT_ALLOWED_FOR_TEXT_QUESTION`. Chữ cái ngoài dải `A..chr(64+optionCount)` → `SKIPPED` / `LETTER_OUT_OF_RANGE`. Hợp lệ → `setCorrect` theo `option_index = letter - 'A'`, `suggestAnswer(sourceRef)`, `SUGGESTED`
   - Mọi cách khoá: số lượng khác `expectedAnswerCount` (khi cột này không null) → `SKIPPED` / `ANSWER_COUNT_MISMATCH`. Loại trùng trước khi đếm.
6. Nạp lại toàn bộ câu, `stillUnansweredQids` = qid của câu `answered == false`, giữ thứ tự `displayNo`.

**Cấu trúc bắt buộc để `dryRun` đúng:** tách thành hai pha. Pha 1 `computePlan(...)` chỉ **đọc** và
trả về danh sách `outcomes` kèm, với mỗi câu áp được, tập `qaid` sẽ bật `is_correct` và `AnswerSource`
sẽ đặt. Pha 2 `persist(plan)` mới gọi `setCorrect` / `applyAnswer` / `suggestAnswer` và `saveAll`.
`dryRun == true` thì chạy pha 1 rồi dừng — tuyệt đối không mutate entity, vì entity đang nằm trong
persistence context nên chỉ cần gán field là Hibernate sẽ flush xuống DB dù chưa gọi `save`. Test
`dryRunComputesOutcomesWithoutWritingAnything` khoá đúng điểm này.

`confirmSuggestion(paperId, qid)`: nạp paper (`READY` → `ConflictException`), nạp câu; `answerSource != SUGGESTED` → `BadRequestException("NO_SUGGESTION_TO_CONFIRM", "Câu này không có gợi ý cần xác nhận.")`; ngược lại `applyAnswer(AnswerSource.MANUAL, question.getAnswerSourceRef())`, save. Cờ `is_correct` trên các option **không đổi** — gợi ý đã set sẵn, xác nhận chỉ nâng mức tin cậy.

`@Transactional` cho cả hai method.

- [ ] **Step 5: Thêm 2 endpoint vào controller**

```java
    @PostMapping("/{paperId}/answers/apply")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<ApplyAnswersResponse> applyAnswers(
            @PathVariable UUID paperId,
            @Valid @RequestBody ApplyAnswersRequest request) {
        return ApiResponse.ok(answerImportService.apply(paperId, request),
                "Đã nhập bộ đáp án");
    }

    @PostMapping("/{paperId}/questions/{qid}/confirm")
    @RequirePermission("grading.paper.admin:update")
    public ApiResponse<Void> confirmSuggestion(@PathVariable UUID paperId, @PathVariable long qid) {
        answerImportService.confirmSuggestion(paperId, qid);
        return ApiResponse.ok(null, "Đã xác nhận đáp án");
    }
```

- [ ] **Step 6: Chạy test và build**

Run: `cd backend && mvn -q -pl grading -am test`
Expected: PASS toàn bộ.

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/grading
git commit -m "feat(grading): apply external answer keys with per-source trust levels"
```

---

## Self-Review

**Spec coverage.** Mục 4 (mô hình dữ liệu) → Task 1. Mục 2.2 + 2.8 + 5 (validator, cổng lưu) → Task 2, 5. Mục 3.2 (fingerprint) → Task 3. Mục 5 (bảng endpoint admin) → Task 4, 5, 6. Mục 3.4 (hash nội dung) → Task 4 step 3. Permission → Task 6.

Mục "Xem trước rồi mới lưu" → Task 4 (`preview`, `confirmedFingerprint`) và Task 7 (`dryRun`). Mục "Nhập lại cùng một đề (idempotent)" → Task 4. Mục "Hai dạng JSON đáp án, hai mức tin cậy" → Task 7.

**Chưa phủ trong plan này, thuộc Plan 2 và 3:** mục 6 (decoder, hàng rào giới hạn, thuật toán chấm), mục 7 (throttle chống dò đáp án), mục 8 (UI). Nhánh import theo `payloadId` từ `public_api_payloads` cũng để Plan 2 vì nó cần repository đọc bảng đó.

**Type consistency.** `expectedAnswerCount` là `Integer` xuyên suốt (nullable). `qid`/`qaid` là `long` primitive trong entity và record, `Long` chỉ trong `List<Long> qaids` của request. `applyAnswer(AnswerSource, String)` / `suggestAnswer(String)` / `setCorrect(boolean)` dùng đúng tên ở Task 1, 5, 6, 7 — không còn `markAnswered` ở đâu. `PaperSummaryResponse` định nghĩa ở Task 4, dùng lại ở Task 5, 6.
