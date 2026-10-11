#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly RUN_TOKEN="$(tr -d '-' < /proc/sys/kernel/random/uuid)"
readonly OWNER_LABEL="com.jaxon.resource-owner=${RUN_TOKEN}"
readonly NETWORK_NAME="jaxon-backup-restore-${RUN_TOKEN}"
readonly SOURCE_NAME="jaxon-backup-source-${RUN_TOKEN}"
readonly TARGET_NAME="jaxon-restore-target-${RUN_TOKEN}"
readonly APP_NAME="jaxon-backup-flyway-${RUN_TOKEN}"
readonly DATABASE_IMAGE="${1:-jaxon-postgres-pgvector-test:18.6-0.8.6}"
readonly APP_IMAGE="${2:-jaxon-backend:ci}"
readonly DATABASE_USER="jaxon_backup_test"
readonly DATABASE_PASSWORD="synthetic-backup-test-password"
readonly SOURCE_DATABASE="jaxon_backup_source"
readonly TARGET_DATABASE="jaxon_restore_target"
readonly SOURCE_ID="source-${RUN_TOKEN}"
readonly TARGET_ID="target-${RUN_TOKEN}"
readonly WORK_DIR="$(mktemp -d "/tmp/jaxon-backup-restore-${RUN_TOKEN}.XXXXXX")"
readonly ARTIFACT_NAME="jaxon-logical-${RUN_TOKEN}"
readonly BACKUP_FILE="${WORK_DIR}/${ARTIFACT_NAME}.dump"
readonly MANIFEST_FILE="${WORK_DIR}/${ARTIFACT_NAME}.manifest.json"

RESOURCES_CREATED=false

fail() {
  printf 'PostgreSQL backup/restore verification failed: %s\n' "$1" >&2
  exit 1
}

resource_is_owned() {
  local type="$1"
  local name="$2"
  local owner
  if [[ "${type}" == "container" ]]; then
    owner="$(docker inspect --format '{{index .Config.Labels "com.jaxon.resource-owner"}}' "${name}" 2>/dev/null || true)"
  else
    owner="$(docker network inspect --format '{{index .Labels "com.jaxon.resource-owner"}}' "${name}" 2>/dev/null || true)"
  fi
  [[ "${owner}" == "${RUN_TOKEN}" ]]
}

cleanup() {
  local name
  if [[ "${RESOURCES_CREATED}" == "true" ]]; then
    for name in "${APP_NAME}" "${SOURCE_NAME}" "${TARGET_NAME}"; do
      if resource_is_owned container "${name}"; then
        docker rm --force --volumes "${name}" >/dev/null 2>&1 || true
      elif docker inspect "${name}" >/dev/null 2>&1; then
        printf 'Skipping cleanup of unowned container %s.\n' "${name}" >&2
      fi
    done
    if resource_is_owned network "${NETWORK_NAME}"; then
      docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
    elif docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
      printf 'Skipping cleanup of unowned network %s.\n' "${NETWORK_NAME}" >&2
    fi
  fi
  if [[ "${WORK_DIR}" == "/tmp/jaxon-backup-restore-${RUN_TOKEN}."* && -d "${WORK_DIR}" ]]; then
    rm -rf -- "${WORK_DIR}"
  fi
}
trap cleanup EXIT

ensure_names_are_unused() {
  local name
  for name in "${APP_NAME}" "${SOURCE_NAME}" "${TARGET_NAME}"; do
    ! docker inspect "${name}" >/dev/null 2>&1 || fail "generated container name is already in use"
  done
  ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1 || fail "generated network name is already in use"
}

wait_for_database() {
  local container="$1"
  local database="$2"
  for _ in {1..60}; do
    if docker exec "${container}" pg_isready --username "${DATABASE_USER}" --dbname "${database}" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  docker logs "${container}" >&2
  fail "PostgreSQL did not become ready"
}

database_query() {
  local container="$1"
  local database="$2"
  local sql="$3"
  docker exec "${container}" psql --no-psqlrc --set ON_ERROR_STOP=1 --tuples-only --no-align \
    --username "${DATABASE_USER}" --dbname "${database}" --command "${sql}"
}

