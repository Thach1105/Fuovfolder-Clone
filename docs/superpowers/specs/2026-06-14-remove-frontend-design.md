# Remove Legacy Frontend, Keep Only Fuexam

**Date:** 2026-06-14  
**Status:** Approved  
**Approach:** Immediate complete removal (Approach A)

## 1. Overview and Goals

### Objective
Remove the legacy frontend (`frontend/` directory) completely from the codebase and migrate all web traffic to Fuexam running on port 3336. This is a one-way change - after completion, the legacy frontend will no longer exist in the repository.

### Scope of Changes
1. Delete `frontend/` directory from repository
2. Remove `frontend` service from Docker Compose (both root and deploy configs)
3. Remove frontend references from CI/CD workflow
4. Update backend configuration: port 19080 → 3336
5. Update documentation and example files

### Expected Outcome
- Only Fuexam running on port 3336
- Backend APIs interact with Fuexam via port 3336
- CI/CD builds and deploys only: backend + fuexam
- Port 19080 is no longer used

### Critical Assumptions
- Fuexam has implemented all necessary features
- No users are actively accessing port 19080 in production
- Backend is compatible with Fuexam

## 2. Components to Change

### 2.1 Files to Delete
- `frontend/` - entire directory (git rm -rf)

### 2.2 Files to Modify

**Docker Compose:**
- `docker-compose.yml` - remove `frontend` service (lines 108-126)
- `deploy/docker-compose.yml` - remove `frontend` service (lines 108-126)

**CI/CD:**
- `.github/workflows/deploy.yml`:
  - Remove `FRONTEND_PORT` env var (line 16)
  - Remove frontend from build command (line 69)
  - Remove frontend health check logic (lines 75-86, 92)
  - Remove frontend systemd stop (line 46)
  - Remove frontend logs (line 100)
  - Remove frontend port validation (line 38)

**Environment & Configuration:**
- `.env.example`:
  - Remove or comment `FRONTEND_PUBLISH_PORT=19080`
  - Update `CORS_ALLOWED_ORIGINS` from `:19080` → `:3336`
  - Update `AUTH_EMAIL_VERIFICATION_URL_BASE` from `:19080` → `:3336`
  - Update `AUTH_PASSWORD_RESET_URL_BASE` from `:19080` → `:3336`
  - Update comments about Fuexam

**Documentation:**
- `CLAUDE.md` - update if frontend is mentioned
- Existing spec doc `docs/superpowers/specs/2026-06-14-fuexam-cicd-design.md` - mark as outdated or remove

## 3. Detailed Change Specifications

### 3.1 Docker Compose Changes

Both `docker-compose.yml` and `deploy/docker-compose.yml` need to have the `frontend` service completely removed:

**Section to DELETE:**
```yaml
  frontend:
    build:
      context: ./frontend  # or ../frontend in deploy/
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

### 3.2 CI/CD Workflow Changes

File `.github/workflows/deploy.yml` modifications:

**Line 16 - DELETE:**
```yaml
  FRONTEND_PORT: "19080"
```

**Lines 38-40 - DELETE frontend port validation:**
```yaml
             [ "${FRONTEND_PUBLISH_PORT:-19080}" != "${{ env.FRONTEND_PORT }}" ] || \
```

**Line 46 - MODIFY systemd service stop (remove fuoverflow-frontend):**
```yaml
# BEFORE:
          for svc in fuoverflow-backend fuoverflow-frontend; do

# AFTER:
          for svc in fuoverflow-backend; do
```

**Line 69 - MODIFY build command (remove frontend):**
```yaml
# BEFORE:
          "${compose[@]}" build --pull backend frontend fuexam

# AFTER:
          "${compose[@]}" build --pull backend fuexam
```

**Lines 75-86 - DELETE entire frontend health check section:**
```yaml
# DELETE this entire block:
          frontend_ok=0
          ...
          if [ "$frontend_ok" -eq 0 ] && echo "$frontend_code" | grep -qE '^(200|307|308)$'; then
            frontend_ok=1
            echo "Frontend health OK on port ${{ env.FRONTEND_PORT }} (HTTP $frontend_code)."
          fi
```

**Line 92 - MODIFY success condition (remove frontend_ok check):**
```yaml
# BEFORE:
            if [ "$backend_ok" -eq 1 ] && [ "$frontend_ok" -eq 1 ] && [ "$fuexam_ok" -eq 1 ]; then

# AFTER:
            if [ "$backend_ok" -eq 1 ] && [ "$fuexam_ok" -eq 1 ]; then
```

**Line 100 - DELETE frontend logs:**
```yaml
# DELETE:
          docker compose -f "${{ env.COMPOSE_FILE }}" --env-file "${{ env.ENV_FILE }}" logs frontend --tail 80 || true
