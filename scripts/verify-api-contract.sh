#!/usr/bin/env bash
set -euo pipefail

contract="${1:-contracts/openapi.json}"
generated="${2:-}"
contract_dir="$(dirname "$contract")"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$contract" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "API contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "2.4.0") and
    (.components.securitySchemes.serviceToken
        | .type == "apiKey" and .in == "header" and .name == "X-Service-Token") and
    (.paths["/api/v1/stripe/checkout-sessions"].post.operationId == "createCheckoutSession") and
    (.paths["/api/v1/stripe/checkout-sessions"].post.security
        == [{"serviceToken": []}]) and
    (.paths["/api/v1/stripe/checkout-sessions"].post.parameters
        | any(.name == "X-Payment-Owner" and .in == "header" and .required == true)) and
    (.paths["/api/v1/stripe/checkout-sessions"].post.parameters
        | all(.name != "X-User-Id")) and
    (.paths["/api/v1/stripe/webhook"].post.operationId == "webhook") and
    (.paths["/api/v1/stripe/webhook"].post.parameters[0].name == "Stripe-Signature") and
    (.components.schemas.CreateCheckoutSessionRequest.required
        | index("userId") != null and index("pricingPlanId") != null) and
    (.components.schemas.CreateCheckoutSessionRequest.properties.tokenAmount.minimum == 1) and
    (.components.schemas.CreateCheckoutSessionRequest.properties.priceGbpPence.minimum == 1) and
    (.components.schemas.CreateCheckoutSessionResponse.properties
        | has("sessionId") and has("checkoutUrl")) and
    (.paths["/api/v2/stripe/checkout-sessions"].post.operationId
        == "createOwnedStripeCheckoutSession") and
    (.paths["/api/v2/stripe/readiness"].get.operationId == "getOwnedStripeReadiness") and
    (.paths["/internal/v2/stripe/checkout-sessions/expire"].post.operationId
        == "expireOwnedStripeCheckoutSession") and
    (.paths["/api/v2/stripe/checkout-sessions"].post.security
        == [{"serviceToken": []}]) and
    (.paths["/api/v2/stripe/checkout-sessions"].post.parameters
        | any(.name == "X-Payment-Owner" and .in == "header" and .required == true)) and
    (.paths["/api/v2/stripe/checkout-sessions"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header" and .required == true)) and
    (.paths["/api/v2/stripe/readiness"].get.security == [{"serviceToken": []}]) and
    (.paths["/internal/v2/stripe/checkout-sessions/expire"].post.security
        == [{"serviceToken": []}]) and
    (.paths["/internal/v2/stripe/checkout-sessions/expire"].post.parameters
        | any(.name == "X-Payment-Owner" and .in == "header" and .required == true)) and
    (.components.schemas.CreateOwnedCheckoutSessionRequest.required == ["orderId"]) and
    (.components.schemas.CreateOwnedCheckoutSessionResponse.properties
        | has("orderId") and has("sessionId") and has("url") and
          has("expiresAt") and has("promotionGuaranteed")) and
    (.components.schemas.ExpireOwnedCheckoutSessionRequest.required
        | index("orderId") != null and index("providerSessionId") != null) and
    (.components.schemas.StripeReadinessResponse.properties
        | has("checkoutAvailable") and has("code") and has("mode")) and
    (.components.schemas.StripeReadinessResponse.properties.code.enum
        | index("STRIPE_CATALOG_NOT_CONFIGURED") != null)
' "$contract" >/dev/null

if [[ -n "$generated" ]]; then
    if [[ ! -f "$generated" || -L "$generated" ]]; then
        echo "API contract policy: generated contract is missing or is a symlink: $generated" >&2
        exit 1
    fi
    temporary_dir="$(mktemp -d)"
    trap 'rm -rf "$temporary_dir"' EXIT
    jq -S . "$contract" > "$temporary_dir/reviewed.json"
    jq -S . "$generated" > "$temporary_dir/generated.json"
    if ! cmp -s "$temporary_dir/reviewed.json" "$temporary_dir/generated.json"; then
        echo "API contract policy: generated OpenAPI differs from contracts/openapi.json" >&2
        diff -u "$temporary_dir/reviewed.json" "$temporary_dir/generated.json" >&2 || true
        exit 1
    fi
fi

echo "API contract policy: reviewed Stripe Gateway contract is intact and compatible"
