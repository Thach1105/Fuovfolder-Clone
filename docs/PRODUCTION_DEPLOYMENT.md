# Production Deployment Guide

## Task 7: Production Environment Preparation (Frontend Removal Complete)

This guide documents the production environment changes required after the complete removal of the legacy frontend. All code changes are committed to the `main` branch. This document covers the manual operations needed on the production server.

---

## Step 1: Production .env Configuration Changes

Production server path: `/opt/fuoverflow/.env`

After pulling the latest code from `main`, update the environment file with the following changes:

### Required Changes

Add or update these variables in `/opt/fuoverflow/.env`:

```bash
# CORS configuration (update YOUR_PRODUCTION_IP with actual server IP or domain)
CORS_ALLOWED_ORIGINS=http://YOUR_PRODUCTION_IP:3336
CORS_ALLOW_CREDENTIALS=true

# Authentication email verification (for email-based account recovery flows)
AUTH_EMAIL_VERIFICATION_URL_BASE=http://YOUR_PRODUCTION_IP:3336/verify-email
AUTH_EMAIL_VERIFICATION_SUBJECT='Verify your FuOverflow email'

# Authentication password reset (for account recovery)
AUTH_PASSWORD_RESET_URL_BASE=http://YOUR_PRODUCTION_IP:3336/reset-password
AUTH_PASSWORD_RESET_SUBJECT='Reset your FuOverflow password'

# Fuexam frontend (primary frontend, replaces legacy frontend)
FUEXAM_PUBLISH_PORT=3336

# Fuexam build args (must all be set for frontend to build correctly)
FUEXAM_NEXT_PUBLIC_API_URL=http://YOUR_PRODUCTION_IP:18080
FUEXAM_NEXT_PUBLIC_API_BASE_URL=http://YOUR_PRODUCTION_IP:18080
FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://YOUR_PRODUCTION_IP:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL=http://YOUR_PRODUCTION_IP:9000/fuoverflow-media
FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://YOUR_PRODUCTION_IP:9000/fuoverflow-media
```

### Remove or Comment Out (Legacy Frontend)

If these variables exist in the current `/opt/fuoverflow/.env`, remove or comment them out:

```bash
# Deprecated: legacy frontend removed in latest release
# FRONTEND_PUBLISH_PORT=19080
# NEXT_PUBLIC_API_URL=...
# NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=...
```

### Verify Required Variables

Before deploying, confirm these critical variables are set and correct:

- `SPRING_PROFILES_ACTIVE=prod` (backend profile)
- `BACKEND_PUBLISH_PORT=18080` (backend API)
- `POSTGRES_PUBLISH_PORT=5433` (PostgreSQL)
- `REDIS_PUBLISH_PORT=6379` (Redis cache)
- `MINIO_API_PUBLISH_PORT=9000` (object storage API)
- `MINIO_CONSOLE_PUBLISH_PORT=9001` (object storage console)
- All `FUEXAM_NEXT_PUBLIC_*` variables set (7 variables total)

### Notes on Environment Variables

- **YOUR_PRODUCTION_IP**: Replace with actual server IP address or fully qualified domain name (e.g., `192.168.1.100` or `api.yourdomain.com`)
- **Port 3336**: This is the primary frontend port. After deployment, users access the application at `http://YOUR_PRODUCTION_IP:3336`
- **Port 18080**: Backend API continues to run on this port (not publicly exposed; accessed via frontend)
- **Email URLs**: Auth links in verification and password reset emails will use these base URLs. Ensure they match the domain users see in their browser.

---

## Step 2: Deployment Verification Script

After the CI/CD pipeline completes and containers are running, execute these verification commands on the production server to confirm successful deployment:

### Pre-Deployment (Before Pushing to Main)

Ensure the database and dependencies are ready:

```bash
# 1. Check database migrations
docker logs fuoverflow-postgres --tail 20 | grep -i "ready\|error"

# 2. Check Redis cache is ready
docker logs fuoverflow-redis --tail 5 | grep -i "ready\|error"

# 3. Check MinIO object storage is ready
docker logs fuoverflow-minio --tail 10 | grep -i "ready\|error"
```

