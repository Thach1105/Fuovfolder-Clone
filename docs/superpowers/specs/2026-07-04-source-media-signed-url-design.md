# Source Media Signed URL + Fallback Proxy

## Problem

Source question images (paid content) are served via direct public S3/MinIO URLs with no auth. Once a user receives the URL from the questions API, that URL is permanent and shareable — anyone with the link can view the paid content forever.

Cover images are marketing content and remain public.

## Solution

Replace public URLs for source question images with HMAC-signed URLs that expire after 15 minutes. Add a proxy endpoint that verifies the signature and streams the file. When the signed URL expires, FE can fall back to the same endpoint without a signature — the proxy then checks authentication and purchase status before serving.

## Scope

**Protected:** `questionImageUrl`, `questionImageUrls`, option `optionImageUrl` — all images under `source/questions/` storage prefix.

**Not protected:** cover images (`source/covers/`), admin responses (admins always see direct URLs).

## Architecture

### URL Generation

When `SourceQuestionQueryService.listForUser()` returns questions (user has already passed `SourceAccessGuard` purchase check), `SourceMediaUrlResolver.resolvePublic()` generates signed URLs instead of plain public URLs:

```
/api/v1/source/media/{base64url(objectKey)}?sig={signature}&exp={unixEpochSeconds}
```

- `signature = HMAC-SHA256(mediaSigningSecret, objectKey + ":" + exp)`
- `exp = now + 900` (15 minutes)
- Base64 URL-safe encoding for objectKey to avoid `/` path issues

### Proxy Endpoint

Single endpoint handling both modes:

```
GET /api/v1/source/media/{encodedKey}
```

**Signed URL mode** (has `?sig=` and `?exp=` params):
1. Decode `encodedKey` from Base64 URL-safe to get `objectKey`
2. Check `exp > now` — if expired, return 403 `SIGNED_URL_EXPIRED`
3. Verify `HMAC-SHA256(secret, objectKey + ":" + exp) == sig` — if invalid, return 403 `INVALID_SIGNATURE`
4. Stream file from `ObjectStorage.openStream(objectKey)` with appropriate headers

**Fallback proxy mode** (no `sig`/`exp` params):
1. Require authenticated user — if not, return 401
2. Extract `catalogItemId` by looking up the objectKey: query `source_questions` by `question_image_url` or `question_image_urls` containing the key, or `source_question_options` by `option_image_url`. Return the `catalog_item_id` from the matching question. If no match found, return 404.
3. Check `SourceAccessGuard.hasActiveAccess(userId, catalogItemId)` — if no access, return 403
4. Stream file from storage

### Response Headers

```
Content-Type: image/png (from file)
Cache-Control: private, max-age=900
Content-Disposition: inline
```

## Configuration

Add to `SourceProperties` (prefix `fuexam.source`):

```yaml
fuexam:
  source:
    media-signing-secret: ${SOURCE_MEDIA_SIGNING_SECRET:dev-source-media-secret-change-me}
    signed-url-ttl-seconds: ${SOURCE_MEDIA_URL_TTL:900}
```

Dedicated signing secret — not shared with S3 credentials or JWT keys.

## Security

- Endpoint `/api/v1/source/media/**` is `permitAll` in SecurityConfig (signature is the auth for signed mode)
- Fallback mode enforces authentication + purchase check within the controller
- Admin responses (`resolveAdmin()`) continue using direct public URLs — admins don't need signed URLs
- Signing secret rotatable via env var without affecting other auth mechanisms

## Files to Create/Modify

| File | Change |
|------|--------|
| `SourceProperties` | Add `mediaSigningSecret`, `signedUrlTtlSeconds` |
| **New** `SourceMediaTokenService` | HMAC sign + verify logic |
| **New** `SourceMediaController` | Proxy endpoint: verify sig or fallback auth, stream file |
| `SourceMediaUrlResolver` | `resolvePublic()` generates signed proxy URLs for question images |
| `SecurityConfig` | Add `/api/v1/source/media/**` to permitAll |
| `application.yml` | Add default config values |

## Behavior Summary

| Scenario | What happens |
|----------|-------------|
| User views questions (purchased) | API returns signed URLs, valid 15 min |
| User loads image within 15 min | Signed URL verified, file streamed |
| User loads image after 15 min | 403 expired → FE retries without sig → proxy checks purchase → streams |
| User shares signed URL | Works for 15 min, then expires |
| User shares plain proxy URL | Receiver must be logged in + have active purchase |
| Admin views questions | Direct public URLs (no signing) |
| Cover images | Direct public URLs (no change) |
