# Self-hosting: Shared Hosting (PHP + MySQL)

## Prerequisites

- PHP 8.2+ with PDO MySQL extension
- MySQL 8.0+
- Apache with `mod_rewrite` or Nginx
- Composer (on the server or upload `vendor/` from local)

## Installation Steps

### 1. Upload files

Upload the contents of `servers/php/public/` to your web root (e.g., `public_html/`). Upload everything else (`src/`, `bin/`, `migrations/`, `composer.json`) to a directory **above** the web root.

### 2. Install dependencies

```bash
cd /path/above/webroot
composer install --no-dev
```

If your host doesn't have SSH access, run `composer install` locally and upload the `vendor/` directory.

### 3. Configure environment

Copy `.env.example` to `.env` and fill in your database credentials:

```
QM_DB_HOST=localhost
QM_DB_NAME=quietmetrix
QM_DB_USER=your_db_user
QM_DB_PASSWORD=your_db_password
```

### 4. Run the setup wizard

Visit `https://yourdomain.com/setup` — this runs database migrations and creates the first admin user.

### 5. Set up the cron worker

Add to your crontab:

```
* * * * * php /path/to/bin/qm-worker.php
```

This processes the event inbox and builds daily rollups every minute.

## File Structure

```
your-host/
├── public/           ← web root (index.php lives here)
├── src/              ← application code (above webroot)
├── bin/
│   └── qm-worker.php
├── migrations/
├── vendor/
├── .env
└── composer.json
```

## Security Notes

- The `src/`, `bin/`, `migrations/`, and `vendor/` directories must not be accessible via the web.
- API keys are stored as bcrypt hashes in the database.
- Always use HTTPS.