#!/usr/bin/env bash
set -Eeuo pipefail

readonly APP_IMAGE="${1:-jaxon-backend:ci}"
readonly DATABASE_IMAGE="${2:-jaxon-postgres-pgvector-test:18.6-0.8.6}"
readonly RUN_SUFFIX="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$"
readonly NETWORK_NAME="jaxon-backend-smoke-${RUN_SUFFIX}"
readonly DATABASE_NAME="jaxon-postgres-smoke-${RUN_SUFFIX}"
readonly APP_NAME="jaxon-backend-smoke-${RUN_SUFFIX}"
readonly DATABASE_USER="jaxon_smoke"
readonly DATABASE_PASSWORD="temporary-smoke-password"
readonly DATABASE_DATABASE="jaxon_smoke"

cleanup() {
  docker rm --force "${APP_NAME}" >/dev/null 2>&1 || true
  docker rm --force --volumes "${DATABASE_NAME}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  printf 'Container verification failed: %s\n' "$1" >&2
  exit 1
}

wait_for_database() {
  for _ in {1..60}; do
    if docker exec "${DATABASE_NAME}" pg_isready \
      --username "${DATABASE_USER}" --dbname "${DATABASE_DATABASE}" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  docker logs "${DATABASE_NAME}" >&2
  fail "PostgreSQL did not become ready"
}

wait_for_health() {
  local endpoint="$1"
  local response
  for _ in {1..90}; do
    if response="$(curl --fail --silent --show-error "http://127.0.0.1:${APP_PORT}${endpoint}" 2>/dev/null)"; then
      [[ "${response}" == *'"status":"UP"'* ]] || fail "unexpected response from ${endpoint}: ${response}"
      [[ "${response}" != *'"components"'* && "${response}" != *'"details"'* ]] || \
        fail "sensitive health details exposed by ${endpoint}: ${response}"
      return
    fi
    sleep 1
  done
  docker logs "${APP_NAME}" >&2
  fail "${endpoint} did not become healthy"
}

cleanup

[[ "$(docker image inspect --format '{{.Config.User}}' "${APP_IMAGE}")" == "10001:10001" ]] || \
  fail "backend runtime user is not 10001:10001"
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.title"}}' "${APP_IMAGE}")" == "J-AXON Backend" ]] || \
  fail "backend OCI title is missing"
[[ "$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.source"}}' "${APP_IMAGE}")" == "https://github.com/J-AXON-PLATFORM/BACKEND" ]] || \
  fail "backend OCI source is missing"

docker run --rm --entrypoint sh "${APP_IMAGE}" -c \
  'test "$(id -u)" -ne 0 && ! find /app -name ".env*" -o -name "*.pem" | grep -q .' || \
  fail "runtime identity or secret-file inspection failed"

docker network create "${NETWORK_NAME}" >/dev/null
docker run --detach \
  --name "${DATABASE_NAME}" \
  --network "${NETWORK_NAME}" \
  --tmpfs /var/lib/postgresql:rw,nosuid,nodev,size=512m \
  --env "POSTGRES_USER=${DATABASE_USER}" \
  --env "POSTGRES_PASSWORD=${DATABASE_PASSWORD}" \
  --env "POSTGRES_DB=${DATABASE_DATABASE}" \
  "${DATABASE_IMAGE}" >/dev/null
wait_for_database

docker run --detach \
  --name "${APP_NAME}" \
  --network "${NETWORK_NAME}" \
  --read-only \
  --tmpfs /tmp:rw,nosuid,nodev,noexec,size=64m \
  --publish 127.0.0.1::8080 \
  --env "JAXON_DB_URL=jdbc:postgresql://${DATABASE_NAME}:5432/${DATABASE_DATABASE}" \
  --env "JAXON_DB_USERNAME=${DATABASE_USER}" \
  --env "JAXON_DB_PASSWORD=${DATABASE_PASSWORD}" \
  "${APP_IMAGE}" >/dev/null

APP_PORT="$(docker port "${APP_NAME}" 8080/tcp | awk -F: 'NR == 1 {print $NF}')"
readonly APP_PORT
[[ -n "${APP_PORT}" ]] || fail "backend published port was not assigned"

wait_for_health "/actuator/health"
wait_for_health "/actuator/health/liveness"
wait_for_health "/actuator/health/readiness"

readonly SCHEMA_LIST="$(docker exec "${DATABASE_NAME}" psql \
  --username "${DATABASE_USER}" --dbname "${DATABASE_DATABASE}" --tuples-only --no-align \
  --command "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('inventory','discovery','collector','support','ai','identity','automation','audit_reporting') ORDER BY schema_name")"
readonly EXPECTED_SCHEMAS=$'ai\naudit_reporting\nautomation\ncollector\ndiscovery\nidentity\ninventory\nsupport'
[[ "${SCHEMA_LIST}" == "${EXPECTED_SCHEMAS}" ]] || fail "the approved schemas were not created"

readonly VECTOR_VERSION="$(docker exec "${DATABASE_NAME}" psql \
  --username "${DATABASE_USER}" --dbname "${DATABASE_DATABASE}" --tuples-only --no-align \
  --command "SELECT extversion FROM pg_extension WHERE extname = 'vector'")"
[[ "${VECTOR_VERSION}" == "0.8.6" ]] || fail "pgvector 0.8.6 was not activated"

readonly MIGRATION_COUNT="$(docker exec "${DATABASE_NAME}" psql \
  --username "${DATABASE_USER}" --dbname "${DATABASE_DATABASE}" --tuples-only --no-align \
  --command "SELECT count(*) FROM public.flyway_schema_history WHERE version = '20261006000000' AND success")"
[[ "${MIGRATION_COUNT}" == "1" ]] || fail "Flyway bootstrap migration was not applied"

docker stop --timeout 25 "${APP_NAME}" >/dev/null
readonly EXIT_CODE="$(docker inspect --format '{{.State.ExitCode}}' "${APP_NAME}")"
[[ "${EXIT_CODE}" == "0" || "${EXIT_CODE}" == "143" ]] || fail "unexpected backend exit code ${EXIT_CODE}"

if docker logs "${APP_NAME}" 2>&1 | grep -Fq "${DATABASE_PASSWORD}"; then
  fail "temporary credential appeared in backend logs"
fi

printf 'Backend container verification passed (exit code %s).\n' "${EXIT_CODE}"
