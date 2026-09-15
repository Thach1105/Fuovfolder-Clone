# Exam Webhook Batch Delivery and Auto-Publish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `POST /api/v1/exam/webhook/papers` accept an array of papers in one request, and publish webhook-ingested papers automatically instead of leaving them as drafts for an admin.

**Architecture:** The reader gains a `readAll` that splits any accepted body shape into one-paper deliveries; the receipt service loops over them, writing one `exam_webhook_events` row per paper under a derived event id and isolating per-paper failures; the ingest service publishes a paper once its content is built and lets a redelivery overwrite an already-published paper. The ingest worker, the events table, and the HMAC scheme are untouched.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Jackson, JUnit 5 + Mockito, Maven multi-module.

**Spec:** `docs/superpowers/specs/2026-09-14-exam-webhook-batch-autopublish-design.md`

## Global Constraints

- No Flyway migration. `'published'` already satisfies `exam_papers_status_check`; `exam_webhook_events` is unchanged.
- The HMAC signature covers the exact raw request bytes. The controller must keep binding the body as `String` — never a typed DTO.
- Batch size limit default: **50** papers (`fuexam.exam.webhook.max-papers-per-batch`).
- Batch `eventId` maximum length: **100** characters, so `"<eventId>#<index>"` fits `exam_webhook_events.event_id varchar(120)`.
- Derived event id format: `"<eventId>#<zero-based index>"`, batch bodies only. A single-paper body keeps its `eventId` verbatim and stores the sender's raw bytes verbatim.
- Error codes introduced: `WEBHOOK_PAYLOAD_AMBIGUOUS`, `WEBHOOK_BATCH_TOO_LARGE`. Existing codes keep their meaning.
- Build command for this module: `cd backend && mvn -q -pl exam -am test`. A single test class: `cd backend && mvn -q -pl exam -am test -Dtest=ClassName`.
- Error messages shown to the sender are Vietnamese, matching the surrounding code. Code, comments, and commit messages are English.

---

## File Structure

**Modified:**
- `backend/exam/src/main/java/com/fuoverflow/exam/config/ExamWebhookProperties.java` — new `maxPapersPerBatch` limit.
- `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadReader.java` — new `readAll` + `DeliveredPaper`; existing `read` untouched.
- `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookReceiptService.java` — per-paper loop.
- `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java` — batch response, status codes.
- `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java` — auto-publish, overwrite, active subject.
- `backend/app/src/main/resources/application.yml` — the new limit.

**Created:**
- `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookBatchReceiptResponse.java`
- `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookPaperReceipt.java`
- `backend/exam/src/test/java/com/fuoverflow/exam/api/ExamWebhookControllerTest.java`

**Deleted:**
- `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookReceiptResponse.java`

---

### Task 1: Batch-aware payload reading

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/config/ExamWebhookProperties.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadReader.java`
- Modify: `backend/app/src/main/resources/application.yml:94-103`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadReaderTest.java`
- Modify (compile fix only, positional record constructor gains a 10th argument): `ExamResourceFetcherTest.java:128`, `ExamWebhookIngestWorkerTest.java:150`, `ExamWebhookReceiptServiceTest.java:175`, `ExamWebhookSignatureVerifierTest.java:21`, `ExamPaperWebhookIngestFlowTest.java:80` — all under `backend/exam/src/test/java/com/fuoverflow/exam/application/`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `ExamWebhookPayloadReader.readAll(String rawBody, int maxPapers) : List<ExamWebhookPayloadReader.DeliveredPaper>` and the nested `public record DeliveredPaper(PaperWebhookRequest request, String payloadJson)`. Also `ExamWebhookProperties.maxPapersPerBatchOrDefault() : int`. Task 2 calls both.

The limit is a method parameter rather than a constructor dependency: the reader stays config-free, and its test keeps constructing it with two arguments.

- [ ] **Step 1: Write the failing tests**

Add to `ExamWebhookPayloadReaderTest` (the class already has `PNG` and `reader` fields):

