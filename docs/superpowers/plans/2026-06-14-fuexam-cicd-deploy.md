# Fuexam CI/CD Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add production CI/CD deploy support for `Fuexam/` as a second frontend service on host port `3336`, while keeping the existing `frontend/` deployment unchanged on `19080`.

**Architecture:** Add a dedicated production Dockerfile for `Fuexam/`, then wire a new `fuexam` service into the existing Docker Compose stack and GitHub Actions deploy workflow. Keep all config namespaced with `FUEXAM_*` on the server and map them to the `NEXT_PUBLIC_*` build args expected by the app so the new service can coexist cleanly with the legacy frontend.

**Tech Stack:** Next.js 16, Node 20 Alpine Docker multi-stage builds, Docker Compose, GitHub Actions self-hosted runner, existing Spring Boot backend + MinIO media URLs.

---

## File map

- **Create:** `Fuexam/Dockerfile`
  - Production multi-stage build for the Fuexam Next.js app.
- **Modify:** `deploy/docker-compose.yml`
  - Add new `fuexam` service with dedicated build context, build args, image/container name, port mapping, and backend dependency.
- **Modify:** `.github/workflows/deploy.yml`
  - Build the new service and verify it with a dedicated health check on port `3336`.
- **Modify:** `.env.example`
  - Document the new `FUEXAM_*` deploy variables for server operators.
- **Modify:** `docs/superpowers/specs/2026-06-14-fuexam-cicd-design.md` only if implementation reveals a spec mismatch. Prefer not to touch it.

---

### Task 1: Add production Dockerfile for Fuexam

**Files:**
- Create: `Fuexam/Dockerfile`
- Test: `Fuexam/package.json`

- [ ] **Step 1: Inspect Fuexam build assumptions before writing Dockerfile**

Read these files and confirm the production build inputs:
- `Fuexam/package.json`
- `Fuexam/next.config.mjs`
- `Fuexam/lib/api/client.ts`
- `Fuexam/lib/api/media.ts`
- `Fuexam/lib/api/source.ts`

Expected findings:
- production server command comes from Next (`next start -p 3336` in dev scripts is not needed for container runtime)
- output is not currently configured as `standalone`
- app reads these public env vars:

```ts
NEXT_PUBLIC_API_URL
NEXT_PUBLIC_API_BASE_URL
NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL
NEXT_PUBLIC_MEDIA_BASE_URL
NEXT_PUBLIC_S3_PUBLIC_BASE_URL
```

- [ ] **Step 2: Update `Fuexam/next.config.mjs` to support standalone production output**

Replace file contents with:

```js
/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  output: "standalone",
}

export default nextConfig
```

Reason: this matches the current deploy style used by `frontend/Dockerfile`, which copies `.next/standalone` into a slim runtime image.

- [ ] **Step 3: Write the production Dockerfile**

Create `Fuexam/Dockerfile` with this content:

```dockerfile
# syntax=docker/dockerfile:1

FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL
ARG NEXT_PUBLIC_MEDIA_BASE_URL
ARG NEXT_PUBLIC_S3_PUBLIC_BASE_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=$NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL
ENV NEXT_PUBLIC_MEDIA_BASE_URL=$NEXT_PUBLIC_MEDIA_BASE_URL
ENV NEXT_PUBLIC_S3_PUBLIC_BASE_URL=$NEXT_PUBLIC_S3_PUBLIC_BASE_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
```

- [ ] **Step 4: Build the image locally to verify Dockerfile correctness**

Run:

```bash
docker build \
  -f /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam/Dockerfile \
  --build-arg NEXT_PUBLIC_API_URL=http://103.160.2.147:18080 \
  --build-arg NEXT_PUBLIC_API_BASE_URL=http://103.160.2.147:18080 \
  --build-arg NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media \
  --build-arg NEXT_PUBLIC_MEDIA_BASE_URL=http://103.160.2.147:9000/fuoverflow-media \
  --build-arg NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media \
  -t fuexam:test /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
```

Expected: Docker build completes successfully and produces image `fuexam:test`.

- [ ] **Step 5: Smoke-run the container locally**

Run:

```bash
docker run --rm -p 3336:3000 fuexam:test
```

In another terminal, run:

```bash
curl -I http://localhost:3336
```

Expected: HTTP status `200`, `307`, or `308`.

