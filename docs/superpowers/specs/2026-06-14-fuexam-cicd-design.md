# Fuexam CI/CD deploy design

Date: 2026-06-14

## Goal

Add CI/CD deploy support for `Fuexam/` on current server without replacing current production frontend yet.

Initial rollout goal:
- keep existing `frontend/` deployment unchanged on host port `19080`
- deploy `Fuexam/` in parallel on host port `3336`
- let user test real server build/runtime before switching traffic

## Current state

Current GitHub Actions deploy workflow builds and starts:
- backend
- existing frontend from `frontend/`

Current deploy wiring:
- workflow: `.github/workflows/deploy.yml`
- compose: `deploy/docker-compose.yml`
- existing frontend build context: `../frontend`
- existing frontend published port: `19080`

`Fuexam/` is a separate Next.js app and is not yet part of production deploy.
It currently reads backend/media configuration from multiple env vars:
- `NEXT_PUBLIC_API_URL`
- `NEXT_PUBLIC_API_BASE_URL`
- `NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL`
- `NEXT_PUBLIC_MEDIA_BASE_URL`
- `NEXT_PUBLIC_S3_PUBLIC_BASE_URL`

## Chosen approach

Use side-by-side deployment.

Add a new Docker service named `fuexam` to the existing production compose stack. Build it from `Fuexam/`, expose it on host port `3336`, and keep the legacy `frontend` service running on `19080`.

This is preferred over replacing the current frontend immediately because it minimizes rollback risk and isolates verification of the new app to a separate URL.

## Alternatives considered

### 1. Replace current `frontend` deployment immediately

Pros:
- fewer containers
- less config duplication

Cons:
- high rollback risk
- no safe production-like test window
- any runtime issue in `Fuexam/` immediately impacts user-facing frontend

Rejected for first rollout.

### 2. Make one frontend service switch source folder by flag

Pros:
- single frontend service definition

Cons:
- harder workflow logic
- less explicit operational state
- easy to accidentally deploy wrong app to the live frontend port

Rejected because clarity and safety are more important for rollout 1.

### 3. Deploy `Fuexam/` behind reverse proxy path or subdomain only

Pros:
- cleaner public routing long-term

Cons:
- requires extra infrastructure change outside current repo scope
- slower path to first validation

Deferred. Can be phase 2 if parallel port test succeeds.

## Target architecture

After change, server stack becomes:
- backend on `18080`
- legacy frontend on `19080`
- Fuexam frontend on `3336`

Container-level behavior:
- `fuexam` container runs Next.js production server on internal port `3000`
- host publishes `3336:3000`
- `fuexam` depends on healthy backend, same as existing frontend dependency model

No existing service is removed in this phase.

## Files to change

### 1. `Fuexam/Dockerfile`

Add a production Dockerfile for `Fuexam/`.

Requirements:
- multi-stage Node 20 build, matching current frontend deploy style
- install dependencies from `package.json` and `package-lock.json`
- build Next app for production
- run on container port `3000`
- support build-time public env injection for all frontend public URL variables used by `Fuexam`

### 2. `deploy/docker-compose.yml`

Add new service `fuexam`.

Expected shape:
- `build.context: ../Fuexam`
- production image/container naming for Fuexam
- build args for the required `NEXT_PUBLIC_*` values
- `ports: - "${FUEXAM_PUBLISH_PORT:-3336}:3000"`
- depends on healthy backend
- leave current `frontend` service untouched

### 3. `.github/workflows/deploy.yml`

Extend current workflow to:
- build `fuexam` together with existing services
- bring up `fuexam` with the same compose command
- add separate health check for `http://localhost:3336`
- fail deployment if Fuexam does not respond with expected frontend HTTP status

Current frontend checks should remain intact.

### 4. Production env file on server

Update `/opt/fuoverflow/.env` on the server with Fuexam-specific values.

Minimum additions:
- `FUEXAM_PUBLISH_PORT=3336`
- `FUEXAM_NEXT_PUBLIC_API_URL=http://103.160.2.147:18080`
- `FUEXAM_NEXT_PUBLIC_API_BASE_URL=http://103.160.2.147:18080`
- `FUEXAM_NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media`
- `FUEXAM_NEXT_PUBLIC_MEDIA_BASE_URL=http://103.160.2.147:9000/fuoverflow-media`
- `FUEXAM_NEXT_PUBLIC_S3_PUBLIC_BASE_URL=http://103.160.2.147:9000/fuoverflow-media`

Use dedicated `FUEXAM_...` env names in deploy wiring so they do not accidentally collide with the legacy frontend values.

## Environment and config design

Although `Fuexam/` uses mixed env variable names in code, deploy should normalize them explicitly.

Compose should map dedicated server-side variables into the exact frontend build args expected by the app. This keeps server env readable and avoids accidental coupling with the legacy frontend service.

Recommended mapping direction:
- server env names stay namespaced with `FUEXAM_`
- compose build args translate them into `NEXT_PUBLIC_*` names inside the Fuexam build

This avoids ambiguity when both frontends coexist.

## Health-check design

Workflow should verify all three externally relevant surfaces:
- backend liveness on `18080`
- legacy frontend on `19080`
- Fuexam on `3336`

For Fuexam, accept HTTP:
- `200`
- `307`
- `308`

This matches current frontend health logic and is enough for initial reachability validation.

## Non-goals for this phase

This phase does **not** include:
- removing `frontend/`
- switching live traffic from `19080` to Fuexam
- adding Nginx/subdomain routing for Fuexam
- consolidating duplicate frontend env variables inside app code
- changing backend API behavior

Those are follow-up tasks after production verification.

## Rollout plan

1. Add Dockerfile for `Fuexam/`
2. Add `fuexam` service to deploy compose
3. Extend workflow build and health checks
4. Update `/opt/fuoverflow/.env` on server
5. Push to `main` or run workflow manually
6. Verify:
   - backend still healthy
   - legacy frontend still available on `19080`
   - Fuexam available on `3336`
   - Fuexam can call backend and resolve media URLs correctly

## Verification checklist

After deployment, verify manually:
- `http://103.160.2.147:3336` loads
- pages that call backend succeed
- login/auth behavior is observed with current backend CORS/cookie settings
- media/image URLs render correctly from MinIO/public storage
- existing `http://103.160.2.147:19080` still works unchanged

## Risks and mitigations

### Risk: Fuexam production build fails
Mitigation:
- add dedicated Dockerfile
- keep failure isolated before traffic switch
- workflow fails before any decision to replace old frontend

### Risk: runtime env mismatch for API/media URLs
Mitigation:
- inject all required `NEXT_PUBLIC_*` variables explicitly
- keep namespaced server env vars for clarity

### Risk: existing frontend accidentally broken
Mitigation:
- do not modify old frontend service behavior
- retain old build and health check path
- use separate service name and port

### Risk: server resource usage increases
Mitigation:
- accept temporary overhead during test phase
- remove old frontend only after Fuexam is validated and approved for cutover

## Success criteria

This design is successful when:
- GitHub Actions deploy still completes through current workflow
- old frontend remains reachable on `19080`
- Fuexam is reachable on `3336`
- Fuexam uses production backend/media endpoints correctly
- no live cutover is required to test the new frontend

## Follow-up phase after validation

If Fuexam passes production verification, phase 2 can:
- switch public frontend port or proxy target from legacy frontend to Fuexam
- retire and remove `frontend/`
- simplify CI/CD to single frontend again
