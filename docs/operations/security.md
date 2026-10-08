# Operations: Security

## HTTPS

All production deployments must use HTTPS. QuietMetrix API keys are sent in request headers and should never traverse an unencrypted connection.

### Docker (Caddy)

Use the production override described in [public hosting](public-hosting.md).
The base Compose file serves local HTTP on loopback. The production override adds
Caddy, removes the direct API port, and requires an owned DNS name, TLS email,
and reachable ports 80/443. Verify certificate issuance before accepting customers.

### Shared Hosting (Apache)

```apache
<VirtualHost *:80>
    ServerName yourdomain.com
    Redirect permanent / https://yourdomain.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName yourdomain.com
    SSLEngine on
    SSLCertificateFile /etc/letsencrypt/live/yourdomain.com/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/yourdomain.com/privkey.pem
    # ... rest of your config
</VirtualHost>
```

## API key Rotation

If an API key is compromised, rotate it immediately:

```bash
# Generate a new API key via the admin API
curl -X POST https://yourhost/api/v1/projects/{projectId}/regenerate-key \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

The old API key stops working immediately. Update all SDK configurations with the new key.

### Best Practices

- Use a dedicated API key per environment (dev, staging, production)
- Never commit API keys to version control — use environment variables
- Rotate API keys periodically (e.g., every 90 days) even if not compromised

## Rate Limiting

See [Rate Limits](rate-limits.md) for the implemented shared budgets and local ingest
limits. Cloud startup rejects disabled limits.

## CORS

Set `QM_CORS_ALLOWED_ORIGINS` to explicit dashboard origins for Ktor. PHP reads
`ALLOWED_ORIGIN` and `ALLOWED_ORIGINS` from its protected configuration file.
Configure the application rather than adding conflicting wildcard web-server headers.
Browser-origin checks apply to ingestion; native SDKs can send requests without Origin.

## JWT Secret

The JWT secret (`QM_JWT_SECRET`) is used to sign admin session tokens. It must be a strong, random string in production:

```bash
# Generate a secure JWT secret
openssl rand -base64 48
```

**Never use the default value `change-me-in-production` in production.**

If the JWT secret is rotated, all existing admin sessions are invalidated and users must log in again.

## Database Security

- Restrict database access to the application host only (no remote access)
- Use strong, unique passwords for the database user
- In Docker, the Postgres container is on an internal network and not exposed to the host by default

```yaml
# docker-compose.yml — Postgres is not exposed to the host
services:
  postgres:
    # ports: are intentionally omitted
    networks:
      - internal
```

- Enable SSL for database connections in production:

```
QM_DB_URL=jdbc:postgresql://postgres:5432/quietmetrix?ssl=true
```

## Additional Hardening

- **Firewall**: Only expose ports 80 and 443 to the internet
- **Updates**: Keep your Docker images and host OS updated
- **Logging**: Enable access logs but do not log request bodies (they may contain event props with PII)
- **Scan**: Run `trivy` or similar on your Docker images before deploying

## Project Access Control

QuietMetrix projects support three roles for team collaboration:

| Role | Capabilities |
|------|-------------|
| **Owner** | Full control — update project settings, add/remove members, delete the project, view dashboard |
| **Admin** | Manage members (add/remove), update project settings, view dashboard |
| **Viewer** | Read-only dashboard access — view events and aggregates, cannot modify project or members |

- Only the **owner** can delete a project or transfer ownership.
- An **admin** cannot remove the owner or promote/demote other members.
- A **viewer** has no access to project settings or member management.