- [ ] **Step 6: Commit**

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone add Fuexam/Dockerfile Fuexam/next.config.mjs
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone commit -m "feat: add Fuexam production Docker build"
```

---

### Task 2: Add `fuexam` service to deploy compose

**Files:**
- Modify: `deploy/docker-compose.yml`
- Test: `Fuexam/Dockerfile`

- [ ] **Step 1: Add new service definition without changing legacy frontend service**

Insert this new service block directly after the existing `frontend:` service in `deploy/docker-compose.yml`:

```yaml
  fuexam:
    build:
      context: ../Fuexam
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_API_URL: ${FUEXAM_NEXT_PUBLIC_API_URL:?FUEXAM_NEXT_PUBLIC_API_URL is required}
        NEXT_PUBLIC_API_BASE_URL: ${FUEXAM_NEXT_PUBLIC_API_BASE_URL:?FUEXAM_NEXT_PUBLIC_API_BASE_URL is required}
        NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL: ${FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL:?FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL is required}
        NEXT_PUBLIC_MEDIA_BASE_URL: ${FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL:?FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL is required}
        NEXT_PUBLIC_S3_PUBLIC_BASE_URL: ${FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL:?FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL is required}
    image: fuoverflow-fuexam:latest
    container_name: fuoverflow-fuexam
    restart: unless-stopped
    environment:
      NODE_ENV: production
      PORT: "3000"
    ports:
      - "${FUEXAM_PUBLISH_PORT:-3336}:3000"
    depends_on:
      backend:
        condition: service_healthy
```

Do **not** modify the existing `frontend:` block in this task.

- [ ] **Step 2: Validate compose syntax**

Run:

```bash
docker compose -f /home/thachnn/Desktop/github/Fuovfolder-Clone/deploy/docker-compose.yml config >/tmp/fuoverflow-compose.out
```

Expected: command exits `0` and prints fully rendered compose config.

- [ ] **Step 3: Verify both frontend services still exist in rendered config**

Run:

```bash
grep -nE '^  frontend:|^  fuexam:' /tmp/fuoverflow-compose.out
```

Expected output contains both service names, for example:

```text
  frontend:
  fuexam:
```

- [ ] **Step 4: Commit**

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone add deploy/docker-compose.yml
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone commit -m "feat: add Fuexam deploy service"
```

---

### Task 3: Extend deploy workflow to build and health-check Fuexam

**Files:**
- Modify: `.github/workflows/deploy.yml`
- Test: `deploy/docker-compose.yml`

- [ ] **Step 1: Add workflow-level env for Fuexam port**

Update the `env:` block near the top of `.github/workflows/deploy.yml` from:

```yaml
env:
  ENV_FILE: /opt/fuoverflow/.env
  COMPOSE_FILE: deploy/docker-compose.yml
  BACKEND_PORT: "18080"
  FRONTEND_PORT: "19080"
```

to:

```yaml
env:
  ENV_FILE: /opt/fuoverflow/.env
  COMPOSE_FILE: deploy/docker-compose.yml
  BACKEND_PORT: "18080"
  FRONTEND_PORT: "19080"
  FUEXAM_PORT: "3336"
```

- [ ] **Step 2: Extend env-file verification to check Fuexam port**

Replace the existing port validation shell block:

```bash
if [ "${BACKEND_PUBLISH_PORT:-18080}" != "${{ env.BACKEND_PORT }}" ] || [ "${FRONTEND_PUBLISH_PORT:-19080}" != "${{ env.FRONTEND_PORT }}" ]; then
  echo "::error::Expected BACKEND_PUBLISH_PORT=${{ env.BACKEND_PORT }} and FRONTEND_PUBLISH_PORT=${{ env.FRONTEND_PORT }} in ${{ env.ENV_FILE }}."
  exit 1
fi
```

with:

```bash
if [ "${BACKEND_PUBLISH_PORT:-18080}" != "${{ env.BACKEND_PORT }}" ] || \
   [ "${FRONTEND_PUBLISH_PORT:-19080}" != "${{ env.FRONTEND_PORT }}" ] || \
   [ "${FUEXAM_PUBLISH_PORT:-3336}" != "${{ env.FUEXAM_PORT }}" ]; then
  echo "::error::Expected BACKEND_PUBLISH_PORT=${{ env.BACKEND_PORT }}, FRONTEND_PUBLISH_PORT=${{ env.FRONTEND_PORT }}, and FUEXAM_PUBLISH_PORT=${{ env.FUEXAM_PORT }} in ${{ env.ENV_FILE }}."
  exit 1
fi
```

- [ ] **Step 3: Build the new service in the compose build command**

Replace:

```bash
"${compose[@]}" build --pull backend frontend
```

with:

```bash
"${compose[@]}" build --pull backend frontend fuexam
```

- [ ] **Step 4: Extend health-check logic for Fuexam**

Replace the `Health check` step shell body with:

```bash
backend_ok=0
frontend_ok=0
fuexam_ok=0
for attempt in $(seq 1 24); do
  if [ "$backend_ok" -eq 0 ] && curl -fsS "http://localhost:${{ env.BACKEND_PORT }}/actuator/health/liveness" >/dev/null; then
    backend_ok=1
    echo "Backend health OK on port ${{ env.BACKEND_PORT }}."
  fi
  frontend_code="$(curl -fsS -o /dev/null -w '%{http_code}' "http://localhost:${{ env.FRONTEND_PORT }}" 2>/dev/null || echo 000)"
  if [ "$frontend_ok" -eq 0 ] && echo "$frontend_code" | grep -qE '^200|307|308$'; then
    frontend_ok=1
    echo "Frontend health OK on port ${{ env.FRONTEND_PORT }} (HTTP $frontend_code)."
  fi
  fuexam_code="$(curl -fsS -o /dev/null -w '%{http_code}' "http://localhost:${{ env.FUEXAM_PORT }}" 2>/dev/null || echo 000)"
  if [ "$fuexam_ok" -eq 0 ] && echo "$fuexam_code" | grep -qE '^200|307|308$'; then
    fuexam_ok=1
    echo "Fuexam health OK on port ${{ env.FUEXAM_PORT }} (HTTP $fuexam_code)."
  fi
  if [ "$backend_ok" -eq 1 ] && [ "$frontend_ok" -eq 1 ] && [ "$fuexam_ok" -eq 1 ]; then
    exit 0
  fi
  sleep 5
done
echo "::error::Deploy health check failed after 120s."
docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" ps
docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" logs backend --tail 80 || true
docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" logs frontend --tail 80 || true
docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" logs fuexam --tail 80 || true
exit 1
```

- [ ] **Step 5: Validate workflow YAML**

Run:

```bash
python - <<'PY'
import yaml, pathlib
path = pathlib.Path('/home/thachnn/Desktop/github/Fuovfolder-Clone/.github/workflows/deploy.yml')
with path.open() as f:
    yaml.safe_load(f)
print('workflow yaml ok')
PY
```

Expected output:

```text
workflow yaml ok
```

- [ ] **Step 6: Commit**

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone add .github/workflows/deploy.yml
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone commit -m "feat: deploy Fuexam in CI workflow"
```

---

### Task 4: Document the new server env variables

**Files:**
- Modify: `.env.example`
- Test: `.env.example`

- [ ] **Step 1: Add Fuexam publish port and public URL variables to `.env.example`**

Insert these lines near the frontend-related deploy settings in `.env.example`:

```dotenv
# --- Fuexam test frontend (parallel with legacy frontend) ---
FUEXAM_PUBLISH_PORT=3336
FUEXAM_NEXT_PUBLIC_API_URL=http://YOUR_SERVER_IP:18080
FUEXAM_NEXT_PUBLIC_API_BASE_URL=http://YOUR_SERVER_IP:18080
FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://YOUR_SERVER_IP:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL=http://YOUR_SERVER_IP:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://YOUR_SERVER_IP:9000/fuoverflow-media
```

Keep the existing legacy frontend variables unchanged.

- [ ] **Step 2: Add one operator note explaining coexistence**

Add this comment directly above the new block:

```dotenv
# Fuexam is deployed in parallel for production-like testing. Legacy frontend remains on FRONTEND_PUBLISH_PORT.
```

- [ ] **Step 3: Verify all required Fuexam env names are documented**

Run:

```bash
grep -n "FUEXAM_" /home/thachnn/Desktop/github/Fuovfolder-Clone/.env.example
```

Expected output includes all six names:

```text
FUEXAM_PUBLISH_PORT
FUEXAM_NEXT_PUBLIC_API_URL
FUEXAM_NEXT_PUBLIC_API_BASE_URL
FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL
FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL
FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL
```

- [ ] **Step 4: Commit**

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone add .env.example
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone commit -m "docs: add Fuexam deploy environment variables"
```

---

### Task 5: End-to-end local render check before pushing

**Files:**
- Test: `Fuexam/Dockerfile`
- Test: `deploy/docker-compose.yml`
- Test: `.github/workflows/deploy.yml`
- Test: `.env.example`

- [ ] **Step 1: Create a temporary env file for compose rendering**

Run:

```bash
cat >/tmp/fuoverflow-fuexam-test.env <<'EOF'
SPRING_PROFILES_ACTIVE=prod
POSTGRES_DB=fuoverflow
DB_USERNAME=fuoverflow-admin
DB_PASSWORD=test-db-password
BACKEND_PUBLISH_PORT=18080
FRONTEND_PUBLISH_PORT=19080
FUEXAM_PUBLISH_PORT=3336
REDIS_PUBLISH_PORT=6379
MINIO_API_PUBLISH_PORT=9000
MINIO_CONSOLE_PUBLISH_PORT=9001
STORAGE_S3_ACCESS_KEY=fuoverflow
STORAGE_S3_SECRET_KEY=test-minio-secret
STORAGE_S3_BUCKET=fuoverflow-media
NEXT_PUBLIC_API_URL=http://103.160.2.147:18080
NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_API_URL=http://103.160.2.147:18080
FUEXAM_NEXT_PUBLIC_API_BASE_URL=http://103.160.2.147:18080
FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
ENV_FILE=/tmp/fuoverflow-fuexam-test.env
EOF
```

