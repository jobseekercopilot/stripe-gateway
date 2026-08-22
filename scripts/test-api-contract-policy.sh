#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contract() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/contracts/openapi.json" "$repository_root/contracts/SHA256SUMS" "$destination/"
}

"$repository_root/scripts/verify-api-contract.sh" "$repository_root/contracts/openapi.json" >/dev/null

copy_contract "$temporary_dir/checksum-drift"
jq '.info.description = "unreviewed drift"' \
    "$temporary_dir/checksum-drift/openapi.json" \
    > "$temporary_dir/checksum-drift/changed.json"
mv "$temporary_dir/checksum-drift/changed.json" "$temporary_dir/checksum-drift/openapi.json"
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/checksum-drift/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted checksum drift" >&2
    exit 1
fi

copy_contract "$temporary_dir/webhook"
jq 'del(.paths["/api/v1/stripe/webhook"].post)' \
    "$temporary_dir/webhook/openapi.json" > "$temporary_dir/webhook/changed.json"
mv "$temporary_dir/webhook/changed.json" "$temporary_dir/webhook/openapi.json"
(cd "$temporary_dir/webhook" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/webhook/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of the webhook" >&2
    exit 1
fi

copy_contract "$temporary_dir/price-bound"
jq '.components.schemas.CreateCheckoutSessionRequest.properties.priceGbpPence.minimum = 0' \
    "$temporary_dir/price-bound/openapi.json" > "$temporary_dir/price-bound/changed.json"
mv "$temporary_dir/price-bound/changed.json" "$temporary_dir/price-bound/openapi.json"
(cd "$temporary_dir/price-bound" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/price-bound/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted an invalid price bound" >&2
    exit 1
fi

copy_contract "$temporary_dir/service-identity"
jq 'del(.paths["/api/v1/stripe/checkout-sessions"].post.security)' \
    "$temporary_dir/service-identity/openapi.json" > "$temporary_dir/service-identity/changed.json"
mv "$temporary_dir/service-identity/changed.json" "$temporary_dir/service-identity/openapi.json"
(cd "$temporary_dir/service-identity" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/service-identity/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of checkout service authentication" >&2
    exit 1
fi

copy_contract "$temporary_dir/payment-owner"
jq '.paths["/api/v1/stripe/checkout-sessions"].post.parameters
        |= map(select(.name != "X-Payment-Owner"))' \
    "$temporary_dir/payment-owner/openapi.json" > "$temporary_dir/payment-owner/changed.json"
mv "$temporary_dir/payment-owner/changed.json" "$temporary_dir/payment-owner/openapi.json"
(cd "$temporary_dir/payment-owner" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/payment-owner/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of the trusted payment owner" >&2
    exit 1
fi

echo "API contract policy negative tests passed"
