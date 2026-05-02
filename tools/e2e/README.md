# E2E Contract Tests

End-to-end contract tests that spin up each backend (Ktor, PHP) via Docker Compose,
run the Postman collection against them, then tear everything down.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) (running)
- [newman](https://www.npmjs.com/package/newman) — install with `npm install -g newman`

## Running

**Via Gradle** (from the project root):

```
./gradlew e2eTest
```

**Via bash directly** (from `tools/e2e/`):

```
bash run-e2e.sh
```

## How it works

1. Starts the Ktor stack (`docker-compose.ktor.yml`) and waits for `/api/v1/health` to return 200.
2. Runs the Postman collection located at `tools/contract-tests/quietmetrix.postman_collection.json`.
3. Tears down the Ktor stack.
4. Repeats steps 1–3 for the PHP stack (`docker-compose.php.yml`).
5. Prints a summary (pass/fail counts) and exits with a non-zero code if any backend failed.
