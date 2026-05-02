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
define('JWT_EXPIRY_HOURS', 24);

// CORS — the origin allowed to call the API from a browser. When the dashboard
// is hosted on a separate origin, set this to that exact URL. Setting '*' is
// only safe if endpoints don't carry credentials. For QuietMetrix's API-key
// requests, always pin to the dashboard's exact origin URL in production.
define('ALLOWED_ORIGIN', '*');

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

// Trusted proxy IPs — only these IPs' X-Forwarded-For headers are trusted.
// Set to your load balancer or CDN edge IPs. Empty = trust nobody.
define('TRUSTED_PROXIES', serialize([]));