```

### 3.3 Environment Variables

File `.env.example` modifications:

**Remove or comment out:**
```bash
# FRONTEND_PUBLISH_PORT=19080
# # Fuexam is deployed in parallel for production-like testing. Legacy frontend remains on FRONTEND_PUBLISH_PORT.
```

**Update URLs from 19080 → 3336:**
```bash
CORS_ALLOWED_ORIGINS=http://YOUR_SERVER_IP:3336
AUTH_EMAIL_VERIFICATION_URL_BASE=http://YOUR_SERVER_IP:3336/verify-email
AUTH_PASSWORD_RESET_URL_BASE=http://YOUR_SERVER_IP:3336/reset-password
```

**Add new comment:**
```bash
# Fuexam is the primary frontend on port 3336
FUEXAM_PUBLISH_PORT=3336
```

## 4. Risk Mitigation

### Risk 1: Fuexam Not Ready or Has Bugs
- **Impact:** Users cannot use the web application
- **Mitigation:**
  - Test Fuexam thoroughly locally before deploying
  - Check health endpoints after deployment
  - If deployment fails, revert commit and redeploy
  - Git history preserves frontend code if needed for reference

### Risk 2: Backend Configuration Errors (CORS, URLs)
- **Impact:** Fuexam cannot connect to backend, email verification/reset don't work
- **Mitigation:**
  - Check `/opt/fuoverflow/.env` on server before deploying
  - Ensure these variables are updated: `CORS_ALLOWED_ORIGINS`, `AUTH_EMAIL_*_URL_BASE`
  - Test flows after deployment: register → verify email → login

### Risk 3: CI/CD Workflow Syntax Errors
- **Impact:** Deployment fails completely
- **Mitigation:**
  - Review YAML syntax carefully before commit
  - GitHub Actions validates workflow files
  - If it fails, fix and re-trigger workflow

### Risk 4: Docker Compose Syntax Errors
- **Impact:** Cannot start containers
- **Mitigation:**
  - Test locally: `docker compose -f deploy/docker-compose.yml config`
  - If it fails, logs will indicate the exact error

### Rollback Strategy
If deployment fails after applying changes:

1. **Revert git commit:**
   ```bash
   git revert HEAD
   git push origin main
   ```

2. **CI/CD auto-redeploys:**
   - Workflow will automatically redeploy the reverted version (with frontend)

3. **Manual rollback (if needed):**
   ```bash
   git checkout <previous-commit>
   # Manually trigger deploy or push to a rollback branch
   ```

## 5. Verification Strategy

### 5.1 Pre-deployment Verification (Local)

Before committing and pushing:

```bash
# 1. Validate Docker Compose syntax
cd deploy
docker compose config

# 2. Check workflow syntax
# GitHub UI validates on commit, or use:
yamllint .github/workflows/deploy.yml

# 3. Test Fuexam locally on equivalent port
cd Fuexam
npm run dev
# Open http://localhost:3000 and test flows
```

### 5.2 Post-deployment Verification (Production)

After successful CI/CD deployment:

```bash
# 1. Check running containers
docker ps | grep fuoverflow
# Expected: backend, postgres, redis, minio, minio-init, fuexam
# NOT expected: frontend

# 2. Health check Fuexam
curl http://localhost:3336
# Expected: 200 OK or 307/308 redirect

# 3. Check backend health
curl http://localhost:18080/actuator/health/liveness
# Expected: {"status":"UP"}

# 4. Check logs for errors
docker logs fuoverflow-fuexam --tail 50
docker logs fuoverflow-backend --tail 50
```

### 5.3 Functional Testing

Test critical flows through Fuexam UI (port 3336):

**1. Registration Flow:**
- Register new user
- Verify email verification link contains port 3336
- Click verification link and complete

**2. Login Flow:**
- Login with verified user
- Confirm JWT cookies are set correctly

**3. Password Reset Flow:**
- Request password reset
- Verify reset link contains port 3336
- Complete password reset

**4. API Calls:**
- Test several API calls from Fuexam
- Confirm CORS is not blocking requests
- Verify authentication works correctly

### Success Criteria
- ✅ Only backend + fuexam containers running
- ✅ Fuexam accessible on port 3336
- ✅ Backend health check passes
- ✅ Registration, login, password reset flows work
- ✅ No CORS errors
- ✅ CI/CD workflow completes successfully

## 6. Implementation Order

This is a single-phase immediate removal. Changes should be made together in one commit:

1. Delete `frontend/` directory
2. Update `docker-compose.yml` and `deploy/docker-compose.yml`
3. Update `.github/workflows/deploy.yml`
4. Update `.env.example`
5. Update documentation
6. Commit all changes together with clear message
7. Push to trigger CI/CD
8. Monitor deployment and run verification tests

## 7. Production Environment Checklist

Before pushing changes, ensure on the production server:

- [ ] `/opt/fuoverflow/.env` exists and is readable by CI/CD runner
- [ ] `CORS_ALLOWED_ORIGINS` updated to include `:3336`
- [ ] `AUTH_EMAIL_VERIFICATION_URL_BASE` updated to use `:3336`
- [ ] `AUTH_PASSWORD_RESET_URL_BASE` updated to use `:3336`
- [ ] `FUEXAM_PUBLISH_PORT=3336` is set
- [ ] `FUEXAM_NEXT_PUBLIC_API_URL` and related Fuexam env vars are set
- [ ] Docker and Docker Compose are working
- [ ] Self-hosted GitHub Actions runner is active

## 8. Post-Implementation Cleanup

After successful deployment and verification:

- Update any remaining documentation references to the legacy frontend
- Consider whether to keep or remove the outdated Fuexam CI/CD spec document
- Monitor logs for any unexpected errors over the next 24-48 hours
