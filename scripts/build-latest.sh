#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
module_root="$repo_root/Emulator"
destination="$repo_root/Latest_Compiled_Version"
source_jar=""
skip_tests=false

usage() {
    echo "Usage: $0 [--destination DIR] [--source-jar JAR] [--skip-tests]"
}

while (($#)); do
    case "$1" in
        --destination)
            destination="${2:?Missing value for --destination}"
            shift 2
            ;;
        --source-jar)
            source_jar="${2:?Missing value for --source-jar}"
            shift 2
            ;;
        --skip-tests)
            skip_tests=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
done

if [[ -z "$source_jar" ]]; then
    maven_args=(-B clean package)
    if [[ "$skip_tests" == true ]]; then
        maven_args+=(-DskipTests)
    fi
    (
        cd "$module_root"
        ./mvnw "${maven_args[@]}"
    )

    artifact_id="$(
        cd "$module_root"
        ./mvnw -q help:evaluate -Dexpression=project.artifactId -DforceStdout
    )"
    version="$(
        cd "$module_root"
        ./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout
    )"
    source_jar="$module_root/target/$artifact_id-$version-jar-with-dependencies.jar"
fi

if [[ ! -f "$source_jar" ]]; then
    echo "Built JAR not found: $source_jar" >&2
    exit 1
fi

mkdir -p "$destination"
file_name="$(basename -- "$source_jar")"
final_jar="$destination/$file_name"
temporary_jar="$final_jar.tmp-$$"
final_hash="$final_jar.sha256"
temporary_hash="$final_hash.tmp-$$"

cleanup() {
    rm -f -- "$temporary_jar" "$temporary_hash"
}
trap cleanup EXIT

cp -- "$source_jar" "$temporary_jar"
mv -f -- "$temporary_jar" "$final_jar"

if command -v sha256sum >/dev/null 2>&1; then
    hash="$(sha256sum "$final_jar" | awk '{print $1}')"
else
    hash="$(shasum -a 256 "$final_jar" | awk '{print $1}')"
fi
printf '%s  %s\n' "$hash" "$file_name" > "$temporary_hash"
mv -f -- "$temporary_hash" "$final_hash"

echo "JAR: $final_jar"
echo "SHA256: $final_hash"
