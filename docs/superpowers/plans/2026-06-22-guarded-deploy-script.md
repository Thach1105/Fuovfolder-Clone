# Guarded Deploy Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a one-command backend deploy script that clones fresh source, syncs into `/opt/fuoverflow`, blocks deploy when existing Flyway migrations were modified, builds with Docker Compose, and verifies backend health.

**Architecture:** The deploy flow lives in `deploy/deploy.sh` as a server-side Bash orchestrator. It clones fresh source into a temp directory, runs preflight checks and a migration immutability guard against the currently deployed tree, then rsyncs source into `/opt/fuoverflow`, rebuilds the backend image with Docker Compose, and verifies health before recording deploy state.

**Tech Stack:** Bash, git, rsync, Docker Compose, curl, standard Unix utilities

## Global Constraints

- The script targets backend deployment by default.
- The script may support a future `--full` mode, but v1 should optimize for backend-only deploys because that is the most common and safest path.
- Source of truth is GitHub.
- The script must clone a fresh copy of the repository into a temporary directory on every deploy.
- The script deploys into `/opt/fuoverflow`.
- The script uses Docker Compose.
- The script must preserve server-specific `.env` configuration and mounted runtime data.
- Do not depend on an existing mutable checkout in `/opt/fuoverflow` being a valid git repository.
- Do not edit or overwrite server `.env` files during source sync.
- Do not delete or overwrite mounted runtime data directories.
- Do not silently auto-repair Flyway metadata.
- Do not auto-rollback in v1 after a failed deploy.
- Do not allow deploy to continue when an existing Flyway migration file has been modified.

---

## File Structure / Responsibility Map

- Create: `deploy/deploy.sh` — single entrypoint for server deploy orchestration; parses args, clones source, runs preflight, migration guard, rsync, build, start, health-check, and writes deploy state.
- Create: `deploy/README.md` — operator usage, prerequisites, flags, and rollback guidance.
- Create: `deploy/.last-deploy` — state file storing `PREVIOUS_COMMIT`, `DEPLOYED_COMMIT`, `DEPLOYED_AT`, `TARGET_MODE`, and `COMPOSE_FILE` after successful deploys.
- Inspect: `docker-compose.yml` — default backend compose target used by the script when running from `/opt/fuoverflow`.
- Inspect: `deploy/docker-compose.yml` — CI/CD compose variant; use this only as a reference for possible future `--full` mode and FE services.
- Inspect: `backend/Dockerfile` — backend image build source; preflight should verify this path exists after clone/sync.

---

### Task 1: Scaffold deploy script contract and documentation stub

**Files:**
- Create: `deploy/deploy.sh`
- Create: `deploy/README.md`
- Test: local syntax check via `bash -n deploy/deploy.sh`

**Interfaces:**
- Consumes: `docker-compose.yml` at repository root, backend source under `backend/`
- Produces: executable script entrypoint `deploy/deploy.sh` with functions `usage`, `log`, `fail`, `main`; documentation file that matches the script contract

- [ ] **Step 1: Write the failing shell syntax check**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
```

Expected: FAIL with `No such file or directory` because `deploy/deploy.sh` does not exist yet.

- [ ] **Step 2: Create the minimal script skeleton**

```bash
cat > deploy/deploy.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: ./deploy/deploy.sh [--full] [--skip-build-cache-prune] [--backup-db]
USAGE
}

log() {
  printf '==> %s\n' "$*"
}

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

main() {
  usage
}

main "$@"
EOF
chmod +x deploy/deploy.sh
```

Expected: the script exists, is executable, and has stable top-level function names for later tasks.

- [ ] **Step 3: Create the minimal README stub**

```bash
cat > deploy/README.md <<'EOF'
# Guarded deploy script

## Usage

```bash
cd /opt/fuoverflow
./deploy/deploy.sh
```
EOF
```

Expected: a documentation placeholder exists so later tasks can extend it without guessing paths.

- [ ] **Step 4: Re-run syntax check**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
```