```java
    private static final String BATCH = """
            {"eventId":"evt-b","eventType":"exam.paper.upserted",
             "sentAt":"2026-09-14T03:54:59Z",
             "papers":[
               {"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
                "title":"first","questions":[{"externalId":"1","questionText":"stem",
                "images":[{"sortOrder":0,"mimeType":"image/png","contentBase64":"%s"}]}]},
               {"examCode":"PRF192_SU26_FE_2","paperType":"FE","subjectCode":"PRF192",
                "title":"second","questions":[{"externalId":"1","questionText":"stem",
                "images":[{"sortOrder":0,"mimeType":"image/png","contentBase64":"%s"}]}]}]}
            """;

    private static final String SINGLE = """
            {"eventId":"evt-1","eventType":"exam.paper.upserted",
             "sentAt":"2026-09-01T03:54:59Z",
             "paper":{"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
             "title":"canonical","questions":[{"externalId":"1","questionText":"stem",
             "images":[{"sortOrder":0,"mimeType":"image/png","contentBase64":"%s"}]}]}}
            """;

    @Test
    void splitsABatchIntoOneDeliveryPerPaper() {
        List<ExamWebhookPayloadReader.DeliveredPaper> delivered =
                reader.readAll(BATCH.formatted(PNG, PNG), 50);

        assertEquals(2, delivered.size());
        assertEquals("evt-b#0", delivered.get(0).request().eventId());
        assertEquals("evt-b#1", delivered.get(1).request().eventId());
        assertEquals("first", delivered.get(0).request().paper().title());
        assertEquals("second", delivered.get(1).request().paper().title());
    }

    @Test
    void aStoredBatchSliceCarriesTheDerivedEventIdAndOnlyItsOwnPaper() {
        String slice = reader.readAll(BATCH.formatted(PNG, PNG), 50).get(1).payloadJson();

        // The worker re-parses the stored slice through read(), so it must be a canonical envelope.
        PaperWebhookRequest reparsed = reader.read(slice);
        assertEquals("evt-b#1", reparsed.eventId());
        assertEquals("exam.paper.upserted", reparsed.eventType());
        assertEquals("PRF192_SU26_FE_2", reparsed.paper().examCode());
        assertFalse(slice.contains("SCM302_SU26_FE_1"));
    }

    @Test
    void keepsTheSenderRawBytesForASinglePaperBody() {
        String body = SINGLE.formatted(PNG);

        List<ExamWebhookPayloadReader.DeliveredPaper> delivered = reader.readAll(body, 50);

        assertEquals(1, delivered.size());
        assertEquals("evt-1", delivered.get(0).request().eventId());
        assertSame(body, delivered.get(0).payloadJson());
    }

    @Test
    void readsAnEosBodyThroughReadAllToo() {
        String eos = """
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG);

        List<ExamWebhookPayloadReader.DeliveredPaper> delivered = reader.readAll(eos, 50);

        assertEquals(1, delivered.size());
        assertEquals("ENG", delivered.get(0).request().paper().subjectCode());
    }

    @Test
    void rejectsABodyCarryingBothPaperAndPapers() {
        String both = """
                {"eventId":"evt-x",
                 "paper":{"examCode":"A","paperType":"FE","subjectCode":"A"},
                 "papers":[{"examCode":"B","paperType":"FE","subjectCode":"B"}]}
                """;

        assertEquals("WEBHOOK_PAYLOAD_AMBIGUOUS",
                assertThrows(BadRequestException.class, () -> reader.readAll(both, 50)).code());
    }

    @Test
    void rejectsAnEmptyOrNonArrayPapersField() {
        String empty = "{\"eventId\":\"evt-x\",\"papers\":[]}";
        String notArray = "{\"eventId\":\"evt-x\",\"papers\":{\"examCode\":\"A\"}}";

        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.readAll(empty, 50)).code());
        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.readAll(notArray, 50)).code());
    }

    @Test
    void rejectsABatchOverTheLimit() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reader.readAll(BATCH.formatted(PNG, PNG), 1));

        assertEquals("WEBHOOK_BATCH_TOO_LARGE", ex.code());
    }

    @Test
    void rejectsABatchWithNoEventIdOrAnOverlongOne() {
        String missing = BATCH.formatted(PNG, PNG).replace("\"eventId\":\"evt-b\",", "");
        String overlong = BATCH.formatted(PNG, PNG)
                .replace("\"evt-b\"", "\"" + "e".repeat(101) + "\"");

        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.readAll(missing, 50)).code());
        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.readAll(overlong, 50)).code());
    }
```

Add these imports to the test class:

```java
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookPayloadReaderTest`
Expected: compilation failure — `cannot find symbol: method readAll(String,int)`.

- [ ] **Step 3: Add the batch limit to `ExamWebhookProperties`**

Append `Integer maxPapersPerBatch` as the **last** record component, after `pollIntervalMs`:

```java
public record ExamWebhookProperties(
        Map<String, String> clients,
        List<String> allowedResourceHosts,
        Long maxPayloadBytes,
        Long maxImageBytes,
        Long maxResourceBytes,
        Integer maxQuestions,
        Integer maxAttempts,
        Integer signatureToleranceSeconds,
        Long pollIntervalMs,
        Integer maxPapersPerBatch
) {
```

and add the accessor next to its siblings:

```java
    public int maxPapersPerBatchOrDefault() {
        return maxPapersPerBatch != null && maxPapersPerBatch > 0 ? maxPapersPerBatch : 50;
    }
```

- [ ] **Step 4: Fix the five positional constructor call sites in tests**

Each is a `new ExamWebhookProperties(...)` with nine arguments. Append `, null` as the tenth so the default applies:
- `ExamResourceFetcherTest.java:128`
- `ExamWebhookIngestWorkerTest.java:150`
- `ExamWebhookReceiptServiceTest.java:175`
- `ExamWebhookSignatureVerifierTest.java:21`
- `ExamPaperWebhookIngestFlowTest.java:80`

- [ ] **Step 5: Add the property to `application.yml`**

Under `fuexam.exam.webhook`, after the `max-questions` line:

```yaml
      max-papers-per-batch: ${EXAM_WEBHOOK_MAX_PAPERS_PER_BATCH:50}
```

- [ ] **Step 6: Implement `readAll` in `ExamWebhookPayloadReader`**

Add the constant, the record, and the two methods. Keep `read`, `looksLikeEos`, `parseTree`, and `readCanonical` exactly as they are.

```java
    /** Leaves room for the {@code "#<index>"} suffix inside {@code event_id varchar(120)}. */
    private static final int MAX_BATCH_EVENT_ID_LENGTH = 100;
```

```java
    /**
     * One paper of a delivery, paired with the single-paper envelope stored for it. A batch slice is
     * re-serialized rather than the sender's original bytes: the signature was verified against the
     * whole body before the split, and a stored row must hold exactly one paper because the ingest
     * worker re-parses it through {@link #read(String)}.
     */
    public record DeliveredPaper(PaperWebhookRequest request, String payloadJson) {
    }

    /** Splits any accepted body shape into one delivery per paper. */
    public List<DeliveredPaper> readAll(String rawBody, int maxPapers) {
        JsonNode tree = parseTree(rawBody);

        if (tree.hasNonNull("paper") && tree.hasNonNull("papers")) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_AMBIGUOUS",
                    "Body chỉ được có \"paper\" hoặc \"papers\", không được có cả hai.");
        }
        if (tree.has("papers")) {
            return readBatch(tree, maxPapers);
        }
        return List.of(new DeliveredPaper(read(rawBody), rawBody));
    }

    private List<DeliveredPaper> readBatch(JsonNode tree, int maxPapers) {
        JsonNode papers = tree.get("papers");
        if (papers == null || !papers.isArray() || papers.isEmpty()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID",
                    "\"papers\" phải là một mảng không rỗng.");
        }
        if (papers.size() > maxPapers) {
            throw new BadRequestException("WEBHOOK_BATCH_TOO_LARGE",
                    "Batch có " + papers.size() + " đề, vượt giới hạn " + maxPapers + ".");
        }

        String eventId = tree.path("eventId").asText("").trim();
        if (eventId.isEmpty()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Payload thiếu eventId.");
        }
        if (eventId.length() > MAX_BATCH_EVENT_ID_LENGTH) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID",
                    "eventId của batch không được dài quá " + MAX_BATCH_EVENT_ID_LENGTH + " ký tự.");
        }

        List<DeliveredPaper> delivered = new ArrayList<>(papers.size());
        for (int index = 0; index < papers.size(); index++) {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId + "#" + index);
            if (tree.hasNonNull("eventType")) {
                envelope.set("eventType", tree.get("eventType"));
            }
            if (tree.hasNonNull("sentAt")) {
                envelope.set("sentAt", tree.get("sentAt"));
            }
            envelope.set("paper", papers.get(index));

            String slice = writeSlice(envelope);
            delivered.add(new DeliveredPaper(readCanonical(slice), slice));
        }
        return delivered;
    }

    private String writeSlice(ObjectNode envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Không đọc được body.");
        }
    }
```

New imports for the reader:

```java
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
```

`tree.has("papers")` rather than `hasNonNull`: `"papers": null` must be rejected as invalid, not fall through to the single-paper path where it would produce `WEBHOOK_PAYLOAD_UNRECOGNIZED`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookPayloadReaderTest`
Expected: PASS, all tests green.

- [ ] **Step 8: Run the whole exam module to confirm the constructor fixes compile**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadReader.java \
        backend/exam/src/main/java/com/fuoverflow/exam/config/ExamWebhookProperties.java \
        backend/app/src/main/resources/application.yml \
        backend/exam/src/test/java/com/fuoverflow/exam/application/
git commit -m "feat(exam): read a webhook body as one delivery per paper

A sender holding a term of papers should not pay a round trip and a
signature per paper. readAll splits a papers[] batch into canonical
single-paper envelopes under derived event ids, leaving the single-paper
and EOS shapes byte-identical to what they are today."
```

---

### Task 2: One event row per paper, per-paper results

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookPaperReceipt.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookBatchReceiptResponse.java`
- Delete: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookReceiptResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookReceiptService.java:56-95`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java:32-38` (compile fix only; status codes are Task 3)
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookReceiptServiceTest.java`
- Test (compile fix): `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperWebhookIngestFlowTest.java:138,180,183,194,223,250,265,267`
- Modify: `docs/superpowers/specs/2026-09-14-exam-webhook-batch-autopublish-design.md`

**Interfaces:**
- Consumes: `ExamWebhookPayloadReader.readAll(String, int)`, `ExamWebhookPayloadReader.DeliveredPaper`, `ExamWebhookProperties.maxPapersPerBatchOrDefault()` from Task 1.
- Produces: `ExamWebhookReceiptService.receive(String clientId, String rawBody, String signatureHeader) : WebhookBatchReceiptResponse`; `WebhookBatchReceiptResponse(int accepted, int duplicate, int rejected, List<WebhookPaperReceipt> results)`; `WebhookPaperReceipt(int index, String examCode, UUID receiptId, String status, boolean duplicate, String errorCode, String errorMessage)`. Task 3 reads `accepted()`.