### Post-Deployment Verification (After CI/CD Completes)

Run these checks in order:

```bash
# 1. Verify container landscape (should show 5 services, NO legacy frontend)
docker ps | grep fuoverflow
# Expected output (exactly 5 containers):
#   fuoverflow-postgres
#   fuoverflow-redis
#   fuoverflow-minio
#   fuoverflow-backend
#   fuoverflow-fuexam

# 2. Verify no legacy frontend container exists
docker ps | grep -i "frontend" && echo "ERROR: Legacy frontend still running!" || echo "OK: No legacy frontend"

# 3. Check backend API health
curl -s http://localhost:18080/actuator/health/liveness | jq .
# Expected: {"status":"UP"}

# 4. Check backend readiness (all dependencies up)
curl -s http://localhost:18080/actuator/health/readiness | jq .
# Expected: {"status":"UP"} with all components UP

# 5. Test frontend is accessible
curl -s http://localhost:3336 | head -20
# Expected: HTML response (200 status), not error/empty

# 6. Check backend logs for errors during startup
docker logs fuoverflow-backend --tail 30 | grep -iE "error|exception|failed" | head -10
# Expected: No critical errors (warnings OK)

# 7. Check frontend logs for errors
docker logs fuoverflow-fuexam --tail 20 | grep -iE "error|build fail" | head -10
# Expected: No build errors

# 8. Verify CORS configuration is correct by making a request from frontend origin
curl -s -H "Origin: http://YOUR_PRODUCTION_IP:3336" \
     -H "Access-Control-Request-Method: GET" \
     -H "Access-Control-Request-Headers: Content-Type" \
     -X OPTIONS http://localhost:18080/api/v1/health \
     -v 2>&1 | grep "Access-Control-Allow-Origin"
# Expected: Access-Control-Allow-Origin: http://YOUR_PRODUCTION_IP:3336

# 9. Test API connectivity from frontend perspective
curl -s "http://localhost:3336/api/v1/health" 2>&1 | head -5
# Should return a response (may be 200 or redirect depending on auth requirements)
```

### Quick Verification Summary Command

Run this one-liner to perform basic checks:

```bash
echo "=== Container Check ===" && \
docker ps --filter name=fuoverflow | wc -l && \
echo "=== Backend Health ===" && \
curl -s http://localhost:18080/actuator/health/liveness | jq .status && \
echo "=== Frontend Access ===" && \
curl -s -o /dev/null -w "%{http_code}" http://localhost:3336 && \
echo "" && \
echo "All checks passed!"
```

### Troubleshooting Failed Checks

| Check | Failure | Fix |
|-------|---------|-----|
| Container count != 5 | Missing or extra container | Check `docker ps -a` for stopped containers; restart if needed: `docker start <container>` |
| Backend health not UP | Backend service failed | Check logs: `docker logs fuoverflow-backend \| tail -50` |
| Frontend not accessible (non-200) | Frontend build failed or not running | Check logs: `docker logs fuoverflow-fuexam` |
| CORS headers missing | CORS config incorrect | Verify `CORS_ALLOWED_ORIGINS` in `/opt/fuoverflow/.env` matches actual frontend URL |
| Backend logs show migration errors | Database schema mismatch | Run migrations manually: `docker exec fuoverflow-backend /opt/fuoverflow/bin/migrate` |

---

## Step 3: Rollback Procedure

If the deployment has critical issues that cannot be resolved quickly, follow this rollback procedure to revert to the previous working state.

### When to Rollback

Rollback if any of these occur:
- Backend fails to start and logs show migration errors
- Frontend cannot connect to backend (persistent CORS/connectivity errors)
- Critical features are broken (login, forum access, etc.)
- Data integrity issues detected

### Rollback Steps

**Important: This requires git history rewrite. Coordinate with team before executing.**

