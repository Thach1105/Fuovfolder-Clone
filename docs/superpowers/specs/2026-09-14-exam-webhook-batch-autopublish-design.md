# Exam Paper Webhook — Batch Delivery and Auto-Publish — Design

Date: 2026-09-14
Status: approved
Module: `backend/exam`
Supersedes: the "one paper per call" contract and the "papers stay `draft` until an admin
publishes them" lifecycle decision in `2026-08-31-exam-paper-webhook-design.md`.

## Problem

`POST /api/v1/exam/webhook/papers` accepts exactly one paper per call. A sender holding a term's
worth of papers must issue one HTTP request per paper, each with its own HMAC signature and its
own round trip.

Every ingested paper also lands as `draft` and stays invisible until an admin calls
`POST /api/v1/admin/exam/papers/{paperId}/publish`. The original rationale was editorial QA — a
half-built ingest must not go live. That gate is no longer wanted: the third party is the source
of truth for exam content, and manual approval is pure latency between delivery and visibility.

Two secondary facts fall out of removing the gate:

- `ExamPaperIngestService.ingest()` returns `SKIPPED_PUBLISHED` when the target paper is already
  published, and no unpublish endpoint exists. With auto-publish, the first delivery of an exam
  code would freeze that paper forever — a corrected redelivery would be silently dropped.
- `resolveOrCreateSubject` creates an unseen subject with `active = false`, so a published paper
  under a brand-new subject code still does not reach the public catalog.

## Scope

In scope:

- Accept an array of papers in one webhook request, alongside the two shapes already supported.
- Per-paper idempotency, per-paper failure isolation, and a per-paper result in the response.
- Publish webhook-ingested papers automatically once their content is built.
- Let a redelivery overwrite an already-published paper.
- Create auto-created subjects as `active = true`.

Out of scope:

- The admin publish/delete endpoints, which stay for hand-entered papers.
- An unpublish endpoint.
- Flipping subjects that earlier deliveries already created as `active = false`.
- Any schema migration. `'published'` already satisfies `exam_papers_status_check`, and the
  `exam_webhook_events` table is unchanged.
- A top-level bare JSON array body (`[ {...}, {...} ]`). The envelope stays mandatory because
  `eventId` is what idempotency is keyed on.

## Contract

Headers, HMAC verification, and the 32 MB body cap are unchanged. The signature still covers the
exact raw bytes of the whole request.

### Body shapes

`ExamWebhookPayloadReader` gains `readAll(String rawBody) : List<PaperWebhookRequest>`, which
dispatches on shape:

| Body | Result |
| --- | --- |
| has `paper` | one paper, exactly as today |
| has `papers` (non-empty array) | N papers, each wrapped into a canonical single-paper envelope |
| EOS exam file (`ExamCode` + a question array) | one paper, exactly as today |
| has both `paper` and `papers` | `400 WEBHOOK_PAYLOAD_AMBIGUOUS` |
| `papers` present but empty or not an array | `400 WEBHOOK_PAYLOAD_INVALID` |
| none of the above | `400 WEBHOOK_PAYLOAD_UNRECOGNIZED` (unchanged) |

Batch example:

```json
{
  "eventId": "eos-2026-09-14-001",
  "eventType": "exam.paper.upserted",
  "sentAt": "2026-09-14T03:00:00Z",
  "papers": [ { "examCode": "SCM302_SU26_FE_553972", "...": "..." },
              { "examCode": "PRF192_SU26_FE_553980", "...": "..." } ]
}
```

The existing `read(String)` stays and keeps returning a single envelope: the ingest worker uses it
to re-parse a stored row, and a stored row always holds exactly one paper (see Storage).

### Limits

New property `fuexam.exam.webhook.max-papers-per-batch`, default `50`, on
`ExamWebhookProperties` with the same `...OrDefault()` accessor style as its siblings. Over the
limit is `400 WEBHOOK_BATCH_TOO_LARGE`. The existing `max-payload-bytes` (32 MB) still bounds the
request as a whole, and `max-questions` still bounds each paper.

`eventId` must be at most 100 characters (`400 WEBHOOK_PAYLOAD_INVALID` otherwise), so the
per-paper suffix below fits `exam_webhook_events.event_id varchar(120)`.

### Response

```java
public record WebhookBatchReceiptResponse(
        int accepted, int duplicate, int rejected, List<WebhookPaperReceipt> results) {}

public record WebhookPaperReceipt(
        int index, String examCode, UUID receiptId, String status,
        boolean duplicate, String errorCode, String errorMessage) {}
```

`receiptId` is null for a rejected entry; `errorCode`/`errorMessage` are null otherwise. `status`
keeps the existing wire vocabulary (`queued`, `done`, `failed`) plus `rejected`.

Status codes:

| Situation | Code |
| --- | --- |
| `accepted > 0` | `202` |
| `accepted == 0` and `duplicate > 0` | `200` |
| `accepted == 0` and `duplicate == 0` (every paper rejected) | `400` |
| body too large / bad signature / unparseable body | `413` / `401` / `400`, unchanged |

