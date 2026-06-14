# Remove Legacy Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely remove the legacy `frontend/` directory and migrate all web traffic to Fuexam on port 3336.

**Architecture:** Single-phase atomic removal. Delete frontend service from Docker Compose (root + deploy), remove from CI/CD workflow, update backend environment variables to point to port 3336, and delete the frontend directory.

**Tech Stack:** Docker Compose, GitHub Actions, Bash, YAML

**Design Spec:** [docs/superpowers/specs/2026-06-14-remove-frontend-design.md](../specs/2026-06-14-remove-frontend-design.md)

---

## File Structure

**Files to modify:**
- `docker-compose.yml` - remove frontend service (lines 108-126)
- `deploy/docker-compose.yml` - remove frontend service (lines 108-126)
- `.github/workflows/deploy.yml` - remove frontend references (multiple lines)
- `.env.example` - update ports 19080 → 3336

**Files to delete:**
- `frontend/` - entire directory

---

## Task 1: Remove frontend from root docker-compose.yml

**Files:**
- Modify: `docker-compose.yml:108-126`

- [ ] **Step 1: Read current docker-compose.yml**

```bash
cat docker-compose.yml | grep -A 20 "frontend:"
```

Expected: See the frontend service definition

- [ ] **Step 2: Remove frontend service**

Remove lines 108-126 (entire `frontend:` section including all sub-keys)

```yaml
# DELETE this entire block:
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_API_URL: ${NEXT_PUBLIC_API_URL:?NEXT_PUBLIC_API_URL is required}
        NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL: ${NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL:?NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL is required}
    image: fuoverflow-frontend:latest
    container_name: fuoverflow-frontend
    restart: unless-stopped
    environment:
      NODE_ENV: production
      PORT: "3000"
    ports:
      - "${FRONTEND_PUBLISH_PORT:-19080}:3000"
    depends_on:
      backend:
        condition: service_healthy
```

- [ ] **Step 3: Validate YAML syntax**

```bash
docker compose config
```

Expected: Valid YAML output with no frontend service

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml
git commit -m "refactor: remove frontend service from root docker-compose

Frontend has been replaced by Fuexam on port 3336.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Remove frontend from deploy/docker-compose.yml

**Files:**
- Modify: `deploy/docker-compose.yml:108-126`

- [ ] **Step 1: Read current deploy docker-compose**

```bash
cat deploy/docker-compose.yml | grep -A 20 "frontend:"
```

Expected: See the frontend service definition

- [ ] **Step 2: Remove frontend service**

Remove lines 108-126 (entire `frontend:` section including all sub-keys)

```yaml
# DELETE this entire block:
  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_API_URL: ${NEXT_PUBLIC_API_URL:?NEXT_PUBLIC_API_URL is required}
        NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL: ${NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL:?NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL is required}
    image: fuoverflow-frontend:latest
    container_name: fuoverflow-frontend
    restart: unless-stopped
    environment:
      NODE_ENV: production
      PORT: "3000"
    ports:
      - "${FRONTEND_PUBLISH_PORT:-19080}:3000"
    depends_on:
      backend:
        condition: service_healthy
```

- [ ] **Step 3: Validate YAML syntax**

```bash
docker compose -f deploy/docker-compose.yml config
```

Expected: Valid YAML output with no frontend service

- [ ] **Step 4: Commit**

```bash
git add deploy/docker-compose.yml
git commit -m "refactor: remove frontend service from deploy compose

Frontend has been replaced by Fuexam on port 3336.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: Update CI/CD workflow

**Files:**
- Modify: `.github/workflows/deploy.yml` (multiple sections)

- [ ] **Step 1: Remove FRONTEND_PORT env var**

Line 16 - delete:
```yaml
  FRONTEND_PORT: "19080"
```

- [ ] **Step 2: Remove frontend port validation**

Lines 37-39 - modify the validation check to remove frontend:
```yaml
# BEFORE:
          if [ "${BACKEND_PUBLISH_PORT:-18080}" != "${{ env.BACKEND_PORT }}" ] || \
             [ "${FRONTEND_PUBLISH_PORT:-19080}" != "${{ env.FRONTEND_PORT }}" ] || \
             [ "${FUEXAM_PUBLISH_PORT:-3336}" != "${{ env.FUEXAM_PORT }}" ]; then

# AFTER:
          if [ "${BACKEND_PUBLISH_PORT:-18080}" != "${{ env.BACKEND_PORT }}" ] || \
             [ "${FUEXAM_PUBLISH_PORT:-3336}" != "${{ env.FUEXAM_PORT }}" ]; then