wait_for_flyway() {
  for _ in {1..90}; do
    if [[ "$(database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" "SELECT EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE success AND version = '20261011000000')" 2>/dev/null || true)" == "t" ]]; then
      return
    fi
    if [[ "$(docker inspect --format '{{.State.Running}}' "${APP_NAME}" 2>/dev/null || true)" != "true" ]]; then
      docker logs "${APP_NAME}" >&2 || true
      fail "backend stopped before Flyway completed"
    fi
    sleep 1
  done
  docker logs "${APP_NAME}" >&2
  fail "Flyway did not complete"
}

run_backup() {
  local artifact_name="$1"
  shift
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    --network "${NETWORK_NAME}" \
    --env HOME=/tmp \
    --env "PGHOST=${SOURCE_NAME}" \
    --env PGPORT=5432 \
    --env "PGDATABASE=${SOURCE_DATABASE}" \
    --env "PGUSER=${DATABASE_USER}" \
    --env "PGPASSWORD=${DATABASE_PASSWORD}" \
    --env "JAXON_BACKUP_SOURCE_ID=${SOURCE_ID}" \
    --volume "${ROOT_DIR}/scripts:/jaxon-scripts:ro" \
    --volume "${WORK_DIR}:/backup" \
    "$@" \
    --entrypoint /bin/bash \
    "${DATABASE_IMAGE}" \
    /jaxon-scripts/backup-postgresql.sh --output-dir /backup --artifact-name "${artifact_name}"
}

run_restore() {
  local backup_path="$1"
  local manifest_path="$2"
  local target_id="$3"
  shift 3
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    --network "${NETWORK_NAME}" \
    --env HOME=/tmp \
    --env "PGHOST=${TARGET_NAME}" \
    --env PGPORT=5432 \
    --env "PGDATABASE=${TARGET_DATABASE}" \
    --env "PGUSER=${DATABASE_USER}" \
    --env "PGPASSWORD=${DATABASE_PASSWORD}" \
    --env "JAXON_RESTORE_TARGET_ID=${target_id}" \
    --volume "${ROOT_DIR}/scripts:/jaxon-scripts:ro" \
    --volume "${WORK_DIR}:/backup" \
    "$@" \
    --entrypoint /bin/bash \
    "${DATABASE_IMAGE}" \
    /jaxon-scripts/restore-postgresql.sh --backup "${backup_path}" --manifest "${manifest_path}"
}

expect_failure() {
  local description="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    fail "negative case unexpectedly succeeded: ${description}"
  fi
  printf 'Negative case passed: %s\n' "${description}"
}

copy_case() {
  local case_name="$1"
  local case_dir="${WORK_DIR}/cases/${case_name}"
  mkdir -m 0700 -p -- "${case_dir}"
  cp -- "${BACKUP_FILE}" "${case_dir}/${ARTIFACT_NAME}.dump"
  cp -- "${MANIFEST_FILE}" "${case_dir}/${ARTIFACT_NAME}.manifest.json"
  chmod 0600 -- "${case_dir}/${ARTIFACT_NAME}.dump" "${case_dir}/${ARTIFACT_NAME}.manifest.json"
  printf '%s' "${case_dir}"
}

ensure_names_are_unused
chmod 0700 -- "${WORK_DIR}"
RESOURCES_CREATED=true
docker network create --label "${OWNER_LABEL}" "${NETWORK_NAME}" >/dev/null

docker run --detach \
  --name "${SOURCE_NAME}" \
  --label "${OWNER_LABEL}" \
  --network "${NETWORK_NAME}" \
  --tmpfs /var/lib/postgresql:rw,nosuid,nodev,size=512m \
  --env "POSTGRES_USER=${DATABASE_USER}" \
  --env "POSTGRES_PASSWORD=${DATABASE_PASSWORD}" \
  --env "POSTGRES_DB=${SOURCE_DATABASE}" \
  "${DATABASE_IMAGE}" >/dev/null

docker run --detach \
  --name "${TARGET_NAME}" \
  --label "${OWNER_LABEL}" \
  --network "${NETWORK_NAME}" \
  --tmpfs /var/lib/postgresql:rw,nosuid,nodev,size=512m \
  --env "POSTGRES_USER=${DATABASE_USER}" \
  --env "POSTGRES_PASSWORD=${DATABASE_PASSWORD}" \
  --env "POSTGRES_DB=${TARGET_DATABASE}" \
  "${DATABASE_IMAGE}" >/dev/null

wait_for_database "${SOURCE_NAME}" "${SOURCE_DATABASE}"
wait_for_database "${TARGET_NAME}" "${TARGET_DATABASE}"

docker run --detach \
  --name "${APP_NAME}" \
  --label "${OWNER_LABEL}" \
  --network "${NETWORK_NAME}" \
  --read-only \
  --tmpfs /tmp:rw,nosuid,nodev,noexec,size=64m \
  --env "JAXON_DB_URL=jdbc:postgresql://${SOURCE_NAME}:5432/${SOURCE_DATABASE}" \
  --env "JAXON_DB_USERNAME=${DATABASE_USER}" \
  --env "JAXON_DB_PASSWORD=${DATABASE_PASSWORD}" \
  "${APP_IMAGE}" >/dev/null
wait_for_flyway
docker stop --timeout 25 "${APP_NAME}" >/dev/null
docker rm --volumes "${APP_NAME}" >/dev/null

readonly EXPECTED_SCHEMAS=$'ai\naudit_reporting\nautomation\ncollector\ndiscovery\nidentity\ninventory\nsupport'
readonly SOURCE_SCHEMAS="$(database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('inventory','discovery','collector','support','ai','identity','automation','audit_reporting') ORDER BY schema_name")"
[[ "${SOURCE_SCHEMAS}" == "${EXPECTED_SCHEMAS}" ]] || fail "source schemas are incomplete after Flyway"

