#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly RUN_TOKEN="$(tr -d '-' < /proc/sys/kernel/random/uuid)"
readonly PROJECT_NAME="jaxon-compose-smoke-${RUN_TOKEN}"
readonly DATABASE_CONTAINER_NAME="${PROJECT_NAME}-database-1"
readonly APP_CONTAINER_NAME="${PROJECT_NAME}-backend-1"
readonly SFTPGO_CONTAINER_NAME="${PROJECT_NAME}-sftpgo-1"
readonly DATABASE_VOLUME_NAME="${PROJECT_NAME}_jaxon-postgres-data"
readonly MAILBOX_VOLUME_NAME="${PROJECT_NAME}_jaxon-sftpgo-mailboxes"
readonly TASK_VOLUME_NAME="${PROJECT_NAME}_jaxon-sftpgo-tasks"
readonly NETWORK_NAME="${PROJECT_NAME}_jaxon-local"
readonly DATABASE_IMAGE="${1:-jaxon-postgres-pgvector-test:18.6-0.8.6}"
readonly APP_IMAGE="${2:-jaxon-backend:local}"
readonly SFTPGO_IMAGE="${3:-jaxon-sftpgo:2.7.6-local}"

export JAXON_DATABASE_IMAGE="${DATABASE_IMAGE}"
export JAXON_BACKEND_IMAGE="${APP_IMAGE}"
export JAXON_SFTPGO_IMAGE="${SFTPGO_IMAGE}"
export JAXON_DB_HOST_PORT=0
export JAXON_BACKEND_HOST_PORT=0
export JAXON_SFTP_HOST_PORT=0
export JAXON_DB_NAME=jaxon_local
export JAXON_DB_USERNAME=jaxon_local
export JAXON_DB_PASSWORD=local-only-example-password
export JAXON_DB_URL=jdbc:postgresql://127.0.0.1:5432/jaxon_local
export JAXON_ENVIRONMENT=local
export JAXON_RESOURCE_OWNER="${RUN_TOKEN}"

RESOURCES_CREATED=false

readonly -a COMPOSE=(
  docker compose
  --project-directory "${ROOT_DIR}"
  --file "${ROOT_DIR}/compose.yaml"
  --project-name "${PROJECT_NAME}"
  --env-file "${ROOT_DIR}/.env.example"
)

cleanup() {
  if [[ "${RESOURCES_CREATED}" != "true" ]]; then
    return
  fi
  if ! project_resources_are_owned; then
    printf 'Skipping cleanup: project %s contains a resource not owned by this run.\n' "${PROJECT_NAME}" >&2
    return
  fi
  "${COMPOSE[@]}" --profile application down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  printf 'Compose verification failed: %s\n' "$1" >&2
  exit 1
}

