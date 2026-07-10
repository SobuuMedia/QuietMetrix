#!/usr/bin/env bash
set -euo pipefail

# QuietMetrix repo-hygiene check.
# Fails if any git-tracked file contains private infrastructure hostnames,
# internal domains, or leaked credentials. Run before publishing / in CI.
#
# Usage: ./scripts/check-repo-hygiene.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

# This file legitimately names the forbidden patterns, so exclude it from the scan.
SELF="scripts/check-repo-hygiene.sh"

# Forbidden patterns (extended regex). Keep these narrow enough to avoid false
# positives on placeholder examples like db<xxxxx>.hosting or example.com.
PATTERNS=(
  '[a-z0-9]+\.getsobuu\.com'       # private production hostnames (e.g. quietmetrix.getsobuu.com)
  '[a-z0-9]+\.hosting-data\.io'    # real IONOS DB hostnames
  '\bdbs[0-9]{6,}\b'               # IONOS database names
  '\bdbu[0-9]{6,}\b'               # IONOS database users
  'info@getsobuu\.com'             # real admin email
)

fail=0
tracked="$(git ls-files)"

for pat in "${PATTERNS[@]}"; do
  # -I skips binary files; filter out this script from the file list.
  hits="$(printf '%s\n' "$tracked" | grep -v "^${SELF}$" \
    | tr '\n' '\0' \
    | xargs -0 grep -InE "$pat" 2>/dev/null || true)"
  if [[ -n "$hits" ]]; then
    echo "✗ Forbidden pattern found: /$pat/"
    echo "$hits"
    echo
    fail=1
  fi
done

if [[ "$fail" -ne 0 ]]; then
  echo "Repo-hygiene check FAILED — scrub the matches above before publishing."
  exit 1
fi

echo "✓ Repo-hygiene check passed — no private infra strings in tracked files."
