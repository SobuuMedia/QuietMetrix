<?php
// ---------------------------------------------------------------------------
// QuietMetrix PHP backend — configuration
// Copy this file to `config.php` and fill in the values for your hosting.
// `config.php` is NOT committed to the repo. Keep it out of version control.
// ---------------------------------------------------------------------------

// MySQL connection
define('DB_HOST',    'localhost');
define('DB_NAME',    'quietmetrix');
define('DB_USER',    'quietmetrix_user');
define('DB_PASS',    'change_me');
define('DB_CHARSET', 'utf8mb4');

// Admin account — credentials for the initial admin user created automatically
// on first run. Has no effect once the user already exists in the database.
define('ADMIN_EMAIL',    'admin@example.com');
define('ADMIN_PASSWORD', 'change_me');

// JWT — secret used to sign dashboard session tokens.
// Generate with: openssl rand -hex 32
define('JWT_SECRET',       'change_me_to_a_long_random_string_at_least_32_chars');
define('JWT_EXPIRY_HOURS', 2);

// Refresh token lifetime (days). Must match the Ktor backend.
define('JWT_REFRESH_EXPIRY_DAYS', 2);

// CORS — the origin allowed to call the API from a browser. When the dashboard
// is hosted on a separate origin, set this to that exact URL. Setting '*' is
// only safe if endpoints don't carry credentials. For QuietMetrix's API-key
// requests, always pin to the dashboard's exact origin URL in production.
define('ALLOWED_ORIGIN', '*');

// Stage 5 — server-side origin allowlist for ingest routes (/track*). Comma-separated
// origins allowed to post events when an Origin/Referer header is present (browser
// traffic). Mobile SDKs send no Origin and are unaffected. Empty = do not enforce.
define('ALLOWED_ORIGINS', '');

// Content-Security-Policy connect-src directive. Defaults to 'self'.
// Change this if your dashboard is served from a different origin than the API.
define('CSP_CONNECT_SRC', "'self'");

// Public base URL of the dashboard, used to build invitation links sent to new
// users. Example: 'https://example.com/dashboard/'. Leave empty to fall back to
// the request origin. The invite link is APP_BASE_URL . '?invite=<token>'.
define('APP_BASE_URL', '');

// Invitation email delivery (optional). When enabled, new-user invites are sent
// via PHP's mail(). When false (the default), the invite link is only returned
// to the inviting admin in the API response, to share manually.
define('INVITE_EMAIL_ENABLED', false);
define('INVITE_FROM_EMAIL',    'no-reply@example.com');

// Days an invitation link stays valid before it expires.
define('INVITE_EXPIRY_DAYS', 7);

// If this backend is deployed in a subdirectory of your domain, set the prefix.
// Example: served at https://example.com/quietmetrix/api/... → '/quietmetrix'
// Leave empty when deployed at the domain root.
define('BASE_PATH', '');

// Debug flag.
//   - true  → verbose error responses, the dashboard exposes a "Use demo data"
//             switch in Settings, and /api/v1/_demo/* endpoints are mounted.
//   - false → production behaviour: terse errors, no demo switch, no demo
//             endpoints. Always deploy with DEBUG=false in production.
define('DEBUG', false);

// Rate limiting on the public ingest endpoints (/api/v1/track*).
// Turned ON by default — production deployments need this.
define('RATE_LIMIT_ENABLED', true);
define('RATE_LIMIT_RPS',     10);   // requests per second per API key
define('RATE_LIMIT_BURST',   60);   // burst tokens

// Per-IP ingest throttling — abuse defense Stage 1 for the publishable (write-only,
// project-scoped) API key. Bounded blast radius: a single host cannot saturate a
// project's shared bucket. See docs/security/publishable-api-key.md.
define('INGEST_IP_ENABLED', true);
define('INGEST_IP_RPS',     5);   // requests per second per source IP
define('INGEST_IP_BURST',   60);  // burst tokens per source IP

// Per-install (anonymousId) ingest throttling + ramp-up detector — abuse defense Stage 2.
// The SDK's anonymousId is salt-hashed before storage; the raw id is never stored.
define('INGEST_INSTALL_ENABLED', true);
define('INGEST_INSTALL_RPS',       1);    // requests per second per install
define('INGEST_INSTALL_BURST',     30);    // burst tokens per install
define('INGEST_INSTALL_RAMP_EVENTS', 500); // auto-revoke threshold (events)
define('INGEST_INSTALL_RAMP_MINUTES', 10); // ramp-up window (minutes)

// Trusted proxy IPs — only these IPs' X-Forwarded-For headers are trusted.
// Set to your load balancer or CDN edge IPs. Empty = trust nobody.
define('TRUSTED_PROXIES', serialize([]));
