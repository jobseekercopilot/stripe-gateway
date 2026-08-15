# Stripe Gateway

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| Stripe checkout and signed-webhook provider boundary | Payment Gateway; Stripe webhook | Stripe/System Data and Payment Service | None | 8100 |

The browser payment path and standard live Stripe profile are not enabled on `develop`. See the central [payment status](https://docs.jobseekercopilot.com/journeys/reporting-payments/) and [external integrations](https://docs.jobseekercopilot.com/infrastructure/external-integrations/).

Spring Boot boundary for the inherited Stripe Checkout and webhook integration.

This repository is a sanitised audit baseline, not an approved live-provider
release. It builds its System Data fixture client deterministically from a
reviewed, checksum-protected producer contract. The handwritten Payment Service
adapter is checked against its own pinned producer contract. No copied JAR is a
build input.

See [`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).
See [`docs/PAYMENT_IDENTITY_BOUNDARY.md`](docs/PAYMENT_IDENTITY_BOUNDARY.md)
for the service-token and owner trust boundary.

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The command needs no sibling checkout or `libs` directory. Do not commit
generated sources, JARs or real Stripe credentials.

The captured OpenAPI contract is in `contracts/openapi.json`.

Version `2.2.0` binds provider Checkout creation to the durable order expiry,
replays create/bind outcomes by stable order identity, and reports a terminal
provider status before local cancellation or account-revocation progress.

Checkout creation requires `PAYMENT_GATEWAY_TO_STRIPE_GATEWAY_TOKEN`; webhook
fulfilment authenticates to Payment Service with
`STRIPE_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`; account-deletion Checkout expiry
requires `PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN`. All three secrets
must contain at least 32 bytes and must be distinct. Authentication Service does
not call Stripe Gateway directly. Stripe webhook calls remain authenticated by
`Stripe-Signature` and `STRIPE_WEBHOOK_SECRET`.

The inherited caller-priced `POST /api/v1/stripe/checkout-sessions` route is
runtime-disabled unless `STRIPE_LEGACY_CHECKOUT_ENABLED=true`; production
startup rejects that value. Public-beta Checkout uses only the owned v2 order
route.

Run the source and compatibility gates with:

```bash
./scripts/test-contract-policy.sh
./scripts/verify-contracts.sh
./scripts/test-api-contract-policy.sh
./scripts/verify-api-contract.sh
./scripts/test-client-generation.sh
```

See [`docs/CONTRACT_GOVERNANCE.md`](docs/CONTRACT_GOVERNANCE.md) for ownership,
revision/checksum pins, generation and rollback.

## Licence

Proprietary and confidential. See `LICENSE`.
