# Exam Paper Webhook — Design

Date: 2026-08-31
Status: approved
Module: `backend/exam` (+ migrations in `backend/app`)

## Problem

Exam papers (FE/PE) can only be created by hand through the admin UI today. A third party
already holds the data — the EOS delivery payloads shown in `temp/*.json` — and needs a way
to push it in. Two facts from reading those payloads shape everything below:

- Question content lives entirely in `ImageData` (base64 PNG containing the stem *and* the
  options). `Text` only carries a `"(Choose N answers)"` marker, and `QuestionAnswers[].Text`
  is empty in every question of both sample files.
- Paper identity (`SCM302_SU26_FE_553972`) carries subject, term and type, but the exam module
  has nowhere to store any of it: `exam_fe_questions` hangs flat off a subject with no notion
  of a paper, no term, no exam code, and no content hash to dedupe a re-delivery.

A webhook that wrote into today's schema would therefore lose the term/exam-code, duplicate
every paper on retry, and — because FE gating counts image index *within a post* — publish all
50 questions for free to non-members the moment each question became a one-image post.

## Scope

In scope:

- `POST /api/v1/exam/webhook/papers`: HMAC-authenticated ingest of one paper per call, FE or PE.
- Paper-level storage (`exam_papers`) with content fingerprint, `draft` → `published` lifecycle.
- Asynchronous processing: decode/upload images, download PE resources, build the paper.
- Fix FE preview gating so it counts images across the paper rather than within a question.
- Admin endpoints to list/publish/delete papers and inspect webhook receipts.
- Public endpoints `papers[]` on subject detail and `GET /exam/catalog/papers/{paperId}`,
  which the existing frontend already calls.

Out of scope:

- Answer keys. Neither sample payload carries one (`QuestionAnswers` has no correctness flag,
  matching `Solution` arrives masked), so nothing can be imported.
- PE detection from EOS payloads. PE is a practical exam delivered as paper images plus
  resource archives, never as an EOS multiple-choice payload; the contract accepts PE from a
  third party but no PE arrives in the sample format.
- Any change to the `grading` module.

## Contract

`POST /api/v1/exam/webhook/papers` — `permitAll` in `SecurityConfig`, authenticated by
signature rather than JWT.

### Headers

```
X-Exam-Signature: t=1756630530,v1=<hex HMAC-SHA256 over "<t>.<raw body>">
X-Exam-Client: eos-crawler
Content-Type: application/json
```

The secret is per client (`fuexam.exam.webhook.clients.<clientId>`). Requests where
`|now - t| > 300s` are rejected so a captured body cannot be replayed. Comparison is
constant-time, mirroring `ExamMediaTokenService`.

### Envelope

```json
{
  "eventId": "3f2b9c14-8f0e-4a51-9b77-1c6d5e8a0f21",
  "eventType": "exam.paper.upserted",
  "sentAt": "2026-08-31T03:54:59Z",
  "paper": {
    "examCode": "SCM302_SU26_FE_553972",
    "paperType": "FE",
    "subjectCode": "SCM302",
    "term": "SU26",
    "retakeLabel": null,
    "title": "SCM302 — Summer 2026 — Final Exam",
    "description": null,
    "durationMinutes": 60,
    "totalMark": 50.0,
    "declaredQuestionCount": 50,
    "source": {
      "system": "eos-crawler",
      "externalPaperId": "553972",
      "capturedAt": "2026-07-26T03:54:59Z"
    },
    "questions": [],
    "images": [],
    "resources": []
  }
}
```

`questions` is FE-only; `images` and `resources` are PE-only. Sending the wrong set for the
declared `paperType` is a `400`, not a silent drop.

`subjectCode`, `term` and `paperType` are required as separate fields rather than parsed out of
`examCode`, because payloads that do not follow the naming convention already exist in
production data (`TEST_EOS_Client_278333`). When `examCode` *does* match
`^(\w+)_([A-Z]{2}\d{2})_(FE|PE|PT|MID)_(\d+)$` and the parsed values disagree with the explicit
fields, the request is rejected.

### FE questions

```json
{
  "externalId": "1920809737",
  "displayNo": 1,
  "questionText": null,
  "expectedAnswerCount": 1,
  "chapterId": 11001,
  "mark": 1.0,
  "images": [
    {
      "sortOrder": 0,
      "mimeType": "image/png",
      "sizeBytes": 6853,
      "sha256": "9c1f…",
      "contentBase64": "iVBORw0KGgoAAAANSUhEUg…"
    }
  ],
  "answerOptionIds": [1811788491, 986528664, 1839740298, 1537100498]
}
```

- `questionText` is the stem with the `"(Choose N answers)"` marker stripped. `null` is valid:
  `ExamFeQuestionAdminService` requires text *or* at least one image.
