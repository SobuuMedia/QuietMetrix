# Operations: Payment Integration

Payment integration is only relevant for the **cloud** profile (`QM_PROFILE=cloud`). Self-hosted installations do not need payment configuration.

## Supported Providers

QuietMetrix supports two payment providers:

| Provider | Use case |
|----------|----------|
| Stripe | Global SaaS, subscriptions, credit cards |
| Adyen | EU-focused, alternative payment methods, localized checkout |

Set the default provider via `QM_BILLING_PROVIDER` (values: `stripe` or `adyen`). Users can override per checkout session via the `provider` field.

## Stripe Setup

### 1. Create a Stripe account

Sign up at [stripe.com](https://stripe.com) and obtain your API keys from the Dashboard.

### 2. Configure environment variables

```
QM_PROFILE=cloud
QM_BILLING_PROVIDER=stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

### 3. Create products and prices in Stripe

Create one product per QuietMetrix plan in the Stripe Dashboard:

| QuietMetrix Plan | Stripe Price | Monthly events |
|------------------|-------------|----------------|
| Hobby | `price_hobby_monthly` | 100,000 |
| Startup | `price_startup_monthly` | 1,000,000 |
| Business | `price_business_monthly` | 10,000,000 |

Map Stripe price IDs to QuietMetrix plan IDs in the server configuration:

```
QM_STRIPE_PLAN_MAP=hobby:price_hobby_monthly,startup:price_startup_monthly,business:price_business_monthly
```

### 4. Configure the webhook

In the Stripe Dashboard, create a webhook endpoint pointing to:

```
https://yourhost/api/v1/billing/webhook/stripe
```

Subscribe to these events:

- `checkout.session.completed`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `invoice.payment_failed`

### 5. Verify the integration

```bash
# Create a checkout session
curl -X POST https://yourhost/api/v1/billing/checkout \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan_id": "hobby", "provider": "stripe", "success_url": "https://app.example.com/success", "cancel_url": "https://app.example.com/cancel"}'
```

## Adyen Setup

### 1. Create an Adyen account

Sign up at [adyen.com](https://adyen.com) and obtain your API key and HMAC secret from the Customer Area.

### 2. Configure environment variables

```
QM_PROFILE=cloud
QM_BILLING_PROVIDER=adyen
ADYEN_API_KEY=...
ADYEN_MERCHANT_ACCOUNT=YourMerchantAccount
ADYEN_HMAC_KEY=...
ADYEN_CLIENT_KEY=...
```

### 3. Configure the webhook

In the Adyen Customer Area, create a webhook endpoint pointing to:

```
https://yourhost/api/v1/billing/webhook/adyen
```

Enable these notification types:

- `AUTHORISATION`
- `REFUND`
- `CANCELLATION`

### 4. Plan mapping

Map Adyen payment links to QuietMetrix plan IDs:

```
QM_ADYEN_PLAN_MAP=hobby:adyen_plan_hobby,startup:adyen_plan_startup,business:adyen_plan_business
```

## Checkout Flow

```
┌──────────┐     POST /billing/checkout      ┌──────────────┐
│  Client   │ ─────────────────────────────► │  QuietMetrix  │
└──────────┘                                  └──────┬───────┘
                                                     │
                                              Creates session
                                                     │
                                                     ▼
┌──────────┐     Redirect to checkout_url    ┌──────────────┐
│  Client   │ ─────────────────────────────► │ Stripe/Adyen  │
└──────────┘                                  └──────┬───────┘
                                                     │
                                            Payment completed
                                                     │
                                                     ▼
┌──────────────┐    Webhook                   ┌──────────────┐
│  QuietMetrix  │ ◄────────────────────────── │ Stripe/Adyen  │
│  (updates DB) │                             └──────────────┘
└──────────────┘
```

## Webhook Security

Both providers use signature verification:

- **Stripe**: The `Stripe-Signature` header is verified using your `STRIPE_WEBHOOK_SECRET`
- **Adyen**: The HMAC signature is verified using your `ADYEN_HMAC_KEY`

Never expose these secrets to the client. They are only used server-side for webhook verification.

## Plan Mapping

QuietMetrix plans determine event limits and rate limits. See [Rate Limits](rate-limits.md) for the full plan matrix.

The plan is stored on the project and updated via webhook when a subscription changes. To manually set a plan (e.g., for testing):

```bash
curl -X PATCH https://yourhost/api/v1/projects/{projectId} \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan_id": "startup"}'
```

## Testing

Use Stripe test mode and Adyen test environment during development. Test API keys are set with the `_test_` prefix:

```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...  # from Stripe CLI: stripe listen --forward-to localhost:8080/api/v1/billing/webhook/stripe
```

For local testing with Stripe, use the [Stripe CLI](https://stripe.com/docs/stripe-cli):

```bash
stripe listen --forward-to http://localhost:8080/api/v1/billing/webhook/stripe
```