database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" \
  "CREATE TABLE public.jaxon_backup_restore_probe (probe_id integer PRIMARY KEY, marker text NOT NULL); INSERT INTO public.jaxon_backup_restore_probe VALUES (1, 'synthetic-alpha'), (2, 'synthetic-beta');" >/dev/null
database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" \
  "INSERT INTO inventory.assets (id, name, asset_type, status, created_at, updated_at, version) VALUES ('8b90cfac-9304-4a11-9422-7c834431f920', 'restore-inventory-probe', 'DESKTOP', 'ACTIVE', now(), now(), 0); INSERT INTO inventory.outbox_events (event_id, event_type, event_version, occurred_at, producer_module, actor_type, actor_id, resource_type, resource_id, resource_version, result, correlation_id, payload, next_attempt_at) VALUES ('fb7560d3-405a-4053-b948-5bb2d410ced4', 'ASSET_CREATED', '1.0', now(), 'INVENTORY', 'HUMAN_USER', '89c15d62-ae46-46aa-b985-a1b1a3be13a1', 'ASSET', '8b90cfac-9304-4a11-9422-7c834431f920', 0, 'SUCCESS', '4d8561cf-cf2b-47cf-98a5-abda0b6d4414', '{}'::jsonb, now());" >/dev/null

database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" "CREATE SCHEMA undeclared_backup_probe" >/dev/null
expect_failure "undeclared source schema" run_backup "undeclared-schema-${RUN_TOKEN}"
database_query "${SOURCE_NAME}" "${SOURCE_DATABASE}" "DROP SCHEMA undeclared_backup_probe" >/dev/null

run_backup "${ARTIFACT_NAME}"
[[ "$(stat -c '%a' -- "${BACKUP_FILE}")" == "600" ]] || fail "backup permissions are not 0600"
[[ "$(stat -c '%a' -- "${MANIFEST_FILE}")" == "600" ]] || fail "manifest permissions are not 0600"
docker run --rm --user "$(id -u):$(id -g)" --volume "${WORK_DIR}:/backup:ro" \
  --entrypoint pg_restore "${DATABASE_IMAGE}" --list "/backup/${ARTIFACT_NAME}.dump" >/dev/null

