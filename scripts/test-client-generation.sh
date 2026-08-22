#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

manifest() {
    local output="$1"
    (
        cd "$repository_root"
        find target/generated-sources/openapi/system-data-service/src/main/java \
            -type f -name '*.java' -print0 \
            | sort -z \
            | xargs -0 sha256sum \
            | sed 's#target/generated-sources/openapi/system-data-service/##'
    ) > "$output"
}

cd "$repository_root"
mvn -B --no-transfer-progress clean generate-sources
manifest "$temporary_dir/first"
test -s "$temporary_dir/first"
grep -F 'src/main/java/com/jobseekercopilot/generated/systemdataservice/api/FixtureControllerApi.java' \
    "$temporary_dir/first" >/dev/null
grep -F 'src/main/java/com/jobseekercopilot/generated/systemdataservice/model/FixtureStripeRequest.java' \
    "$temporary_dir/first" >/dev/null

mvn -B --no-transfer-progress clean generate-sources
manifest "$temporary_dir/second"
cmp "$temporary_dir/first" "$temporary_dir/second"

if find src -path '*generated/systemdataservice*' -print -quit | grep -q .; then
    echo "client generation policy: generated client source was committed" >&2
    exit 1
fi
if grep -q '<systemPath>' pom.xml; then
    echo "client generation policy: Maven systemPath dependency remains" >&2
    exit 1
fi

echo "client generation policy: System Data client generation is deterministic and source-only"
