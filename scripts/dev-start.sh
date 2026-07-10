#!/usr/bin/env bash
set -euo pipefail

# QuietMetrix dev launcher
# Builds the dashboard, installs it into the Ktor server resources, kills any
# process using port 8080, starts the Ktor server, and opens the browser.
#
# Usage:
#   ./scripts/dev-start.sh
#   ./scripts/dev-start.sh --no-browser    # skip auto-open

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="$ROOT_DIR/.dev-ktor.pid"
OPEN_BROWSER=true

for arg in "$@"; do
  case "$arg" in
    --no-browser) OPEN_BROWSER=false ;;
  esac
done

# ── Kill anything on port 8080 ──────────────────────────────────────────────
kill_port() {
  local port=$1
  local pids
  if command -v lsof >/dev/null 2>&1; then
    pids=$(lsof -ti tcp:"$port" 2>/dev/null || true)
  elif command -v ss >/dev/null 2>&1; then
    pids=$(ss -tlnp 2>/dev/null | grep ":$port " | awk '{for(i=1;i<=NF;i++) if($i ~ /pid=/) print $i}' | sed 's/.*pid=//' | sed 's/,.*//' | sort -u || true)
  elif command -v netstat >/dev/null 2>&1; then
    pids=$(netstat -tlnp 2>/dev/null | grep ":$port " | awk '{print $NF}' | sed 's|/.*||' | grep -E '^[0-9]+$' | sort -u || true)
  else
    echo "[dev] Warning: cannot detect process on port $port (install lsof, ss, or netstat)"
    return
  fi

  if [[ -n "${pids:-}" ]]; then
    echo "[dev] Killing process(es) using port $port: $pids"
    echo "$pids" | xargs kill -9 2>/dev/null || true
    sleep 1
  fi
}

# Kill existing Ktor from PID file first, then anything on 8080
if [[ -f "$PID_FILE" ]]; then
  OLD_PID=$(cat "$PID_FILE" 2>/dev/null || true)
  if [[ -n "${OLD_PID:-}" ]] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "[dev] Killing previous Ktor process ($OLD_PID)…"
    kill "$OLD_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$OLD_PID" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
fi

kill_port 8080

# ── Build & install dashboard ───────────────────────────────────────────────
echo "[dev] Building dashboard…"
"$ROOT_DIR/gradlew" :dashboard:installDashboard --quiet --no-daemon

# ── Start Ktor server in background ─────────────────────────────────────────
echo "[dev] Starting Ktor server…"

LOG_FILE="$ROOT_DIR/.dev-ktor.log"
rm -f "$LOG_FILE"
(
  cd "$ROOT_DIR"
  ./gradlew :servers:ktor:run --quiet --no-daemon > "$LOG_FILE" 2>&1 &
  echo $! > "$PID_FILE"
)

NEW_PID=$(cat "$PID_FILE")
echo "[dev] Ktor started (PID $NEW_PID). Logs: $LOG_FILE"

# ── Wait for server health check ────────────────────────────────────────────
echo "[dev] Waiting for server on http://localhost:8080 …"
for i in {1..60}; do
  if curl -sf http://localhost:8080/api/v1/health >/dev/null 2>&1; then
    echo "[dev] Server ready!"
    break
  fi
  sleep 1
done

# ── Open browser ────────────────────────────────────────────────────────────
if [[ "$OPEN_BROWSER" == true ]]; then
  echo "[dev] Opening http://localhost:8080/dashboard/ …"
  open "http://localhost:8080/dashboard/" 2>/dev/null || true
fi

# ── Keep script alive until Ctrl+C, then clean up ───────────────────────────
echo "[dev] Press Ctrl+C to stop the server."
trap 'echo "[dev] Stopping Ktor (PID $NEW_PID)…"; kill "$NEW_PID" 2>/dev/null || true; rm -f "$PID_FILE"; exit 0' INT TERM EXIT

tail -f "$LOG_FILE" &
TAIL_PID=$!
trap 'echo "[dev] Stopping Ktor (PID $NEW_PID)…"; kill "$NEW_PID" 2>/dev/null || true; kill "$TAIL_PID" 2>/dev/null || true; rm -f "$PID_FILE"; exit 0' INT TERM EXIT

wait "$NEW_PID" 2>/dev/null || true
