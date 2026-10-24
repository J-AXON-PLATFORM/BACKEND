#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly platform="${1:-${SFTPGO_BUILD_PLATFORM:-}}"
readonly cache_schema="v1"
readonly inputs=(
  .dockerignore
  .github/workflows/backend-ci.yml
  docker/sftpgo/Dockerfile
  docker/sftpgo/build-local.sh
  docker/sftpgo/cache-integrity.sh
  docker/sftpgo/cache-key.sh
  docker/sftpgo/verify-image.sh
  docker/sftpgo/verify-source.sh
)

if [[ ! "${platform}" =~ ^linux/(amd64|arm64)$ ]]; then
  printf 'Expected cache platform linux/amd64 or linux/arm64, got: %s\n' "${platform:-<empty>}" >&2
  exit 1
fi

digest="$({
  printf 'cache-schema=%s\nplatform=%s\n' "${cache_schema}" "${platform}"
  for input in "${inputs[@]}"; do
    input_digest="$(sha256sum "${repository_root}/${input}" | cut -d ' ' -f 1)"
    printf '%s  %s\n' "${input_digest}" "${input}"
  done
} | sha256sum | cut -d ' ' -f 1)"

printf 'sftpgo-build-%s-%s-%s\n' "${cache_schema}" "${platform//\//-}" "${digest}"