**Spec refinement to apply in Step 1 of this task.** The spec says the response "always lists per-item results" *and* that an all-rejected batch is a `400`. Those conflict: a thrown `ApiException` renders the shared error envelope, not a results array. The `400` wins, because it is what a single-paper sender gets today and must keep getting — including the `413` a single oversized paper produces. When every paper is rejected, the **first** rejection is rethrown unchanged.

- [ ] **Step 1: Correct the spec**

In `docs/superpowers/specs/2026-09-14-exam-webhook-batch-autopublish-design.md`, replace the sentence after the status-code table that begins "A batch mixing duplicates with rejections…" with:

```markdown
A batch mixing duplicates with rejections is therefore a `200` carrying per-paper errors, not a
`400`: the delivery did reach a known state for every paper, and the sender reads `results` to
see which ones need fixing.

When *every* paper is rejected there is no results array to return — the first rejection is
rethrown unchanged and rendered by the shared error advice. A single-paper delivery therefore
keeps exactly today's failure behaviour, including the `413` that an over-long question list
raises rather than a `400`.
```

- [ ] **Step 2: Write the failing tests**

In `ExamWebhookReceiptServiceTest`, first apply this mechanical rewrite to the six existing assertions so they read one paper out of the batch response — every downstream accessor keeps its name:

- line 60: `WebhookPaperReceipt response = service.receive("eos-crawler", body, header).results().get(0);`
- line 83: same rewrite
- line 101: `service.receive("eos-crawler", body, header).results().get(0).status()`
- line 160: `service.receive("eos-crawler", extra, headerFor(extra)).results().get(0).duplicate()`
- import `com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt` instead of `WebhookReceiptResponse`

Then add:

```java
    @Test
    void storesOneRowPerPaperOfABatchUnderDerivedEventIds() {
        String batch = batchBody();
        when(eventRepository.findByClientIdAndEventId(eq("eos-crawler"), anyString()))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookBatchReceiptResponse response =
                service.receive("eos-crawler", batch, headerFor(batch));

        assertEquals(2, response.accepted());
        assertEquals(0, response.duplicate());
        assertEquals(0, response.rejected());
        assertEquals(2, response.results().size());
        assertEquals(0, response.results().get(0).index());
        assertEquals("SCM302_SU26_FE_1", response.results().get(0).examCode());
        assertEquals("queued", response.results().get(1).status());

        ArgumentCaptor<ExamWebhookEventEntity> saved =
                ArgumentCaptor.forClass(ExamWebhookEventEntity.class);
        verify(eventRepository, times(2)).save(saved.capture());
        assertEquals("evt-b#0", saved.getAllValues().get(0).getEventId());
        assertEquals("evt-b#1", saved.getAllValues().get(1).getEventId());
        assertTrue(saved.getAllValues().get(0).getPayloadJson().contains("SCM302_SU26_FE_1"));
        assertFalse(saved.getAllValues().get(0).getPayloadJson().contains("PRF192_SU26_FE_2"));
    }

    @Test
    void oneInvalidPaperDoesNotBlockItsSiblings() {
        String batch = """
                {"eventId":"evt-b","eventType":"exam.paper.upserted",
                 "sentAt":"2026-09-14T03:54:59Z",
                 "papers":[
                   {"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
                    "title":"first","questions":[{"externalId":"1","questionText":"stem",
                    "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":70,
                    "sha256":"%s","contentBase64":"%s"}]}]},
                   {"examCode":"PRF192_SU26_FE_2","paperType":"FE","subjectCode":"PRF192",
                    "title":"second","questions":[]}]}
                """.formatted("a".repeat(64), PNG_BASE64);
        when(eventRepository.findByClientIdAndEventId(eq("eos-crawler"), anyString()))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookBatchReceiptResponse response =
                service.receive("eos-crawler", batch, headerFor(batch));

        assertEquals(1, response.accepted());
        assertEquals(1, response.rejected());
        assertEquals("WEBHOOK_FE_QUESTIONS_REQUIRED", response.results().get(1).errorCode());
        assertNull(response.results().get(1).receiptId());
        assertEquals("rejected", response.results().get(1).status());
        verify(eventRepository, times(1)).save(any());
    }

    @Test
    void rethrowsTheFirstRejectionWhenNoPaperIsAccepted() {
        String noQuestions = """
                {"eventId":"evt-9","eventType":"exam.paper.upserted",
                 "sentAt":"2026-08-31T03:54:59Z",
                 "papers":[{"examCode":"TEST_EOS_Client_1","paperType":"FE","subjectCode":"TEST",
                 "title":"t","questions":[]}]}
                """;
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-9#0"))
                .thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.receive("eos-crawler", noQuestions, headerFor(noQuestions)));

        assertEquals("WEBHOOK_FE_QUESTIONS_REQUIRED", ex.code());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void aBatchOfOnlyDuplicatesIsStillASuccessfulReceipt() {
        String batch = batchBody();
        ExamWebhookEventEntity existing = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-b#0", "exam.paper.upserted",
                "{}", "b".repeat(64), true, Instant.now());
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-b#0"))
                .thenReturn(Optional.of(existing));
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-b#1"))
                .thenReturn(Optional.of(existing));

        WebhookBatchReceiptResponse response =
                service.receive("eos-crawler", batch, headerFor(batch));

        assertEquals(0, response.accepted());
        assertEquals(2, response.duplicate());
        assertTrue(response.results().get(0).duplicate());
        verify(eventRepository, never()).save(any());
    }

    private static String batchBody() {
        return """
                {"eventId":"evt-b","eventType":"exam.paper.upserted",
                 "sentAt":"2026-09-14T03:54:59Z",
                 "papers":[
                   {"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
                    "title":"first","questions":[{"externalId":"1","questionText":"stem",
                    "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":70,
                    "sha256":"%s","contentBase64":"%s"}]}]},
                   {"examCode":"PRF192_SU26_FE_2","paperType":"FE","subjectCode":"PRF192",
                    "title":"second","questions":[{"externalId":"2","questionText":"stem",
                    "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":70,
                    "sha256":"%s","contentBase64":"%s"}]}]}]}
                """.formatted("a".repeat(64), PNG_BASE64, "b".repeat(64), PNG_BASE64);
    }
```