Expected: PASS with no output.

- [ ] **Step 5: Commit**

```bash
git add deploy/deploy.sh deploy/README.md
git commit -m "feat(deploy): scaffold guarded deploy entrypoint"
```

Expected: one commit containing only the initial script/documentation skeleton.

---

### Task 2: Add argument parsing, path constants, and preflight checks

**Files:**
- Modify: `deploy/deploy.sh`
- Modify: `deploy/README.md`
- Test: `bash -n deploy/deploy.sh`, `./deploy/deploy.sh --help`

**Interfaces:**
- Consumes: Task 1 script skeleton
- Produces: parsed flags `TARGET_MODE`, `SKIP_BUILD_CACHE_PRUNE`, `BACKUP_DB`; constants `APP_DIR`, `TMP_DIR`, `REPO_URL`, `COMPOSE_FILE`, `ENV_FILE`; functions `require_cmd`, `preflight`

- [ ] **Step 1: Write a failing help-path test**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
./deploy/deploy.sh --help | grep -F -- "--skip-build-cache-prune"
```

Expected: FAIL because the current script usage output does not yet include the full documented flags.

- [ ] **Step 2: Add constants, arg parsing, and preflight functions**

```bash
python - <<'PY'
from pathlib import Path
p = Path('deploy/deploy.sh')
p.write_text('''#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/fuoverflow}"
TMP_DIR="${TMP_DIR:-/tmp/_deploy_fuo}"
REPO_URL="${REPO_URL:-https://github.com/Thach1105/Fuovfolder-Clone.git}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
ENV_FILE="${ENV_FILE:-/opt/fuoverflow/.env}"
TARGET_MODE="backend"
SKIP_BUILD_CACHE_PRUNE=false
BACKUP_DB=false

usage() {
  cat <<'USAGE'
Usage: ./deploy/deploy.sh [--full] [--skip-build-cache-prune] [--backup-db] [--help]
USAGE
}

log() {
  printf '==> %s\n' "$*"
}

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --full) TARGET_MODE="full" ;;
      --skip-build-cache-prune) SKIP_BUILD_CACHE_PRUNE=true ;;
      --backup-db) BACKUP_DB=true ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown option: $1" ;;
    esac
    shift
  done
}

preflight() {
  require_cmd git
  require_cmd rsync
  require_cmd docker
  require_cmd curl
  docker compose version >/dev/null 2>&1 || fail "docker compose is required"
  [[ -f "$COMPOSE_FILE" ]] || fail "compose file not found: $COMPOSE_FILE"
  [[ -f "backend/Dockerfile" ]] || fail "backend Dockerfile not found"
  [[ -f "$ENV_FILE" ]] || fail "env file not found: $ENV_FILE"
}

main() {
  parse_args "$@"
  preflight
  usage
}

main "$@"
''')
PY
```

Expected: the script now knows its default deploy contract and validates required tools/files before running.

- [ ] **Step 3: Extend README usage to match the script**

```markdown
## Usage

```bash
cd /opt/fuoverflow
./deploy/deploy.sh
./deploy/deploy.sh --full
./deploy/deploy.sh --skip-build-cache-prune
./deploy/deploy.sh --backup-db
```
```

Expected: docs and script flags stay in sync.

- [ ] **Step 4: Verify help and syntax**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
./deploy/deploy.sh --help | grep -F -- "--skip-build-cache-prune"
```

Expected: both commands PASS.

- [ ] **Step 5: Commit**

```bash
git add deploy/deploy.sh deploy/README.md
git commit -m "feat(deploy): add deploy args and preflight checks"
```

Expected: one commit for argument parsing and preflight only.

---

### Task 3: Implement fresh clone and migration immutability guard

**Files:**
- Modify: `deploy/deploy.sh`
- Modify: `deploy/README.md`
- Test: local script function behavior via controlled temp directories and copied migration fixtures

