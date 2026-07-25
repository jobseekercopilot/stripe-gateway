# Stripe Gateway contract governance

System Data owns the guarded fixture API. Stripe Gateway pins the exact merged
producer revision and checksum under `src/main/openapi` and generates the Java
client into `target/generated-sources` during Maven `generate-sources` with
OpenAPI Generator 7.5.0. Generated source and binaries are disposable output
and are never committed or copied through `libs`.

Payment Service owns the fulfilment API. The current Stripe Gateway adapter is
handwritten, so its reviewed Payment Service contract is pinned and checked
without retaining an unused generated-client JAR.

Stripe Gateway owns `contracts/openapi.json` for its checkout/webhook provider
boundary. `contracts/SHA256SUMS`, the API policy and generated-contract equality
gate keep that producer source authoritative. The policy also requires service
authentication and a trusted payment owner on checkout creation; removing
either control is contract drift.

## Updating a dependency

1. Merge and verify the producer contract change.
2. Review compatibility, identity and value-integrity impact.
3. Copy the exact producer bytes and update `.SOURCE` plus `SHA256SUMS`.
4. Update policy assertions only for an intentional reviewed consumer change.
5. Run all contract policies, deterministic generation, Maven verification and
   the source-only container build.

Rollback restores the prior contract pins and consumer code together. This
build policy does not approve live Stripe mode or fulfilment semantics; those
remain in PAY-03 through PAY-13.