```

And update error message (line 40):
```yaml
# BEFORE:
            echo "::error::Expected BACKEND_PUBLISH_PORT=${{ env.BACKEND_PORT }}, FRONTEND_PUBLISH_PORT=${{ env.FRONTEND_PORT }}, and FUEXAM_PUBLISH_PORT=${{ env.FUEXAM_PORT }} in ${{ env.ENV_FILE }}."

# AFTER:
            echo "::error::Expected BACKEND_PUBLISH_PORT=${{ env.BACKEND_PORT }} and FUEXAM_PUBLISH_PORT=${{ env.FUEXAM_PORT }} in ${{ env.ENV_FILE }}."
```

- [ ] **Step 3: Update systemd service stop**

Line 46 - remove fuoverflow-frontend:
```yaml
# BEFORE:
          for svc in fuoverflow-backend fuoverflow-frontend; do

# AFTER:
          for svc in fuoverflow-backend; do
```

- [ ] **Step 4: Update build command**

Line 69 - remove frontend from build:
```yaml
# BEFORE:
          "${compose[@]}" build --pull backend frontend fuexam

# AFTER:
          "${compose[@]}" build --pull backend fuexam
```

- [ ] **Step 5: Remove frontend health check variables**

Line 75 - remove frontend_ok initialization:
```yaml
# DELETE:
          frontend_ok=0
```

Lines 82-86 - remove entire frontend health check block:
```yaml
# DELETE this entire block:
            frontend_code="$(curl -sS -o /dev/null -w '%{http_code}' "http://localhost:${{ env.FRONTEND_PORT }}" 2>/dev/null || echo 000)"
            if [ "$frontend_ok" -eq 0 ] && echo "$frontend_code" | grep -qE '^(200|307|308)$'; then
              frontend_ok=1
              echo "Frontend health OK on port ${{ env.FRONTEND_PORT }} (HTTP $frontend_code)."
            fi
```

- [ ] **Step 6: Update success condition**

Line 92 - remove frontend_ok check:
```yaml
# BEFORE:
            if [ "$backend_ok" -eq 1 ] && [ "$frontend_ok" -eq 1 ] && [ "$fuexam_ok" -eq 1 ]; then

# AFTER:
            if [ "$backend_ok" -eq 1 ] && [ "$fuexam_ok" -eq 1 ]; then
```

- [ ] **Step 7: Remove frontend logs**

Line 100 - delete frontend logs command:
```yaml
# DELETE:
          docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" logs frontend --tail 80 || true
```

- [ ] **Step 8: Validate workflow syntax**

Check YAML syntax locally:
```bash
yamllint .github/workflows/deploy.yml || echo "Install yamllint if needed"
# Or just commit - GitHub will validate
```

- [ ] **Step 9: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: remove frontend from deployment workflow

Remove frontend service from build, health checks, and validation.
Only backend and fuexam are deployed now.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: Update environment variable examples

**Files:**
- Modify: `.env.example`

- [ ] **Step 1: Read current env example**

```bash
grep -E "FRONTEND_PUBLISH_PORT|19080" .env.example
```

Expected: See references to port 19080

- [ ] **Step 2: Remove or comment FRONTEND_PUBLISH_PORT**

Find and comment out or delete:
```bash
# FRONTEND_PUBLISH_PORT=19080
```

Remove or update the comment:
```bash
# DELETE or UPDATE:
# Fuexam is deployed in parallel for production-like testing. Legacy frontend remains on FRONTEND_PUBLISH_PORT.
```

- [ ] **Step 3: Update CORS and auth URLs to port 3336**

Change all instances of `:19080` to `:3336`:

```bash
# BEFORE:
CORS_ALLOWED_ORIGINS=http://YOUR_SERVER_IP:19080
AUTH_EMAIL_VERIFICATION_URL_BASE=http://YOUR_SERVER_IP:19080/verify-email
AUTH_PASSWORD_RESET_URL_BASE=http://YOUR_SERVER_IP:19080/reset-password

# AFTER:
CORS_ALLOWED_ORIGINS=http://YOUR_SERVER_IP:3336
AUTH_EMAIL_VERIFICATION_URL_BASE=http://YOUR_SERVER_IP:3336/verify-email
AUTH_PASSWORD_RESET_URL_BASE=http://YOUR_SERVER_IP:3336/reset-password
```

- [ ] **Step 4: Add clarifying comment**

Add comment near FUEXAM_PUBLISH_PORT:
```bash
# Fuexam is the primary frontend on port 3336
FUEXAM_PUBLISH_PORT=3336
```

- [ ] **Step 5: Verify changes**

```bash
grep -E "19080|3336|FRONTEND" .env.example
```

Expected: No 19080 references, 3336 for frontend URLs, FRONTEND_PUBLISH_PORT commented/removed

- [ ] **Step 6: Commit**

```bash
git add .env.example
git commit -m "config: update frontend port from 19080 to 3336