New imports for this test class:

```java
import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookReceiptServiceTest`
Expected: compilation failure — `WebhookBatchReceiptResponse` does not exist.

- [ ] **Step 4: Create the two response records**

`WebhookPaperReceipt.java`:

```java
package com.fuoverflow.exam.api.dto.webhook;

import java.util.UUID;

/**
 * The outcome for one paper of a delivery. {@code receiptId} is null for a rejected paper, and
 * {@code errorCode}/{@code errorMessage} are null for every other status.
 */
public record WebhookPaperReceipt(
        int index,
        String examCode,
        UUID receiptId,
        String status,
        boolean duplicate,
        String errorCode,
        String errorMessage
) {
}
```

`WebhookBatchReceiptResponse.java`:

```java
package com.fuoverflow.exam.api.dto.webhook;

import java.util.List;

/**
 * The outcome of one webhook request. A single-paper body returns a one-element {@code results},
 * so senders read the same shape whichever body they post.
 */
public record WebhookBatchReceiptResponse(
        int accepted,
        int duplicate,
        int rejected,
        List<WebhookPaperReceipt> results
) {
}
```

Then delete `WebhookReceiptResponse.java`.

- [ ] **Step 5: Rewrite `ExamWebhookReceiptService.receive`**

Replace the method body (lines 56-95) with:

```java
    @Transactional
    public WebhookBatchReceiptResponse receive(String clientId, String rawBody, String signatureHeader) {
        long size = rawBody == null ? 0 : rawBody.getBytes(StandardCharsets.UTF_8).length;
        if (size > properties.maxPayloadBytesOrDefault()) {
            throw new PayloadTooLargeException("WEBHOOK_TOO_LARGE",
                    "Body " + size + " byte vượt giới hạn " + properties.maxPayloadBytesOrDefault() + ".");
        }

        signatureVerifier.verify(clientId, rawBody, signatureHeader);

        List<ExamWebhookPayloadReader.DeliveredPaper> delivered =
                payloadReader.readAll(rawBody, properties.maxPapersPerBatchOrDefault());
        String normalizedClientId = clientId.trim();

        List<WebhookPaperReceipt> results = new ArrayList<>(delivered.size());
        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        ApiException firstRejection = null;

        for (int index = 0; index < delivered.size(); index++) {
            PaperWebhookRequest request = delivered.get(index).request();
            String examCode = request.paper() == null ? null : request.paper().examCode();
            String eventId = request.eventId() == null ? null : request.eventId().trim();

            Optional<ExamWebhookEventEntity> existing = eventId == null
                    ? Optional.empty()
                    : eventRepository.findByClientIdAndEventId(normalizedClientId, eventId);
            if (existing.isPresent()) {
                ExamWebhookEventEntity event = existing.get();
                log.info("Exam paper webhook duplicate: client={} eventId={} status={}",
                        normalizedClientId, eventId, event.getStatus());
                results.add(new WebhookPaperReceipt(index, examCode, event.getId(),
                        wireStatus(event.getStatus()), true, null, null));
                duplicates++;
                continue;
            }

            try {
                payloadValidator.validate(request);
            } catch (ApiException e) {
                log.warn("Exam paper webhook paper {} rejected: {} {}", index, e.code(), e.getMessage());
                results.add(new WebhookPaperReceipt(index, examCode, null, "rejected", false,
                        e.code(), e.getMessage()));
                rejected++;
                if (firstRejection == null) {
                    firstRejection = e;
                }
                continue;
            }

            String payloadJson = delivered.get(index).payloadJson();
            ExamWebhookEventEntity saved = eventRepository.save(ExamWebhookEventEntity.received(
                    UUID.randomUUID(),
                    normalizedClientId,
                    eventId,
                    request.eventType() == null ? "exam.paper.upserted" : request.eventType().trim(),
                    payloadJson,
                    Sha256.hexUtf8(payloadJson),
                    true,
                    Instant.now()));
            log.info("Exam paper webhook queued: client={} eventId={} receiptId={}",
                    normalizedClientId, eventId, saved.getId());
            results.add(new WebhookPaperReceipt(index, examCode, saved.getId(), "queued", false,
                    null, null));
            accepted++;
        }

        if (accepted == 0 && duplicates == 0) {
            // Nothing landed: a sender of a single paper must still see the exact failure it
            // sees today rather than a 200 carrying an error buried in a results array.
            throw firstRejection;
        }
        log.info("Exam paper webhook received: client={} accepted={} duplicate={} rejected={} bytes={}",
                normalizedClientId, accepted, duplicates, rejected, size);
        return new WebhookBatchReceiptResponse(accepted, duplicates, rejected, results);
    }
```

