# QuietMetrix — `php-hosting/`

Drop-in PHP backend + static dashboard for IONOS / shared hosting.

- No Composer, no framework, no Docker — upload, configure, done.
- Auto-creates the database schema and the first admin user on first request.
- Same HTTP API as the Ktor server in `servers/ktor/`, so SDKs and curl scripts
  work identically against either.
- Static dashboard ships in `dashboard/`, served from the same domain.

See [SETUP.md](SETUP.md) for step-by-step IONOS install instructions.
