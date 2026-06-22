#!/usr/bin/env bash
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

CURRENT_PHASE="startup"

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
  set_phase "preflight"
  preflight
  set_phase "clone-source"
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
  log "SUCCESS: backend healthy, commit $(git -C "$TMP_DIR" rev-parse --short HEAD) deployed"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
