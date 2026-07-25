# Stripe Gateway

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

Checkout creation requires `PAYMENT_GATEWAY_TO_STRIPE_GATEWAY_TOKEN`; webhook
fulfilment authenticates to Payment Service with
`STRIPE_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`. Both secrets must contain at least
32 bytes and must be distinct. Stripe webhook calls remain authenticated by
`Stripe-Signature` and `STRIPE_WEBHOOK_SECRET`.

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
