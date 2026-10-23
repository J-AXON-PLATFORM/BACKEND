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
readonly SPEC_V24="doc/contracts/openapi/2.4/openapi.yaml"
readonly SPEC_V25="doc/contracts/openapi/2.5/openapi.yaml"
readonly SPEC_V30="doc/contracts/openapi/3.0/openapi.yaml"
readonly SPEC_V31="doc/contracts/openapi/3.1/openapi.yaml"
readonly SPEC_V40="doc/contracts/openapi/4.0/openapi.yaml"
readonly SPEC_V41="doc/contracts/openapi/4.1/openapi.yaml"
readonly SPEC_V42="doc/contracts/openapi/4.2/openapi.yaml"
readonly SPEC_V43="doc/contracts/openapi/4.3/openapi.yaml"
readonly SPEC_V10_SHA256="bf3b1df5f28fb2e1c34f55b645312e7df896aa8a50fa8fbbec4b7e72d2068236"
readonly SPEC_V11_SHA256="96dfa1d01c63339089685a2e6c3011e050d24727dad71863df244a631b3c1d3b"
readonly SPEC_V20_SHA256="832037d2e554ca680b89776430d8a01a12a945215ba5cac0335c849e349732fe"
readonly SPEC_V21_SHA256="9abd5f0b4f9200f30ae71b2edae1eb7fa32ef8a238a27d62325fc464176cc326"
readonly SPEC_V22_SHA256="24ffc076b43dc61b8779c9b367e1189b0904544fcbec85bb8d3ca72bd91f7e05"
readonly SPEC_V23_SHA256="ad03306e05b8bd28d4933a3d6b37e72088e5f490a79e2180f5ae4de9fe9a8b36"
readonly SPEC_V24_SHA256="ef6a21e8a53a03cb7888b8cd49dfc65def31fd3d28ebcb8d6a1771c0490edbd6"
readonly SPEC_V25_SHA256="bbf7bf28316ab6b81553f2aabdc2c3529c482e1d72c5a56f82b9411cf1090335"
readonly SPEC_V30_SHA256="cd80013004c2304015d28102436d729d73e29fe922e8e178650b4c5f40cd0950"
readonly SPEC_V31_SHA256="244a760407bfa5dc6c2fbd35f29a4dc1ae4c56cb43757924a2e3fd1687767c9e"
readonly SPEC_V40_SHA256="41d47b69b517ee8d9384e609f7aba518e69f363c98ec8b12f92df7d0d07f44ab"
readonly SPEC_V41_SHA256="805c2fc9f8015fde0db161da39c9c58f54aae2c9789a8937f3cb324a2836a748"
readonly SPEC_V42_SHA256="ff4961375b266d9ca7dc217fb7993219efe166b77d2aed918f4d934607676c70"

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
printf '%s  %s\n' "${SPEC_V23_SHA256}" "${SPEC_V23}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V24_SHA256}" "${SPEC_V24}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V25_SHA256}" "${SPEC_V25}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V30_SHA256}" "${SPEC_V30}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V31_SHA256}" "${SPEC_V31}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V40_SHA256}" "${SPEC_V40}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V41_SHA256}" "${SPEC_V41}" | sha256sum --check --strict
printf '%s  %s\n' "${SPEC_V42_SHA256}" "${SPEC_V42}" | sha256sum --check --strict
"${work_dir}/oasdiff" validate "${SPEC_V10}"
"${work_dir}/oasdiff" validate "${SPEC_V11}"
"${work_dir}/oasdiff" validate "${SPEC_V20}"
"${work_dir}/oasdiff" validate "${SPEC_V21}"
"${work_dir}/oasdiff" validate "${SPEC_V22}"
"${work_dir}/oasdiff" validate "${SPEC_V23}"
"${work_dir}/oasdiff" validate "${SPEC_V24}"
"${work_dir}/oasdiff" validate "${SPEC_V25}"
"${work_dir}/oasdiff" validate "${SPEC_V30}"
"${work_dir}/oasdiff" validate "${SPEC_V31}"
"${work_dir}/oasdiff" validate "${SPEC_V40}"
"${work_dir}/oasdiff" validate "${SPEC_V41}"
"${work_dir}/oasdiff" validate "${SPEC_V42}"
"${work_dir}/oasdiff" validate "${SPEC_V43}"
"${work_dir}/oasdiff" breaking "${SPEC_V10}" "${SPEC_V11}"
"${work_dir}/oasdiff" breaking "${SPEC_V20}" "${SPEC_V21}"
"${work_dir}/oasdiff" breaking "${SPEC_V21}" "${SPEC_V22}"
"${work_dir}/oasdiff" breaking "${SPEC_V22}" "${SPEC_V23}"
"${work_dir}/oasdiff" breaking "${SPEC_V23}" "${SPEC_V24}"
"${work_dir}/oasdiff" breaking "${SPEC_V24}" "${SPEC_V25}"
"${work_dir}/oasdiff" breaking "${SPEC_V30}" "${SPEC_V31}"
"${work_dir}/oasdiff" breaking "${SPEC_V40}" "${SPEC_V41}"
"${work_dir}/oasdiff" breaking "${SPEC_V41}" "${SPEC_V42}"
"${work_dir}/oasdiff" breaking "${SPEC_V42}" "${SPEC_V43}"

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