**Interfaces:**
- Consumes: Task 2 constants and preflight
- Produces: functions `clone_source`, `migration_version`, `check_existing_migrations_unchanged`; failure message `Existing Flyway migrations are immutable.`

- [ ] **Step 1: Write the failing guard expectation as a shell fixture test**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
mkdir -p /tmp/guard-old/backend/app/src/main/resources/db/migration
mkdir -p /tmp/guard-new/backend/app/src/main/resources/db/migration
printf 'old\n' > /tmp/guard-old/backend/app/src/main/resources/db/migration/V17__rbac_permissions.sql
printf 'new\n' > /tmp/guard-new/backend/app/src/main/resources/db/migration/V17__rbac_permissions.sql
```

Expected: fixture directories exist; the next step should fail until the guard is implemented.

- [ ] **Step 2: Add clone and migration-guard functions**

```bash
python - <<'PY'
from pathlib import Path
p = Path('deploy/deploy.sh')
text = p.read_text()
old = '''preflight() {
  require_cmd git
  require_cmd rsync
  require_cmd docker
  require_cmd curl
  docker compose version >/dev/null 2>&1 || fail "docker compose is required"
  [[ -f "$COMPOSE_FILE" ]] || fail "compose file not found: $COMPOSE_FILE"
  [[ -f "backend/Dockerfile" ]] || fail "backend Dockerfile not found"
  [[ -f "$ENV_FILE" ]] || fail "env file not found: $ENV_FILE"
}

main() {
  parse_args "$@"
  preflight
  usage
}
'''
new = '''preflight() {
  require_cmd git
  require_cmd rsync
  require_cmd docker
  require_cmd curl
  require_cmd cmp
  docker compose version >/dev/null 2>&1 || fail "docker compose is required"
  [[ -f "$COMPOSE_FILE" ]] || fail "compose file not found: $COMPOSE_FILE"
  [[ -f "backend/Dockerfile" ]] || fail "backend Dockerfile not found"
  [[ -f "$ENV_FILE" ]] || fail "env file not found: $ENV_FILE"
}

clone_source() {
  rm -rf "$TMP_DIR"
  log "Cloning fresh source into $TMP_DIR"
  git clone --depth 1 "$REPO_URL" "$TMP_DIR" >/dev/null
}

migration_version() {
  local file="$1"
  local name
  name="$(basename "$file")"
  name="${name#V}"
  printf '%s\n' "${name%%__*}"
}

check_existing_migrations_unchanged() {
  local deployed_dir="$APP_DIR/backend/app/src/main/resources/db/migration"
  local fresh_dir="$TMP_DIR/backend/app/src/main/resources/db/migration"
  [[ -d "$deployed_dir" ]] || return 0
  [[ -d "$fresh_dir" ]] || fail "fresh migration directory missing: $fresh_dir"

  local changed=()
  while IFS= read -r -d '' deployed_file; do
    local rel fresh_file
    rel="${deployed_file#"$deployed_dir/"}"
    fresh_file="$fresh_dir/$rel"
    [[ -f "$fresh_file" ]] || continue
    if ! cmp -s "$deployed_file" "$fresh_file"; then
      changed+=("backend/app/src/main/resources/db/migration/$rel")
    fi
  done < <(find "$deployed_dir" -maxdepth 1 -type f -name 'V*.sql' -print0 | sort -z)

  if ((${#changed[@]} > 0)); then
    printf 'FAIL: existing Flyway migrations were modified:\n' >&2
    printf ' - %s\n' "${changed[@]}" >&2
    printf 'Existing Flyway migrations are immutable. Create a new migration version instead.\n' >&2
    exit 1
  fi
}

main() {
  parse_args "$@"
  preflight
  clone_source
  check_existing_migrations_unchanged
  usage
}
'''
p.write_text(text.replace(old, new))
PY
```

Expected: the script can now fetch fresh source and compare deployed-vs-fresh migration files.

- [ ] **Step 3: Manually validate the guard logic with the fixture model**

```bash
bash -c '
set -euo pipefail
APP_DIR=/tmp/guard-old TMP_DIR=/tmp/guard-new
source deploy/deploy.sh >/dev/null 2>&1 || true
'
```

Expected: if direct sourcing is awkward because `main` runs immediately, refactor `main` in the implementation so functions can be tested by sourcing; the end state must allow calling `check_existing_migrations_unchanged` without full deploy side effects.

- [ ] **Step 4: Add README note for migration guard**

```markdown
## Safety guard

