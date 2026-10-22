#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly dockerfile="${repository_root}/docker/sftpgo/Dockerfile"
readonly image="jaxon-sftpgo:2.7.6-local"
readonly cache_dir="${SFTPGO_BUILD_CACHE_DIR:-}"
readonly cache_read_only="${SFTPGO_BUILD_CACHE_READ_ONLY:-false}"
readonly platform="${SFTPGO_BUILD_PLATFORM:-linux/$(docker version --format '{{.Server.Arch}}')}"
readonly buildkit_image="docker.io/moby/buildkit:buildx-stable-1@sha256:cec9f139f45e93c5c69c60f8b07cfad9f43f4ef6b6a6cd917527fea5ff2e3dea"

cache_from=()
cache_to=()
builder_args=()
builder_name=""

cleanup_builder() {
  if [[ -n "${builder_name}" ]]; then
    docker buildx rm --force "${builder_name}" >/dev/null 2>&1 || true
  fi
}

prepare_cache_export() {
  local export_cache="$1"
  local next_cache_dir

  if [[ -z "${cache_dir}" ]]; then
    return
  fi

  if [[ "${cache_dir}" != /* || "${cache_dir}" == "/" ]]; then
    printf 'SFTPGO_BUILD_CACHE_DIR must be an absolute, non-root path.\n' >&2
    exit 1
  fi

  next_cache_dir="${cache_dir}.next"
  rm -rf -- "${next_cache_dir}"
  cache_from=()
  if [[ -f "${cache_dir}/index.json" ]]; then
    cache_from=(--cache-from "type=local,src=${cache_dir}")
  fi
  cache_to=()
  if [[ "${export_cache}" != "true" || "${cache_read_only}" == "true" ]]; then
    return
  fi
  cache_to=(--cache-to "type=local,dest=${next_cache_dir},mode=min")
}

commit_cache_export() {
  if [[ ${#cache_to[@]} -eq 0 ]]; then
    return
  fi

  "${repository_root}/docker/sftpgo/cache-integrity.sh" write "${cache_dir}.next"
  rm -rf -- "${cache_dir}"
  mv -- "${cache_dir}.next" "${cache_dir}"
}

build_target() {
  local export_cache="$1"
  local target="$2"
  shift 2

  prepare_cache_export "${export_cache}"
  docker buildx build \
    "${builder_args[@]}" \
    --file "${dockerfile}" \
    --platform "${platform}" \
    --progress plain \
    --target "${target}" \
    "${cache_from[@]}" \
    "${cache_to[@]}" \
    "$@" \
    "${repository_root}"
  if [[ "${export_cache}" == "true" && -n "${builder_name}" ]]; then
    docker buildx prune --builder "${builder_name}" --all --force >/dev/null
  fi
  commit_cache_export
}

if [[ "${cache_read_only}" != "true" && "${cache_read_only}" != "false" ]]; then
  printf 'SFTPGO_BUILD_CACHE_READ_ONLY must be true or false.\n' >&2
  exit 1
fi

if [[ -n "${cache_dir}" ]]; then
  builder_name="jaxon-sftpgo-cache-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$"
  trap cleanup_builder EXIT
  docker buildx create \
    --name "${builder_name}" \
    --driver docker-container \
    --driver-opt "image=${buildkit_image}" >/dev/null
  builder_args=(--builder "${builder_name}")
fi

if [[ -n "${cache_dir}" && -e "${cache_dir}" ]] \
  && ! "${repository_root}/docker/sftpgo/cache-integrity.sh" verify "${cache_dir}"; then
  printf 'Ignoring an incomplete, corrupt, or incompatible SFTPGo build cache.\n' >&2
  rm -rf -- "${cache_dir}"
fi

"${repository_root}/docker/sftpgo/verify-source.sh"
build_target true verification \
  --no-cache-filter upstream-tests \
  --no-cache-filter vulnerability-check \
  --no-cache-filter verification
build_target false runtime --load --tag "${image}"
"${repository_root}/docker/sftpgo/verify-image.sh" "${image}"

printf 'Built %s for J-AXON Local/Test use only; the image was not published.\n' "${image}"