- `images` is a list so a question with several images needs no contract change.
- `answerOptionIds` (EOS QAIDs) are never displayed. They exist so the fingerprint is
  order-independent in the same way `grading.support.PaperFingerprint` is, which lets an exam
  paper be matched to a grading paper later. Optional.

The fingerprint is `sha256` over a canonical string built to be independent of delivery order:

```
<examCode>|<paperType>|<unit>;<unit>;…
```

where units are sorted ascending as strings. For FE each unit is
`<externalId>:<comma-joined ascending answerOptionIds>` when `answerOptionIds` is present, and
`<externalId>:<comma-joined ascending image sha256>` when it is not. For PE each unit is the
`sha256` of an image or resource. Two deliveries of the same paper therefore collapse to one
row whatever order the third party sends things in.

### PE images and resources

```json
{
  "images": [
    { "sortOrder": 0, "mimeType": "image/png", "sizeBytes": 148230,
      "sha256": "41ab…", "contentBase64": "iVBORw0…" }
  ],
  "resources": [
    { "sortOrder": 0, "folderLabel": "Starter code", "filename": "PE01_starter.zip",
      "mimeType": "application/zip", "sizeBytes": 4823910, "sha256": "7d2e…",
      "sourceUrl": "https://cdn.example.com/pe/PE01_starter.zip" }
  ]
}
```

Images travel inline because they are small (both sample papers are under 400 KB in total).
Resource archives travel by URL because the existing limit allows 50 MB per archive, which
inline base64 would inflate past any sane request size.

`sourceUrl` is fetched only when its host is in `fuexam.exam.webhook.allowed-resource-hosts`,
only over `https`, never to a private/loopback address, and never following a redirect that
leaves the allowlist. `sha256` is mandatory and verified after download; a mismatch fails the
resource rather than storing an unverified file.

### Limits

Per `fuexam.upload.*` already configured: 5 MB per image, 50 MB per archive. Additionally
200 questions per paper and 32 MB per request body. Exceeding any of them is `413`.

### Responses

| Status | Meaning |
|---|---|
| `202` | Accepted, queued. `{ "receiptId", "status": "queued", "duplicate": false }` |
| `200` | `(clientId, eventId)` already seen; returns the earlier receipt, `duplicate: true` |
| `400` | Payload rejected, with a stable `code` (see below) |
| `401` | Missing/invalid/expired signature |
| `413` | Body or an embedded asset over limit |

Error codes: `WEBHOOK_PAYLOAD_INVALID`, `WEBHOOK_PAPER_TYPE_MISMATCH`,
`WEBHOOK_SUBJECT_CODE_REQUIRED`, `WEBHOOK_FE_QUESTIONS_REQUIRED`,
`WEBHOOK_PE_CONTENT_REQUIRED`, `WEBHOOK_IMAGE_INVALID_BASE64`,
`WEBHOOK_IMAGE_TYPE_UNSUPPORTED`, `WEBHOOK_RESOURCE_HOST_NOT_ALLOWED`,
`WEBHOOK_TOO_LARGE`.

Processing status is read back at `GET /api/v1/admin/exam/webhook-events/{receiptId}`.

## Storage

### V53 — paper level and webhook inbox

```sql
create table exam_papers (
    id uuid primary key,
    subject_id uuid not null,
    paper_type varchar(8) not null,              -- FE | PE
    exam_code varchar(120) not null,
    term varchar(16) null,
    retake_label varchar(64) null,
    title varchar(500) not null,
    description text null,
    duration_minutes int null,
    total_mark numeric(6,2) null,
    declared_question_count int null,
    fingerprint varchar(64) not null,
    status varchar(16) not null default 'draft', -- draft | published
    ingest_source varchar(64) null,
    external_paper_id varchar(64) null,
    sort_order int not null default 0,
    view_count bigint not null default 0,
    lock_version int not null default 0,
    published_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint exam_papers_type_check check (paper_type in ('FE','PE')),
    constraint exam_papers_status_check check (status in ('draft','published'))
);
```

Unique on `fingerprint` and on `lower(exam_code)` where `deleted_at is null`; lookup index on
`(subject_id, paper_type, sort_order)`; `(status, created_at desc)` for the admin draft queue.

`exam_fe_questions.paper_id` and `exam_pe_items.paper_id` are added nullable, then backfilled:
every subject holding FE questions gets one `<CODE>_LEGACY_FE` paper (`published`), same for PE.
Existing hand-entered content keeps behaving as before and the read path never has to handle
rows without a paper.

