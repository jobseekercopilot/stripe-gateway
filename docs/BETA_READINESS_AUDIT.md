# Stripe Gateway beta-readiness audit

> **Historical audit.** This document records the 23 July 2026 inherited
> baseline. Its findings drove the owned-v2 Checkout, signed-event,
> idempotency, recovery and contract work now present in the repository. It is
> not the current release decision. See `STRIPE_CUTOVER.md`, the current tests
> and the central launch approval record for the active fail-closed decision.

Audit date: 2026-07-23  
Decision at audit date: **Not ready for private beta or live Stripe traffic**

This is an audit baseline only. No provider call, charge or deployment was made.

## Verified behavior

- Creates Stripe Checkout sessions using inline GBP `price_data`.
- Adds user, pricing-plan and token metadata to the session.
- Verifies LIVE webhook HMAC signatures with a five-minute timestamp tolerance.
- Handles `checkout.session.completed` by forwarding metadata to Payment Service.
- Supports a deterministic System Data fixture provider mode.
- With two inherited local generated-client JARs present, `mvn -q clean verify`
  passed 4 tests with no failures/errors/skips.

## Build result

The POM uses `systemPath` for Payment Service and System Data clients and the
Dockerfile copies `libs`. Sanitised clean clones omit those binaries and cannot
build until clients/contracts are reproducible.

## Critical findings

### Untrusted checkout request

`/api/v1/stripe/checkout-sessions` is unauthenticated and accepts user ID, token
amount and GBP price from its caller. Payment Gateway normally derives values from
Payment Service, but Stripe Gateway does not enforce that trusted path or verify
the plan independently.

### Unsafe provider-mode default

Provider mode defaults to LIVE, including ordinary/default profile startup. Safety
only rejects FIXTURE under a production profile; it does not require an explicit
live opt-in. Default success/cancel URLs use HTTP localhost values.

### Incomplete checkout/order lifecycle

Checkout creation has no Stripe idempotency key, durable owned order/quote,
pricing-version snapshot, session expiry or reconciliation state. No API version is
pinned in the inspected request path.

### Incomplete webhook validation

LIVE signature/timestamp validation exists, but processing does not verify
payment status, livemode/environment, GBP currency, amount total, expected owned
order, event identity or price snapshot. Fixture mode bypasses webhook signature
validation completely. No durable event record/unique constraint prevents
concurrent replay.

### Missing payment lifecycle

No implemented handling was found for checkout expiry, asynchronous payment
failure, refund, dispute/chargeback or reconciliation. Payment Service is called
synchronously from the webhook and has no authenticated service boundary.

### Privacy, secrets and provider compliance

Logs contain raw user and Stripe session identifiers plus amounts. Secret rotation,
least privilege, test/live separation, event-retention and incident procedures are
not documented. The hand-built API/webhook integration needs an evidence-based
Stripe compatibility/compliance review. OpenAPI metadata incorrectly says MIT.

## Functional classification

| Capability | Result |
|---|---|
| Fixture checkout | Working narrowly; unsafe boundary if externally reachable |
| Live checkout | Incomplete and not approved |
| Webhook signature | Working narrowly in LIVE |
| Event/payment/order validation | Absent but required |
| Idempotent fulfillment | Incomplete across concurrency |
| Refund/dispute/expiry | Absent |
| Clean build/contract | Absent but required |
| Provider operations | Absent |

The Payments epic contains focused follow-up issues. All remain Backlog and no
issue was implemented during this audit.
