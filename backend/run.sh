#!/usr/bin/env bash
# FuOverflow backend: clean, build, and run (same steps as manual workflow).
# From backend folder:
#   mvn clean
#   mvn install package
#   cd app && mvn spring-boot:run
#
# Usage:
#   ./run.sh
#   ./run.sh --skip-docker
#   ./run.sh --skip-clean
#   ./run.sh --skip-tests

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR/app"

SKIP_DOCKER=false
SKIP_CLEAN=false
SKIP_TESTS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-docker) SKIP_DOCKER=true ;;
    --skip-clean) SKIP_CLEAN=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    -h|--help)
      echo "Usage: $0 [--skip-docker] [--skip-clean] [--skip-tests]"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
  shift
done

command -v mvn >/dev/null 2>&1 || { echo "mvn not found on PATH" >&2; exit 1; }

echo "==> FuOverflow backend ($SCRIPT_DIR)"

if [[ "$SKIP_DOCKER" == false ]]; then
  command -v docker >/dev/null 2>&1 || { echo "docker not found on PATH" >&2; exit 1; }
  echo "==> docker compose up -d postgres redis minio minio-init"
  (cd "$SCRIPT_DIR" && docker compose up -d postgres redis minio minio-init)
else
  echo "==> Skipping docker compose (--skip-docker)"
fi

cd "$SCRIPT_DIR"

if [[ "$SKIP_CLEAN" == false ]]; then
  echo "==> mvn clean"
  mvn clean
else
  echo "==> Skipping mvn clean (--skip-clean)"
fi

INSTALL_ARGS=(install package)
if [[ "$SKIP_TESTS" == true ]]; then
  INSTALL_ARGS+=(-DskipTests)
fi
echo "==> mvn ${INSTALL_ARGS[*]}"
mvn "${INSTALL_ARGS[@]}"

echo "==> cd app && mvn spring-boot:run"
echo "    Health: http://localhost:8080/actuator/health"
echo "    Press Ctrl+C to stop."
cd "$APP_DIR"
mvn spring-boot:run
