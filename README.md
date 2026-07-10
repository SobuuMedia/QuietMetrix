# QuietMetrix

Privacy-respecting analytics platform — open source, self-hostable. A Ktor + PostgreSQL server (Docker), a PHP + MySQL server (shared hosting), and a Kotlin Multiplatform SDK for 7 targets.

Everything is MIT-licensed. No vendor lock-in.

---

## Table of Contents

- [What is QuietMetrix?](#what-is-quietmetrix)
- [Quick Starts](#quick-starts)
  - [SDK (5 minutes)](#sdk-5-minutes)
  - [Docker backend (5 minutes)](#docker-backend-5-minutes)
  - [PHP backend, Docker (10 minutes)](#php-backend-docker-10-minutes)
  - [PHP backend, IONOS / shared hosting (10 minutes)](#php-backend-ionos--shared-hosting-10-minutes)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Backend Setup — Detailed](#backend-setup--detailed)
  - [Ktor + PostgreSQL (Docker)](#ktor--postgresql-docker)
  - [PHP + MySQL (Shared Hosting)](#php--mysql-shared-hosting)
  - [Environment Reference](#environment-reference)
  - [Database Migrations](#database-migrations)
  - [First-time Setup (Admin & Projects)](#first-time-setup-admin--projects)
- [SDK — Detailed](#sdk--detailed)
  - [Installation by Platform](#installation-by-platform)
  - [Configuration](#configuration)
  - [Tracking Events](#tracking-events)
  - [Consent & Privacy Controls](#consent--privacy-controls)
  - [User Identification](#user-identification)
  - [Offline & Queue Management](#offline--queue-management)
  - [Device Context](#device-context)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [Tracking Endpoints](#tracking-endpoints)
  - [Admin Endpoints](#admin-endpoints)
  - [Project Management](#project-management)
  - [Members & Teams](#members--teams)
- [Multi-Project Support](#multi-project-support)
  - [Creating Projects](#creating-projects)
  - [Team Members & Roles](#team-members--roles)
  - [Deleting Projects](#deleting-projects)
- [Development](#development)
  - [Prerequisites](#prerequisites)
  - [Building the SDK](#building-the-sdk)
  - [Running the Ktor Server Locally](#running-the-ktor-server-locally)
  - [Running the PHP Server Locally](#running-the-php-server-locally)
  - [Running Tests](#running-tests)
- [Docker](#docker)
- [Testing](#testing)
  - [Unit Tests](#unit-tests)
  - [Integration Tests](#integration-tests)
  - [Contract Tests](#contract-tests)
  - [Load Tests](#load-tests)
- [CI / CD](#ci--cd)
- [Operations](#operations)
- [License](#license)

---

## What is QuietMetrix?

QuietMetrix is a full-stack analytics platform. It gives you:

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **SDK** | Kotlin Multiplatform | Client library for tracking events |
| **Ktor server** | Kotlin + Ktor 3 + PostgreSQL | Docker-native backend |
| **PHP server** | PHP 8.2+ + MySQL | Shared-hosting compatible backend |

**SDK targets:** Android, iOS, macOS, Windows (MinGW), Linux, Web (Wasm/JS), JVM

Both backends implement the same OpenAPI 3.1 contract (`docs/openapi.yaml`). Any SDK or HTTP client works against either backend — swap backends without changing a single line of SDK code.

**Key design decisions:**
- API keys are bcrypt-hashed in the database — never stored in plaintext
- Events go through an async inbox → processing → rollup pipeline
- Fully self-hosted and unlimited: no quotas, no per-plan caps, no telemetry

---

## Quick Starts

### SDK (5 minutes)

```kotlin
// 1. Add dependency (KMP project, build.gradle.kts)
implementation(project(":quietmetrix-sdk"))
// or from Maven Central when published:
// implementation("com.quietmetrix:quietmetrix-sdk:0.2.0")

// 2. Initialize once at app startup
import com.quietmetrix.analytics.*

QuietMetrix.init(
    QuietMetrixConfig(
        storageKeyPrefix = "myapp_",
        trackingEndpoint = "https://your-server.com/api/v1/track",
        apiKey = "qm_ak_your_api_key_here",
    )
)

// 3. Track events
trackEvent("page_view", screen = "home")
trackEvent("button_click", screen = "settings", props = mapOf("button_id" to "save"))

// 4. Consent (GDPR / cookie law)
setCookieConsent(true)
setAnalyticsEnabled(true)

// 5. Force flush (e.g., before app goes to background)
suspend fun onPause() { QuietMetrix.flush() }

// 6. Identify a user
QuietMetrix.identify("user_123")
```

### Docker backend (5 minutes)

One command brings up Postgres, runs migrations, builds the dashboard, and starts
the Ktor server:

```bash
git clone https://github.com/SobuuMedia/QuietMetrix.git
cd QuietMetrix
cp docker/.env.example .env      # set QM_DB_PASSWORD, QM_JWT_SECRET
docker compose up --build
# API:       http://localhost:8080/api/v1/health  → {"ok":true}
# Dashboard: http://localhost:8080/dashboard/
# Create the admin user via direct DB insert (see detailed setup below),
# then log in and create a project.
```

### PHP backend, IONOS / shared hosting (10 minutes)

The fastest path for IONOS, Plesk, or any cPanel host — zero Composer, zero
SSH required. Drop-in flat layout that bundles a static dashboard.

```bash
# 1. Edit php-hosting/config.php — fill in DB creds, ADMIN_EMAIL/PASSWORD,
#    JWT_SECRET (openssl rand -hex 32). Keep DEBUG=false in production.

# 2. Upload the contents of php-hosting/ to your IONOS webspace
#    (FTP/SFTP/File Manager — whatever you already use).

# 3. Open https://your-domain.com/api/v1/health in a browser.
#    The first request creates the schema and the admin user automatically.
#    Expected: {"ok":true,"version":"0.2.0","db":"connected"}

# 4. Visit https://your-domain.com/dashboard/ → sign in with the
#    ADMIN_EMAIL / ADMIN_PASSWORD from config.php → create a project →
#    copy the API key once (shown only at creation).
```

Full IONOS-specific instructions (panel screenshots, mod_rewrite checks,
demo-mode walkthrough, troubleshooting): [`php-hosting/SETUP.md`](php-hosting/SETUP.md).

### PHP backend, Docker (10 minutes)

The Slim 4 + Composer + Phinx variant in `servers/php/`, used when you want
to run the PHP server in a container or on infrastructure where you control
the runtime.

```bash
# 1. Upload servers/php/ to your web host
#    - public/ → your web root (public_html/)
#    - src/, bin/, migrations/, vendor/ → ABOVE web root

# 2. Install dependencies
cd /path/above/webroot
cp .env.example .env
# Edit .env with your MySQL credentials + JWT secret
composer install --no-dev

# 3. Run Phinx migrations
vendor/bin/phinx migrate

# 4. Create first admin user (direct DB insert — see detailed setup)

# 5. Add cron worker (processes events every minute)
#    * * * * * php /path/to/bin/qm-worker.php

# 6. Login via POST /api/v1/auth/login and create a project
```

## Project Structure

```
QuietMetrix/
├── quietmetrix-sdk/              # KMP SDK (7 targets)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/            # Shared business logic
│       ├── commonTest/            # Shared unit tests
│       ├── androidMain/           # Android-specific (ConnectivityMonitor, etc.)
│       ├── jvmMain/               # JVM desktop
│       ├── iosMain/               # iOS (Darwin)
│       ├── macosMain/             # macOS (Darwin)
│       ├── linuxMain/             # Linux (curl)
│       ├── mingwMain/             # Windows (WinHttp)
│       └── wasmJsMain/            # Web / Wasm
│
├── servers/
│   ├── ktor/                      # Ktor + PostgreSQL server
│   │   ├── build.gradle.kts
│   │   ├── Dockerfile
│   │   ├── migrations/            # Flyway SQL migrations
│   │   └── src/main/kotlin/.../
│   │       ├── Application.kt     # Entry point
│   │       ├── config/            # AppConfig, DiModule (Koin)
│   │       ├── domain/            # Event, Project, User, Plan, ProjectMember
│   │       ├── ingest/            # EventValidator, EventNormalizer, IngestChannel
│   │       ├── persistence/       # Exposed tables + repositories
│   │       ├── plugins/           # CORS, Monitoring, Security, RateLimiting
│   │       ├── ratelimit/         # RateLimiter, QuotaEnforcer (unlimited self-host)
│   │       └── routes/            # Track, Auth, Project, Dashboard
│   │
│   └── php/                       # PHP + MySQL server (Docker / Slim 4 variant)
│       ├── composer.json
│       ├── .env.example
│       ├── public/index.php       # Web root entry
│       ├── bin/qm-worker.php      # Cron job for inbox processing
│       ├── migrations/            # Phinx migrations
│       └── src/
│           ├── Config/
│           ├── Controller/
│           ├── Domain/
│           ├── Http/              # App router, Middleware
│           └── Persistence/       # PDO repositories
│
├── php-hosting/                  # PHP + MySQL server (flat IONOS / shared-host variant)
│   ├── index.php                 # Front controller
│   ├── .htaccess                 # Apache rewrites + Authorization preservation
│   ├── config.example.php        # Template config (commit-safe)
│   ├── config.php                # Local config — gitignored
│   ├── schema.sql                # MySQL schema, applied on first run
│   ├── SETUP.md                  # IONOS step-by-step install guide
│   ├── src/
│   │   ├── helpers.php
│   │   ├── db.php
│   │   ├── jwt.php               # Pure-PHP HS256 JWT
│   │   ├── auth.php              # Bearer + api-key validators
│   │   ├── bootstrap.php         # First-run schema + admin auto-create
│   │   └── routes/               # auth, projects, track, dashboard, demo
│   └── dashboard/                # Static HTML+JS+CSS dashboard (no build step)
│       ├── index.html
│       ├── css/style.css
│       └── js/{api,app}.js
│
├── docker-compose.yml             # One-command stack: Postgres + migrate + Ktor
├── docker/
│   ├── .env.example               # Copy to repo-root .env
│   └── caddy/Caddyfile            # Optional TLS reverse proxy for production
│
├── docs/                          # MkDocs documentation site
│   ├── mkdocs.yml
│   ├── index.md
│   ├── api-reference.md
│   ├── openapi.yaml               # Shared API contract (OpenAPI 3.1)
│   ├── sdk/                       # Platform-specific SDK guides
│   ├── self-hosting/              # Docker, PHP, upgrade, backup
│   └── operations/                # Rate limits, GDPR, security
│
├── samples/
│   ├── android/                   # Android sample app
│   ├── desktop-jvm/               # JVM desktop sample
│   ├── ios/                       # iOS sample (Swift)
│   └── web/index.html             # Web sample
│
├── tools/
│   ├── contract-tests/            # Postman collection (Newman)
│   ├── load/                      # k6 load test script
│   └── e2e/                       # End-to-end test runner
│
├── .github/workflows/             # CI workflows
│   ├── ci.yml                     # Build + test
│   ├── docker.yml                 # Docker image publish
│   ├── publish-sdk.yml            # SDK publish
│   └── docs.yml                   # Docs deploy
│
├── gradle/
│   └── libs.versions.toml         # Version catalog (single source of truth)
├── build.gradle.kts               # Root Gradle config
├── settings.gradle.kts            # Module includes
├── gradle.properties
├── LICENSE
└── README.md
```

---

## Architecture

```
                        HTTPS (X-QM-Api-Key)
┌──────────────────┐ ───────────────────────────► ┌──────────────────────────┐
│   KMP SDK        │                               │  Ktor Server (JVM)       │
│ • Android        │                               │  • POST /api/v1/track    │
│ • iOS            │                               │  • POST /track/batch     │
│ • macOS          │                               │  • JWT auth + rate limit │
│ • Windows        │                               │  • Postgres 16           │
│ • Linux          │ ◄───────────────────────────  │  • Flyway migrations     │
│ • Web (Wasm)     │  202 Accepted {ok, queued}    │  • Serves the dashboard  │
│ • JVM Desktop    │                               └──────────────────────────┘
│                  │
│ • Event Queue    │                                          ▲
│ • Offline buffer │                                          │ same API contract
│ • Consent gate   │                                          ▼
│ • Auto-flush     │                               ┌──────────────────────────┐
│ • Backoff retry  │                               │  PHP Server              │
└──────────────────┘                               │  • Same routes & schemas │
                                                   │  • MySQL 8               │
                                                   │  • Phinx migrations      │
                                                   │  • Cron event processor  │
                                                   └──────────────────────────┘
```

**Event flow:**
1. SDK enqueues event → local `EventQueue` (memory + connectivity check)
2. `FlushManager` fires every `flushIntervalMs` (default 30s) or when connectivity returns
3. Event batch POSTed to `/api/v1/track` or `/api/v1/track/batch`
4. Server validates API key → validates schema → enqueues to `events_inbox`
5. Background worker processes inbox → inserts to `events` table → updates `event_counts_daily`
6. Dashboard queries read from `events`, `event_counts_daily`, `usage_counters`

---

## Backend Setup — Detailed

### Ktor + PostgreSQL (Docker)

#### Requirements
- Docker Engine 24+ and Docker Compose v2+
- 1 GB RAM minimum (Postgres uses ~256 MB, Ktor ~128 MB)
- Ports 80/443 (or custom) for the Caddy reverse proxy

#### Step-by-step

```bash
# Clone
git clone https://github.com/SobuuMedia/QuietMetrix.git
cd QuietMetrix

# Prepare environment variables
cp docker/.env.example .env
# Edit .env and set strong values for QM_DB_PASSWORD and QM_JWT_SECRET

# Start everything: Postgres, Flyway migrations, dashboard build, Ktor server
docker compose up --build

# Check health
curl http://localhost:8080/api/v1/health
# → {"ok":true,"version":"0.2.0"}

# Create admin user (bcrypt hash cost 12)
# Generate hash: python3 -c "import bcrypt; print(bcrypt.hashpw(b'password', bcrypt.gensalt(12)).decode())"
docker compose exec postgres psql -U quietmetrix -d quietmetrix -c \
  "INSERT INTO users (email, password_hash) VALUES ('admin@example.com', '\$2a\$12\$HASHED_VALUE');"

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"your-password"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Create a project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"My First Project"}'
# Response: {"api_key":"qm_ak_abc123...",
#             "message":"Project created. Store it securely — it will not be shown again."}

# Send a test event
curl -X POST http://localhost:8080/api/v1/track \
  -H "Content-Type: application/json" \
  -H "X-QM-Api-Key: qm_ak_abc123..." \
  -d '{"event":"page_view","screen":"home"}'
# → 202 {"ok":true,"queued":1}
```

#### Docker Compose services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `ktor` | Built from `servers/ktor/Dockerfile` | 8080 | Analytics API server |
| `postgres` | `postgres:16-alpine` | 5432 | Database |
| `caddy` | `caddy:2-alpine` | 80, 443 | Reverse proxy + TLS |

#### Production checklist
- [ ] Copy `docker/.env.example` to `docker/.env` and fill in strong passwords
- [ ] Change `QM_JWT_SECRET` to a random 64-character string
- [ ] Set a strong `QM_DB_PASSWORD`
- [ ] Configure `QM_TRUSTED_PROXIES` if running behind a reverse proxy / CDN
- [ ] Configure a real domain in `docker/caddy/Caddyfile` for TLS
- [ ] Set up backups (`docker compose exec postgres pg_dump ...`)
- [ ] Pin image tags in compose (replace `latest` with specific versions)

### PHP + MySQL (Shared Hosting)

#### Requirements
- PHP 8.2 or 8.3 with extensions: `pdo_mysql`, `json`, `mbstring`, `ctype`
- MySQL 8.0+ (or MariaDB 10.11+)
- Apache with `mod_rewrite` (or Nginx with equivalent config)
- Composer (local or server-side)

#### Step-by-step

```bash
# 1. Prepare files locally
cd servers/php
cp .env.example .env
# Edit .env:
#   QM_DB_HOST=localhost
#   QM_DB_NAME=quietmetrix
#   QM_DB_USER=your_db_user
#   QM_DB_PASSWORD=your_db_password
#   QM_JWT_SECRET=<generate a random 64-char string>
composer install --no-dev

# 2. Upload to server
#    public/     → public_html/ or your web root
#    src/        → /home/user/quietmetrix/src/
#    bin/        → /home/user/quietmetrix/bin/
#    migrations/ → /home/user/quietmetrix/migrations/
#    vendor/     → /home/user/quietmetrix/vendor/
#    .env        → /home/user/quietmetrix/.env

# 3. Run Phinx migrations
vendor/bin/phinx migrate

# 4. Create first admin user (insert into MySQL with bcrypt hash)
#    Generate hash: python3 -c "import bcrypt; print(bcrypt.hashpw(b'password', bcrypt.gensalt(12)).decode())"
mysql> INSERT INTO users (email, password_hash) VALUES ('admin@example.com', '$2a$12$HASH');

# 4. Add cron
#    * * * * * php /home/user/quietmetrix/bin/qm-worker.php >> /home/user/quietmetrix/logs/worker.log 2>&1

# 5. Secure the installation
#    - Ensure src/, bin/, migrations/, vendor/ are not web-accessible
#    - Set restrictive file permissions (640 for .env, 755 for directories)
```

#### Security
- The `src/`, `bin/`, `migrations/`, and `vendor/` directories **must not be web-accessible**.
- The `.env` file contains secrets — ensure it's above the web root.
- Use HTTPS. Let's Encrypt with certbot or your hosting panel's SSL.

### Environment Reference

#### Ktor server

| Variable | Default | Description |
|----------|---------|-------------|
| `QM_PROFILE` | `selfhost` | Deployment profile (self-hosted, unlimited) |
| `QM_DB_URL` | `jdbc:postgresql://localhost:5432/quietmetrix` | JDBC URL |
| `QM_DB_USER` | `quietmetrix` | DB username |
| `QM_DB_PASSWORD` | `quietmetrix` | DB password |
| `QM_DB_POOL_SIZE` | `10` | HikariCP connection pool size |
| `QM_CORS_ALLOWED_ORIGINS` | *(required)* | Comma-separated origins allowed to call the API from a browser (e.g. the dashboard origin) |
| `QM_JWT_SECRET` | `change-me...` | HMAC256 signing secret |
| `QM_JWT_ISSUER` | `quietmetrix` | JWT issuer claim |
| `QM_JWT_AUDIENCE` | `quietmetrix-api` | JWT audience claim |
| `QM_SESSION_TTL_HOURS` | `2` | Access token lifetime |
| `QM_TRUSTED_PROXIES` | *(empty)* | Comma-separated trusted proxy IPs for `X-Forwarded-For` |
| `QM_RATE_LIMIT_ENABLED` | `false` (selfhost) / `true` (cloud) | Enable per-project rate limiting |
| `QM_RATE_LIMIT_RPS` | `10` | Request per second limit |
| `QM_RATE_LIMIT_BURST` | `60` | Burst per minute |
| `QM_INGEST_IP_ENABLED` | `true` | Per-IP ingest throttling (abuse defense for the publishable key) |
| `QM_INGEST_IP_RPS` | `5` | Per-IP requests/second on `/track` + `/track/batch` |
| `QM_INGEST_IP_BURST` | `60` | Per-IP burst tokens |
| `QM_INGEST_INSTALL_ENABLED` | `true` | Per-install (`anonymousId`) ingest throttling |
| `QM_INGEST_INSTALL_RPS` | `1` | Per-install requests/second |
| `QM_INGEST_INSTALL_BURST` | `30` | Per-install burst tokens |
| `QM_INGEST_INSTALL_RAMP_EVENTS` | `500` | Auto-revoke an install that sends this many events in the ramp window |
| `QM_INGEST_INSTALL_RAMP_MINUTES` | `10` | Ramp-up window length (minutes) |

#### PHP server

| Variable | Default | Description |
|----------|---------|-------------|
| `QM_PROFILE` | `selfhost` | `selfhost` or `cloud` |
| `QM_DB_HOST` | `127.0.0.1` | MySQL host |
| `QM_DB_PORT` | `3306` | MySQL port |
| `QM_DB_NAME` | `quietmetrix` | Database name |
| `QM_DB_USER` | `quietmetrix` | Database user |
| `QM_DB_PASSWORD` | `quietmetrix` | Database password |
| `QM_JWT_SECRET` | `change-me...` | HMAC256 signing secret |
| `QM_JWT_ISSUER` | `quietmetrix` | JWT issuer |
| `QM_JWT_AUDIENCE` | `quietmetrix-api` | JWT audience |
| `QM_SESSION_TTL_HOURS` | `2` | Access token lifetime |
| `JWT_REFRESH_EXPIRY_DAYS` | `2` | Refresh token lifetime |
| `CSP_CONNECT_SRC` | `'self'` | CSP `connect-src` directive |
| `QM_RATE_LIMIT_ENABLED` | `false` | Enable rate limiting |
| `QM_RATE_LIMIT_RPS` | `10` | Requests per second |
| `QM_RATE_LIMIT_BURST` | `60` | Burst per minute |

### Database Migrations

#### Ktor (Flyway)

Migrations run automatically on Ktor server startup. Files live in `servers/ktor/migrations/`:

```
V1__init.sql           — users, projects, events_inbox, events, event_counts_daily, usage_counters
V2__project_members.sql — project_members table, deleted_at on projects, plan_id on users
```

To run manually:
```bash
cd servers/ktor
./gradlew flywayMigrate
```

#### PHP (Phinx)

Run the Phinx migrations:
```bash
cd servers/php
vendor/bin/phinx migrate
```

### First-time Setup (Admin & Projects)

After starting either backend:

1. **Create admin user** — Insert directly into the `users` table with a bcrypt hash:
   ```sql
   -- Postgres (Ktor)
   INSERT INTO users (email, password_hash) VALUES ('admin@example.com', '$2a$12$...');
   
   -- MySQL (PHP)
   INSERT INTO users (email, password_hash) VALUES ('admin@example.com', '$2a$12$...');
   ```
   Generate a bcrypt hash (cost 12) using:
   ```bash
   python3 -c "import bcrypt; print(bcrypt.hashpw(b'your-password', bcrypt.gensalt(12)).decode())"
   ```

2. **Login** — `POST /api/v1/auth/login` with email/password to get a JWT token

3. **Create a project** — `POST /api/v1/projects` with the JWT token

4. **Save the API key** — The response includes `api_key`. Store it securely — it is shown only once.

5. **Configure the SDK** — Set `apiKey` in `QuietMetrixConfig` to the API key from step 4

---

## SDK — Detailed

### Installation by Platform

#### Android / KMP (Gradle)

```kotlin
// settings.gradle.kts
repositories { mavenCentral() }

// module build.gradle.kts
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.2.0")
}
```

#### iOS (Swift Package / XCFramework)

Add the `QuietMetrix.xcframework` produced by `./gradlew :quietmetrix-sdk:assembleXCFramework` to your Xcode project, or include the KMP shared module directly.

```swift
import QuietMetrix

let config = QuietMetrixConfig(
    storageKeyPrefix: "myapp_",
    trackingEndpoint: "https://your-server.com/api/v1/track",
    apiKey: "qm_ak_..."
)
QuietMetrix.shared.initialize(config: config)
QuietMetrix.shared.trackEvent(event: "page_view", screen: "home")
```

#### JVM Desktop

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.quietmetrix:quietmetrix-sdk:0.2.0")
}
```

```kotlin
// Main.kt
import com.quietmetrix.analytics.*

fun main() {
    QuietMetrix.init(QuietMetrixConfig(
        storageKeyPrefix = "myapp_",
        trackingEndpoint = "https://your-server.com/api/v1/track",
        apiKey = "qm_ak_..."
    ))
    trackEvent("app_start", screen = "main")
}
```

#### Web (Wasm / JS)

```html
<!-- Option A: CDN -->
<script src="https://cdn.quietmetrix.com/quietmetrix.js"></script>

<!-- Option B: Local bundle -->
<script src="quietmetrix.js"></script>

<script>
  QuietMetrix.init({
    storageKeyPrefix: 'myapp_',
    trackingEndpoint: 'https://your-server.com/api/v1/track',
    apiKey: 'qm_ak_...'
  });
  QuietMetrix.trackEvent('page_view', { screen: 'home' });
</script>
```

#### macOS / Linux / Windows (native Kotlin)

Use the Ktor Client engine for each platform. The SDK auto-selects:
- macOS/iOS: Darwin engine
- Linux: Curl engine
- Windows: WinHttp engine

Add the SDK as a Gradle dependency — no platform-specific config needed.

### Configuration

```kotlin
data class QuietMetrixConfig(
    val storageKeyPrefix: String,           // Unique prefix per app (e.g. "myapp_")
    val trackingEndpoint: String? = null,   // Server URL (null = no network sending)
    val apiKey: String? = null,           // Project API key
    val flushIntervalMs: Long = 30_000L,    // Auto-flush interval (ms)
    val maxQueueSize: Int = 1000,           // Max buffered events (oldest dropped when full)
    val autoTrackInitialPageView: Boolean = true, // Fire page_view on init?
    val trackingAllowedByDefault: Boolean = true, // Track before consent?
    val userAgent: String? = null,          // Custom User-Agent header
)
```

### Tracking Events

```kotlin
// Simple event
trackEvent("page_view")

// With screen context
trackEvent("page_view", screen = "home")

// With custom properties
trackEvent("button_click", screen = "settings", props = mapOf(
    "button_id" to "save_btn",
    "section" to "profile",
    "duration_ms" to 150
))

// With session ID (useful for session-based analytics)
// The SDK auto-generates SIDs on web; on other platforms you can provide one
trackEvent("page_view", screen = "home")

// Events are auto-flushed every flushIntervalMs (default 30s)
// and on connectivity restore (from offline to online)
```

### Consent & Privacy Controls

QuietMetrix has three consent layers:

```kotlin
// Layer 1: Cookie consent (GDPR)
setCookieConsent(true)   // User accepted cookies
setCookieConsent(false)  // User declined cookies
val hasConsent = hasCookieConsent()

// Layer 2: Analytics kill switch
setAnalyticsEnabled(true)   // Analytics on
setAnalyticsEnabled(false)  // Analytics off (purges queue, stops all tracking)
val enabled = isAnalyticsEnabled

// Layer 3: Tracking allowed (combines consent + enabled)
val allowed = isTrackingAllowed()
```

**How it works:**
- `trackingAllowedByDefault = true` (default): Tracks until user declines
- `trackingAllowedByDefault = false`: Blocks tracking until user opts in
- `hasCookieConsent() == false && trackingAllowedByDefault == true` → Tracks anyway (implied consent)
- `hasCookieConsent() == false && trackingAllowedByDefault == false` → Blocks
- `isAnalyticsEnabled == false` → Blocks regardless of consent
- All three gates are checked by `Gate.shouldTrack()` before any event is enqueued

### User Identification

```kotlin
// Identify a user (stored locally, sent as uid field in events)
QuietMetrix.identify("user_123")

// Clear identity
QuietMetrix.identify(null)
```

The `uid` is included in the `TrackEventRequest` payload and added to all subsequent events in the flush batch. It persists across app restarts (stored in `InMemoryStore` with the project's `storageKeyPrefix`).

### Offline & Queue Management

The SDK buffers events locally when offline and flushes them when connectivity returns.

```kotlin
// Events are enqueued locally — no network call happens inline
trackEvent("page_view")  // Enqueued, not sent yet

// Force flush all buffered events (suspend function)
QuietMetrix.flush()

// Flush interval is configurable
QuietMetrixConfig(flushIntervalMs = 10_000L)  // Flush every 10 seconds

// Max queue size prevents unbounded memory growth
QuietMetrixConfig(maxQueueSize = 500)  // Keep max 500 events in memory

// When offline, events are buffered with was_offline=true flag
// When connectivity returns, FlushManager triggers an immediate drain
```

**Connectivity monitoring** is platform-specific:
- Android: `ConnectivityManager` API
- iOS/macOS: `NWPathMonitor`
- JVM: Always assumes online
- Web: `navigator.onLine` + `online`/`offline` events
- Linux/Windows: Always assumes online

**Exponential backoff:** After failed flushes (5xx errors), the FlushManager accumulates a backoff delay — `1s → 2s → 4s → 8s → 16s → 32s → 60s (cap)`. Backoff resets on the first successful flush or when connectivity returns.

### Device Context

The SDK auto-detects device context per platform:

| Field | Android | iOS | JVM | Web |
|-------|---------|-----|-----|-----|
| `platform` | `android` | `ios` | `jvm` | `wasmJs` |
| `language` | `Locale.getDefault()` | `Locale.current` | `user.language` | `navigator.language` |
| `screenWidth` / `screenHeight` | DisplayMetrics | UIScreen | null | `window.innerWidth/Height` |
| `userAgent` | null | null | `os.name` | `navigator.userAgent` |

---

## API Reference

The authoritative API spec is `docs/openapi.yaml` (OpenAPI 3.1). Both backends implement it identically.

### Authentication

**API key** — For tracking endpoints. Passed as `X-QM-Api-Key` header. The API key is **publishable**: write-only and project-scoped, so it is safe to embed in client code (browser bundles, mobile apps, F-Droid builds). It cannot read analytics or access admin routes — those require a Bearer token. See [Threat model & abuse defense](docs/security/publishable-api-key.md). Rotate it via `POST /api/v1/projects/{id}/regenerate-key` (Bearer-auth) when a published key is abused.

```
X-QM-Api-Key: qm_ak_abc123def456ghi789
```

**Bearer Token** — For admin/dashboard endpoints. Obtained via `POST /api/v1/auth/login`.

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### Tracking Endpoints

#### `POST /api/v1/track` — Track a single event

Auth: API key

**Request:**
```json
{
  "event": "page_view",
  "screen": "home",
  "props": { "language": "en", "theme": "dark" },
  "sid": "abc123def456",
  "ts": "2026-04-30T12:34:56Z",
  "was_offline": false,
  "uid": "hashed_user_id",
  "sdk": { "platform": "android", "version": "0.2.0" },
  "ctx": {
    "language": "en",
    "ua": "Mozilla/5.0 ...",
    "viewport": "412x914",
    "referrer": "https://google.com"
  }
}
```

**Response:** `202 Accepted`
```json
{ "ok": true, "queued": 1 }
```

Only `event` is required. The server adjusts `ts` if >7 days from server time.

#### `POST /api/v1/track/batch` — Track up to 100 events

Auth: API key

**Request:**
```json
{
  "events": [
    { "event": "page_view", "screen": "home" },
    { "event": "click", "screen": "home", "props": { "target": "logo" } }
  ]
}
```

**Response:** `202 Accepted`
```json
{ "ok": true, "queued": 2 }
```

#### `GET /api/v1/health` — Health check

No auth required.

**Response:** `200 OK`
```json
{ "ok": true, "version": "0.1.0" }
```

### Admin Endpoints

#### `POST /api/v1/auth/login` — Login

**Request:**
```json
{ "email": "admin@example.com", "password": "your-password" }
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": "usr_1",
    "email": "admin@example.com",
    "createdAt": "2026-04-30T12:00:00Z"
  }
}
```

#### `GET /api/v1/projects/:id/events` — Paginated raw events

Auth: Bearer Token

Query params: `limit` (1–1000, default 100), `offset`, `event`, `screen`, `from`, `to` (ISO 8601)

#### `GET /api/v1/projects/:id/aggregates` — Aggregated metrics

Auth: Bearer Token

Query params: `from`, `to` (required, ISO 8601), `granularity` (day, default)

### Project Management

#### `POST /api/v1/projects` — Create project

Auth: Bearer Token

Request: `{ "name": "My App" }` (required, non-blank, max 255 chars)

Response: `201 Created`
```json
{
  "api_key": "qm_ak_abc123...",
  "message": "Project created. Store it securely — it will not be shown again."
}
```

#### `GET /api/v1/projects` — List accessible projects

Auth: Bearer Token

Query params: `limit`, `offset`, `owner_only` (bool)

Returns projects where the authenticated user is owner or member. Keys are masked as `"***"`.

#### `GET /api/v1/projects/:id` — Get single project

Auth: Bearer Token, must be owner or member. Keys are masked.

#### `PATCH /api/v1/projects/:id` — Update project name

Auth: Bearer Token, must be owner or admin

Request: `{ "name": "New Name" }`

#### `DELETE /api/v1/projects/:id` — Soft-delete project

Auth: Bearer Token, must be owner

Response: `{ "deleted": true, "project_id": "proj_42" }`

Events are preserved. Project is hidden from listings.

### Members & Teams

#### `GET /api/v1/projects/:id/members` — List members

Auth: Bearer Token, must be a member

Response:
```json
{
  "members": [
    { "id": "1", "project_id": "proj_42", "user_id": "3", "email": "viewer@example.com", "role": "viewer", "created_at": "..." }
  ],
  "total": 1
}
```

#### `POST /api/v1/projects/:id/members` — Add a member

Auth: Bearer Token, must be owner or admin

Request: `{ "email": "colleague@example.com", "role": "viewer" }`

Role must be `viewer` or `admin` (not `owner`). Returns `201 Created`.

#### `DELETE /api/v1/projects/:id/members/:userId` — Remove a member

Auth: Bearer Token, must be owner or admin. Cannot remove self if last admin.

---

## Multi-Project Support

QuietMetrix is self-hosted and unlimited: create as many projects and ingest as
many events as your database can hold. No plans, no quotas, no billing.

### Creating Projects

Each project has its own API key. You can create multiple projects and use
different API keys for different apps:

```kotlin
// App 1
QuietMetrix.init(QuietMetrixConfig(
    storageKeyPrefix = "app1_",
    apiKey = "qm_ak_app1_key..."
))

// App 2 — same SDK, different key
QuietMetrix.init(QuietMetrixConfig(
    storageKeyPrefix = "app2_",
    apiKey = "qm_ak_app2_key..."
))
```

Each project appears separately in the dashboard with its own events, aggregates, and members.

### Team Members & Roles

| Role | Permissions |
|------|------------|
| **Owner** | Full control. Can delete project, manage members, update settings. Always the original creator. |
| **Admin** | Can invite/remove members (except other admins/owners), update project name/settings. Cannot delete the project. |
| **Viewer** | Read-only. Can view dashboard data but cannot modify anything. |

Only owners and admins can manage members. Viewers can only see data.

### Deleting Projects

Projects are soft-deleted — `deleted_at` is set, events are preserved. The project disappears from listings and API keys stop working. Only the owner can delete a project.

---

## Development

### Prerequisites

- JDK 21 (Amazon Corretto or OpenJDK)
- Gradle 8.11+ (the project includes `gradlew`)
- Android SDK (for SDK Android target compilation) — `ANDROID_HOME` set
- Docker (for running the Ktor backend locally)
- PHP 8.2+ and Composer (for the PHP backend)
- Node.js + Newman (`npm install -g newman`) for contract tests

### Building the SDK

```bash
# Compile all targets
./gradlew :quietmetrix-sdk:compileKotlinJvm
./gradlew :quietmetrix-sdk:compileDebugKotlinAndroid

# Run SDK tests (JVM)
./gradlew :quietmetrix-sdk:jvmTest

# Publish to local Maven (~/.m2)
./gradlew :quietmetrix-sdk:publishToMavenLocal

# Build XCFramework for iOS
./gradlew :quietmetrix-sdk:assembleXCFramework
```

### Running the Ktor Server Locally

```bash
# Start Postgres (or use Docker)
docker run -d --name qm-postgres \
  -e POSTGRES_DB=quietmetrix -e POSTGRES_USER=quietmetrix -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 postgres:16-alpine

# Run the server
export QM_DB_PASSWORD=changeme
export QM_JWT_SECRET=$(openssl rand -hex 32)
export QM_CORS_ALLOWED_ORIGINS=http://localhost:8080
./gradlew :servers:ktor:run

# Or from IntelliJ: run ApplicationKt.main()
```

The server starts on `http://localhost:8080`. Flyway migrations run automatically on first boot.

### Running the PHP Server Locally

```bash
cd servers/php

# Start MySQL (or use Docker)
docker run -d --name qm-mysql \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=quietmetrix \
  -p 3306:3306 mysql:8.0

# Install dependencies
composer install

# Run migrations
vendor/bin/phinx migrate

# Start PHP built-in server
php -S localhost:8081 -t public/

# Run cron worker manually (for testing)
php bin/qm-worker.php
```

### Running Tests

```bash
# All Ktor server tests (JUnit 5 + Kotest)
./gradlew :servers:ktor:test

# All SDK tests (JVM target)
./gradlew :quietmetrix-sdk:jvmTest

# Specific test class
./gradlew :servers:ktor:test --tests "*RouteIntegrationTest*"
./gradlew :servers:ktor:test --tests "*ProjectRoutesIntegrationTest*"

# PHP tests
cd servers/php
vendor/bin/phpunit
```

---

## Docker

### `docker-compose.yml` — the one-command stack

```bash
cp docker/.env.example .env    # set QM_DB_PASSWORD, QM_JWT_SECRET
docker compose up --build
```

Three services run in order: **postgres** (16-alpine, healthchecked) →
**migrate** (Flyway applies `servers/ktor/migrations/`) → **ktor** (builds the
Wasm dashboard and fat jar, then serves the API + dashboard on port 8080).

For production TLS, put the Ktor service behind the reverse proxy in
`docker/caddy/Caddyfile`.

### Building Images

```bash
# Ktor server
docker build -f servers/ktor/Dockerfile -t quietmetrix-ktor:latest .

# PHP server
docker build -f servers/php/Dockerfile -t quietmetrix-php:latest .
```

---

## Testing

### Unit Tests

| Module | Framework | Count | Location |
|--------|-----------|-------|----------|
| SDK | kotlin.test | 20+ | `quietmetrix-sdk/src/commonTest/` |
| Ktor server | Kotest + JUnit 5 | 36 | `servers/ktor/src/test/` |
| PHP server | PHPUnit 11 | 5+ | `servers/php/tests/` |

### Integration Tests

Ktor integration tests use H2 in-memory database and `testApplication`:

- `RouteIntegrationTest` — Health, track, auth endpoints
- `ProjectRoutesIntegrationTest` — CRUD, limits, members

### Contract Tests

Using Newman (Postman CLI):

```bash
cd tools/contract-tests
newman run quietmetrix.postman_collection.json --env-var base_url=http://localhost:8080
```

Run against both backends to verify API contract compliance.

### Load Tests

Using k6:

```bash
cd tools/load
k6 run track.k6.js
```

Simulates 500 RPS against the track endpoint.

### E2E Tests

```bash
./gradlew e2eTest
# or
cd tools/e2e && bash run-e2e.sh
```

Spins up both backends in Docker, runs contract tests, tears down.

---

## CI / CD

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | Actions |
|----------|---------|---------|
| `ci.yml` | Push to main, PRs | SDK JVM tests, Ktor tests, PHP tests |
| `docker.yml` | Tags (`v*`) | Build & push Ktor + PHP Docker images to GHCR |
| `publish-sdk.yml` | Tags (`v*`) | Publish SDK to Maven Local/Central, create GitHub Release |
| `docs.yml` | Push to main | Build and deploy MkDocs site |

---

## Operations

- **Rate limits:** Configurable per-project, per-IP, and per-install throttling. See `docs/operations/rate-limits.md`.
- **GDPR compliance:** Built-in consent gates, data export/delete-friendly design. See `docs/operations/gdpr.md`.
- **Security:** API keys are bcrypt-hashed. JWT secrets must be strong. See `docs/operations/security.md`.
- **Backups:** `pg_dump` for Postgres, `mysqldump` for MySQL. See `docs/self-hosting/backup.md`.
- **Upgrading:** `docker compose pull && up -d` or `git pull && vendor/bin/phinx migrate`. See `docs/self-hosting/upgrade.md`.

---

## License

MIT — see [LICENSE](LICENSE).

All code is MIT-licensed. No feature gating, no closed-source components. You can fork, modify, and redistribute freely. Commercial use is explicitly permitted.

---

**Get started:** `docker compose up --build` → `curl localhost:8080/api/v1/health`