A batch mixing duplicates with rejections is therefore a `200` carrying per-paper errors, not a
`400`: the delivery did reach a known state for every paper, and the sender reads `results` to
see which ones need fixing.

When *every* paper is rejected there is no results array to return — the first rejection is
rethrown unchanged and rendered by the shared error advice. A single-paper delivery therefore
keeps exactly today's failure behaviour, including the `413` that an over-long question list
raises rather than a `400`.

`WebhookReceiptResponse` is replaced by this shape for every body, single or batch. A one-paper
delivery therefore returns a one-element `results` array. This is a visible response change for
existing senders; the status codes they branch on (`200`/`202`) do not change.

## Storage

One delivered paper becomes one `exam_webhook_events` row. The table, its unique index
`(client_id, event_id)`, and the ingest worker are all unchanged.

- **Derived event id.** Paper *i* of a batch is stored under `"<eventId>#<i>"`, zero-based. A
  single-paper body (`paper` or EOS) keeps `eventId` verbatim with no suffix, so an existing
  sender's idempotency behaviour is bit-for-bit what it is today.
- **Stored payload.** Each row's `payload_json` is a re-serialized canonical single-paper envelope
  (derived `eventId`, the batch's `eventType` and `sentAt`, and that paper). `payload_sha256` is
  the hash of that slice. A batch row is therefore *not* the sender's original bytes — the one
  place this design trades away a property the previous one held. The signature was already
  verified against the full raw body before splitting, and each row still records
  `signature_valid = true`.
- **Rejected papers are not stored.** A paper that fails validation produces a `results` entry and
  no row, so the inbox holds only work the worker can actually do.

## Processing

### Receipt path

`ExamWebhookReceiptService.receive(clientId, rawBody, signatureHeader)` returns
`WebhookBatchReceiptResponse` and runs: size check → signature → `readAll` → batch-size check →
then, per paper in order:

1. duplicate lookup on `(clientId, derivedEventId)` → record `duplicate`, no write;
2. `payloadValidator.validate(...)` → on `ApiException`, record `rejected` with the exception's
   code and message and continue to the next paper;
3. save one `ExamWebhookEventEntity` → record `accepted`.

One paper's rejection never affects its siblings. Whole-request failures (size, signature, an
unreadable or ambiguous body) still abort everything before any row is written.

### Ingest path

`ExamPaperIngestService.ingest(IngestPaper, String)` — whose only production caller is
`ExamWebhookIngestWorker` — changes in two places:

- The `existing.isPresent() && existing.get().isPublished()` early return is deleted, along with
  `Outcome.SKIPPED_PUBLISHED`. A redelivery now takes the normal update path:
  `applyMetadata` + `clearExistingContent` + rebuild, and the paper stays published.
- After `buildFeQuestions`/`buildPeContent` returns and before the method returns its outcome, the
  paper is published: `entity.publish(now)` and save. Publishing *after* the content is built is
  the point — the existing `catch (RuntimeException)` rolls back stored objects and deletes a
  newly created paper, so a failed image upload or resource download can never leave a published
  paper with no content.

A paper whose built content is empty stays `draft`. `ExamWebhookPayloadValidator` already rejects
an FE paper with no questions and a PE paper with no images or resources, so this is a guard
rather than a live path, and it keeps the `EXAM_PAPER_EMPTY` invariant that
`ExamPaperAdminService.publish` enforces.

Known cost, accepted: because overwrite is unconditional, a redelivery of unchanged content
re-uploads every image and re-downloads every resource. If the sender ever starts redelivering on
a schedule, a fingerprint-equality skip should be added then.

### Subject creation

`resolveOrCreateSubject` creates an unseen subject with `active = true` instead of `false`, so an
auto-published paper is actually reachable from the public catalog. The title remains the subject
code until an admin edits it, and the Javadoc explaining the old choice is rewritten to state the
new one.

## API surface

Unchanged paths. `POST /api/v1/exam/webhook/papers` keeps its raw-`String` body binding — the
signature still covers exact bytes — and only its response body changes.
`POST /api/v1/admin/exam/papers/{paperId}/publish` and the admin draft queue stay in place for
hand-entered papers.

## Testing

`ExamWebhookPayloadReader`: a two-paper batch; `papers` empty; `papers` not an array; `paper` and
`papers` together; a batch over the limit; an EOS body and a single-`paper` body still returning
one element.

`ExamWebhookReceiptService`: three papers with the middle one invalid → two rows saved, one
`rejected` entry, `202`; all duplicates → `200`; all invalid → `400`; derived event ids
`evt#0`/`evt#1`; a single-paper body storing `evt` unsuffixed.

`ExamPaperIngestService`: an FE ingest leaves `status = published` with `publishedAt` set; a
redelivery of a published paper replaces its content and stays published; a newly created subject
has `active = true`; a failure during content build leaves no published paper behind.

`ExamWebhookController` MVC: each status-code branch.