- [ ] **Step 2: Render compose with the temporary env file**

Run:

```bash
docker compose \
  -f /home/thachnn/Desktop/github/Fuovfolder-Clone/deploy/docker-compose.yml \
  --env-file /tmp/fuoverflow-fuexam-test.env config >/tmp/fuoverflow-fuexam-rendered.yml
```

Expected: command exits `0`.

- [ ] **Step 3: Verify rendered compose contains the new service and correct port mapping**

Run:

```bash
grep -nE 'fuexam|3336:3000|fuoverflow-fuexam' /tmp/fuoverflow-fuexam-rendered.yml
```

Expected output contains lines matching all of:

```text
fuexam:
fuoverflow-fuexam
3336:3000
```

- [ ] **Step 4: Verify no legacy frontend regression in rendered compose**

Run:

```bash
grep -nE 'frontend:|19080:3000|fuoverflow-frontend' /tmp/fuoverflow-fuexam-rendered.yml
```

Expected output still contains the legacy frontend service and `19080:3000` mapping.

- [ ] **Step 5: Commit**

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone status --short
```

Expected: only intended files are modified:

```text
M .env.example
M .github/workflows/deploy.yml
M deploy/docker-compose.yml
M Fuexam/next.config.mjs
?? Fuexam/Dockerfile
```

If correct, create a final integration commit:

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone add Fuexam/Dockerfile Fuexam/next.config.mjs deploy/docker-compose.yml .github/workflows/deploy.yml .env.example
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone commit -m "feat: add Fuexam parallel CI deploy"
```

---

### Task 6: Server rollout and verification

**Files:**
- Modify on server: `/opt/fuoverflow/.env`
- Test live services after GitHub Actions deploy

- [ ] **Step 1: Update `/opt/fuoverflow/.env` on the server**

Append these production values:

```dotenv
FUEXAM_PUBLISH_PORT=3336
FUEXAM_NEXT_PUBLIC_API_URL=http://103.160.2.147:18080
FUEXAM_NEXT_PUBLIC_API_BASE_URL=http://103.160.2.147:18080
FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media
```

Do not modify the existing `FRONTEND_*` values in this step.

- [ ] **Step 2: Trigger the deploy workflow**

Use either:

```bash
git -C /home/thachnn/Desktop/github/Fuovfolder-Clone push origin main
```

or manual dispatch from GitHub Actions UI.

Expected: workflow `Deploy-FuOverflow` completes successfully.

- [ ] **Step 3: Verify backend, legacy frontend, and Fuexam on the server**

Run:

```bash
curl -fsS http://103.160.2.147:18080/actuator/health/liveness
curl -I http://103.160.2.147:19080
curl -I http://103.160.2.147:3336
```

Expected:
- backend health returns JSON
- `19080` returns `200`, `307`, or `308`
- `3336` returns `200`, `307`, or `308`

- [ ] **Step 4: Verify `fuexam` container is actually running in the deploy stack**

Run on the server:

```bash
docker ps --format '{{.Names}}\t{{.Ports}}' | grep fuoverflow-fuexam
```

Expected output resembles:

```text
fuoverflow-fuexam	0.0.0.0:3336->3000/tcp
```

- [ ] **Step 5: Smoke-test a Fuexam page that needs backend/media config**

Open in browser:

```text
http://103.160.2.147:3336
```

Then verify at least one page that calls backend or resolves media. Suggested manual checks:
- landing page loads
- a page that hits API does not throw frontend fetch errors
- any image/media URL resolves against `103.160.2.147:9000/fuoverflow-media` or backend uploads route as expected

- [ ] **Step 6: Commit operational note if needed**

If the rollout exposed a missing assumption, update docs before merging any future cutover work. Otherwise, no code change in this step.

---

## Spec coverage check

Covered spec requirements:
- dedicated production Dockerfile for `Fuexam/`
- parallel `fuexam` service in compose
- CI workflow build + up + health-check support
- namespaced `FUEXAM_*` server env vars mapped to `NEXT_PUBLIC_*`
- no removal of legacy frontend in this phase
- verification against backend/media URLs and coexistence on different ports

No spec gaps found.

## Placeholder scan

Checked for:
- `TBD`
- `TODO`
- vague “add error handling” instructions
- “similar to previous task” shortcuts

None remain.

## Type consistency check

Confirmed consistent naming across tasks:
- service name: `fuexam`
- image name: `fuoverflow-fuexam:latest`
- container name: `fuoverflow-fuexam`
- host port env: `FUEXAM_PUBLISH_PORT`
- build arg env names: `FUEXAM_NEXT_PUBLIC_*`
- in-container app port: `3000`

