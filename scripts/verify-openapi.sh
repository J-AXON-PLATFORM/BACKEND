#!/usr/bin/env bash

set -euo pipefail

readonly OASDIFF_VERSION="1.33.0"
readonly OASDIFF_ARCHIVE="oasdiff_${OASDIFF_VERSION}_linux_amd64.tar.gz"
readonly OASDIFF_SHA256="43a4e328e2d13ba1552d760aa68d2485c75c5621f309f6ff64ae895188345247"
readonly OASDIFF_URL="https://github.com/oasdiff/oasdiff/releases/download/v${OASDIFF_VERSION}/${OASDIFF_ARCHIVE}"
readonly OPENAPI_ROOT="doc/contracts/openapi"
readonly MANIFEST="${OPENAPI_ROOT}/manifest.tsv"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT

curl --fail --silent --show-error --location \
  --output "${work_dir}/${OASDIFF_ARCHIVE}" \
  "${OASDIFF_URL}"
printf '%s  %s\n' "${OASDIFF_SHA256}" "${work_dir}/${OASDIFF_ARCHIVE}" \
  | sha256sum --check --status
tar -xzf "${work_dir}/${OASDIFF_ARCHIVE}" -C "${work_dir}" oasdiff

declare -A declared_versions=()
current_count=0
while IFS='|' read -r version checksum compatible_from extra; do
  [[ -z "${version}" || "${version}" == \#* ]] && continue
  if [[ -n "${extra:-}" \
      || ! "${version}" =~ ^[0-9]+\.[0-9]+$ \
      || ! "${checksum}" =~ ^([0-9a-f]{64}|CURRENT)$ \
      || ! "${compatible_from}" =~ ^(-|[0-9]+\.[0-9]+)$ \
      || -n "${declared_versions[${version}]:-}" ]]; then
    printf 'Invalid OpenAPI manifest entry for version %s.\n' "${version:-<empty>}" >&2
    exit 1
  fi
  declared_versions["${version}"]=1
  spec="${OPENAPI_ROOT}/${version}/openapi.yaml"
  if [[ ! -f "${spec}" ]]; then
    printf 'Declared OpenAPI file does not exist: %s\n' "${spec}" >&2
    exit 1
  fi
  if [[ "${checksum}" == "CURRENT" ]]; then
    current_count=$((current_count + 1))
  else
    printf '%s  %s\n' "${checksum}" "${spec}" | sha256sum --check --strict
  fi
  "${work_dir}/oasdiff" validate "${spec}"
  if [[ "${compatible_from}" != "-" ]]; then
    predecessor="${OPENAPI_ROOT}/${compatible_from}/openapi.yaml"
    if [[ -z "${declared_versions[${compatible_from}]:-}" || ! -f "${predecessor}" ]]; then
      printf 'Unknown or unordered OpenAPI predecessor: %s\n' "${compatible_from}" >&2
      exit 1
    fi
    "${work_dir}/oasdiff" breaking "${predecessor}" "${spec}"
  fi
done < "${MANIFEST}"

if [[ "${current_count}" -ne 1 ]]; then
  printf 'OpenAPI manifest must declare exactly one CURRENT contract.\n' >&2
  exit 1
fi

for spec in "${OPENAPI_ROOT}"/*/openapi.yaml; do
  version="$(basename "$(dirname "${spec}")")"
  if [[ -z "${declared_versions[${version}]:-}" ]]; then
    printf 'OpenAPI contract is missing from the manifest: %s\n' "${spec}" >&2
    exit 1
  fi
done
