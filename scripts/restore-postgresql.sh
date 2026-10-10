#!/usr/bin/env bash
set -Eeuo pipefail

readonly EXPECTED_KEYS=$'manifestVersion\nartifactName\ncreatedAt\nsourceInstanceId\npostgresServerVersion\npgDumpVersion\nbackupFormat\nsizeBytes\nsha256\nflywayVersions\nschemas\npgvectorVersion\ndurationMs\nresult'
readonly EXPECTED_SCHEMAS_JSON='["ai","audit_reporting","automation","collector","discovery","identity","inventory","support"]'

BACKUP_FILE=""
MANIFEST_FILE=""
RESTORE_WORK_DIR=""

fail() {
  printf 'PostgreSQL restore rejected: %s\n' "$1" >&2
  exit 1
}

usage() {
  printf 'Usage: %s --backup ABSOLUTE_FILE --manifest ABSOLUTE_FILE\n' "${0##*/}" >&2
}

cleanup() {
  if [[ -n "${RESTORE_WORK_DIR}" && "${RESTORE_WORK_DIR}" == "/tmp/jaxon-postgresql-restore."* && -d "${RESTORE_WORK_DIR}" ]]; then
    rm -rf -- "${RESTORE_WORK_DIR}"
  fi
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

require_environment() {
  local name
  for name in PGHOST PGPORT PGDATABASE PGUSER JAXON_RESTORE_TARGET_ID; do
    [[ -n "${!name:-}" ]] || fail "required environment variable is missing: ${name}"
  done
  [[ "${PGPORT}" =~ ^[0-9]{1,5}$ ]] || fail "PGPORT must be numeric"
  [[ "${JAXON_RESTORE_TARGET_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || \
    fail "JAXON_RESTORE_TARGET_ID must be an opaque safe identifier"
}

validate_input_file() {
  local path="$1"
  local description="$2"
  local mode
  [[ "${path}" == /* ]] || fail "${description} path must be absolute"
  [[ -f "${path}" && ! -L "${path}" ]] || fail "${description} must be an existing regular non-symlink file"
  [[ "$(stat -c '%u' -- "${path}")" == "$(id -u)" ]] || fail "${description} must be owned by the current user"
  mode="$(stat -c '%a' -- "${path}")"
  [[ "${mode}" == "600" ]] || fail "${description} permissions must be 0600"
}

validate_input_directory() {
  local backup_directory
  local manifest_directory
  local mode
  local resolved

  backup_directory="$(dirname -- "${BACKUP_FILE}")"
  manifest_directory="$(dirname -- "${MANIFEST_FILE}")"
  [[ "${backup_directory}" == "${manifest_directory}" ]] || fail "backup and manifest must share a directory"
  [[ -d "${backup_directory}" && ! -L "${backup_directory}" ]] || fail "input directory must be a non-symlink directory"
  resolved="$(cd -- "${backup_directory}" && pwd -P)"
  [[ "${resolved}" == "${backup_directory}" ]] || fail "input directory must be canonical"
  [[ "$(stat -c '%u' -- "${resolved}")" == "$(id -u)" ]] || fail "input directory must be owned by the current user"
  mode="$(stat -c '%a' -- "${resolved}")"
  (( (8#${mode} & 0022) == 0 )) || fail "input directory must not be group/world writable"
}

manifest_string() {
  local key="$1"
  local value
  local count
  count="$(grep -cE "^  \"${key}\": \"[^\"]*\",?$" "${MANIFEST_FILE}" || true)"
  [[ "${count}" == "1" ]] || fail "manifest field is missing or malformed: ${key}"
  value="$(sed -nE "s/^  \"${key}\": \"([^\"]*)\",?$/\\1/p" "${MANIFEST_FILE}")"
  printf '%s' "${value}"
}

manifest_number() {
  local key="$1"
  local value
  local count
  count="$(grep -cE "^  \"${key}\": [0-9]+,?$" "${MANIFEST_FILE}" || true)"
  [[ "${count}" == "1" ]] || fail "manifest numeric field is missing or malformed: ${key}"
  value="$(sed -nE "s/^  \"${key}\": ([0-9]+),?$/\\1/p" "${MANIFEST_FILE}")"
  printf '%s' "${value}"
}

manifest_array() {
  local key="$1"
  local value
  local count
  count="$(grep -cE "^  \"${key}\": \[[^]]*\],?$" "${MANIFEST_FILE}" || true)"
  [[ "${count}" == "1" ]] || fail "manifest array field is missing or malformed: ${key}"
  value="$(sed -nE "s/^  \"${key}\": (\[[^]]*\]),?$/\\1/p" "${MANIFEST_FILE}")"
  printf '%s' "${value}"
}

query_destination() {
  local sql="$1"
  local value
  if ! value="$(psql --no-password --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 \
      --dbname "${PGDATABASE}" --command "${sql}" 2>/dev/null)"; then
    fail "destination database query failed"
  fi
  printf '%s' "${value}"
}

while (( $# > 0 )); do
  case "$1" in
    --backup)
      (( $# >= 2 )) || fail "--backup requires a value"
      BACKUP_FILE="$2"
      shift 2
      ;;
    --manifest)
      (( $# >= 2 )) || fail "--manifest requires a value"
      MANIFEST_FILE="$2"
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

require_command psql
require_command pg_restore
require_command sha256sum
require_command stat
require_command grep
require_command sed
require_command wc
require_command awk
require_command dirname
require_command cp
require_command mktemp
require_environment
[[ -n "${BACKUP_FILE}" && -n "${MANIFEST_FILE}" ]] || fail "backup and manifest are required"
validate_input_file "${BACKUP_FILE}" "backup"
validate_input_file "${MANIFEST_FILE}" "manifest"
validate_input_directory

umask 077
RESTORE_WORK_DIR="$(mktemp -d '/tmp/jaxon-postgresql-restore.XXXXXX')"
cp --no-dereference -- "${BACKUP_FILE}" "${RESTORE_WORK_DIR}/${BACKUP_FILE##*/}"
cp --no-dereference -- "${MANIFEST_FILE}" "${RESTORE_WORK_DIR}/${MANIFEST_FILE##*/}"
BACKUP_FILE="${RESTORE_WORK_DIR}/${BACKUP_FILE##*/}"
MANIFEST_FILE="${RESTORE_WORK_DIR}/${MANIFEST_FILE##*/}"
chmod 0600 -- "${BACKUP_FILE}" "${MANIFEST_FILE}"
validate_input_file "${BACKUP_FILE}" "staged backup"
validate_input_file "${MANIFEST_FILE}" "staged manifest"

readonly ACTUAL_KEYS="$(sed -nE 's/^  "([A-Za-z][A-Za-z0-9]*)":.*$/\1/p' "${MANIFEST_FILE}")"
[[ "${ACTUAL_KEYS}" == "${EXPECTED_KEYS}" ]] || fail "manifest fields are incomplete, duplicated, reordered, or unsupported"
[[ "$(wc -l < "${MANIFEST_FILE}")" == "16" ]] || fail "manifest contains unexpected content"
[[ "$(sed -n '1p' "${MANIFEST_FILE}")" == "{" && "$(sed -n '$p' "${MANIFEST_FILE}")" == "}" ]] || \
  fail "manifest JSON envelope is malformed"
[[ "$(sed -n '2,14p' "${MANIFEST_FILE}" | grep -cE ',$' || true)" == "13" ]] || \
  fail "manifest JSON separators are malformed"
[[ "$(sed -n '15p' "${MANIFEST_FILE}")" != *, ]] || fail "manifest JSON terminator is malformed"

readonly MANIFEST_VERSION="$(manifest_string manifestVersion)"
readonly ARTIFACT_NAME="$(manifest_string artifactName)"
readonly CREATED_AT="$(manifest_string createdAt)"
readonly SOURCE_INSTANCE_ID="$(manifest_string sourceInstanceId)"
readonly SERVER_VERSION="$(manifest_string postgresServerVersion)"
readonly DUMP_VERSION="$(manifest_string pgDumpVersion)"
readonly BACKUP_FORMAT="$(manifest_string backupFormat)"
readonly EXPECTED_SIZE="$(manifest_number sizeBytes)"
readonly EXPECTED_SHA256="$(manifest_string sha256)"
readonly FLYWAY_VERSIONS="$(manifest_array flywayVersions)"
readonly SCHEMAS="$(manifest_array schemas)"
readonly VECTOR_VERSION="$(manifest_string pgvectorVersion)"
readonly DURATION_MS="$(manifest_number durationMs)"
readonly RESULT="$(manifest_string result)"

[[ "${MANIFEST_VERSION}" == "1.0" ]] || fail "manifest version is unsupported"
[[ "${ARTIFACT_NAME}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}\.dump$ ]] || fail "manifest artifact name is unsafe"
[[ "${ARTIFACT_NAME}" == "${BACKUP_FILE##*/}" ]] || fail "backup filename does not match manifest"
[[ "${CREATED_AT}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || fail "manifest timestamp is not UTC"
[[ "${SOURCE_INSTANCE_ID}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]] || fail "source instance identifier is unsafe"
[[ "${SOURCE_INSTANCE_ID}" != "${JAXON_RESTORE_TARGET_ID}" ]] || fail "source and destination identifiers must differ"
[[ "${SERVER_VERSION}" =~ ^18\.[0-9]+([A-Za-z0-9 ._()+-]*)$ ]] || fail "manifest PostgreSQL version is unsupported"
[[ "${DUMP_VERSION}" =~ ^pg_dump\ \(PostgreSQL\)\ 18\.[0-9]+([A-Za-z0-9 ._()+-]*)$ ]] || fail "manifest pg_dump version is unsupported"
[[ "${BACKUP_FORMAT}" == "custom" ]] || fail "backup format is unsupported"
[[ "${EXPECTED_SHA256}" =~ ^[0-9a-f]{64}$ ]] || fail "manifest checksum is malformed"
[[ "${FLYWAY_VERSIONS}" =~ ^\[\"[A-Za-z0-9._-]+\"(,\"[A-Za-z0-9._-]+\")*\]$ ]] || fail "manifest Flyway versions are malformed"
[[ "${SCHEMAS}" == "${EXPECTED_SCHEMAS_JSON}" ]] || fail "manifest schemas do not match J-AXON baseline"
[[ "${VECTOR_VERSION}" == "0.8.6" ]] || fail "manifest pgvector version is unsupported"
[[ "${DURATION_MS}" =~ ^[0-9]+$ ]] || fail "manifest duration is malformed"
[[ "${RESULT}" == "SUCCESS" ]] || fail "manifest result is not successful"

readonly ACTUAL_SIZE="$(stat -c '%s' -- "${BACKUP_FILE}")"
[[ "${ACTUAL_SIZE}" == "${EXPECTED_SIZE}" ]] || fail "backup size does not match manifest"
readonly ACTUAL_SHA256="$(sha256sum -- "${BACKUP_FILE}" | awk '{print $1}')"
[[ "${ACTUAL_SHA256}" == "${EXPECTED_SHA256}" ]] || fail "backup SHA-256 does not match manifest"
pg_restore --list "${BACKUP_FILE}" >/dev/null 2>&1 || fail "backup archive cannot be listed"
[[ "$(pg_restore --version)" =~ ^pg_restore\ \(PostgreSQL\)\ 18\.[0-9]+ ]] || fail "pg_restore 18 is required"

readonly TARGET_SERVER_VERSION="$(query_destination 'SHOW server_version')"
[[ "${TARGET_SERVER_VERSION}" =~ ^18\.[0-9]+([A-Za-z0-9 ._()+-]*)$ ]] || fail "destination PostgreSQL version is unsupported"
readonly TARGET_VECTOR_VERSION="$(query_destination "SELECT default_version FROM pg_available_extensions WHERE name = 'vector'")"
[[ "${TARGET_VECTOR_VERSION}" == "0.8.6" ]] || fail "destination pgvector 0.8.6 is unavailable"

readonly DESTINATION_CONNECTIONS="$(query_destination "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()")"
[[ "${DESTINATION_CONNECTIONS}" == "0" ]] || fail "destination database has another active connection"
readonly DESTINATION_OBJECTS="$(query_destination "SELECT (SELECT count(*) FROM pg_namespace WHERE nspname NOT IN ('public','pg_catalog','information_schema') AND nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_extension WHERE extname <> 'plpgsql') + (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast' AND t.typrelid = 0) + (SELECT count(*) FROM pg_collation c JOIN pg_namespace n ON n.oid = c.collnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_conversion c JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_operator o JOIN pg_namespace n ON n.oid = o.oprnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast') + (SELECT count(*) FROM pg_largeobject_metadata) + (SELECT count(*) FROM pg_default_acl) + (SELECT count(*) FROM pg_publication) + (SELECT count(*) FROM pg_foreign_data_wrapper) + (SELECT count(*) FROM pg_foreign_server) + (SELECT count(*) FROM pg_event_trigger)")"
[[ "${DESTINATION_OBJECTS}" == "0" ]] || fail "destination database is not empty"

if ! pg_restore --exit-on-error --single-transaction --no-owner --no-privileges \
    --dbname "${PGDATABASE}" "${BACKUP_FILE}" 2>/dev/null; then
  fail "pg_restore did not complete; transaction was rolled back"
fi

printf 'PostgreSQL logical restore completed for artifact: %s\n' "${ARTIFACT_NAME}"