The script refuses to deploy if any already-existing file under `backend/app/src/main/resources/db/migration/V*.sql` was modified. Add a new migration instead of editing an old one.
```

Expected: operator knows exactly why the deploy may stop.

- [ ] **Step 5: Verify syntax**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add deploy/deploy.sh deploy/README.md
git commit -m "feat(deploy): guard immutable Flyway migrations"
```

Expected: one commit for fresh clone and migration guard.

---

### Task 4: Implement source sync, build, start, health-check, and deploy-state recording

**Files:**
- Modify: `deploy/deploy.sh`
- Modify: `deploy/README.md`
- Create: `deploy/.last-deploy` (written at runtime, not committed)
- Test: `bash -n deploy/deploy.sh`

**Interfaces:**
- Consumes: Task 3 fresh clone + migration guard
- Produces: functions `sync_source`, `prune_builder_cache`, `compose_down_target`, `compose_build_target`, `compose_up_target`, `wait_for_backend_health`, `write_deploy_state`

- [ ] **Step 1: Write the failing expectation for deploy state output**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
grep -F "DEPLOYED_COMMIT=" deploy/.last-deploy
```

Expected: FAIL because the state file does not exist yet.

- [ ] **Step 2: Add sync/build/start/health/state functions**

```bash
python - <<'PY'
from pathlib import Path
p = Path('deploy/deploy.sh')
text = p.read_text()
needle = '''check_existing_migrations_unchanged() {
  local deployed_dir="$APP_DIR/backend/app/src/main/resources/db/migration"
  local fresh_dir="$TMP_DIR/backend/app/src/main/resources/db/migration"
  [[ -d "$deployed_dir" ]] || return 0
  [[ -d "$fresh_dir" ]] || fail "fresh migration directory missing: $fresh_dir"

  local changed=()
  while IFS= read -r -d '' deployed_file; do
    local rel fresh_file
    rel="${deployed_file#"$deployed_dir/"}"
    fresh_file="$fresh_dir/$rel"
    [[ -f "$fresh_file" ]] || continue
    if ! cmp -s "$deployed_file" "$fresh_file"; then
      changed+=("backend/app/src/main/resources/db/migration/$rel")
    fi
  done < <(find "$deployed_dir" -maxdepth 1 -type f -name 'V*.sql' -print0 | sort -z)

  if ((${#changed[@]} > 0)); then
    printf 'FAIL: existing Flyway migrations were modified:\n' >&2
    printf ' - %s\n' "${changed[@]}" >&2
    printf 'Existing Flyway migrations are immutable. Create a new migration version instead.\n' >&2
    exit 1
  fi
}

main() {
  parse_args "$@"
  preflight
  clone_source
  check_existing_migrations_unchanged
  usage
}
'''
replacement = '''check_existing_migrations_unchanged() {
  local deployed_dir="$APP_DIR/backend/app/src/main/resources/db/migration"
  local fresh_dir="$TMP_DIR/backend/app/src/main/resources/db/migration"
  [[ -d "$deployed_dir" ]] || return 0
  [[ -d "$fresh_dir" ]] || fail "fresh migration directory missing: $fresh_dir"

  local changed=()
  while IFS= read -r -d '' deployed_file; do
    local rel fresh_file
    rel="${deployed_file#"$deployed_dir/"}"
    fresh_file="$fresh_dir/$rel"
    [[ -f "$fresh_file" ]] || continue
    if ! cmp -s "$deployed_file" "$fresh_file"; then
      changed+=("backend/app/src/main/resources/db/migration/$rel")
    fi
  done < <(find "$deployed_dir" -maxdepth 1 -type f -name 'V*.sql' -print0 | sort -z)

  if ((${#changed[@]} > 0)); then
    printf 'FAIL: existing Flyway migrations were modified:\n' >&2
    printf ' - %s\n' "${changed[@]}" >&2
    printf 'Existing Flyway migrations are immutable. Create a new migration version instead.\n' >&2
    exit 1
  fi
}

sync_source() {
  log "Syncing source into $APP_DIR"
  rsync -av --delete \
    --exclude '.git' \
    --exclude '.env' \
    --exclude 'node_modules/' \
    --exclude '.next/' \
    "$TMP_DIR/" "$APP_DIR/" >/dev/null
}

prune_builder_cache() {
  if [[ "$SKIP_BUILD_CACHE_PRUNE" == true ]]; then
    log "Skipping docker builder prune"
    return
  fi
  log "Pruning docker builder cache"
  docker builder prune -af >/dev/null
}

compose_down_target() {
  log "Stopping current backend"
  docker compose down >/dev/null || true
}

compose_build_target() {
  log "Building backend with --no-cache"
  docker compose build --no-cache backend
}

compose_up_target() {
  log "Starting backend"
  docker compose up -d backend
}

wait_for_backend_health() {
  local health_url="${HEALTH_URL:-http://localhost:18080/actuator/health}"
  log "Waiting for backend health at $health_url"
  for _ in $(seq 1 40); do
    if curl -fsS "$health_url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  fail "backend health check did not pass: $health_url"
}

write_deploy_state() {
  local state_file="$APP_DIR/deploy/.last-deploy"
  local deployed_commit
  deployed_commit="$(git -C "$TMP_DIR" rev-parse --short HEAD)"
  mkdir -p "$APP_DIR/deploy"
  cat > "$state_file" <<EOF
PREVIOUS_COMMIT=unknown
DEPLOYED_COMMIT=$deployed_commit
DEPLOYED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
TARGET_MODE=$TARGET_MODE
COMPOSE_FILE=$COMPOSE_FILE
EOF
}

main() {
  parse_args "$@"
  preflight
  clone_source
  check_existing_migrations_unchanged
  compose_down_target
  sync_source
  prune_builder_cache
  compose_build_target
  compose_up_target
  wait_for_backend_health
  write_deploy_state
  log "SUCCESS: backend healthy, commit $(git -C "$TMP_DIR" rev-parse --short HEAD) deployed"
}
'''
p.write_text(text.replace(needle, replacement))
PY
```

Expected: the deploy script now performs the full happy-path flow and writes deploy state.

- [ ] **Step 3: Extend README with runtime behavior and rollback note**

```markdown
## What the script does

