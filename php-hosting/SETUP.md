# QuietMetrix — IONOS / shared-hosting install

This is the **drop-in PHP backend** for shared hosting (IONOS, Plesk, cPanel,
SiteGround…). It runs the full QuietMetrix API and bundles a static dashboard
in the same upload — no Composer, no framework, no Docker, no SSH required.

If you want the Docker / Ktor distribution instead, see
`servers/ktor/Dockerfile` at the repo root.

---

## 1. What you get

```
your-domain.com/                  → redirects to /dashboard/
your-domain.com/dashboard/        → the QuietMetrix dashboard (HTML + JS)
your-domain.com/api/v1/track      → the public ingest endpoint (API-key auth)
your-domain.com/api/v1/projects   → admin/dashboard endpoints (JWT auth)
your-domain.com/api/v1/health     → health check
```

---

## 2. Server requirements

- **PHP 8.0 or newer** (8.2+ recommended) with `pdo_mysql` and `json` extensions.
- **MySQL 5.7+** or **MariaDB 10.3+**.
- **Apache** with `mod_rewrite`, **or** Nginx with an equivalent rewrite rule.
- **HTTPS** strongly recommended in production.

IONOS satisfies all of these on the standard "Web Hosting" plans.

---

## 3. Configure your hosting

In the IONOS control panel:

1. **Create the MySQL database** (Hosting → Databases → Create database).
   Note down the host, database name, user, and password — IONOS gives you
   values shaped like `db<XXXXXXXXXX>.hosting-data.io` / `dbs<XXXXXXXX>`.
2. **Pick the domain or subdomain** you want QuietMetrix on (e.g.
   `analytics.yourdomain.com`) and point its document root at the folder you
   are about to upload to (e.g. `/quietmetrix/`).
3. **Set PHP version** to 8.2 or newer for that webspace.

---

## 4. Edit `config.php`

Open `config.php` and fill in:

```php
define('DB_HOST', 'db<XXXXXXXXXX>.hosting-data.io');   // from IONOS panel
define('DB_NAME', 'dbs<XXXXXXXX>');
define('DB_USER', 'dbu<XXXXXXX>');
define('DB_PASS', 'your-database-password');

define('ADMIN_EMAIL',    'you@yourdomain.com');
define('ADMIN_PASSWORD', 'pick-a-strong-one');     // used to create the first admin user

// Generate with: openssl rand -hex 32
define('JWT_SECRET', 'paste-a-long-random-string-here');

// '*' is fine because dashboard + API are same-origin.
// Lock it down only if you serve the dashboard from a different host.
define('ALLOWED_ORIGIN', '*');

define('DEBUG', false);    // KEEP false in production
```

> The server **refuses to start** if `JWT_SECRET` or `ADMIN_PASSWORD` are still
> at their placeholder values — this is intentional, to stop accidental
> deploys with weak secrets.

---

## 5. Build the dashboard

The dashboard is the Compose Multiplatform wasmJs bundle in the top-level
`dashboard/` module. It is **not** committed — you build it once before each
deploy:

```bash
# from the repo root
./gradlew :dashboard:installDashboard
```

This compiles the production wasmJs bundle and copies it into
`php-hosting/dashboard/` (and into the Ktor server's resources, see the
top-level README for the Docker path). The same artifact serves both backends.

After the task finishes you'll see in `php-hosting/dashboard/`:

```
dashboard/
├── index.html
├── dashboard.js
├── dashboard.js.map
├── *.wasm                ← Compose runtime + your code
├── composeResources/     ← localized strings (en, es, …)
└── ...
```

## 6. Upload

Upload the **entire `php-hosting/` folder contents** (not the folder itself) to
your IONOS webspace. The destination should look like:

```
/clickandbuilds-or-your-doc-root/
├── index.php
├── .htaccess
├── config.php
├── schema.sql
├── src/...
└── dashboard/         ← the built wasmJs bundle from step 5
    ├── index.html
    ├── dashboard.js
    └── *.wasm
```

Use the IONOS File Manager, FTP/SFTP, or `rsync`:

```bash
rsync -avz --delete --exclude=.git php-hosting/ \
    user@access.your-host.io:/path/to/your/webspace/
```

The included `.htaccess` already:

- routes any non-file path to `index.php`,
- preserves the `Authorization` header (IONOS Apache strips it on FastCGI),
- blocks direct web access to `config.php`, `*.sql`, and `*.md`.

---

## 7. First request — automatic install

Open `https://your-domain.com/api/v1/health` in a browser.

On that very first request the backend will:

1. Detect that the database is empty and execute `schema.sql`.
2. Detect that no user exists with `ADMIN_EMAIL` and create one with the
   `ADMIN_PASSWORD` you configured.

If the response is `{"ok": true, "version": "0.2.0", "db": "connected"}`
you're done.

