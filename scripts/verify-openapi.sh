#!/usr/bin/env bash

set -euo pipefail

readonly OASDIFF_VERSION="1.33.0"
readonly OASDIFF_ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz"
readonly OASDIFF_SHA256="43a4e328e2d13ba1552d760aa68d2485c75c5621f309f6ff64ae895188345247"
readonly OASDIFF_URL="https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}/${OASDIFF_ARCHIVE}"
readonly SPEC_PATH="doc/contracts/openapi/1.0/openapi.yaml"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

curl --fail --silent --show-error --location \
  --output "${work_dir}/${OASDIFF_ARCHIVE}" \
  "${OASDIFF_URL}"
printf '%s  %s\n' "${OASDIFF_SHA256}" "${work_dir}/${OASDIFF_ARCHIVE}" \
  | sha256sum --check --status
tar -xzf "${work_dir}/${OASDIFF_ARCHIVE}" -C "${work_dir}" oasdiff

"${work_dir}/oasdiff" validate "${SPEC_PATH}"

base_ref=""
if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify --quiet "origin/${GITHUB_BASE_REF}" >/dev/null; then
  base_ref="origin/${GITHUB_BASE_REF}"
elif git rev-parse --verify --quiet HEAD^ >/dev/null; then
  base_ref="HEAD^"
fi

if [[ -n "${base_ref}" ]] && git cat-file -e "${base_ref}:${SPEC_PATH}" 2>/dev/null; then
  git show "${base_ref}:${SPEC_PATH}" >"${work_dir}/base-openapi.yaml"
  "${work_dir}/oasdiff" breaking "${work_dir}/base-openapi.yaml" "${SPEC_PATH}"
else
  printf 'No previous OpenAPI baseline exists; structural validation completed.\n'
fi