readonly ALTERED_DIR="$(copy_case altered-backup)"
printf 'tamper' >> "${ALTERED_DIR}/${ARTIFACT_NAME}.dump"
expect_failure "altered backup" run_restore "/backup/cases/altered-backup/${ARTIFACT_NAME}.dump" "/backup/cases/altered-backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

readonly HASH_DIR="$(copy_case incorrect-hash)"
sed -i -E 's/("sha256": ")[0-9a-f]{64}/\1ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff/' \
  "${HASH_DIR}/${ARTIFACT_NAME}.manifest.json"
expect_failure "incorrect manifest hash" run_restore "/backup/cases/incorrect-hash/${ARTIFACT_NAME}.dump" "/backup/cases/incorrect-hash/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

readonly ALTERED_MANIFEST_DIR="$(copy_case altered-manifest)"
sed -i '2i\  "unexpectedField": "tampered",' "${ALTERED_MANIFEST_DIR}/${ARTIFACT_NAME}.manifest.json"
expect_failure "altered manifest" run_restore "/backup/cases/altered-manifest/${ARTIFACT_NAME}.dump" "/backup/cases/altered-manifest/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

readonly INCOMPLETE_DIR="$(copy_case incomplete-manifest)"
sed -i '/"result"/d' "${INCOMPLETE_DIR}/${ARTIFACT_NAME}.manifest.json"
expect_failure "incomplete manifest" run_restore "/backup/cases/incomplete-manifest/${ARTIFACT_NAME}.dump" "/backup/cases/incomplete-manifest/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

expect_failure "missing backup file" run_restore "/backup/missing.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

readonly FORMAT_DIR="$(copy_case unsupported-format)"
sed -i 's/"backupFormat": "custom"/"backupFormat": "tar"/' "${FORMAT_DIR}/${ARTIFACT_NAME}.manifest.json"
expect_failure "unsupported backup format" run_restore "/backup/cases/unsupported-format/${ARTIFACT_NAME}.dump" "/backup/cases/unsupported-format/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

expect_failure "source and destination are identical" run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${SOURCE_ID}"

mkdir -m 0700 -- "${WORK_DIR}/fault-bin"
cat > "${WORK_DIR}/fault-bin/pg_dump" <<'EOF'
#!/bin/sh
if [ "${1:-}" = "--version" ]; then
  printf 'pg_dump (PostgreSQL) 18.6\n'
  exit 0
fi
exit 42
EOF
chmod 0755 -- "${WORK_DIR}/fault-bin/pg_dump"
expect_failure "pg_dump command failure" run_backup "failed-dump-${RUN_TOKEN}" \
  --env PATH=/backup/fault-bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
[[ ! -e "${WORK_DIR}/failed-dump-${RUN_TOKEN}.dump" && ! -e "${WORK_DIR}/failed-dump-${RUN_TOKEN}.manifest.json" ]] || \
  fail "failed pg_dump published an artifact"

expect_failure "missing required variables" docker run --rm \
  --user "$(id -u):$(id -g)" --network "${NETWORK_NAME}" --env HOME=/tmp \
  --volume "${ROOT_DIR}/scripts:/jaxon-scripts:ro" --volume "${WORK_DIR}:/backup" \
  --entrypoint /bin/bash "${DATABASE_IMAGE}" /jaxon-scripts/backup-postgresql.sh \
  --output-dir /backup --artifact-name "missing-env-${RUN_TOKEN}"

expect_failure "unauthorized overwrite" run_backup "${ARTIFACT_NAME}"

readonly PERMISSIONS_DIR="$(copy_case unsafe-permissions)"
chmod 0644 -- "${PERMISSIONS_DIR}/${ARTIFACT_NAME}.dump"
expect_failure "unsafe backup permissions" run_restore "/backup/cases/unsafe-permissions/${ARTIFACT_NAME}.dump" "/backup/cases/unsafe-permissions/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