Imports to change: drop `WebhookReceiptResponse`, add

```java
import com.fuoverflow.common.exception.ApiException;
import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;
import com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt;

import java.util.ArrayList;
import java.util.List;
```

`BadRequestException` may become an unused import — remove it if so.

Also update the class Javadoc's second paragraph to say the split happens here:

```java
 * <p>Both accepted body shapes — the canonical envelope and a raw EOS exam file — come in through
 * {@link ExamWebhookPayloadReader}, which also splits a {@code papers[]} batch, so one request
 * becomes one queued row per paper and everything downstream still sees a single paper.
```

- [ ] **Step 6: Fix the controller so the module compiles**

In `ExamWebhookController`, change the type only — status codes come in Task 3:

```java
    @PostMapping("/papers")
    public ResponseEntity<ApiResponse<WebhookBatchReceiptResponse>> receivePaper(
            @RequestHeader(value = "X-Exam-Client", required = false) String clientId,
            @RequestHeader(value = "X-Exam-Signature", required = false) String signature,
            @RequestBody(required = false) String rawBody) {
        WebhookBatchReceiptResponse receipt = receiptService.receive(clientId, rawBody, signature);
        HttpStatus status = receipt.accepted() > 0 ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(receipt));
    }
```

- [ ] **Step 7: Fix `ExamPaperWebhookIngestFlowTest` call sites**

At lines 138, 180, 183, 194, 223, 250, 265, 267 the pattern is
`WebhookReceiptResponse x = receiptService.receive(...)`. Rewrite each as

```java
WebhookPaperReceipt x = receiptService.receive(...).results().get(0);
```

and swap the import. Every existing `.receiptId()`, `.status()`, and `.duplicate()` call on those variables then compiles unchanged.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/ \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ \
        docs/superpowers/specs/2026-09-14-exam-webhook-batch-autopublish-design.md
git commit -m "feat(exam): queue one webhook event row per delivered paper

A batch is only useful if one bad paper cannot sink its siblings, so each
paper is validated and queued on its own and reported back individually.
An all-rejected delivery still rethrows the first rejection, which keeps
a single-paper sender's failures byte-identical to today's."
```

---

### Task 3: Response status codes

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java`
- Create: `backend/exam/src/test/java/com/fuoverflow/exam/api/ExamWebhookControllerTest.java`

**Interfaces:**
- Consumes: `WebhookBatchReceiptResponse` and `ExamWebhookReceiptService.receive` from Task 2.
- Produces: nothing later tasks use.

The repo has no `MockMvc` anywhere, so this is a plain Mockito unit test on the controller rather than the MVC slice the spec names. The `400` branch is not tested here: it is produced by the shared `@RestControllerAdvice` from the exception Task 2 rethrows, and `ExamWebhookReceiptServiceTest.rethrowsTheFirstRejectionWhenNoPaperIsAccepted` already covers that the exception escapes.

- [ ] **Step 1: Write the failing test**

Create `backend/exam/src/test/java/com/fuoverflow/exam/api/ExamWebhookControllerTest.java`:

```java
package com.fuoverflow.exam.api;

import com.fuoverflow.exam.api.dto.webhook.WebhookBatchReceiptResponse;
import com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt;
import com.fuoverflow.exam.application.ExamWebhookReceiptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamWebhookControllerTest {

    @Mock private ExamWebhookReceiptService receiptService;
    @InjectMocks private ExamWebhookController controller;

    @Test
    void acceptsWithTwoOhTwoWhenAtLeastOnePaperIsNew() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(1, 1, 0, List.of(
                        queued(0), duplicate(1))));

        assertEquals(HttpStatus.ACCEPTED,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    @Test
    void returnsTwoHundredWhenEveryPaperWasAlreadySeen() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(0, 2, 0, List.of(
                        duplicate(0), duplicate(1))));

        assertEquals(HttpStatus.OK,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    @Test
    void returnsTwoHundredWhenADuplicateBatchAlsoCarriesARejection() {
        when(receiptService.receive(any(), any(), any())).thenReturn(
                new WebhookBatchReceiptResponse(0, 1, 1, List.of(
                        duplicate(0),
                        new WebhookPaperReceipt(1, "B", null, "rejected", false,
                                "WEBHOOK_FE_QUESTIONS_REQUIRED", "Đề FE phải có ít nhất một câu hỏi."))));

        assertEquals(HttpStatus.OK,
                controller.receivePaper("eos-crawler", "sig", "{}").getStatusCode());
    }

    private static WebhookPaperReceipt queued(int index) {
        return new WebhookPaperReceipt(index, "A", UUID.randomUUID(), "queued", false, null, null);
    }

    private static WebhookPaperReceipt duplicate(int index) {
        return new WebhookPaperReceipt(index, "A", UUID.randomUUID(), "queued", true, null, null);
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

The controller change already landed in Task 2 Step 6, so this test should pass on first run — it is a regression guard on the status-code rule, written after the fact because the rule is one line inside a method the previous task had to touch anyway.

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookControllerTest`
Expected: PASS. If it fails, the controller is not returning `ACCEPTED` only when `accepted() > 0` — fix the controller, not the test.

