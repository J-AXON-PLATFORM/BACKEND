#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly integrity_script="${repository_root}/docker/sftpgo/cache-integrity.sh"
work_dir="$(mktemp --directory)"
trap 'rm -rf "${work_dir}"' EXIT

mkdir -p "${work_dir}/blobs/sha256"
printf '{"schemaVersion":2}\n' > "${work_dir}/index.json"
printf '{"imageLayoutVersion":"1.0.0"}\n' > "${work_dir}/oci-layout"
printf 'cache content\n' > "${work_dir}/blobs/sha256/test-blob"

"${integrity_script}" write "${work_dir}"
"${integrity_script}" verify "${work_dir}"
printf 'corrupt cache content\n' > "${work_dir}/blobs/sha256/test-blob"
if "${integrity_script}" verify "${work_dir}" >/dev/null 2>&1; then
  printf 'Corrupt SFTPGo build cache was accepted.\n' >&2
  exit 1
fi
printf 'cache content\n' > "${work_dir}/blobs/sha256/test-blob"
printf 'unmanifested content\n' > "${work_dir}/blobs/sha256/unmanifested-blob"
if "${integrity_script}" verify "${work_dir}" >/dev/null 2>&1; then
  printf 'SFTPGo build cache with unmanifested content was accepted.\n' >&2
  exit 1
fi
rm "${work_dir}/blobs/sha256/unmanifested-blob"
sed -i '$d' "${work_dir}/.jaxon-cache-manifest.sha256"
if "${integrity_script}" verify "${work_dir}" >/dev/null 2>&1; then
  printf 'SFTPGo build cache with a truncated manifest was accepted.\n' >&2
  exit 1
fi

printf 'SFTPGo build cache integrity verification rejects corrupt or incomplete content.\n'
