#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp --directory)"
trap 'rm -rf "${work_dir}"' EXIT

mkdir -p "${work_dir}/.github/workflows" "${work_dir}/docker/sftpgo"
cp "${repository_root}/.dockerignore" "${work_dir}/.dockerignore"
cp "${repository_root}/.github/workflows/backend-ci.yml" "${work_dir}/.github/workflows/backend-ci.yml"
cp "${repository_root}"/docker/sftpgo/{Dockerfile,build-local.sh,cache-integrity.sh,cache-key.sh,verify-image.sh,verify-source.sh} \
  "${work_dir}/docker/sftpgo/"

readonly base_key="$(${work_dir}/docker/sftpgo/cache-key.sh linux/amd64)"
test "${base_key}" = "$(${work_dir}/docker/sftpgo/cache-key.sh linux/amd64)"
test "${base_key}" != "$(${work_dir}/docker/sftpgo/cache-key.sh linux/arm64)"

for input in \
  .dockerignore \
  .github/workflows/backend-ci.yml \
  docker/sftpgo/Dockerfile \
  docker/sftpgo/build-local.sh \
  docker/sftpgo/cache-integrity.sh \
  docker/sftpgo/cache-key.sh \
  docker/sftpgo/verify-image.sh \
  docker/sftpgo/verify-source.sh; do
  cp "${repository_root}/${input}" "${work_dir}/${input}"
  printf '\n# cache invalidation probe\n' >> "${work_dir}/${input}"
  test "${base_key}" != "$(${work_dir}/docker/sftpgo/cache-key.sh linux/amd64)"
  cp "${repository_root}/${input}" "${work_dir}/${input}"
done

if "${work_dir}/docker/sftpgo/cache-key.sh" linux/unsupported >/dev/null 2>&1; then
  printf 'Unsupported cache platform was accepted.\n' >&2
  exit 1
fi

printf 'SFTPGo build cache key is deterministic and covers every effective input.\n'