readonly DIRECTORY_PERMISSIONS_DIR="$(copy_case unsafe-directory-permissions)"
chmod 0777 -- "${DIRECTORY_PERMISSIONS_DIR}"
expect_failure "unsafe input directory permissions" run_restore "/backup/cases/unsafe-directory-permissions/${ARTIFACT_NAME}.dump" "/backup/cases/unsafe-directory-permissions/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"
chmod 0700 -- "${DIRECTORY_PERMISSIONS_DIR}"

database_query "${TARGET_NAME}" postgres "ALTER DATABASE ${TARGET_DATABASE} SET default_transaction_read_only = on" >/dev/null
expect_failure "pg_restore command failure" run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"
database_query "${TARGET_NAME}" postgres "ALTER DATABASE ${TARGET_DATABASE} SET default_transaction_read_only = off" >/dev/null
readonly OBJECTS_AFTER_FAILED_RESTORE="$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname !~ '^pg_toast' AND c.relkind IN ('r','p','v','m','S','f')")"
[[ "${OBJECTS_AFTER_FAILED_RESTORE}" == "0" ]] || fail "failed restore left partial objects"

database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "CREATE FUNCTION public.non_empty_probe() RETURNS integer LANGUAGE SQL IMMUTABLE AS 'SELECT 1'" >/dev/null
expect_failure "function-only destination" run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"
database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "DROP FUNCTION public.non_empty_probe()" >/dev/null

database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT lo_create(12345)" >/dev/null
expect_failure "large-object-only destination" run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"
database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT lo_unlink(12345)" >/dev/null

readonly RESTORE_START="$(date +%s)"
run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"
readonly RESTORE_END="$(date +%s)"
readonly RESTORE_DURATION_SECONDS="$(( RESTORE_END - RESTORE_START ))"

readonly TARGET_SCHEMAS="$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('inventory','discovery','collector','support','ai','identity','automation','audit_reporting') ORDER BY schema_name")"
[[ "${TARGET_SCHEMAS}" == "${EXPECTED_SCHEMAS}" ]] || fail "restored schemas do not match"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SHOW server_version")" == 18.6* ]] || fail "target PostgreSQL version is not 18.6"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT extversion FROM pg_extension WHERE extname = 'vector'")" == "0.8.6" ]] || fail "restored pgvector version is not 0.8.6"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT NOT EXISTS (SELECT required.version FROM (VALUES ('20261006000000'), ('20261010000000'), ('20261011000000')) AS required(version) EXCEPT SELECT version FROM public.flyway_schema_history WHERE success)")" == "t" ]] || fail "restored Flyway history is incomplete"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT count(*) FROM public.flyway_schema_history WHERE NOT success")" == "0" ]] || fail "restored Flyway history contains failed migrations"
readonly RESTORED_DATA="$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT probe_id || ':' || marker FROM public.jaxon_backup_restore_probe ORDER BY probe_id")"
[[ "${RESTORED_DATA}" == $'1:synthetic-alpha\n2:synthetic-beta' ]] || fail "synthetic verification data does not match"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT name || ':' || asset_type || ':' || status || ':' || version FROM inventory.assets WHERE id = '8b90cfac-9304-4a11-9422-7c834431f920'")" == "restore-inventory-probe:DESKTOP:ACTIVE:0" ]] || fail "restored Inventory asset does not match"
[[ "$(database_query "${TARGET_NAME}" "${TARGET_DATABASE}" "SELECT event_type || ':' || resource_type || ':' || result FROM inventory.outbox_events WHERE resource_id = '8b90cfac-9304-4a11-9422-7c834431f920'")" == "ASSET_CREATED:ASSET:SUCCESS" ]] || fail "restored Inventory outbox event does not match"

expect_failure "non-empty destination" run_restore "/backup/${ARTIFACT_NAME}.dump" "/backup/${ARTIFACT_NAME}.manifest.json" "${TARGET_ID}"

printf 'PostgreSQL backup/restore verification passed (restore duration: %ss, run: %s).\n' \
  "${RESTORE_DURATION_SECONDS}" "${RUN_TOKEN}"
