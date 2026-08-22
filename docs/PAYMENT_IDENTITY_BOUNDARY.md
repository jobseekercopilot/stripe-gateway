# Stripe Gateway payment identity boundary

Stripe Gateway has two distinct authenticated boundaries.

## Checkout creation

Only Payment Gateway may call the owned
`POST /api/v2/stripe/checkout-sessions` route. The call
must contain exactly one valid `X-Service-Token` and one valid
`X-Payment-Owner`. Stripe Gateway rejects missing, forged or ambiguous
credentials and rejects the legacy caller-selected `X-User-Id` header.

The request carries a Payment Service order identifier, not a browser-selected
price or user identifier. Stripe Gateway resolves the authoritative order and
requires its owner to match `X-Payment-Owner` before the provider is called.
The inherited `/api/v1/stripe/checkout-sessions` route is disabled by default
and cannot be enabled in production.

`PAYMENT_GATEWAY_TO_STRIPE_GATEWAY_TOKEN` authenticates this boundary.

## Webhook fulfilment

Stripe webhooks remain external provider calls authenticated by
`Stripe-Signature` and `STRIPE_WEBHOOK_SECRET`. Stripe Gateway derives the owner
for fulfilment from verified webhook metadata and sends that same owner to
Payment Service as `X-Payment-Owner`.

`STRIPE_GATEWAY_TO_PAYMENT_SERVICE_TOKEN` authenticates Stripe Gateway to
Payment Service. It is distinct from the inbound Payment Gateway token.

No browser credential or caller-supplied identity is forwarded across either
service boundary. Tokens must contain at least 32 bytes and are compared in
constant time on the inbound boundary.
