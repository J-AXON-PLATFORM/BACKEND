#!/usr/bin/env bash

set -euo pipefail

readonly OASDIFF_VERSION="1.33.0"
readonly OASDIFF_ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz"
readonly OASDIFF_SHA256="43a4e328e2d13ba1552d760aa68d2485c75c5621f309f6ff64ae895188345247"
readonly OASDIFF_URL="https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}/${OASDIFF_ARCHIVE}"
readonly SPEC_V10="doc/contracts/openapi/1.0/openapi.yaml"
readonly SPEC_V11="doc/contracts/openapi/1.1/openapi.yaml"
readonly SPEC_V20="doc/contracts/openapi/2.0/openapi.yaml"
readonly SPEC_V21="doc/contracts/openapi/2.1/openapi.yaml"
readonly SPEC_V22="doc/contracts/openapi/2.2/openapi.yaml"
readonly SPEC_V23="doc/contracts/openapi/2.3/openapi.yaml"
readonly SPEC_V10_SHA256="bf3b1df5f28fb2e1c34f55b645312e7df896aa8a50fa8fbbec4b7e72d2068236"
readonly SPEC_V11_SHA256="96dfa1d01c63339089685a2e6c3011e050d24727dad71863df244a631b3c1d3b"
readonly SPEC_V20_SHA256="832037d2e554ca680b89776430d8a01a12a945215ba5cac0335c849e349732fe"
readonly SPEC_V21_SHA256="9abd5f0b4f9200f30ae71b2edae1eb7fa32ef8a238a27d62325fc464176cc326"
readonly SPEC_V22_SHA256="24ffc076b43dc61b8779c9b367e1189b0904544fcbec85bb8d3ca72bd91f7e05"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

curl --fail --silent --show-error --location \
  --output "${work_dir}/${OASDIFF_ARCHIVE}" \
  "${OASDIFF_URL}"
printf '%s  %s\n' "${OASDIFF_SHA256}" "${work_dir}/${OASDIFF_ARCHIVE}" \
  | sha256sum --check --status
tar -xzf "${work_dir}/${OASDIFF_ARCHIVE}" -C "${work_dir}" oasdiff

printf '%s  %s\n' "${SPEC_V10_SHA256}" "${SPEC_V10}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V11_SHA256}" "${SPEC_V11}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V20_SHA256}" "${SPEC_V20}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V21_SHA256}" "${SPEC_V21}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V22_SHA256}" "${SPEC_V22}" | sha256sum --check --strict
"${work_dir}/oasdiff" validate "${SPEC_V10}"
"${work_dir}/oasdiff" validate "${SPEC_V11}"
"${work_dir}/oasdiff" validate "${SPEC_V20}"
"${work_dir}/oasdiff" validate "${SPEC_V21}"
"${work_dir}/oasdiff" validate "${SPEC_V22}"
"${work_dir}/oasdiff" validate "${SPEC_V23}"
"${work_dir}/oasdiff" breaking "${SPEC_V10}" "${SPEC_V11}"
"${work_dir}/oasdiff" breaking "${SPEC_V20}" "${SPEC_V21}"
"${work_dir}/oasdiff" breaking "${SPEC_V21}" "${SPEC_V22}"
"${work_dir}/oasdiff" breaking "${SPEC_V22}" "${SPEC_V23}"

base_ref=""
if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify --quiet "origin/${GITHUB_BASE_REF}" >/dev/null; then
  base_ref="origin/${GITHUB_BASE_REF}"
elif git rev-parse --verify --quiet HEAD^ >/dev/null; then
  base_ref="HEAD^"
fi

if [[ -n "${base_ref}" ]] && git cat-file -e "${base_ref}:${SPEC_V11}" 2>/dev/null; then
  mkdir -p "${work_dir}/base/1.0" "${work_dir}/base/1.1" "${work_dir}/base/2.0" "${work_dir}/base/2.1"
  git show "${base_ref}:${SPEC_V10}" >"${work_dir}/base/1.0/openapi.yaml"
  git show "${base_ref}:${SPEC_V11}" >"${work_dir}/base/1.1/openapi.yaml"
  git show "${base_ref}:${SPEC_V20}" >"${work_dir}/base/2.0/openapi.yaml"
  "${work_dir}/oasdiff" breaking "${work_dir}/base/1.1/openapi.yaml" "${SPEC_V11}"
  "${work_dir}/oasdiff" breaking "${work_dir}/base/2.0/openapi.yaml" "${SPEC_V20}"
  if git cat-file -e "${base_ref}:${SPEC_V21}" 2>/dev/null; then
    git show "${base_ref}:${SPEC_V21}" >"${work_dir}/base/2.1/openapi.yaml"
    "${work_dir}/oasdiff" breaking "${work_dir}/base/2.1/openapi.yaml" "${SPEC_V21}"
  fi
else
  printf 'No previous OpenAPI 1.1 baseline exists; 1.0 to 1.1 compatibility completed.\n'
fi
