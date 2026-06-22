# Guarded deploy script design

Date: 2026-06-22

## Goal

Create a standard one-command deploy script for this server setup so future deploys are reliable, always deploy fresh source, and fail early if an already-existing Flyway migration file has been modified.

## Approved scope

### Primary target

The script targets backend deployment by default.

### Future extensibility

The script may support a future `--full` mode, but v1 should optimize for backend-only deploys because that is the most common and safest path.

### Deployment model

- Source of truth is GitHub.
- The script must clone a fresh copy of the repository into a temporary directory on every deploy.
- The script deploys into `/opt/fuoverflow`.
- The script uses Docker Compose.
- The script must preserve server-specific `.env` configuration and mounted runtime data.

## Core constraints

- Do not depend on an existing mutable checkout in `/opt/fuoverflow` being a valid git repository.
- Do not edit or overwrite server `.env` files during source sync.
- Do not delete or overwrite mounted runtime data directories.
- Do not silently auto-repair Flyway metadata.
- Do not auto-rollback in v1 after a failed deploy.
- Do not allow deploy to continue when an existing Flyway migration file has been modified.

## Recommended approach

Use a server-side guarded deploy script.

### Why this approach

This approach best fits the actual environment and failure mode already observed:

- The server deployment directory may drift and is not guaranteed to be a real git checkout.
- Fresh clone per deploy removes ambiguity about which source version is being built.
- A server-local guard can stop the exact Flyway checksum class of failure before any build or container restart.
- A one-command shell script matches the user's operational goal with minimal moving parts.

### Alternatives considered

#### CI-only deploy workflow

More centralized, but it requires more CI integration work and does not directly solve the current operational need for a dependable one-command server deploy.

#### Hybrid CI-sync plus server deploy

Viable later, but unnecessary for v1. The simpler server-side approach is sufficient and easier to operate now.

## Deployment flow

The v1 deploy flow should be:

1. Parse arguments.
2. Clone fresh source into a temporary directory.
3. Verify expected files exist.
4. Run migration guard checks.
5. Render or inspect Docker Compose target configuration.
6. Stop target services.
7. Rsync fresh source into `/opt/fuoverflow` while preserving `.env` and mounted runtime data.
8. Optionally prune build cache.
9. Build target images using `--no-cache`.
10. Start target services.
11. Wait for backend health at `/actuator/health`.
12. Print success summary including commit SHA and target.

## Migration guard design

### Purpose

Prevent future deploys from repeating the Flyway checksum incident caused by modifying an already-existing migration file.

### Guard rule

The script must inspect `backend/app/src/main/resources/db/migration/V*.sql` in both:

- the currently deployed source tree under `/opt/fuoverflow`
- the freshly cloned source tree in the temporary directory

For every migration file version that already exists in the deployed tree:

- if the file content differs in the fresh clone, the deploy must fail immediately
- if the file is unchanged, the deploy may continue
- if a migration file exists only in the fresh clone and has a higher version, it is allowed as a new migration

### Failure behavior

When a guarded migration change is detected, the script must:

- exit non-zero
- print the exact migration file(s) that changed
- print a clear message stating that existing migrations must not be edited
- instruct the operator to create a new migration version instead

### Allowed migration change pattern

- Existing migrations: immutable
- New migrations with a higher version number: allowed

## Preflight checks

The script must fail early if any of these are missing or invalid:

- git available
- docker available
- docker compose available
- repository clone succeeds
- Docker Compose file exists
- backend Dockerfile exists
- deploy `.env` file exists when required by Compose/env-file configuration

Optional future preflight:

- database backup before deploy via `--backup-db`

## File sync rules

When syncing fresh source into `/opt/fuoverflow`, the script must preserve:

- server `.env`
- mounted runtime data
- other clearly server-owned runtime artifacts

At minimum, rsync exclusions should cover:

- `.git`
- `.env`
- `node_modules/`
- `.next/`

The implementation may add more exclusions if the current server layout requires them.

## Health verification

For backend-only deploys, the script should verify:

- backend container starts successfully
- `GET http://localhost:18080/actuator/health` returns success, or the equivalent configured published backend port

The health-check target should be derived from the current compose/env setup rather than assumed blindly when feasible.

## Logging and state

The script should record enough deploy state to support investigation and manual rollback.

Recommended recorded state:

- previous deployed commit SHA
- new deployed commit SHA
- deploy timestamp
- target mode (`backend` or `full`)

Suggested storage:

- `deploy/.last-deploy`

## Rollback strategy

V1 should support manual rollback, not auto-rollback.

### Rationale

Automatic rollback is unsafe in a Flyway-managed system because database state may already have advanced. Reverting containers or source automatically could create an application/schema mismatch.

### Expected operator support

On failure, the script should print:

- which phase failed
- the exact docker logs command to inspect
- the last deployed commit SHA
- the recommended redeploy path for a known-good commit

## Files to create

- `deploy/deploy.sh` — single entrypoint deploy script
- `deploy/README.md` — operator usage and rollback notes
- `deploy/.last-deploy` — state file written by the script after successful deploys

## Expected operator UX

### Default usage

```bash
cd /opt/fuoverflow
./deploy/deploy.sh
```

### Example failure output

```text
FAIL: backend/app/src/main/resources/db/migration/V17__rbac_permissions.sql was modified.
Existing Flyway migrations are immutable.
Create a new migration version (for example V30__...) instead of editing V17.
```

### Example success output

```text
Deploying commit 7a0a6b0 to /opt/fuoverflow
Building backend with --no-cache
Waiting for backend health at http://localhost:18080/actuator/health
SUCCESS: backend healthy, commit 7a0a6b0 deployed
```

## Success criteria

The finished deploy script is successful if it:

- deploys backend with a single command
- always clones fresh source before deploying
- preserves server-specific environment and runtime data
- uses Docker Compose for build/start
- blocks deploy when an existing Flyway migration file was modified
- allows deploy when only new higher-version migrations were added
- verifies backend health before reporting success
- prints actionable diagnostics on failure
- avoids hidden auto-repair or auto-rollback behavior
