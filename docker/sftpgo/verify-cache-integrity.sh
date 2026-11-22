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

"${integrity_script}" write "${work_dir}"

mkdir "${work_dir}/ingest-locks"
printf '' > "${work_dir}/ingest-locks/0123456789abcdef"
printf '' > "${work_dir}/cache.lock"
if "${integrity_script}" write "${work_dir}" >/dev/null 2>&1; then
  printf 'SFTPGo build cache with transient BuildKit locks was accepted before pruning.\n' >&2
  exit 1
fi
"${integrity_script}" prune-transient "${work_dir}"
if [[ -e "${work_dir}/ingest-locks" || -e "${work_dir}/cache.lock" ]]; then
  printf 'Transient BuildKit locks were not pruned.\n' >&2
  exit 1
fi
"${integrity_script}" write "${work_dir}"
"${integrity_script}" verify "${work_dir}"

mkdir -p "${work_dir}/ingest-locks/nested"
if "${integrity_script}" prune-transient "${work_dir}" >/dev/null 2>&1; then
  printf 'Nested ingest-locks content was pruned.\n' >&2
  exit 1
fi
rm -r "${work_dir}/ingest-locks"
mkdir "${work_dir}/ingest-locks"
ln -s "${work_dir}/index.json" "${work_dir}/ingest-locks/link"
if "${integrity_script}" prune-transient "${work_dir}" >/dev/null 2>&1; then
  printf 'A symlink inside ingest-locks was pruned.\n' >&2
  exit 1
fi
rm -r "${work_dir}/ingest-locks"
ln -s "${work_dir}/blobs" "${work_dir}/ingest-locks"
if "${integrity_script}" prune-transient "${work_dir}" >/dev/null 2>&1; then
  printf 'A symlinked ingest-locks directory was pruned.\n' >&2
  exit 1
fi
rm "${work_dir}/ingest-locks"

printf 'SFTPGo build cache integrity verification rejects corrupt or incomplete content.\n'