project_resources_are_owned() {
  local resource_id
  local owner

  while IFS= read -r resource_id; do
    [[ -z "${resource_id}" ]] && continue
    owner="$(docker inspect --format '{{index .Config.Labels "com.jaxon.resource-owner"}}' "${resource_id}")"
    [[ "${owner}" == "${RUN_TOKEN}" ]] || return 1
  done < <(docker container ls --all --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")

  while IFS= read -r resource_id; do
    [[ -z "${resource_id}" ]] && continue
    owner="$(docker volume inspect --format '{{index .Labels "com.jaxon.resource-owner"}}' "${resource_id}")"
    [[ "${owner}" == "${RUN_TOKEN}" ]] || return 1
  done < <(docker volume ls --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")

  while IFS= read -r resource_id; do
    [[ -z "${resource_id}" ]] && continue
    owner="$(docker network inspect --format '{{index .Labels "com.jaxon.resource-owner"}}' "${resource_id}")"
    [[ "${owner}" == "${RUN_TOKEN}" ]] || return 1
  done < <(docker network ls --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")

  if docker volume inspect "${DATABASE_VOLUME_NAME}" >/dev/null 2>&1; then
    owner="$(docker volume inspect --format '{{index .Labels "com.jaxon.resource-owner"}}' "${DATABASE_VOLUME_NAME}")"
    [[ "${owner}" == "${RUN_TOKEN}" ]] || return 1
  fi
  if docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1; then
    owner="$(docker network inspect --format '{{index .Labels "com.jaxon.resource-owner"}}' "${NETWORK_NAME}")"
    [[ "${owner}" == "${RUN_TOKEN}" ]] || return 1
  fi
}

ensure_project_is_unused() {
  local container_name

  [[ -z "$(docker container ls --all --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")" ]] || \
    fail "generated project name already has containers"
  [[ -z "$(docker volume ls --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")" ]] || \
    fail "generated project name already has volumes"
  [[ -z "$(docker network ls --quiet --filter "label=com.docker.compose.project=${PROJECT_NAME}")" ]] || \
    fail "generated project name already has networks"
  for container_name in "${DATABASE_CONTAINER_NAME}" "${SFTPGO_CONTAINER_NAME}" "${APP_CONTAINER_NAME}"; do
    ! docker container inspect "${container_name}" >/dev/null 2>&1 || \
      fail "generated project name already has service container ${container_name}"
  done
  ! docker volume inspect "${DATABASE_VOLUME_NAME}" >/dev/null 2>&1 || \
    fail "generated project name already has the expected volume"
  ! docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1 || \
    fail "generated project name already has the expected network"
}

wait_for_health() {
  local endpoint="$1"
  local response
  for _ in {1..90}; do
    if response="$(curl --fail --silent --show-error "http://${APP_BINDING}${endpoint}" 2>/dev/null)"; then
      [[ "${response}" == *'"status":"UP"'* ]] || fail "unexpected response from ${endpoint}: ${response}"
      [[ "${response}" != *'"components"'* && "${response}" != *'"details"'* ]] || \
        fail "sensitive health details exposed by ${endpoint}: ${response}"
      return
    fi
    sleep 1
  done
  "${COMPOSE[@]}" --profile application logs backend >&2
  fail "${endpoint} did not become healthy"
}

ensure_project_is_unused
"${COMPOSE[@]}" config --quiet

if ! docker image inspect "${DATABASE_IMAGE}" >/dev/null 2>&1; then
  "${COMPOSE[@]}" build database
fi
if ! docker image inspect "${APP_IMAGE}" >/dev/null 2>&1; then
  "${COMPOSE[@]}" --profile application build backend
fi
if ! docker image inspect "${SFTPGO_IMAGE}" >/dev/null 2>&1; then
  "${COMPOSE[@]}" build sftpgo
fi

RESOURCES_CREATED=true
"${COMPOSE[@]}" up --detach --wait --no-build database sftpgo

mapfile -t RUNNING_SERVICES < <("${COMPOSE[@]}" ps --services --status running)
[[ "${#RUNNING_SERVICES[@]}" -eq 2 ]] || fail "the default project must start database and sftpgo"
[[ " ${RUNNING_SERVICES[*]} " == *" database "* && " ${RUNNING_SERVICES[*]} " == *" sftpgo "* ]] || \
  fail "the default project did not start database and sftpgo"

DATABASE_CONTAINER="$("${COMPOSE[@]}" ps --quiet database)"
[[ -n "${DATABASE_CONTAINER}" ]] || fail "database container was not created"

readonly DATABASE_BINDING="$("${COMPOSE[@]}" port database 5432)"
[[ "${DATABASE_BINDING}" =~ ^127\.0\.0\.1:[0-9]+$ ]] || \
  fail "database port is not bound exclusively to loopback: ${DATABASE_BINDING}"

readonly SFTPGO_CONTAINER="$("${COMPOSE[@]}" ps --quiet sftpgo)"
[[ -n "${SFTPGO_CONTAINER}" ]] || fail "sftpgo container was not created"
[[ "$(docker inspect --format '{{.Config.User}}' "${SFTPGO_CONTAINER}")" == "65532:65532" ]] || \
  fail "sftpgo runtime user is not 65532:65532"
readonly SFTPGO_BINDING="$("${COMPOSE[@]}" port sftpgo 2022)"
[[ "${SFTPGO_BINDING}" =~ ^127\.0\.0\.1:[0-9]+$ ]] || \
  fail "sftpgo port is not bound exclusively to loopback: ${SFTPGO_BINDING}"
readonly SFTPGO_PUBLISHED_PORTS="$(docker inspect --format \
  '{{range $port, $bindings := .NetworkSettings.Ports}}{{if $bindings}}{{$port}} {{end}}{{end}}' \
  "${SFTPGO_CONTAINER}")"
[[ "${SFTPGO_PUBLISHED_PORTS}" == "2022/tcp " ]] || \
  fail "sftpgo publishes ports other than SFTP: ${SFTPGO_PUBLISHED_PORTS}"

readonly DATABASE_VOLUME="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql"}}{{.Name}}{{end}}{{end}}' "${DATABASE_CONTAINER}")"
[[ "${DATABASE_VOLUME}" == "${DATABASE_VOLUME_NAME}" ]] || \
  fail "database does not use the isolated project volume"

docker exec --user postgres "${DATABASE_CONTAINER}" sh -c 'touch "${PGDATA}/.jaxon-compose-persistence-check"'
"${COMPOSE[@]}" up --detach --force-recreate --no-deps --wait --no-build database
DATABASE_CONTAINER="$("${COMPOSE[@]}" ps --quiet database)"
docker exec --user postgres "${DATABASE_CONTAINER}" sh -c 'test -f "${PGDATA}/.jaxon-compose-persistence-check"' || \
  fail "database data did not survive container recreation"

readonly RECREATED_DATABASE_BINDING="$("${COMPOSE[@]}" port database 5432)"
[[ "${RECREATED_DATABASE_BINDING}" =~ ^127\.0\.0\.1:[0-9]+$ ]] || \
  fail "recreated database port is not bound exclusively to loopback: ${RECREATED_DATABASE_BINDING}"

"${COMPOSE[@]}" --profile application up --detach --no-build backend

readonly APP_CONTAINER="$("${COMPOSE[@]}" --profile application ps --quiet backend)"
[[ -n "${APP_CONTAINER}" ]] || fail "backend container was not created"
[[ "$(docker inspect --format '{{.Config.User}}' "${APP_CONTAINER}")" == "10001:10001" ]] || \
  fail "backend runtime user is not 10001:10001"
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${APP_CONTAINER}")" == "true" ]] || \
  fail "backend root filesystem is not read-only"
readonly APP_MAILBOX_MOUNT="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/srv/sftpgo/data"}}{{.Name}}:{{.RW}}{{end}}{{end}}' "${APP_CONTAINER}")"
[[ "${APP_MAILBOX_MOUNT}" == "${MAILBOX_VOLUME_NAME}:false" ]] || \
  fail "backend mailbox volume is not the isolated read-only project volume"
