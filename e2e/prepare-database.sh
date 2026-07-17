#!/usr/bin/env bash
set -euo pipefail

: "${E2E_DB_HOST:?E2E_DB_HOST is required}"
: "${E2E_DB_PORT:?E2E_DB_PORT is required}"
: "${E2E_DB_NAME:?E2E_DB_NAME is required}"
: "${E2E_DB_USER:?E2E_DB_USER is required}"
: "${E2E_SSO_TICKET:?E2E_SSO_TICKET is required}"
: "${E2E_SECOND_SSO_TICKET:?E2E_SECOND_SSO_TICKET is required}"

[[ "$E2E_DB_HOST" == '127.0.0.1' || "$E2E_DB_HOST" == 'localhost' || "$E2E_DB_HOST" == '::1' ]] || { echo 'E2E_DB_HOST must use a loopback host' >&2; exit 2; }
[[ "$E2E_DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'invalid E2E_DB_NAME' >&2; exit 2; }
[[ "$E2E_SSO_TICKET" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'invalid E2E_SSO_TICKET' >&2; exit 2; }
[[ "$E2E_SECOND_SSO_TICKET" =~ ^[A-Za-z0-9._-]+$ ]] || { echo 'invalid E2E_SECOND_SSO_TICKET' >&2; exit 2; }

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base_database="$repo/Emulator/src/main/resources/db/migration/V20260518000000__base_database.sql"

password=()
[[ -z "${E2E_DB_PASSWORD:-}" ]] || password=("--password=$E2E_DB_PASSWORD")
mysql "--host=$E2E_DB_HOST" "--port=$E2E_DB_PORT" "--user=$E2E_DB_USER" "${password[@]}" \
  "--database=$E2E_DB_NAME" --default-character-set=utf8mb4 < "$base_database"
{
  printf "SET @e2e_sso_ticket='%s';\n" "$E2E_SSO_TICKET"
  printf "SET @e2e_second_sso_ticket='%s';\n" "$E2E_SECOND_SSO_TICKET"
  cat "$repo/e2e/seed.sql"
} |
  mysql "--host=$E2E_DB_HOST" "--port=$E2E_DB_PORT" "--user=$E2E_DB_USER" "${password[@]}" "--database=$E2E_DB_NAME"