- [ ] **Step 3: Update the controller class Javadoc**

The class comment explains the raw-`String` binding. Add a sentence on the status contract:

```java
 * <p>{@code 202} means at least one paper was newly queued, {@code 200} that every paper was
 * already known; a delivery where nothing at all landed fails with the first paper's error.
```

- [ ] **Step 4: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java \
        backend/exam/src/test/java/com/fuoverflow/exam/api/ExamWebhookControllerTest.java
git commit -m "test(exam): pin the webhook response status contract"
```

---

### Task 4: Publish on ingest, and let a redelivery overwrite

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java:33-41,86-141,295-297`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java:196-212`
- Modify: `docs/superpowers/specs/2026-08-31-exam-paper-webhook-design.md`

**Interfaces:**
- Consumes: nothing from earlier tasks — this task is independent of Tasks 1-3 and could run first.
- Produces: `ExamPaperIngestService.Outcome` loses `SKIPPED_PUBLISHED`; `ingest` still returns `IngestOutcome(UUID paperId, Outcome outcome)`.

- [ ] **Step 1: Write the failing tests**

In `ExamPaperIngestServiceTest`, replace the whole `skipsAPaperThatIsAlreadyPublished` test (lines 196-212) with:

```java
    @Test
    void publishesAPaperOnceItsContentIsBuilt() {
        stubSubject();

        ExamPaperIngestService.IngestOutcome outcome =
                service.ingest(fePaper(1), "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.CREATED, outcome.outcome());
        ArgumentCaptor<ExamPaperEntity> saved = ArgumentCaptor.forClass(ExamPaperEntity.class);
        verify(paperRepository, times(2)).save(saved.capture());
        ExamPaperEntity persisted = saved.getAllValues().get(1);
        assertTrue(persisted.isPublished(),
                "a webhook paper goes live without an admin step");
        assertNotNull(persisted.getPublishedAt());
    }

    @Test
    void rebuildsAPaperThatIsAlreadyPublishedAndKeepsItPublished() {
        stubSubject();
        IngestPaper incoming = fePaper(1);
        ExamPaperEntity published = draftPaper(ExamPaperFingerprint.of(incoming));
        published.publish(Instant.now());
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(published.getFingerprint()))
                .thenReturn(Optional.of(published));

        ExamPaperIngestService.IngestOutcome outcome =
                service.ingest(incoming, "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.UPDATED, outcome.outcome());
        assertEquals(published.getId(), outcome.paperId());
        assertTrue(published.isPublished());
        // The sender is the source of truth, so its correction must actually replace the content.
        verify(feQuestionRepository).save(any());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest`
Expected: FAIL — `rebuildsAPaperThatIsAlreadyPublishedAndKeepsItPublished` gets `SKIPPED_PUBLISHED` instead of `UPDATED`, and `publishesAPaperOnceItsContentIsBuilt` sees only one `paperRepository.save` of a still-draft paper.

- [ ] **Step 3: Delete the skip-published branch**

In `ExamPaperIngestService.ingest`, remove lines 90-93 entirely:

```java
        if (existing.isPresent() && existing.get().isPublished()) {
            log.info("Exam paper {} already published; ingest skipped", paper.examCode());
            return new IngestOutcome(existing.get().getId(), Outcome.SKIPPED_PUBLISHED);
        }
```

and drop the now-unused enum constant:

```java
    public enum Outcome {
        CREATED, UPDATED
    }
```

- [ ] **Step 4: Publish after the content is built**

Between the `catch` block that ends at line 138 and the `return` at line 140, insert:

```java
        if (hasContent(paper)) {
            entity.publish(now);
            paperRepository.save(entity);
        }
```

and add the helper next to the other private methods:

```java
    /**
     * The EXAM_PAPER_EMPTY rule {@code ExamPaperAdminService.publish} enforces, read off the payload
     * the content was just built from instead of a repository round trip. The validator already
     * rejects an empty paper, so this is a guard: a paper that somehow has nothing stays a draft and
     * shows up in the admin queue rather than going live blank.
     */
    private static boolean hasContent(IngestPaper paper) {
        return paper.paperType() == ExamPaperType.FE
                ? !paper.questions().isEmpty()
                : !paper.images().isEmpty() || !paper.resources().isEmpty();
    }
```

Publishing *after* the build is the whole point: the existing `catch (RuntimeException)` rolls back stored objects and deletes a paper this run created, so a failed image upload can never leave a published paper with no content.

- [ ] **Step 5: Update the class Javadoc**

Replace the "Two rules drive the shape of this class" paragraph's first rule:

```java
 * <p>Two rules drive the shape of this class. First, a redelivery must not duplicate: a paper is
 * identified by content fingerprint, falling back to exam code, and an existing paper is rebuilt in
 * place — published or not, because the sender is the source of truth and a correction has to be
 * able to land. Second, a partial build must not survive: every object key written during a run is
 * tracked, and a failure deletes them along with a paper this run created, so a retry starts from a
 * clean slate instead of accumulating orphans.
```

Also change the summary line from "into a draft paper" to:

```java
 * Turns a validated {@link IngestPaper} into a published paper plus its FE questions or PE content.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest`
Expected: PASS. `deletesTheDraftAndUploadedObjectsWhenStorageFails` must stay green — publishing happens after the build, so the failure path is unchanged.

- [ ] **Step 7: Run the module and the flow test**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS. `ExamPaperWebhookIngestFlowTest` exercises redelivery end to end; if a case there asserted the old skip behaviour, update it to assert the paper was rebuilt and is still published.

- [ ] **Step 8: Mark the old spec superseded**

At the top of `docs/superpowers/specs/2026-08-31-exam-paper-webhook-design.md`, under the `Module:` line, add:

```markdown
Superseded in part (2026-09-14): one paper per call, and the `draft` → admin-publish lifecycle,
are replaced by `2026-09-14-exam-webhook-batch-autopublish-design.md`. The rest of this document
still describes the system.
```

- [ ] **Step 9: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java \
        docs/superpowers/specs/2026-08-31-exam-paper-webhook-design.md
git commit -m "feat(exam): publish webhook papers without an admin step

The third party owns this content, so a manual approval only delayed it.
Publishing happens after the content is built, which keeps the existing
rollback the thing that decides whether a paper exists at all, and an
already-published paper is now rebuilt in place so a correction can land."
```

---

### Task 5: Auto-created subjects are active

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java:145-158`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java`

**Interfaces:**
- Consumes: nothing. Independent of Tasks 1-3; pairs with Task 4.
- Produces: nothing.

A published paper under an inactive subject is still invisible in the public catalog, which would make Task 4 a no-op for any subject code seen for the first time.

- [ ] **Step 1: Write the failing test**

Add to `ExamPaperIngestServiceTest`:

```java
    @Test
    void createsAnUnseenSubjectAsActiveSoThePublishedPaperIsReachable() {
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("SCM302"))
                .thenReturn(Optional.empty());
        when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingest(fePaper(1), "webhook:eos-crawler");

        ArgumentCaptor<ExamSubjectEntity> saved = ArgumentCaptor.forClass(ExamSubjectEntity.class);
        verify(subjectRepository).save(saved.capture());
        assertEquals("SCM302", saved.getValue().getCode());
        assertTrue(saved.getValue().isActive(),
                "an inactive subject would hide the paper we just published");
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest#createsAnUnseenSubjectAsActiveSoThePublishedPaperIsReachable`
Expected: FAIL — `isActive()` is false.

- [ ] **Step 3: Flip the flag and rewrite the Javadoc**

In `resolveOrCreateSubject`, the `ExamSubjectEntity.create(...)` call's `active` argument (currently `false`, right after `examProperties.defaultFePreviewImageCountOrDefault()`) becomes `true`, and the method comment becomes:

```java
    /**
     * A code we have never seen becomes a subject titled with the code itself, and active: the paper
     * the delivery carries is published immediately, and an inactive subject would keep it out of
     * the public catalog anyway. An admin replaces the placeholder title when they get to it.
     */
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest`
Expected: PASS.

- [ ] **Step 5: Run the full build**

Run: `cd backend && mvn -q -DskipTests package && mvn -q test`
Expected: BUILD SUCCESS, no failing tests in any module.

- [ ] **Step 6: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java
git commit -m "feat(exam): create webhook-discovered subjects as active

Publishing a paper under an inactive subject leaves it invisible, which
would have made auto-publish a no-op for any subject code arriving for
the first time."
```

---

## Manual verification

After Task 5, with `docker compose up -d postgres redis` and `mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local`:

1. Sign a two-paper batch body with the local dev secret and POST it to `/api/v1/exam/webhook/papers`. Expect `202` and `accepted: 2`.
2. POST the identical body again. Expect `200` and `duplicate: 2`.
3. Wait one worker cycle (30s default), then `GET /api/v1/admin/exam/papers?status=published` and confirm both papers are there with no admin publish call.
4. Re-POST one paper under a new `eventId` with an extra question and confirm the paper is rebuilt and still published.
