# Self-hosting: Shared Hosting (PHP + MySQL)

`php-hosting/` is a flat, no-framework PHP backend built for shared hosting (IONOS, Plesk, any
cPanel host) — no Composer, no Docker, no shell access required. The entire directory (API +
static dashboard) uploads as one unit to your web root.

## Prerequisites

- PHP 8.2+ with the `pdo_mysql`, `json`, `mbstring`, and `ctype` extensions
- MySQL 8.0+ (or MariaDB 10.11+)
- Apache with `mod_rewrite` (or Nginx with equivalent rewrite rules)

## Installation Steps

### 1. Configure

Copy `php-hosting/config.example.php` to `php-hosting/config.php` and fill in your values —
these are plain PHP `define()` constants, not a `.env` file:

```php
define('DB_HOST', 'localhost');
define('DB_NAME', 'quietmetrix');
define('DB_USER', 'your_db_user');
define('DB_PASS', 'your_db_password');

define('ADMIN_EMAIL', 'admin@example.com');
define('ADMIN_PASSWORD', 'change_me');   // creates the first admin user automatically

define('JWT_SECRET', 'change_me_to_a_long_random_string_at_least_32_chars');

define('DEBUG', false);   // always false in production
```

Generate `JWT_SECRET` with `openssl rand -hex 32`. See `php-hosting/config.example.php` for
the full, commented list of settings (rate limiting, CORS, invitations, etc.).

### 2. Upload

Upload the entire contents of `php-hosting/` to your web root (FTP/SFTP/File Manager — no
build step, no `vendor/` directory to generate). `config.php`, `schema.sql`, and `*.md` are
denied direct web access by the bundled `.htaccess`.

### 3. First request creates the schema

Visit `https://yourdomain.com/api/v1/health` in a browser. The first request applies
`schema.sql` and creates the admin user from `config.php` automatically — there is no setup
wizard to visit and no migration command to run.

### 4. Sign in

Visit `https://yourdomain.com/dashboard/` and sign in with `ADMIN_EMAIL` / `ADMIN_PASSWORD`
from `config.php`, then create a project to get an API key.

## File Structure

```
php-hosting/
├── index.php            ← front controller (web root)
├── .htaccess             ← rewrites + denies config.php/schema.sql/*.md
├── config.php             ← your local config (gitignored, not web-accessible)
├── schema.sql             ← applied automatically on first request
├── src/
│   ├── bootstrap.php      ← first-run schema + admin auto-create, in-place upgrades
│   └── routes/
└── dashboard/             ← static HTML+JS+CSS dashboard, no build step
```

## Security Notes

- `config.php`, `schema.sql`, and `*.md` are blocked from direct web access by `.htaccess` —
  verify this holds on your host (`curl https://yourdomain.com/config.php` should 403/404, not
  return PHP source).
- API keys are stored as bcrypt hashes in the database.
- Always use HTTPS.