readonly APP_TASK_MOUNT="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/srv/sftpgo/tasks"}}{{.Name}}:{{.RW}}{{end}}{{end}}' "${APP_CONTAINER}")"
[[ "${APP_TASK_MOUNT}" == "${TASK_VOLUME_NAME}:true" ]] || \
  fail "backend task volume is not the isolated writable project volume"
readonly APP_GROUPS="$(docker inspect --format '{{json .HostConfig.GroupAdd}}' "${APP_CONTAINER}")"
[[ "${APP_GROUPS}" == *'65532'* ]] || fail "backend lacks the SFTPGo mailbox supplementary group"
docker exec "${APP_CONTAINER}" sh -c '[ "${JAXON_ENVIRONMENT}" = "local" ]' || \
  fail "backend did not receive JAXON_ENVIRONMENT=local"
docker exec "${APP_CONTAINER}" sh -c '[ -r /srv/sftpgo/data ] && [ ! -w /srv/sftpgo/data ]' || \
  fail "backend mailbox root is not safely readable and read-only"
docker exec "${APP_CONTAINER}" sh -c '[ -r /srv/sftpgo/tasks ] && [ -w /srv/sftpgo/tasks ]' || \
  fail "backend task publication root is not safely writable"

readonly APP_BINDING="$("${COMPOSE[@]}" --profile application port backend 8080)"
[[ "${APP_BINDING}" =~ ^127\.0\.0\.1:[0-9]+$ ]] || \
  fail "backend port is not bound exclusively to loopback: ${APP_BINDING}"

wait_for_health "/actuator/health"
wait_for_health "/actuator/health/liveness"
wait_for_health "/actuator/health/readiness"

readonly SCHEMA_LIST="$(docker exec "${DATABASE_CONTAINER}" psql \
  --username jaxon_local --dbname jaxon_local --tuples-only --no-align \
  --command "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('inventory','discovery','collector','support','ai','identity','automation','audit_reporting') ORDER BY schema_name")"
readonly EXPECTED_SCHEMAS=$'ai\naudit_reporting\nautomation\ncollector\ndiscovery\nidentity\ninventory\nsupport'
[[ "${SCHEMA_LIST}" == "${EXPECTED_SCHEMAS}" ]] || fail "the approved schemas were not created"

readonly VECTOR_VERSION="$(docker exec "${DATABASE_CONTAINER}" psql \
  --username jaxon_local --dbname jaxon_local --tuples-only --no-align \
  --command "SELECT extversion FROM pg_extension WHERE extname = 'vector'")"
[[ "${VECTOR_VERSION}" == "0.8.6" ]] || fail "pgvector 0.8.6 was not activated"

printf 'Compose verification passed for isolated project %s.\n' "${PROJECT_NAME}"
