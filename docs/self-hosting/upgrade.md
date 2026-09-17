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

`php-hosting/` has no Composer, no git-based deploy, and no separate migration command — you
upgrade by re-uploading files, and the schema catches up on the next request.

```bash
# 1. Back up the database
mysqldump -u your_db_user -p quietmetrix > backup_$(date +%Y%m%d).sql

# 2. Back up config.php (it is not in version control)
cp config.php config.php.backup

# 3. Re-upload the changed files from php-hosting/ (re-upload everything if in doubt)
#    — via FTP/SFTP/File Manager, same as the initial install

# 4. Load any page once — schema.sql is idempotent, and bootstrap.php's
#    addColumnIfMissing/createTableIfMissing apply any new columns or tables
#    to the existing database automatically. No migration command to run.

# 5. Verify
curl -s https://yourdomain.com/api/v1/health | jq .
```

If you'd rather apply a specific new column by hand instead of loading a page, `SETUP.md`
documents the equivalent `ALTER TABLE` statements for recent additions.

### This repo's own production instance

`quietmetrix.getsobuu.com` is deployed via `.github/workflows/deploy-php-hosting.yml`
(`workflow_dispatch` only — it never runs on push, since it's a real production cutover, not a
routine CI step). Before running it:

1. Back up: `mysqldump -u <user> -p quietmetrix > backup_$(date +%Y%m%d).sql` against the
   production database, and copy the live `config.php` off the host.
2. Set the one-time repo secrets it needs (`QM_PROD_FTP_HOST`/`_USER`/`_PASS`/`_REMOTE_PATH`,
   see the workflow file's header comment for what each one is).
3. Run the workflow from the Actions tab (or `gh workflow run deploy-php-hosting.yml`). It
   mirrors `php-hosting/` over SFTP (excluding `config.php` and `tests/`) and then verifies the
   health endpoint plus that `/api/v1/track` is retired and `/api/v1/counters` is live.

## Version Compatibility

| QuietMetrix Version | Min. Postgres | Min. MySQL | Notes |
|--------------------|---------------|-------------|-------|
| 0.1.x | 14 | 8.0 | Initial release |
| 0.2.x | 16 | 8.0 | Adds batch tracking, consent endpoints, session enrichment |

## Breaking Changes

Breaking changes are announced in [GitHub Releases](https://github.com/sobuumedia/quietmetrix/releases). Migration guides are included in release notes for any version that requires manual intervention beyond automatic migrations.