```bash
# 1. Stop running containers
docker compose -f /opt/fuoverflow/deploy/docker-compose.yml down

# 2. Revert the 6 commits related to frontend removal
cd /opt/fuoverflow  # or your deployment git repo
git revert --no-edit HEAD~5..HEAD
# This creates 6 new revert commits (HEAD~5 through HEAD, inclusive)

# 3. Push reverted changes to main
git push origin main
# CI/CD will automatically detect the push and redeploy

# 4. Monitor CI/CD deployment
# Wait for GitHub Actions (or your CI/CD) to rebuild and redeploy
# Expected: ~5-10 minutes for full rebuild with frontend

# 5. Verify rollback succeeded
docker ps | grep fuoverflow  # Should see legacy frontend service now
curl http://localhost:19080  # Legacy frontend should be accessible
curl http://localhost:3336   # Fuexam should NOT be running (check fails OK)
```

### Rollback Verification Checklist

After rollback completes, verify:

- [ ] Legacy frontend container (`fuoverflow-frontend`) is running on port 19080
- [ ] Backend is accessible on port 18080
- [ ] Database is not corrupted (check logs for migration errors)
- [ ] Users can log in and access the application via legacy frontend
- [ ] No Fuexam container is running (port 3336 should be unresponsive)

### If Rollback Fails

If the rollback itself fails:

1. **Check disk space** (deployments require ~2GB temporary space)
   ```bash
   df -h /opt/fuoverflow
   ```

2. **Check git history integrity**
   ```bash
   cd /opt/fuoverflow
   git log --oneline | head -20
   ```

3. **Manual fallback: reset to previous tag**
   ```bash
   # Find previous working tag/commit
   git tag -l | sort -V | tail -5
   
   # Reset to known-good commit (example)
   git reset --hard <previous_commit_hash>
   git push origin main --force-with-lease
   ```

4. **Contact development team** if manual reset needed

---

## Deployment Checklist

Use this checklist when deploying to production:

### Pre-Deployment
- [ ] All code changes committed to `main` branch
- [ ] CI/CD pipeline passes (unit tests, integration tests, Docker build)
- [ ] `/opt/fuoverflow/.env` has been reviewed and `YOUR_PRODUCTION_IP` replaced with actual IP
- [ ] All `FUEXAM_NEXT_PUBLIC_*` variables are set
- [ ] Database backups created (optional but recommended)
- [ ] Team informed of deployment window

### During Deployment
- [ ] Push code to main; CI/CD begins automatically
- [ ] Monitor CI/CD pipeline logs for build errors
- [ ] Wait for pipeline completion (~10-15 minutes)
- [ ] Monitor container startup logs: `docker logs -f fuoverflow-backend` (watch for errors)

### Post-Deployment
- [ ] Run verification script (Step 2 above)
- [ ] All 9 verification checks pass
- [ ] Test critical user journeys:
  - [ ] User signup and email verification
  - [ ] User login
  - [ ] Forum browsing and search
  - [ ] Creating/editing posts and threads
  - [ ] File uploads (course materials, media)
- [ ] Monitor logs for errors: `docker logs --tail 50 fuoverflow-backend`
- [ ] Alert team that production is stable

### If Issues Found
- [ ] Document the issue in deployment log
- [ ] Attempt fix if minor (e.g., restart container)
- [ ] If critical or fix unsure, execute Rollback Procedure (Step 3)
- [ ] Notify team of status

---

## Related Documentation

- **Architecture**: See `.knowledge/system-architecture-design.md` for module structure
- **Auth Design**: See `.knowledge/auth-module-plan.md` for JWT/session details
- **Database Schema**: See `.knowledge/database-schema.sql` for schema reference
- **Local Development**: See `docs/` for local setup instructions

## Support

For deployment questions or issues:
1. Check this document's Troubleshooting section (Step 2)
2. Review CI/CD pipeline logs in GitHub Actions
3. Check container logs: `docker logs <container_name>`
4. Contact the development team with error messages and deployment context
