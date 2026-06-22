# Guarded deploy script

## Usage

```bash
cd /opt/fuoverflow
./deploy/deploy.sh
./deploy/deploy.sh --full
./deploy/deploy.sh --skip-build-cache-prune
./deploy/deploy.sh --backup-db
```

## Safety guard

The script refuses to deploy if any already-existing file under `backend/app/src/main/resources/db/migration/V*.sql` was modified. Add a new migration instead of editing an old one.

## What the script does

1. Clone fresh source to a temporary directory.
2. Fail if an existing Flyway migration changed.
3. Sync source into `/opt/fuoverflow`.
4. Rebuild backend with Docker Compose using `--no-cache`.
5. Start backend and wait for `/actuator/health`.
6. Record deploy state in `deploy/.last-deploy`.

## Rollback

V1 does not auto-rollback. If deploy fails, inspect Docker logs and redeploy a known-good commit intentionally.

## Troubleshooting

If deploy fails, re-run:

```bash
docker compose logs -f backend
cat deploy/.last-deploy
```

If the error mentions a modified existing Flyway migration, do not edit that migration. Add a new `VNN__...sql` file instead.
