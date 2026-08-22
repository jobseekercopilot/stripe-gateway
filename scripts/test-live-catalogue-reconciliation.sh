#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf -- "$temporary_dir"' EXIT

bash -n "$repository_root/scripts/reconcile-live-catalogue.sh"

printf '%s\n' 'STRIPE_LIVE_CATALOG_ADMIN_KEY=sk_live_fixture_only_not_a_real_key' >"$temporary_dir/.secrets.env"
chmod 644 "$temporary_dir/.secrets.env"
if "$repository_root/scripts/reconcile-live-catalogue.sh" \
    "$temporary_dir/.secrets.env" "$temporary_dir/catalogue.json" >/dev/null 2>&1; then
  echo "catalogue reconciler accepted an over-permissive secrets file" >&2
  exit 1
fi
chmod 600 "$temporary_dir/.secrets.env"

printf '%s\n' 'STRIPE_LIVE_CATALOG_ADMIN_KEY=' >"$temporary_dir/blank.env"
chmod 600 "$temporary_dir/blank.env"
if "$repository_root/scripts/reconcile-live-catalogue.sh" \
    "$temporary_dir/blank.env" "$temporary_dir/catalogue.json" >/dev/null 2>&1; then
  echo "catalogue reconciler accepted a blank live key" >&2
  exit 1
fi

if "$repository_root/scripts/reconcile-live-catalogue.sh" \
    "$temporary_dir/.secrets.env" "$temporary_dir/.secrets.env" >/dev/null 2>&1; then
  echo "catalogue reconciler accepted the secrets file as its output" >&2
  exit 1
fi

mkdir "$temporary_dir/bin"
cat >"$temporary_dir/bin/curl" <<'FIXTURE'
#!/usr/bin/env bash
set -euo pipefail
joined=" $* "
if [[ "$joined" == *"sk_live_"* || "$joined" == *"rk_live_"* ]]; then
  echo "Stripe credential was exposed in curl process arguments" >&2
  exit 91
fi
if [[ "$joined" == *"/v1/products/search"* ]]; then
  printf '%s\n' '{"data":[]}'
elif [[ "$joined" == *"/v1/products "* && "$joined" == *"Job Seeker Copilot Starter"* ]]; then
  printf '%s\n' '{"id":"prod_liveStarter"}'
elif [[ "$joined" == *"/v1/products "* && "$joined" == *"Job Seeker Copilot Active"* ]]; then
  printf '%s\n' '{"id":"prod_liveActive"}'
elif [[ "$joined" == *"/v1/products "* && "$joined" == *"Job Seeker Copilot Power"* ]]; then
  printf '%s\n' '{"id":"prod_livePower"}'
elif [[ "$joined" == *" --get "* && "$joined" == *"/v1/prices "* ]]; then
  printf '%s\n' '{"data":[]}'
elif [[ "$joined" == *"/v1/prices "* && "$joined" == *"unit_amount=499"* ]]; then
  printf '%s\n' '{"id":"price_liveStarter499"}'
elif [[ "$joined" == *"/v1/prices "* && "$joined" == *"unit_amount=1199"* ]]; then
  printf '%s\n' '{"id":"price_liveActive1199"}'
elif [[ "$joined" == *"/v1/prices "* && "$joined" == *"unit_amount=1999"* ]]; then
  printf '%s\n' '{"id":"price_livePower1999"}'
else
  echo "Unexpected fixture Stripe request" >&2
  exit 90
fi
FIXTURE
chmod 755 "$temporary_dir/bin/curl"

PATH="$temporary_dir/bin:$PATH" "$repository_root/scripts/reconcile-live-catalogue.sh" \
  "$temporary_dir/.secrets.env" "$temporary_dir/catalogue.json" >/dev/null

jq -e '
  .catalogVersion == "public-beta-2026-08-22" and .liveMode == true and
  .products == [
    {id:"starter",documentGenerations:10,priceMinor:499,currency:"gbp",productId:"prod_liveStarter",priceId:"price_liveStarter499"},
    {id:"active",documentGenerations:25,priceMinor:1199,currency:"gbp",productId:"prod_liveActive",priceId:"price_liveActive1199"},
    {id:"power",documentGenerations:60,priceMinor:1999,currency:"gbp",productId:"prod_livePower",priceId:"price_livePower1999"}
  ]' "$temporary_dir/catalogue.json" >/dev/null

test "$(stat -c '%a' "$temporary_dir/catalogue.json")" = 600
echo "live Stripe catalogue reconciliation safety tests passed"