```sql
create table exam_webhook_events (
    id uuid primary key,
    client_id varchar(64) not null,
    event_id varchar(120) not null,
    event_type varchar(64) not null,
    payload_json jsonb not null,
    payload_sha256 varchar(64) not null,
    signature_valid boolean not null default false,
    status varchar(16) not null default 'pending',   -- pending|processing|done|failed
    attempt_count int not null default 0,
    paper_id uuid null,
    error_code varchar(64) null,
    error_message text null,
    available_at timestamptz not null default now(),
    processed_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint exam_webhook_status_check check (status in ('pending','processing','done','failed'))
);
```

Unique on `(client_id, event_id)` — the transport-level idempotency key. The status set matches
`outbox_events`. Because base64 PNG does not compress, once an event reaches `done` the worker
rewrites `payload_json` with every `contentBase64` set to `null`, keeping the `sha256` values:
the row drops from roughly a megabyte to a few kilobytes and stays useful for audit.

### V54 — permissions

Following the shape of V44: `exam.paper.admin:read|update|delete|publish` and
`exam.webhook.admin:read`. ADMIN gets all of them, SUB_ADMIN gets the `:read` grants only.

## Processing

`ExamWebhookController` verifies the signature, stores the event, returns `202`. An
`@Scheduled` `ExamWebhookIngestWorker` claims pending rows by flipping them to `processing`,
then `ExamPaperIngestService` does the work:

1. Resolve the subject by `subjectCode`. If absent, create it with `is_active = false` and
   `title = subjectCode`, so an unnamed subject card never reaches the public catalog.
2. Compute the fingerprint. A live paper with the same fingerprint is updated in place;
   otherwise a `draft` is created. A paper already `published` is left untouched and the event
   records `PAPER_ALREADY_PUBLISHED` — updating it requires an admin unpublish first.
3. FE: decode each image, validate it by magic bytes through `FileContentValidator` rather than
   trusting the declared `mimeType`, upload via `ExamMediaService.uploadWithBlur`, and create
   the `exam_fe_questions` row bound to the paper.
4. PE: images as above; resources are downloaded under the allowlist/size/hash rules and stored
   as `exam_pe_resources`.
5. Transient failures (network, storage) increment `attempt_count` and push `available_at` out
   by an exponential backoff, up to 5 attempts. Payload-level failures go straight to `failed`.
6. On `failed`, the half-built draft paper and everything already uploaded for it are deleted,
   so a broken delivery leaves no orphaned objects in storage.

Configuration lives under `fuexam.exam.webhook`: `clients`, `allowed-resource-hosts`,
`max-payload-bytes`, `poll-interval-ms`, `max-attempts`.

## FE preview gating

`ExamCatalogQueryService.listFeQuestions` currently decides per image *within a question*
(`isMember || i < previewImageCount`). With one image per question — exactly what an imported
EOS paper produces — index 0 always passes and the whole paper is free.

The index becomes cumulative across the paper: question 1 image 0 → 0, question 2 image 0 → 1,
and so on. The meaning of `fe_preview_image_count` is unchanged ("the first N images are
viewable"), the column is not renamed a second time, and questions carrying several images
still behave sensibly.

This tightens what existing hand-entered data exposes: previously the first two images of
*every* post were free, now the first two of the *paper* are. That is the intended commercial
behaviour; a subject that wants more can raise `fe_preview_image_count`.

## API surface

Admin (`/api/v1/admin/exam`):

- `GET /papers?subjectId=&status=` — list, newest draft first.
- `POST /papers/{id}/publish`, `DELETE /papers/{id}`.
- `GET /webhook-events/{id}` — receipt status, attempts, error.

Public (`/api/v1/exam/catalog`):

- `GET /{idOrCode}` gains `papers[]` and `related[]`.
- `GET /papers/{paperId}` — published papers only, member-gated the same way PE is today.

Both public shapes are what `Fuexam/lib/api/exam.ts` already declares, so this also closes the
three frontend/backend mismatches recorded during the review that preceded this design:
`detail.papers`, the `fePaperCount` field name, and the comment update/delete path.

## Testing

Unit:

- Signature verification: valid, tampered body, expired `t`, unknown client.
- Payload validation: FE without questions, PE with neither images nor resources, invalid
  base64, `paperType` disagreeing with `examCode`, over-limit assets.
- Fingerprint stability under reordered questions and reordered `answerOptionIds`.
- Cumulative gating: 50 questions × 1 image, non-member sees exactly 2 `full` and 48 `blur`.

Integration:

- Webhook → worker → draft paper with 50 questions, 50 images, 50 blur objects.
- Replay with the same `eventId` creates nothing new; a different `eventId` with the same
  content updates the existing draft.
- A `sourceUrl` outside the allowlist is refused.

MVC/security: the endpoint is reachable unauthenticated but a bad signature returns `401`;
admin endpoints require their permissions.

Fixtures are trimmed copies (2–3 questions) of the sample payloads in `temp/`, to keep the
repository small.
