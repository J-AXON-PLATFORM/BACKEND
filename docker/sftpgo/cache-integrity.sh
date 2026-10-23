#!/usr/bin/env bash
set -euo pipefail

readonly operation="${1:-}"
readonly cache_dir="${2:-}"
readonly manifest_name=".jaxon-cache-manifest.sha256"

if [[ "${cache_dir}" != /* || "${cache_dir}" == "/" || ! -d "${cache_dir}" ]]; then
  printf 'Expected an existing absolute, non-root cache directory.\n' >&2
  exit 1
fi

validate_cache_layout() {
  local unexpected_entry

  if [[ ! -f "${cache_dir}/index.json" ]]; then
    printf 'SFTPGo build cache is missing index.json.\n' >&2
    return 1
  fi
  if [[ ! -f "${cache_dir}/oci-layout" ]]; then
    printf 'SFTPGo build cache is missing oci-layout.\n' >&2
    return 1
  fi
  if [[ ! -d "${cache_dir}/blobs/sha256" ]]; then
    printf 'SFTPGo build cache is missing blobs/sha256.\n' >&2
    return 1
  fi
  unexpected_entry="$(find "${cache_dir}" -type l -print -quit)"
  if [[ -n "${unexpected_entry}" ]]; then
    printf 'SFTPGo build cache contains a symlink: %s\n' "${unexpected_entry}" >&2
    return 1
  fi
  unexpected_entry="$(find "${cache_dir}" ! -type d ! -type f -print -quit)"
  if [[ -n "${unexpected_entry}" ]]; then
    printf 'SFTPGo build cache contains a special entry: %s\n' "${unexpected_entry}" >&2
    return 1
  fi
  unexpected_entry="$(find "${cache_dir}" -type f \
    ! -path "${cache_dir}/index.json" \
    ! -path "${cache_dir}/oci-layout" \
    ! -path "${cache_dir}/blobs/sha256/*" \
    ! -path "${cache_dir}/${manifest_name}" \
    -print -quit)"
  if [[ -n "${unexpected_entry}" ]]; then
    printf 'SFTPGo build cache contains an unexpected file: %s\n' "${unexpected_entry}" >&2
    return 1
  fi
}

generate_manifest() {
  (
    cd "${cache_dir}"
    sha256sum index.json oci-layout
    find blobs/sha256 -type f -print0 | sort -z | xargs -0 -r sha256sum
  )
}

write_manifest() {
  local manifest_tmp

  validate_cache_layout
  manifest_tmp="$(mktemp "${cache_dir}/.jaxon-cache-manifest.XXXXXX")"
  trap 'rm -f "${manifest_tmp}"' RETURN
  generate_manifest > "${manifest_tmp}"
  mv -- "${manifest_tmp}" "${cache_dir}/${manifest_name}"
  trap - RETURN
}

verify_manifest() {
  local calculated_manifest
  local status=0

  validate_cache_layout
  if [[ ! -f "${cache_dir}/${manifest_name}" ]]; then
    printf 'SFTPGo build cache is missing %s.\n' "${manifest_name}" >&2
    return 1
  fi
  calculated_manifest="$(mktemp)"
  generate_manifest > "${calculated_manifest}"
  cmp --silent "${cache_dir}/${manifest_name}" "${calculated_manifest}" || status=1
  rm -f -- "${calculated_manifest}"
  if [[ "${status}" -ne 0 ]]; then
    return "${status}"
  fi
  (
    cd "${cache_dir}"
    sha256sum --check --strict --quiet "${manifest_name}"
  )
}

case "${operation}" in
  write) write_manifest ;;
  verify) verify_manifest ;;
  *)
    printf 'Usage: %s <write|verify> <cache-directory>\n' "$0" >&2
    exit 1
    ;;
esac
