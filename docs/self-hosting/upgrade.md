# Self-hosting: Upgrade

## General Process

1. Back up your database (see [Backup](backup.md))
2. Pull the latest image or code
3. Run database migrations
4. Restart services
5. Verify the health endpoint

## Docker (Ktor + PostgreSQL)

```bash
# 1. Back up
docker compose exec postgres pg_dump -U quietmetrix quietmetrix > backup_$(date +%Y%m%d).sql

# 2. Pull the latest image
docker compose pull

# 3. Restart — Flyway migrations run automatically on startup
docker compose up -d

# 4. Verify
curl -s https://yourhost/api/v1/health | jq .
```

Flyway applies pending migrations automatically when the Ktor server starts. You do not need to run migration scripts manually.

### Rollback

If a migration fails or the new version has issues:

```bash
# 1. Stop the server
docker compose down

# 2. Restore the database
cat backup_YYYYMMDD.sql | docker compose exec -T postgres psql -U quietmetrix quietmetrix

# 3. Pin to the previous version in docker-compose.yml
#    Change the image tag, e.g. quietmetrix:0.1.0 → quietmetrix:0.1.0

# 4. Start the previous version
docker compose up -d
```

## Shared Hosting (PHP + MySQL)

```bash
# 1. Back up the database
mysqldump -u your_db_user -p quietmetrix > backup_$(date +%Y%m%d).sql

# 2. Back up the .env file
cp .env .env.backup

# 3. Pull the latest code
cd /path/above/webroot
git pull origin main
composer install --no-dev

# 4. Run migrations
php bin/qm-migrate.php

# 5. Verify
curl -s https://yourdomain.com/api/v1/health | jq .
```

### Manual Migration

If `qm-migrate.php` is not available, run the SQL files in order:

```bash
for f in migrations/*.sql; do
    mysql -u your_db_user -p quietmetrix < "$f"
done
```

## Version Compatibility

| QuietMetrix Version | Min. Postgres | Min. MySQL | Notes |
|--------------------|---------------|-------------|-------|
| 0.1.x | 14 | 8.0 | Initial release |
| 0.2.x | 16 | 8.0 | Adds batch tracking, consent endpoints, session enrichment |

## Breaking Changes

Breaking changes are announced in [GitHub Releases](https://github.com/sobuumedia/quietmetrix/releases). Migration guides are included in release notes for any version that requires manual intervention beyond automatic migrations.