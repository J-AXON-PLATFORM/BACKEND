#!/usr/bin/env bash

set -euo pipefail

readonly OASDIFF_VERSION="1.33.0"
readonly OASDIFF_ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz"
readonly OASDIFF_SHA256="43a4e328e2d13ba1552d760aa68d2485c75c5621f309f6ff64ae895188345247"
readonly OASDIFF_URL="https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}/${OASDIFF_ARCHIVE}"
readonly SPEC_V10="doc/contracts/openapi/1.0/openapi.yaml"
readonly SPEC_V11="doc/contracts/openapi/1.1/openapi.yaml"
readonly SPEC_V20="doc/contracts/openapi/2.0/openapi.yaml"
readonly SPEC_V10_SHA256="bf3b1df5f28fb2e1c34f55b645312e7df896aa8a50fa8fbbec4b7e72d2068236"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

curl --fail --silent --show-error --location \
  --output "${work_dir}/${OASDIFF_ARCHIVE}" \
  "${OASDIFF_URL}"
printf '%s  %s\n' "${OASDIFF_SHA256}" "${work_dir}/${OASDIFF_ARCHIVE}" \
  | sha256sum --check --status
tar -xzf "${work_dir}/${OASDIFF_ARCHIVE}" -C "${work_dir}" oasdiff

printf '%s  %s\n' "${SPEC_V10_SHA256}" "${SPEC_V10}" | sha256sum --check --strict
"${work_dir}/oasdiff" validate "${SPEC_V10}"
"${work_dir}/oasdiff" validate "${SPEC_V11}"
"${work_dir}/oasdiff" validate "${SPEC_V20}"
"${work_dir}/oasdiff" breaking "${SPEC_V10}" "${SPEC_V11}"

base_ref=""
if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify --quiet "origin/${GITHUB_BASE_REF}" >/dev/null; then
  base_ref="origin/${GITHUB_BASE_REF}"
elif git rev-parse --verify --quiet HEAD^ >/dev/null; then
  base_ref="HEAD^"
fi

if [[ -n "${base_ref}" ]] && git cat-file -e "${base_ref}:${SPEC_V11}" 2>/dev/null; then
  mkdir -p "${work_dir}/base/1.0" "${work_dir}/base/1.1"
  git show "${base_ref}:${SPEC_V10}" >"${work_dir}/base/1.0/openapi.yaml"
  git show "${base_ref}:${SPEC_V11}" >"${work_dir}/base/1.1/openapi.yaml"
  "${work_dir}/oasdiff" breaking "${work_dir}/base/1.1/openapi.yaml" "${SPEC_V11}"
else
  printf 'No previous OpenAPI 1.1 baseline exists; 1.0 to 1.1 compatibility completed.\n'
fi