Fuexam is now the primary frontend. Update CORS and auth URLs
to point to port 3336 instead of legacy frontend port 19080.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: Delete frontend directory

**Files:**
- Delete: `frontend/` (entire directory)

- [ ] **Step 1: Verify frontend directory exists**

```bash
ls -la frontend/
```

Expected: Directory listing with Next.js project files

- [ ] **Step 2: Remove frontend directory from git**

```bash
git rm -rf frontend/
```

Expected: All files in frontend/ staged for deletion

- [ ] **Step 3: Verify removal**

```bash
git status | grep frontend
ls -la | grep frontend
```

Expected: Shows deleted files in git status, directory no longer exists

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: remove legacy frontend directory

The legacy frontend has been completely replaced by Fuexam.
All references have been removed from Docker Compose, CI/CD,
and environment configurations.

Frontend code is preserved in git history if needed for reference.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: Local verification

**Files:**
- None (verification only)

- [ ] **Step 1: Validate Docker Compose configs**

```bash
docker compose config > /dev/null && echo "Root compose: OK"
docker compose -f deploy/docker-compose.yml config > /dev/null && echo "Deploy compose: OK"
```

Expected: Both configs are valid, no frontend service

- [ ] **Step 2: Verify no frontend references remain**

```bash
grep -r "fuoverflow-frontend\|frontend:" . \
  --include="*.yml" --include="*.yaml" \
  --exclude-dir=.git --exclude-dir=node_modules
```

Expected: No matches (or only in docs/specs if referenced there)

- [ ] **Step 3: Check for port 19080 references**

```bash
grep -r "19080" . \
  --include="*.md" --include="*.yml" --include="*.yaml" \
  --exclude-dir=.git --exclude-dir=node_modules
```

Expected: Only in old spec docs, not in active config files

- [ ] **Step 4: Review git diff**

```bash
git diff HEAD~5 --stat
git log --oneline -5
```

Expected: See all 5 commits for the changes made

---

## Task 7: Production environment preparation

**Files:**
- Manual operations on production server

- [ ] **Step 1: Document production .env changes needed**

Create a checklist for production server `/opt/fuoverflow/.env`:

```bash
# Required changes in /opt/fuoverflow/.env:
# 1. Update or add:
CORS_ALLOWED_ORIGINS=http://YOUR_PRODUCTION_IP:3336
AUTH_EMAIL_VERIFICATION_URL_BASE=http://YOUR_PRODUCTION_IP:3336/verify-email
AUTH_PASSWORD_RESET_URL_BASE=http://YOUR_PRODUCTION_IP:3336/reset-password
FUEXAM_PUBLISH_PORT=3336

# 2. Remove or comment out (if exists):
# FRONTEND_PUBLISH_PORT=19080

# 3. Verify all FUEXAM_NEXT_PUBLIC_* variables are set
```

- [ ] **Step 2: Create deployment verification script**

Add this to commit message or docs:

```bash
# After deployment, run these checks:
docker ps | grep fuoverflow  # Should see: backend, postgres, redis, minio, fuexam (NO frontend)
curl http://localhost:3336  # Should return 200 or redirect
curl http://localhost:18080/actuator/health/liveness  # Backend health
docker logs fuoverflow-fuexam --tail 20  # Check for errors
docker logs fuoverflow-backend --tail 20  # Check for errors
```

- [ ] **Step 3: Note rollback procedure**

Document rollback if needed:

```bash
# If deployment fails, rollback:
git revert HEAD~5..HEAD  # Revert all 5 commits
git push origin main     # CI/CD will redeploy with frontend
```

---

## Success Criteria

After completing all tasks:
- ✅ `frontend/` directory deleted from repository
- ✅ No `frontend` service in docker-compose.yml or deploy/docker-compose.yml
- ✅ CI/CD workflow has no frontend references
- ✅ `.env.example` uses port 3336 for frontend URLs
- ✅ All docker compose configs validate successfully
- ✅ No unintentional references to port 19080 or frontend service
- ✅ All changes committed with clear messages
- ✅ Production environment checklist documented

**Ready for deployment:** Push to trigger CI/CD, monitor health checks.
