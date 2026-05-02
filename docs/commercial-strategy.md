# Commercial Strategy

## Open Core Model

QuietMetrix is open-core: the core analytics engine and SDK are [MIT-licensed](https://github.com/sobuumedia/quietmetrix), while cloud-specific features (billing, multi-tenancy, managed dashboards) are proprietary.

| Component | License | Availability |
|-----------|---------|-------------|
| KMP SDK (all targets) | MIT | Open source |
| Ktor + PostgreSQL server | MIT | Open source |
| PHP + MySQL server | MIT | Open source |
| Dashboard UI | MIT | Open source |
| Cloud billing integration | Proprietary | Cloud only |
| Managed hosting | Proprietary | Cloud only |

## Pricing Tiers

| Tier | Price | Events/Month | Projects | Retention |
|------|-------|-------------|----------|-----------|
| **Free** | $0 | 10,000 | 1 | 30 days |
| **Hobby** | $9/mo | 100,000 | 3 | 90 days |
| **Startup** | $29/mo | 1,000,000 | 10 | 365 days |
| **Business** | $99/mo | 10,000,000 | 50 | 730 days |
| **Self-hosted** | Free | Unlimited | Unlimited | Unlimited |

## Feature Gates

| Feature | Free | Hobby | Startup | Business | Self-hosted |
|---------|------|-------|---------|----------|-------------|
| Event tracking | ✅ | ✅ | ✅ | ✅ | ✅ |
| Dashboard | ✅ | ✅ | ✅ | ✅ | ✅ |
| Aggregates API | ✅ | ✅ | ✅ | ✅ | ✅ |
| Batch tracking | ❌ | ✅ | ✅ | ✅ | ✅ |
| Data export (CSV) | ❌ | ❌ | ✅ | ✅ | ✅ |
| Custom retention | ❌ | ❌ | ❌ | ✅ | ✅ |
| SSO / SAML | ❌ | ❌ | ❌ | ✅ | Configurable |
| Priority support | ❌ | ❌ | ❌ | ✅ | ❌ |
| SLA | ❌ | ❌ | ❌ | ✅ | ❌ |

## Self-hosted vs Cloud

### When to choose self-hosted

- Full data ownership — events never leave your infrastructure
- No event limits or rate limits (you control the hardware)
- Compliance requirements that require on-premises data storage
- Ability to modify the codebase for custom integrations
- Zero cost for high-volume deployments

### When to choose cloud

- No infrastructure management
- Automatic updates and security patches
- Built-in billing and team management
- Guaranteed uptime SLA (Business plan)
- Priority support

## Pricing Philosophy

- **Generous free tier** — Developers should be able to use QuietMetrix for side projects without hitting a paywall
- **Transparent pricing** — Event-based pricing is simple and predictable. No per-seat charges.
- **No vendor lock-in** — Self-hosted is always free and feature-complete. The open-source version is not a crippled demo.
- **Privacy as default** — IP stripping and UA classification are on by default in every tier, including self-hosted

## Go-to-Market

1. **Developer adoption** — The free tier and open-source core drive organic adoption
2. **Upgrade triggers** — Users hit the event cap or need batch tracking / data export
3. **Enterprise expansion** — Business plan for SSO, SLA, and custom retention
4. **Community growth** — GitHub stars, contributions, and self-hosted deployments build trust and visibility