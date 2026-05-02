# Self-hosting: Backup and Recovery

## Docker (Ktor + PostgreSQL)

### Database Backup

```bash
# Single backup
docker compose -f docker/docker-compose.ktor.yml exec postgres pg_dump -U quietmetrix quietmetrix > backup_$(date +%Y%m%d).sql
```

### Automated Daily Backups

Add to your server's crontab:

```bash
0 2 * * * docker compose -f /path/to/quietmetrix/docker/docker-compose.ktor.yml exec -T postgres pg_dump -U quietmetrix quietmetrix | gzip > /backups/qm_$(date +\%Y\%m\%d).sql.gz
```

### Config Backup

The Ktor server is configured entirely via environment variables. Back up your `docker-compose.ktor.yml` and `.env` file:

```bash
cp docker/docker-compose.ktor.yml ~/backups/
cp .env ~/backups/
```

### Database Restore

```bash
# Stop the server to prevent writes during restore
docker compose -f docker/docker-compose.ktor.yml stop ktor

# Drop and recreate the database
docker compose -f docker/docker-compose.ktor.yml exec postgres psql -U quietmetrix -c "DROP DATABASE quietmetrix;"
docker compose -f docker/docker-compose.ktor.yml exec postgres psql -U quietmetrix -c "CREATE DATABASE quietmetrix;"

# Restore from backup
cat backup_YYYYMMDD.sql | docker compose -f docker/docker-compose.ktor.yml exec -T postgres psql -U quietmetrix quietmetrix

# Start the server
docker compose -f docker/docker-compose.ktor.yml start ktor
```

### Point-in-Time Recovery (PITR)

For production deployments, enable PostgreSQL WAL archiving for point-in-time recovery:

```ini
# In postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /backups/wal/%f'
```

Then restore to a specific timestamp:

```bash
# 1. Restore base backup
# 2. Create recovery.signal in the data directory
# 3. Set recovery_target_time in postgresql.conf:
restore_command = 'cp /backups/wal/%f %p'
recovery_target_time = '2026-04-30 12:00:00 UTC'
```

## Shared Hosting (PHP + MySQL)

### Database Backup

```bash
mysqldump -u your_db_user -p quietmetrix > backup_$(date +%Y%m%d).sql
```

### Automated Daily Backups

```bash
0 2 * * * mysqldump -u your_db_user -pyour_db_password quietmetrix | gzip > /backups/qm_$(date +\%Y\%m\%d).sql.gz
```

### Config Backup

```bash
cp .env ~/backups/
cp composer.json ~/backups/
```

### Database Restore

```bash
mysql -u your_db_user -p quietmetrix < backup_YYYYMMDD.sql
```

## Disaster Recovery Checklist

1. **Database**: Restore from the most recent backup (see procedures above)
2. **Config**: Restore `.env` and compose files from your config backup
3. **Verify health**: `curl -s https://yourhost/api/v1/health`
4. **Check event flow**: Send a test event from the SDK and verify it appears in the dashboard
5. **Review logs**: Check application logs for errors

## Backup Retention

| Strategy | Retention | Storage needed (approx.) |
|----------|-----------|--------------------------|
| Daily gzip | 30 days | ~2 GB / million events |
| Weekly full | 12 weeks | ~4 GB / million events |
| Monthly full | 12 months | ~4 GB / million events |

Adjust retention based on your event volume and compliance requirements.