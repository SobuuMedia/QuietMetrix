#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
COLLECTION="$SCRIPT_DIR/../contract-tests/quietmetrix.postman_collection.json"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass_count=0
fail_count=0

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

health_check() {
    local url="$1"
    local label="$2"
    local timeout=30
    local elapsed=0

    log_info "Waiting for $label health check at $url (timeout ${timeout}s)..."
    while [ "$elapsed" -lt "$timeout" ]; do
        if curl -sf -o /dev/null "$url"; then
            log_info "$label is healthy."
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    log_error "$label did not become healthy within ${timeout}s."
    return 1
}

run_contract_tests() {
    local base_url="$1"
    local label="$2"

    log_info "Running contract tests against $label ($base_url)..."
    if newman run "$COLLECTION" --env-var "base_url=$base_url" --suppress-exit-code; then
        log_info "$label contract tests PASSED."
        pass_count=$((pass_count + 1))
    else
        log_error "$label contract tests FAILED."
        fail_count=$((fail_count + 1))
    fi
}

teardown() {
    local file="$1"
    local label="$2"
    log_info "Tearing down $label..."
    docker compose -f "$file" down -v 2>/dev/null || true
    log_info "$label torn down."
}

print_summary() {
    local total=$((pass_count + fail_count))
    echo ""
    echo "======================================"
    echo "         E2E Test Summary"
    echo "======================================"
    echo "  Total backends tested: $total"
    echo "  Passed:                $pass_count"
    echo "  Failed:                $fail_count"
    echo "======================================"
    if [ "$fail_count" -gt 0 ]; then
        log_error "Some tests failed."
        exit 1
    else
        log_info "All tests passed."
    fi
}

# --- Ktor backend ---
log_info "Starting Ktor backend..."
docker compose -f "$ROOT_DIR/docker/docker-compose.ktor.yml" up -d

health_check "http://localhost:8080/api/v1/health" "Ktor" || {
    teardown "$ROOT_DIR/docker/docker-compose.ktor.yml" "Ktor"
    fail_count=$((fail_count + 1))
}
if [ "$fail_count" -eq 0 ]; then
    run_contract_tests "http://localhost:8080" "Ktor"
fi
teardown "$ROOT_DIR/docker/docker-compose.ktor.yml" "Ktor"

# --- PHP backend ---
log_info "Starting PHP backend..."
docker compose -f "$ROOT_DIR/docker/docker-compose.php.yml" up -d

health_check "http://localhost:8080/api/v1/health" "PHP" || {
    teardown "$ROOT_DIR/docker/docker-compose.php.yml" "PHP"
    fail_count=$((fail_count + 1))
}
if [ "$fail_count" -le 1 ]; then
    run_contract_tests "http://localhost:8080" "PHP"
fi
teardown "$ROOT_DIR/docker/docker-compose.php.yml" "PHP"

print_summary
