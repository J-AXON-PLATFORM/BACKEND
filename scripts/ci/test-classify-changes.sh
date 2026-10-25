#!/usr/bin/env bash
set -euo pipefail

readonly classifier="$(dirname "$0")/classify-changes.sh"
readonly work_file="$(mktemp)"
trap 'rm -f "${work_file}"' EXIT

assert_level() {
  local expected="$1"
  shift
  printf '%s\n' "$@" > "${work_file}"
  local actual
  actual="$(${classifier} "${work_file}")"
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'Expected %s, got %s for: %s\n' "${expected}" "${actual}" "$*" >&2
    exit 1
  fi
}

assert_level DOCS_ONLY doc/docs/16-contexto/TRABAJO_ACTUAL.md
assert_level APPLICATION src/main/java/com/jaxon/support/TicketService.java
assert_level APPLICATION doc/contracts/openapi/4.3/openapi.yaml doc/docs/04-arquitectura/API_CONTRACTS.md
assert_level PLATFORM_FULL src/test/java/com/jaxon/discovery/DiscoverySftpGoClientIntegrationTest.java
assert_level PLATFORM_FULL .github/workflows/backend-ci.yml
assert_level PLATFORM_FULL pom.xml
assert_level PLATFORM_FULL docker/sftpgo/SFTPGo.Dockerfile
assert_level PLATFORM_FULL scripts/verify-openapi.sh
assert_level PLATFORM_FULL unknown/new-path.txt
assert_level PLATFORM_FULL
