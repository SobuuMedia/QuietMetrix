#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COLLECTION="$SCRIPT_DIR/../contract-tests/quietmetrix.postman_collection.json"
command -v newman >/dev/null
: "${QM_E2E_KTOR_URL:?Set a disposable Ktor backend URL}"
: "${QM_E2E_KTOR_API_KEY:?Set its disposable project API key}"
: "${QM_E2E_PHP_URL:?Set a disposable PHP backend URL}"
: "${QM_E2E_PHP_API_KEY:?Set its disposable project API key}"
curl --fail --silent --show-error "$QM_E2E_KTOR_URL/api/v1/ready" >/dev/null
newman run "$COLLECTION" --env-var "base=$QM_E2E_KTOR_URL" --env-var "apiKey=$QM_E2E_KTOR_API_KEY"
curl --fail --silent --show-error "$QM_E2E_PHP_URL/api/v1/health" >/dev/null
newman run "$COLLECTION" --env-var "base=$QM_E2E_PHP_URL" --env-var "apiKey=$QM_E2E_PHP_API_KEY"
