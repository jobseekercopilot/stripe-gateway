#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contracts() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root"/src/main/openapi/* "$destination/"
}

"$repository_root/scripts/verify-contracts.sh" "$repository_root/src/main/openapi" >/dev/null

copy_contracts "$temporary_dir/checksum-drift"
jq '.info.description = "unreviewed drift"' \
    "$temporary_dir/checksum-drift/system-data-service.json" \
    > "$temporary_dir/checksum-drift/changed.json"
mv "$temporary_dir/checksum-drift/changed.json" "$temporary_dir/checksum-drift/system-data-service.json"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/checksum-drift" >/dev/null 2>&1; then
    echo "contract policy negative test accepted checksum drift" >&2
    exit 1
fi

copy_contracts "$temporary_dir/fixture-operation"
jq 'del(.paths["/internal/fixtures/stripe/respond"])' \
    "$temporary_dir/fixture-operation/system-data-service.json" \
    > "$temporary_dir/fixture-operation/changed.json"
mv "$temporary_dir/fixture-operation/changed.json" "$temporary_dir/fixture-operation/system-data-service.json"
(cd "$temporary_dir/fixture-operation" && sha256sum payment-service.json system-data-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/fixture-operation" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of the Stripe fixture operation" >&2
    exit 1
fi

copy_contracts "$temporary_dir/confirmation-field"
jq '.components.schemas.ConfirmStripePurchaseRequest.required -= ["stripeSessionId"]' \
    "$temporary_dir/confirmation-field/payment-service.json" \
    > "$temporary_dir/confirmation-field/changed.json"
mv "$temporary_dir/confirmation-field/changed.json" "$temporary_dir/confirmation-field/payment-service.json"
(cd "$temporary_dir/confirmation-field" && sha256sum payment-service.json system-data-service.json > SHA256SUMS)
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/confirmation-field" >/dev/null 2>&1; then
    echo "contract policy negative test accepted removal of the fulfilment key" >&2
    exit 1
fi

copy_contracts "$temporary_dir/source-revision"
sed 's/revision=c687e41/revision=0000000/' \
    "$temporary_dir/source-revision/system-data-service.SOURCE" \
    > "$temporary_dir/source-revision/changed.SOURCE"
mv "$temporary_dir/source-revision/changed.SOURCE" "$temporary_dir/source-revision/system-data-service.SOURCE"
if "$repository_root/scripts/verify-contracts.sh" "$temporary_dir/source-revision" >/dev/null 2>&1; then
    echo "contract policy negative test accepted unreviewed producer revision metadata" >&2
    exit 1
fi

echo "contract policy negative tests passed"