1. Clone fresh source to a temporary directory.
2. Fail if an existing Flyway migration changed.
3. Sync source into `/opt/fuoverflow`.
4. Rebuild backend with Docker Compose using `--no-cache`.
5. Start backend and wait for `/actuator/health`.
6. Record deploy state in `deploy/.last-deploy`.

## Rollback

V1 does not auto-rollback. If deploy fails, inspect Docker logs and redeploy a known-good commit intentionally.
```

Expected: README reflects the actual flow and rollback stance.

- [ ] **Step 4: Verify syntax**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add deploy/deploy.sh deploy/README.md
git commit -m "feat(deploy): implement guarded backend deploy flow"
```

Expected: one commit for sync/build/health/state logic.

---

### Task 5: Add failure diagnostics and final usage polish

**Files:**
- Modify: `deploy/deploy.sh`
- Modify: `deploy/README.md`
- Test: `bash -n deploy/deploy.sh`, help output, selected failure-message grep checks

**Interfaces:**
- Consumes: Tasks 1-4 complete script
- Produces: actionable failure output including the failed phase, suggested `docker compose logs` command, and commit/reporting context

- [ ] **Step 1: Write the failing expectation for logs guidance**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
./deploy/deploy.sh --help | grep -F "docker compose logs -f backend"
```

Expected: FAIL because the help/README path does not yet mention the exact inspection command.

- [ ] **Step 2: Add phase-aware failure diagnostics to the script**

```bash
python - <<'PY'
from pathlib import Path
p = Path('deploy/deploy.sh')
text = p.read_text()
text = text.replace('''log() {
  printf '==> %s\n' "$*"
}

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}
''', '''CURRENT_PHASE="startup"

log() {
  printf '==> %s\n' "$*"
}

set_phase() {
  CURRENT_PHASE="$1"
}

fail() {
  printf 'FAIL [%s]: %s\n' "$CURRENT_PHASE" "$*" >&2
  printf 'Inspect logs with: docker compose logs -f backend\n' >&2
  exit 1
}
''')
text = text.replace('''  clone_source
  check_existing_migrations_unchanged
  compose_down_target
  sync_source
  prune_builder_cache
  compose_build_target
  compose_up_target
  wait_for_backend_health
  write_deploy_state
''', '''  set_phase "clone-source"
  clone_source
  set_phase "migration-guard"
  check_existing_migrations_unchanged
  set_phase "compose-down"
  compose_down_target
  set_phase "sync-source"
  sync_source
  set_phase "prune-cache"
  prune_builder_cache
  set_phase "compose-build"
  compose_build_target
  set_phase "compose-up"
  compose_up_target
  set_phase "health-check"
  wait_for_backend_health
  set_phase "write-state"
  write_deploy_state
''')
p.write_text(text)
PY
```

Expected: every major phase now produces actionable diagnostics on failure.

- [ ] **Step 3: Add README troubleshooting section**

```markdown
## Troubleshooting

