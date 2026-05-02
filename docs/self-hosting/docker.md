# Self-hosting: Docker (Ktor + PostgreSQL)

## Prerequisites

- Docker and Docker Compose v2+
- A domain name pointing to your server (optional, for TLS)

## Quick Start

```bash
git clone https://github.com/sobuumedia/quietmetrix.git
cd quietmetrix
docker compose -f docker/docker-compose.ktor.yml up -d
```

This starts three containers:
- **ktor** — The Kotlin+Ktor analytics server on port 8080
- **postgres** — PostgreSQL 16 database
- **caddy** — Reverse proxy with automatic HTTPS

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `QM_PROFILE` | `selfhost` | `selfhost` or `cloud` |
| `QM_DB_URL` | `jdbc:postgresql://postgres:5432/quietmetrix` | Database JDBC URL |
| `QM_DB_USER` | `quietmetrix` | Database username |
| `QM_DB_PASSWORD` | `quietmetrix` | Database password |
| `QM_JWT_SECRET` | `change-me-in-production` | JWT signing secret |
| `QM_RATE_LIMIT_ENABLED` | `false` (selfhost) | Enable rate limiting |

## First-time Setup

1. Visit `https://yourhost/setup` to create an admin user and run database migrations.
2. Create a project via the admin API.
3. Copy the API key into your SDK configuration.

## Backups

```bash
docker compose -f docker/docker-compose.ktor.yml exec postgres pg_dump -U quietmetrix quietmetrix > backup.sql
```

## Upgrading

```bash
docker compose -f docker/docker-compose.ktor.yml pull
docker compose -f docker/docker-compose.ktor.yml up -d
# Flyway migrations run automatically on startup
```