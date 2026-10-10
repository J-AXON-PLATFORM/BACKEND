#!/usr/bin/env bash
set -Eeuo pipefail

readonly MANIFEST_VERSION="1.0"
readonly EXPECTED_SCHEMAS_CSV="ai,audit_reporting,automation,collector,discovery,identity,inventory,support"
readonly EXPECTED_SCHEMAS_JSON='["ai","audit_reporting","automation","collector","discovery","identity","inventory","support"]'

OUTPUT_DIR=""
ARTIFACT_NAME=""
TEMP_BACKUP=""
TEMP_MANIFEST=""
FINAL_BACKUP=""
FINAL_MANIFEST=""
BACKUP_PUBLISHED=false
MANIFEST_PUBLISHED=false

fail() {
  printf 'PostgreSQL backup failed: %s\n' "$1" >&2
  exit 1
}

usage() {
  printf 'Usage: %s --output-dir ABSOLUTE_DIRECTORY --artifact-name SAFE_NAME\n' "${0##*/}" >&2
}

cleanup() {
  if [[ -n "${TEMP_BACKUP}" && "${TEMP_BACKUP}" == "${OUTPUT_DIR}/."* ]]; then
    rm -f -- "${TEMP_BACKUP}"
  fi
  if [[ -n "${TEMP_MANIFEST}" && "${TEMP_MANIFEST}" == "${OUTPUT_DIR}/."* ]]; then
    rm -f -- "${TEMP_MANIFEST}"
  fi
  if [[ "${BACKUP_PUBLISHED}" == "true" && "${MANIFEST_PUBLISHED}" != "true" && -n "${FINAL_BACKUP}" ]]; then
    rm -f -- "${FINAL_BACKUP}"
  fi
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

require_environment() {
  local name
  for name in PGHOST PGPORT PGDATABASE PGUSER JAXON_BACKUP_SOURCE_ID; do
    [[ -n "${!name:-}" ]] || fail "required environment variable is missing: ${name}"
  done
  [[ "${PGPORT}" =~ ^[0-9]{1,5}$ ]] || fail "PGPORT must be numeric"
  [[ "${JAXON_BACKUP_SOURCE_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || \
    fail "JAXON_BACKUP_SOURCE_ID must be an opaque safe identifier"
}

validate_output_directory() {
  local owner
  local mode
  local resolved

  [[ -n "${OUTPUT_DIR}" ]] || fail "--output-dir is required"
  [[ "${OUTPUT_DIR}" == /* ]] || fail "output directory must be absolute"
  [[ -d "${OUTPUT_DIR}" && ! -L "${OUTPUT_DIR}" ]] || fail "output directory must be an existing non-symlink directory"
  resolved="$(cd -- "${OUTPUT_DIR}" && pwd -P)"
  [[ "${resolved}" == "${OUTPUT_DIR}" ]] || fail "output directory must be canonical"
  [[ "${resolved}" != "/" && "${resolved}" != "${HOME:-/}" && "${resolved}" != "$(pwd -P)" ]] || \
    fail "output directory is too broad"
  [[ ! -e "${resolved}/.git" && ! -e "${resolved}/pom.xml" ]] || \
    fail "output directory must not be a workspace root"
  owner="$(stat -c '%u' -- "${resolved}")"
  [[ "${owner}" == "$(id -u)" ]] || fail "output directory must be owned by the current user"
  mode="$(stat -c '%a' -- "${resolved}")"
  (( (8#${mode} & 0022) == 0 )) || fail "output directory must not be group/world writable"
  OUTPUT_DIR="${resolved}"
}

query_database() {
  local sql="$1"
  local value
  if ! value="$(psql --no-password --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 \
      --dbname "${PGDATABASE}" --command "${sql}" 2>/dev/null)"; then
    fail "database metadata query failed"
  fi
  printf '%s' "${value}"
}

json_array_from_lines() {
  local value
  local separator=""
  printf '['
  while IFS= read -r value; do
    [[ -z "${value}" ]] && continue
    [[ "${value}" =~ ^[A-Za-z0-9._-]+$ ]] || fail "Flyway version contains unsupported characters"
    printf '%s"%s"' "${separator}" "${value}"
    separator=','
  done
  printf ']'
}

while (( $# > 0 )); do
  case "$1" in
    --output-dir)
      (( $# >= 2 )) || fail "--output-dir requires a value"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --artifact-name)
      (( $# >= 2 )) || fail "--artifact-name requires a value"
      ARTIFACT_NAME="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "unsupported argument"
      ;;
  esac
done

umask 077
require_command psql
require_command pg_dump
require_command pg_restore
require_command sha256sum
require_command stat
require_command awk
require_environment
validate_output_directory
[[ "${ARTIFACT_NAME}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || fail "artifact name is not safe"

FINAL_BACKUP="${OUTPUT_DIR}/${ARTIFACT_NAME}.dump"
FINAL_MANIFEST="${OUTPUT_DIR}/${ARTIFACT_NAME}.manifest.json"
[[ ! -e "${FINAL_BACKUP}" && ! -e "${FINAL_MANIFEST}" ]] || fail "backup or manifest already exists"

readonly SERVER_VERSION="$(query_database 'SHOW server_version')"
[[ "${SERVER_VERSION}" =~ ^18\.[0-9]+([A-Za-z0-9 ._()+-]*)$ ]] || fail "PostgreSQL server version is unsupported"
readonly DUMP_VERSION="$(pg_dump --version)"
[[ "${DUMP_VERSION}" =~ ^pg_dump\ \(PostgreSQL\)\ 18\.[0-9]+ ]] || fail "pg_dump 18 is required"
readonly VECTOR_VERSION="$(query_database "SELECT extversion FROM pg_extension WHERE extname = 'vector'")"
[[ "${VECTOR_VERSION}" == "0.8.6" ]] || fail "pgvector 0.8.6 is required"
readonly SCHEMAS="$(query_database "SELECT string_agg(schema_name, ',' ORDER BY schema_name) FROM information_schema.schemata WHERE schema_name IN ('inventory','discovery','collector','support','ai','identity','automation','audit_reporting')")"
[[ "${SCHEMAS}" == "${EXPECTED_SCHEMAS_CSV}" ]] || fail "approved J-AXON schemas are incomplete"
readonly USER_SCHEMAS="$(query_database "SELECT string_agg(schema_name, ',' ORDER BY schema_name) FROM information_schema.schemata WHERE schema_name <> 'public' AND schema_name <> 'information_schema' AND schema_name !~ '^pg_'")"
[[ "${USER_SCHEMAS}" == "${EXPECTED_SCHEMAS_CSV}" ]] || fail "database contains an undeclared user schema"
readonly FLYWAY_VERSION_LINES="$(query_database 'SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank')"
[[ -n "${FLYWAY_VERSION_LINES}" ]] || fail "no successful Flyway version was found"
readonly FLYWAY_VERSIONS_JSON="$(json_array_from_lines <<< "${FLYWAY_VERSION_LINES}")"

TEMP_BACKUP="$(mktemp --tmpdir="${OUTPUT_DIR}" ".${ARTIFACT_NAME}.dump.tmp.XXXXXX")"
TEMP_MANIFEST="$(mktemp --tmpdir="${OUTPUT_DIR}" ".${ARTIFACT_NAME}.manifest.tmp.XXXXXX")"
chmod 0600 -- "${TEMP_BACKUP}" "${TEMP_MANIFEST}"

readonly START_SECONDS="$(date +%s)"
if ! pg_dump --format=custom --no-owner --no-privileges --file "${TEMP_BACKUP}" --dbname "${PGDATABASE}" 2>/dev/null; then
  fail "pg_dump did not complete successfully"
fi
readonly END_SECONDS="$(date +%s)"
readonly DURATION_MS="$(( (END_SECONDS - START_SECONDS) * 1000 ))"

[[ "$(query_database 'SHOW server_version')" == "${SERVER_VERSION}" ]] || fail "PostgreSQL version changed during backup"
[[ "$(query_database "SELECT extversion FROM pg_extension WHERE extname = 'vector'")" == "${VECTOR_VERSION}" ]] || fail "pgvector version changed during backup"
[[ "$(query_database "SELECT string_agg(schema_name, ',' ORDER BY schema_name) FROM information_schema.schemata WHERE schema_name <> 'public' AND schema_name <> 'information_schema' AND schema_name !~ '^pg_'")" == "${USER_SCHEMAS}" ]] || fail "user schemas changed during backup"
[[ "$(query_database 'SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank')" == "${FLYWAY_VERSION_LINES}" ]] || fail "Flyway history changed during backup"

pg_restore --list "${TEMP_BACKUP}" >/dev/null 2>&1 || fail "pg_restore could not list the backup"
readonly SIZE_BYTES="$(stat -c '%s' -- "${TEMP_BACKUP}")"
[[ "${SIZE_BYTES}" =~ ^[1-9][0-9]*$ ]] || fail "backup size is invalid"
readonly SHA256="$(sha256sum -- "${TEMP_BACKUP}" | awk '{print $1}')"
[[ "${SHA256}" =~ ^[0-9a-f]{64}$ ]] || fail "backup checksum is invalid"
readonly CREATED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

cat > "${TEMP_MANIFEST}" <<EOF
{
  "manifestVersion": "${MANIFEST_VERSION}",
  "artifactName": "${ARTIFACT_NAME}.dump",
  "createdAt": "${CREATED_AT}",
  "sourceInstanceId": "${JAXON_BACKUP_SOURCE_ID}",
  "postgresServerVersion": "${SERVER_VERSION}",
  "pgDumpVersion": "${DUMP_VERSION}",
  "backupFormat": "custom",
  "sizeBytes": ${SIZE_BYTES},
  "sha256": "${SHA256}",
  "flywayVersions": ${FLYWAY_VERSIONS_JSON},
  "schemas": ${EXPECTED_SCHEMAS_JSON},
  "pgvectorVersion": "${VECTOR_VERSION}",
  "durationMs": ${DURATION_MS},
  "result": "SUCCESS"
}
EOF
chmod 0600 -- "${TEMP_MANIFEST}"

mv --no-clobber -- "${TEMP_BACKUP}" "${FINAL_BACKUP}"
[[ ! -e "${TEMP_BACKUP}" ]] || fail "backup destination appeared concurrently"
BACKUP_PUBLISHED=true
mv --no-clobber -- "${TEMP_MANIFEST}" "${FINAL_MANIFEST}"
[[ ! -e "${TEMP_MANIFEST}" ]] || fail "manifest destination appeared concurrently"
MANIFEST_PUBLISHED=true

printf 'PostgreSQL logical backup created: %s\n' "${ARTIFACT_NAME}"
