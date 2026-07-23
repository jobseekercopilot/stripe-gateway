# Stripe Gateway

Spring Boot boundary for the inherited Stripe Checkout and webhook integration.

This repository is a sanitised audit baseline, not an approved live-provider
release. The current source requires locally supplied generated Payment Service and
System Data client JARs. Those binaries are intentionally not committed.

See [`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The command fails in a clean clone until generated clients are reproducible. Do
not commit JARs or real Stripe credentials.

The captured OpenAPI contract is in `contracts/openapi.json`.

## Licence

Proprietary and confidential. See `LICENSE`.
