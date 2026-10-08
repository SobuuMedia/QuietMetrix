# HTTP contract tests

`./gradlew :tools:e2e:e2eTest` (or `bash tools/e2e/run-e2e.sh`) checks both backends
using Newman. Provision disposable Ktor and PHP instances and test projects first;
set `QM_E2E_KTOR_URL`, `QM_E2E_KTOR_API_KEY`, `QM_E2E_PHP_URL`, and
`QM_E2E_PHP_API_KEY`. Never point these write tests at customer projects.

The runner fails when prerequisites, readiness, or assertions fail. It does not destroy
Docker volumes or silently skip a missing backend. The collection uses current aggregate
schema-2 ingestion and verifies stable receipt replay.

Build a current Ktor image, then run
`QM_PROOF_IMAGE=quietmetrix-ktor python3 tools/e2e/production-deployment-proof.py`.
This creates a randomly named, disposable production Compose stack and checks a locally
trusted HTTPS certificate, private backend port, bounded database-outage readiness,
recovery, and a quiesced backup/restore checksum comparison. It removes its own stack
and volumes. It does not prove public-domain ACME issuance or configure host trust.
