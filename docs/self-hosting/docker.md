# Self-hosting: Docker (Ktor + PostgreSQL)

## Prerequisites

- Docker and Docker Compose v2+
- A domain name pointing to your server (optional, for TLS)

## Quick Start

```bash
git clone https://github.com/SobuuMedia/QuietMetrix.git
cd QuietMetrix
cp docker/.env.example .env      # set QM_DB_PASSWORD and QM_JWT_SECRET
docker compose up --build
```

This runs three services in order:

- **postgres** — PostgreSQL 16 (healthchecked).
- **migrate** — a one-shot Flyway job that applies `servers/ktor/migrations/`,
  then exits.
- **ktor** — builds the Wasm dashboard and the fat jar, then serves the API and
  dashboard on port 8080.

After startup:

- API: <http://localhost:8080/api/v1/health>
- Dashboard: <http://localhost:8080/dashboard/>

## Environment Variables

Set these in the repo-root `.env` file (copied from `docker/.env.example`):

| Variable | Default | Description |
|----------|---------|-------------|
| `QM_DB_PASSWORD` | *(required)* | Postgres password (shared by all services) |
| `QM_JWT_SECRET` | *(required)* | JWT signing secret — `openssl rand -hex 32` |
| `QM_CORS_ALLOWED_ORIGINS` | `http://localhost:8080` | Comma-separated browser origins allowed to call the API |

## First-time Setup

1. Create an admin user (bcrypt-hashed) with a direct DB insert — see the
   [main README](../../README.md#first-time-setup-admin--projects).
2. Log in at `/dashboard/` and create a project.
3. Copy the project's API key into your SDK configuration.

## TLS / reverse proxy

For production HTTPS, front the Ktor service with a reverse proxy such as Caddy
(see `docker/caddy/Caddyfile`) terminating TLS and forwarding to `ktor:8080`.

## Backups

```bash
docker compose exec postgres pg_dump -U quietmetrix quietmetrix > backup.sql
```

## Upgrading

```bash
git pull
docker compose up --build -d
# The migrate service applies any new Flyway migrations before Ktor restarts.
```
