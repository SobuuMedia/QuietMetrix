#!/usr/bin/env bash
set -euo pipefail

# QuietMetrix dev stopper
# Kills the background Ktor process started by dev-start.sh.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="$ROOT_DIR/.dev-ktor.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "[dev] No PID file found. Is the server running?"
  exit 1
fi

PID=$(cat "$PID_FILE" 2>/dev/null || true)
if [[ -z "${PID:-}" ]]; then
  echo "[dev] PID file is empty."
  rm -f "$PID_FILE"
  exit 1
fi

if kill -0 "$PID" 2>/dev/null; then
  echo "[dev] Stopping Ktor (PID $PID)…"
  kill "$PID" 2>/dev/null || true
  sleep 1
  # Force kill if still running
  if kill -0 "$PID" 2>/dev/null; then
    kill -9 "$PID" 2>/dev/null || true
  fi
  echo "[dev] Stopped."
else
  echo "[dev] Process $PID is not running."
fi

rm -f "$PID_FILE"