If you see `{"error": "misconfigured", ...}` you still have a placeholder
secret in `config.php`. If you see `db: "disconnected"`, recheck the four
`DB_*` constants.

---

## 8. Sign in to the dashboard

Visit `https://your-domain.com/dashboard/` (or just the bare domain — `/`
redirects to it). Sign in with the `ADMIN_EMAIL` / `ADMIN_PASSWORD` from
`config.php`.

Once signed in:

- **Projects** → create your first project. The API key is
  shown **once** — copy it immediately.
- **Overview / Events** → will be empty until you start sending events.

---

## 9. Send your first event

Plain HTTP works without an SDK:

```bash
curl -X POST https://your-domain.com/api/v1/track \
  -H 'Content-Type: application/json' \
  -H 'X-QM-Api-Key: <paste-the-api-key>' \
  -d '{
    "event": "signup",
    "screen": "home",
    "ts": "2026-05-01T12:00:00Z",
    "props": { "plan": "free" },
    "sdk": { "platform": "curl", "version": "0" }
  }'
```

Expected response: `202 { "ok": true, "queued": 1 }`. Refresh the dashboard
Overview page and you'll see it.

To send events from one of the official SDKs, see `docs/sdk/*.md` at the repo
root.

### About the Device and Duration columns

Two Events-table columns depend on how events are captured:

- **Device** is derived server-side. If the event carries a browser user-agent
  (`ctx.ua`) it is classified as `mobile` / `tablet` / `desktop` / `bot`;
  otherwise it falls back to the SDK platform (`android`/`ios` → `mobile`,
  `macos`/`windows`/`linux`/`jvm` → `desktop`). Unrecognised sources show as
  empty. No action is needed — it populates automatically once events arrive.
- **Duration** ("time on screen") is populated **only when the app reports
  screens**. The SDK measures dwell time and attaches `duration_ms` to
  `screen_view` events (and to events fired while a screen is active) — but only
  if your app calls **`trackScreen(...)`**. Apps that never call `trackScreen`
  will show an empty Duration column for every row. This is expected: instrument
  your screens to see time-on-screen data.

---

## 10. Demo mode (debug-only)

Demo mode lets you preview the dashboard with synthetic data — useful for
screenshots, demos, and styling without writing fake events into your real
database.

1. In `config.php`, set `define('DEBUG', true);`.
2. Reload the dashboard, open **Settings**, toggle **Use demo data in this
   dashboard** on.

The Overview and Events pages now render synthetic data and a yellow `Demo
data` badge appears next to the project picker so it's never confused with
real numbers.

When you redeploy with `DEBUG=false`:

- the toggle disappears from Settings,
- any previously-set localStorage flag is force-cleared on next load,
- the `/api/v1/_demo/*` endpoints are not mounted at all (404).

> Production deployments should always run with `DEBUG=false`.

---

## 11. Updating

To deploy a new version, re-upload the changed files (re-upload everything if
in doubt). `schema.sql` is idempotent — re-running it is safe. Future schema
migrations will ship as additional `migrations/NNN_*.sql` files; the worker
will apply them in order on first request after the upload.

> **Upgrading an existing database:** the installer now auto-adds new columns to
> already-installed tables on the first request after you upload (see
> `addColumnIfMissing` in `src/bootstrap.php`) — including `events.duration_ms`
> (time-on-screen) and `events.device_class`. No manual SQL is needed; just
> re-upload `src/` and load any page once. If you prefer to apply them by hand:
>
> ```sql
> ALTER TABLE events ADD COLUMN device_class VARCHAR(20) NULL;
> ALTER TABLE events ADD COLUMN duration_ms  BIGINT      NULL;
> ```

---

## 12. Backups

Recommended IONOS backup approach:

```bash
mysqldump -h db<XXXXXXXXXX>.hosting-data.io -u dbu<XXXXXXX> -p dbs<XXXXXXXX> \
    | gzip > qm-backup-$(date +%F).sql.gz
```

Schedule via the IONOS cron panel or your local machine.

---

## 13. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `misconfigured` JSON on every request | `JWT_SECRET` or `ADMIN_PASSWORD` still placeholder | Edit `config.php` |
| Dashboard login: `Invalid credentials` | Wrong admin password, or you changed `ADMIN_EMAIL` after first run | Reset via `UPDATE users` in phpMyAdmin |
| Dashboard login: `Missing or invalid Authorization header` | `.htaccess` not honored, or `mod_rewrite` off | Enable `mod_rewrite` in IONOS panel |
| `db: disconnected` on `/api/v1/health` | `DB_HOST/USER/NAME/PASS` wrong or DB unreachable | Recheck IONOS database panel |
| 404 on every URL except `/index.php` | `mod_rewrite` not active, or `.htaccess` missing | Make sure `.htaccess` was uploaded and rewrites are allowed |
