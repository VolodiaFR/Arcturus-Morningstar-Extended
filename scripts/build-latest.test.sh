#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
temporary="$(mktemp -d "${TMPDIR:-/tmp}/polaris-build-latest.XXXXXX")"
source_directory="$temporary/source"
destination="$temporary/latest"
source_jar="$source_directory/Polaris-4.2.50-jar-with-dependencies.jar"

cleanup() {
    rm -rf -- "$temporary"
}
trap cleanup EXIT

mkdir -p "$source_directory"
printf '\001\002\003\004\005\377' > "$source_jar"
"$script_dir/build-latest.sh" --source-jar "$source_jar" --destination "$destination"

copy="$destination/$(basename -- "$source_jar")"
hash_file="$copy.sha256"
test -f "$copy"
test -f "$hash_file"
cmp "$source_jar" "$copy"

if command -v sha256sum >/dev/null 2>&1; then
    source_hash="$(sha256sum "$source_jar" | awk '{print $1}')"
else
    source_hash="$(shasum -a 256 "$source_jar" | awk '{print $1}')"
fi
expected="$source_hash  $(basename -- "$copy")"
actual="$(tr -d '\r\n' < "$hash_file")"
[[ "$actual" == "$expected" ]]

grep -q 'build-latest\.test\.sh' "$repo_root/.github/workflows/ci.yml"
grep -q 'sha256sum' "$repo_root/.github/workflows/build-release.yml"
grep -q '\.sha256' "$repo_root/.github/workflows/build-release.yml"

echo "build-latest contract verified."
