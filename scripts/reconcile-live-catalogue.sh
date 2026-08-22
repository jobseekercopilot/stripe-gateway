#!/usr/bin/env bash
set -euo pipefail

secrets_file=${1:-}
output_file=${2:-}
api_version=${STRIPE_API_VERSION:-2026-02-25.clover}
api_base=https://api.stripe.com
catalog_version=public-beta-2026-08-22

if [[ -z "$secrets_file" || -z "$output_file" || ! -f "$secrets_file" ]]; then
  echo "Usage: $0 /secure/.secrets.env /secure/live-catalogue.json" >&2
  exit 2
fi
case "$(stat -c '%a' "$secrets_file")" in
  400|600) ;;
  *) echo "Refusing secrets file unless its permissions are exactly 400 or 600." >&2; exit 2 ;;
esac
if [[ "$api_version" != "2026-02-25.clover" ]]; then
  echo "Refusing an unreviewed Stripe API version." >&2
  exit 2
fi
if [[ -e "$output_file" && ! -f "$output_file" ]]; then
  echo "Refusing a non-regular catalogue output path." >&2
  exit 2
fi
if [[ "$(realpath -m "$secrets_file")" == "$(realpath -m "$output_file")" ]]; then
  echo "Refusing to overwrite the secrets input with catalogue output." >&2
  exit 2
fi

key_name=STRIPE_LIVE_CATALOG_ADMIN_KEY
key_count=$(awk -F= -v key="$key_name" '$1 == key { count++ } END { print count + 0 }' "$secrets_file")
if [[ "$key_count" -ne 1 ]]; then
  echo "Refusing secrets input: expected exactly one $key_name entry." >&2
  exit 3
fi
secret_key=$(awk -F= -v key="$key_name" '$1 == key { sub(/^[^=]*=/, ""); print }' "$secrets_file")
secret_key=${secret_key%$'\r'}
if [[ ! "$secret_key" =~ ^(sk|rk)_live_[A-Za-z0-9_]+$ ]]; then
  echo "Refusing invalid $key_name. Expected an unquoted live secret or restricted key." >&2
  exit 3
fi

umask 077
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
auth_config="$scratch/curl-auth.conf"
printf 'header = "Authorization: Bearer %s"\nheader = "Stripe-Version: %s"\n' \
  "$secret_key" "$api_version" >"$auth_config"
unset secret_key

stripe_get() {
  path=$1
  shift
  curl --config "$auth_config" --fail-with-body --silent --show-error \
    --get "$api_base$path" \
    "$@"
}

stripe_post() {
  path=$1
  idempotency_key=$2
  shift 2
  curl --config "$auth_config" --fail-with-body --silent --show-error \
    "$api_base$path" \
    --header "Idempotency-Key: $idempotency_key" \
    "$@"
}

product_for() {
  pack_id=$1
  name=$2
  generations=$3
  search_file="$scratch/product-$pack_id-search.json"
  stripe_get /v1/products/search \
    --data-urlencode "query=metadata['jsc_pack_id']:'$pack_id'" \
    --data-urlencode 'limit=100' >"$search_file"
  count=$(jq --arg catalog "$catalog_version" \
    '[.data[] | select(.metadata.jsc_catalog == $catalog)] | length' "$search_file")
  if [[ "$count" -gt 1 ]]; then
    echo "Refusing ambiguous live Stripe products for pack $pack_id." >&2
    exit 4
  fi
  if [[ "$count" -eq 1 ]]; then
    jq -r --arg catalog "$catalog_version" \
      '.data[] | select(.metadata.jsc_catalog == $catalog) | .id' "$search_file"
    return
  fi

  product_file="$scratch/product-$pack_id.json"
  stripe_post /v1/products "jsc:$catalog_version:product:$pack_id" \
    --data-urlencode "name=Job Seeker Copilot $name" \
    --data-urlencode "description=$generations Job Seeker Copilot document generations for creating tailored CVs and cover letters." \
    --data-urlencode "metadata[jsc_catalog]=$catalog_version" \
    --data-urlencode "metadata[jsc_pack_id]=$pack_id" \
    --data-urlencode "metadata[document_generations]=$generations" >"$product_file"
  jq -er '.id | select(test("^prod_[A-Za-z0-9]+$"))' "$product_file"
}

price_for() {
  pack_id=$1
  product_id=$2
  amount=$3
  price_file="$scratch/prices-$pack_id.json"
  stripe_get /v1/prices \
    --data-urlencode "product=$product_id" \
    --data-urlencode 'active=true' \
    --data-urlencode 'type=one_time' \
    --data-urlencode 'limit=100' >"$price_file"
  matching=$(jq --argjson amount "$amount" --arg pack "$pack_id" --arg catalog "$catalog_version" \
    '[.data[] | select(.currency == "gbp" and .unit_amount == $amount and
      .metadata.jsc_pack_id == $pack and .metadata.jsc_catalog == $catalog)]' "$price_file")
  count=$(jq 'length' <<<"$matching")
  if [[ "$count" -gt 1 ]]; then
    echo "Refusing ambiguous exact live Stripe prices for pack $pack_id." >&2
    exit 4
  fi
  if [[ "$count" -eq 1 ]]; then
    jq -r '.[0].id' <<<"$matching"
    return
  fi

  created_file="$scratch/price-$pack_id.json"
  stripe_post /v1/prices "jsc:$catalog_version:price:$pack_id:$amount" \
    --data-urlencode "product=$product_id" \
    --data-urlencode 'currency=gbp' \
    --data-urlencode "unit_amount=$amount" \
    --data-urlencode "nickname=JSC $pack_id $amount GBP minor units" \
    --data-urlencode "metadata[jsc_catalog]=$catalog_version" \
    --data-urlencode "metadata[jsc_pack_id]=$pack_id" >"$created_file"
  jq -er '.id | select(test("^price_[A-Za-z0-9]+$"))' "$created_file"
}

starter_product=$(product_for starter Starter 10)
active_product=$(product_for active Active 25)
power_product=$(product_for power Power 60)
starter_price=$(price_for starter "$starter_product" 499)
active_price=$(price_for active "$active_product" 1199)
power_price=$(price_for power "$power_product" 1999)

jq -n \
  --arg catalogVersion "$catalog_version" \
  --arg apiVersion "$api_version" \
  --arg starterProduct "$starter_product" --arg starterPrice "$starter_price" \
  --arg activeProduct "$active_product" --arg activePrice "$active_price" \
  --arg powerProduct "$power_product" --arg powerPrice "$power_price" \
  '{catalogVersion:$catalogVersion, apiVersion:$apiVersion, liveMode:true,
    products:[
      {id:"starter", documentGenerations:10, priceMinor:499, currency:"gbp", productId:$starterProduct, priceId:$starterPrice},
      {id:"active", documentGenerations:25, priceMinor:1199, currency:"gbp", productId:$activeProduct, priceId:$activePrice},
      {id:"power", documentGenerations:60, priceMinor:1999, currency:"gbp", productId:$powerProduct, priceId:$powerPrice}
    ]}' >"$output_file"

chmod 600 "$output_file"
echo "Reconciled the live JSC catalogue. Safe Product/Price IDs were written to $output_file; no key value was printed."
