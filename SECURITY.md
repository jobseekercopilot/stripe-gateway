# Security policy

This repository is not approved for live Stripe traffic.

Report suspected vulnerabilities privately to the repository owner. Never place
secret keys, webhook secrets/signatures, full event payloads, user identifiers,
checkout sessions, payment intents or exploit details in ordinary issues or logs.

The audit found an unauthenticated checkout boundary, a default LIVE provider mode,
fixture signature bypass, incomplete event validation and an unauthenticated
downstream credit-grant path. Keep payment disabled at the client boundary until
the Payments epic is complete.

Rotate exposed Stripe credentials through Stripe and every affected environment.
Removing a Git commit does not revoke a secret.