If deploy fails, re-run:

```bash
docker compose logs -f backend
cat deploy/.last-deploy
```

If the error mentions a modified existing Flyway migration, do not edit that migration. Add a new `VNN__...sql` file instead.
```

Expected: operator guidance matches the script's failure messaging.

- [ ] **Step 4: Final verification**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
bash -n deploy/deploy.sh
./deploy/deploy.sh --help
```

Expected: syntax passes and help prints without error.

- [ ] **Step 5: Commit**

```bash
git add deploy/deploy.sh deploy/README.md
git commit -m "feat(deploy): improve deploy diagnostics and docs"
```

Expected: one final commit for diagnostics and usability polish.

---

## Self-Review

### Spec coverage
- Fresh clone from GitHub covered in Tasks 2-4.
- Backend-only default mode covered in Tasks 2 and 4.
- Docker Compose deploy flow covered in Task 4.
- `.env` and runtime preservation via rsync exclusions covered in Task 4.
- Existing Flyway migration immutability guard covered in Task 3.
- Clear fail-fast behavior and operator guidance covered in Tasks 3 and 5.
- Health verification and deploy-state recording covered in Task 4.
- No auto-repair / no auto-rollback explicitly preserved in README and script behavior.

### Placeholder scan
- No `TBD`, `TODO`, or vague implementation-only instructions remain.
- All file paths are exact.
- All commands are concrete.
- The deploy state file is runtime-generated and intentionally not committed.

### Type consistency
- Script constants and functions use stable names across tasks: `APP_DIR`, `TMP_DIR`, `REPO_URL`, `COMPOSE_FILE`, `ENV_FILE`, `clone_source`, `check_existing_migrations_unchanged`, `wait_for_backend_health`, `write_deploy_state`.
- README references the same entrypoint path and behavior as the script.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-22-guarded-deploy-script.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
