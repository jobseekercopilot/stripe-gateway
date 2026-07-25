#!/usr/bin/env bash
set -euo pipefail

contract_dir="${1:-src/main/openapi}"
manifest="$contract_dir/SHA256SUMS"

for required_file in \
    "$contract_dir/system-data-service.json" \
    "$contract_dir/system-data-service.SOURCE" \
    "$contract_dir/payment-service.json" \
    "$contract_dir/payment-service.SOURCE" \
    "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

test "$(wc -l < "$contract_dir/system-data-service.SOURCE" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/system-data-service' "$contract_dir/system-data-service.SOURCE" >/dev/null
grep -Fx 'revision=c687e416e0055adbe9123a099a94bd17ecbc8e46' "$contract_dir/system-data-service.SOURCE" >/dev/null
grep -Fx 'path=api/openapi.json' "$contract_dir/system-data-service.SOURCE" >/dev/null
grep -Fx 'sha256=817afdea8af835e37b1379c47c6730811f3ef5703b29be048b092e676dcc7675' "$contract_dir/system-data-service.SOURCE" >/dev/null

test "$(wc -l < "$contract_dir/payment-service.SOURCE" | tr -d ' ')" = 4
grep -Fx 'repository=jobseekercopilot/payment-service' "$contract_dir/payment-service.SOURCE" >/dev/null
grep -Fx 'revision=0243471685ef128f84d7011950f2baa2f1450acf' "$contract_dir/payment-service.SOURCE" >/dev/null
grep -Fx 'path=contracts/openapi.json' "$contract_dir/payment-service.SOURCE" >/dev/null
grep -Fx 'sha256=446dc9a1450bf876c3bd477e6120fe1b3b40ff3326334cb985a28e31828b22a0' "$contract_dir/payment-service.SOURCE" >/dev/null

jq -e '
    (.info.version == "1.0.0") and
    (.paths["/internal/fixtures/stripe/respond"].post.operationId == "stripe") and
    (.paths["/internal/fixtures/stripe/respond"].post.requestBody.required == true) and
    (.paths["/internal/fixtures/stripe/respond"].post.requestBody.content["application/json"].schema["$ref"]
        == "#/components/schemas/FixtureStripeRequest") and
    (.paths["/internal/fixtures/stripe/respond"].post.responses["200"].content["application/json"].schema["$ref"]
        == "#/components/schemas/FixtureStripeResponse") and
    (.components.schemas.FixtureStripeRequest.properties
        | has("datasetId") and has("datasetVersion") and has("scenario") and has("operation") and
          has("userId") and has("pricingPlanId") and has("tokenAmount") and has("priceGbpPence")) and
    (.components.schemas.FixtureStripeResponse.properties
        | has("sessionId") and has("paymentIntentId") and has("checkoutUrl") and has("fixtureMode"))
' "$contract_dir/system-data-service.json" >/dev/null

jq -e '
    (.info.version == "2.0.0") and
    (.components.securitySchemes.serviceToken
        | .type == "apiKey" and .in == "header" and .name == "X-Service-Token") and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.operationId == "confirmStripePurchase") and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.security
        == [{"serviceToken": []}]) and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.parameters
        | any(.name == "X-Payment-Owner" and .in == "header" and .required == true)) and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.requestBody.required == true) and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.requestBody.content["application/json"].schema["$ref"]
        == "#/components/schemas/ConfirmStripePurchaseRequest") and
    (.components.schemas.ConfirmStripePurchaseRequest.required
        | index("userId") != null and index("pricingPlanId") != null and index("stripeSessionId") != null) and
    (.components.schemas.ConfirmStripePurchaseRequest.properties
        | has("tokenAmount") and has("stripePaymentIntentId"))
' "$contract_dir/payment-service.json" >/dev/null

echo "contract policy: pinned System Data and Payment Service sources are intact and compatible"
