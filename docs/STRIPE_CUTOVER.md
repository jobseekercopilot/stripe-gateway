# Stripe test rehearsal and production cutover

This is the operator checklist for the public-beta document-credit Checkout
path. It does not authorise a deployment or a charge. Keep Checkout disabled
until every reviewed approval and release condition below is evidenced.

## What Stripe must contain

The application uses three permanent live Products and immutable, one-off GBP
Prices. Payment Service still owns the commercial amount and generation
allowance; Stripe Gateway maps the stable server-side pack ID to the separately
approved live Price ID. The browser supplies neither amount nor Price ID.

| Pack | Product wording | Price | Environment mapping |
|---|---|---:|---|
| Starter | 10 document generations | £4.99 | `STRIPE_PRICE_STARTER` |
| Active | 25 document generations | £11.99 | `STRIPE_PRICE_ACTIVE` |
| Power | 60 document generations | £19.99 | `STRIPE_PRICE_POWER` |

Put the temporary catalogue-management key in the owner-only shared
`config/.secrets.env` file as `STRIPE_LIVE_CATALOG_ADMIN_KEY`. Run
`scripts/reconcile-live-catalogue.sh` with that file and a protected output
path. The script parses only that exact variable without sourcing the file,
reuses exact JSC-owned objects, creates only missing objects and refuses
ambiguous matches. It never prints the key. Review any other active Prices in
the Dashboard before explicitly archiving them; never delete financial history
or archive an unrelated object.

```bash
scripts/reconcile-live-catalogue.sh \
  ../config/.secrets.env \
  ../config/stripe-live-catalogue.json
```

Create a public HTTPS webhook endpoint at:

```text
https://<approved-public-host>/api/v1/stripe/webhook
```

Subscribe it to exactly the event types currently consumed by the gateway:

- `checkout.session.completed`
- `checkout.session.expired`
- `charge.refunded`
- `charge.dispute.created`

The API version is pinned to `2026-02-25.clover`. A change to the API version
or event set requires contract review and another rehearsal.

## Preconditions

Before any network-mode rehearsal:

1. Complete the Stripe account business/KYC and payout setup.
2. Approve the seller identity, contact/address disclosures, consumer terms,
   immediate-supply/cancellation wording, refund procedure and reconciliation
   procedure.
3. Record an accountant-reviewed tax position. The intended launch setting is
   `NOT_VAT_REGISTERED` with `VAT_NOT_CHARGED`, but it must not be enabled from
   this document alone.
4. Confirm the same reviewed legal version and immutable legal-artifact hashes
   in Client, Landing, Authentication, Payment and the launch approval record.
5. Keep the legacy caller-priced route off:
   `STRIPE_LEGACY_CHECKOUT_ENABLED=false`.
6. If the AWS Activate Stripe for Startups offer is available to the account,
   claim its $500 fee credit only when the verified live account is ready to
   process payments. Stripe documents a 12-month period from activation (or
   earlier exhaustion of the offer limit) and one redemption per startup.
   Confirm the credit in Stripe's Reports area before relying on it; do not
   remove normal processing fees from the permanent cost model.

## Test-mode rehearsal

Use an isolated non-production deployment. Network-mode Stripe is represented
by `EXTERNAL_PROVIDER_MODE=LIVE`; the credential type distinguishes the
environment. Non-production startup requires an `sk_test_` key and rejects an
`sk_live_` key. Configure:

```text
EXTERNAL_PROVIDER_MODE=LIVE
STRIPE_LIVE_RELEASE_AUTHORISED=true
STRIPE_LEGACY_CHECKOUT_ENABLED=false
STRIPE_API_BASE_URL=https://api.stripe.com
STRIPE_API_VERSION=2026-02-25.clover
STRIPE_PRICE_STARTER=price_...
STRIPE_PRICE_ACTIVE=price_...
STRIPE_PRICE_POWER=price_...
STRIPE_SUCCESS_URL=https://<test-app-host>/payment/success
STRIPE_CANCEL_URL=https://<test-app-host>/payment/cancel
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Also configure `PAYMENT_SERVICE_URL` and the three distinct, receiver-scoped
service credentials. Each must contain at least 32 bytes:

- `PAYMENT_GATEWAY_TO_STRIPE_GATEWAY_TOKEN`
- `STRIPE_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`
- `PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN`

Run and retain evidence for:

- successful Checkout and exact-once fulfilment after a signed webhook;
- replay of the same event without another grant;
- cancel return followed by late successful completion;
- natural/session expiry with no grant;
- full and partial refund reconciliation;
- dispute creation, wallet restriction and the manual outcome runbook;
- account-revocation versus completion interleavings;
- owner isolation, malformed return identifiers and invalid webhook signatures;
- wallet, payment history and append-only ledger agreement.

The return URL never fulfils an order. Only verified provider evidence can do
that. A green browser redirect without a matching authoritative order/ledger
record is a failed rehearsal.

## Production credential cutover

Production requires a live key and rejects a test key. Keep its least-privilege
runtime value in `STRIPE_LIVE_RUNTIME_KEY` in the shared secrets file. Create a
separate live webhook endpoint in Stripe, subscribe it to the same four events,
and put its own `whsec_` secret in `STRIPE_LIVE_WEBHOOK_SECRET`. Never reuse the
test endpoint secret. These local variable names deliberately differ from the
runtime environment names so the live values are not accidentally consumed by
a local Spring process.

Write the live values through the protected secret-input workflow. In the AWS
release root these map to Secrets Manager fields:

```text
integration/stripe:secret_key
integration/stripe:webhook_secret
```

The production runtime may instead use a least-privilege `rk_live_` restricted
key with Checkout Session read/write access. The temporary catalogue-management
key should not be retained by the application. Product and Price IDs are safe
identifiers, but the protected release contract must bind all three exact live
Price IDs to their reviewed Product IDs, amounts, currency and generation
allowances.

Do not place them in Git, shell history, screenshots, build logs or an approval
JSON file. Rotate any value exposed during setup.

Only after the secret is stored and the test evidence is reviewed, complete
the Stripe launch-approval fields: accountable reviewer and timestamp,
approval/evidence reference, `paymentReadinessStatus=PASS`, refund and
reconciliation runbook references, pinned API version, seller/tax/legal
evidence, and the four matching Checkout/live authorisation booleans. The
Infrastructure checks deliberately keep Payment and the public webhook dark if
these values disagree.

## First live transaction and rollback

1. Prepare the immutable release while Checkout and public traffic remain dark.
2. Verify readiness reports `READY` for Payment Service and Stripe Gateway.
3. Enable the reviewed release through the protected production workflow.
4. Make one smallest-pack purchase with a real card controlled by the operator.
5. Verify the Stripe event, Payment order, receipt fields, wallet balance,
   visible history and append-only ledger before treating the charge as good.
6. Issue and verify a refund; confirm credits are reversed exactly once.
7. Preserve redacted event/order identifiers and timestamps as evidence.

On any mismatch, use the emergency darken procedure first. It closes the Stripe
webhook rule and public listener before draining tasks. Do not retry charges or
edit ledger rows manually. Keep the financial record and follow the reviewed
refund/reconciliation runbook.
