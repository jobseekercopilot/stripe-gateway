# Contributing

This is a private, proprietary repository.

1. Start from `develop` and use a focused branch for an approved issue.
2. Keep one issue and one concern per pull request.
3. Never commit Stripe keys, webhook secrets, event payloads, payment/user data or
   generated JARs.
4. Automated tests and CI must use deterministic fixtures or Stripe-approved test
   mode; never make a real charge.
5. Preserve fail-closed live-mode controls, webhook verification and idempotency.
6. Run `mvn -B clean verify` and relevant contract/security checks before review.
7. Open a pull request into `develop`; do not push implementation work directly.

Report security concerns using `SECURITY.md`.
