#!/usr/bin/env bash
set -euo pipefail

readonly image="${1:-jaxon-sftpgo:2.7.6-local}"
readonly expected_commit="62ae9ba3957e9ed52b44a4f885e805e2d7b35972"
readonly trivy_image="aquasec/trivy:0.75.0@sha256:af6acf9a6b85dfe389a1941505c0ce9efef52a4719635e1a962f022a3d855daa"
readonly trivy_database_primary="ghcr.io/aquasecurity/trivy-db:2"
readonly trivy_database_fallback="public.ecr.aws/aquasecurity/trivy-db:2"

version_output="$(docker run --rm "${image}" --version)"
case "${version_output}" in
  "SFTPGo 2.7.6-${expected_commit}-"*) ;;
  *)
    printf 'Unexpected SFTPGo version output: %s\n' "${version_output}" >&2
    exit 1
    ;;
esac

docker run --rm --volume /var/run/docker.sock:/var/run/docker.sock \
  "${trivy_image}" image --db-repository "${trivy_database_primary}" \
  --db-repository "${trivy_database_fallback}" \
  --exit-code 1 --severity HIGH,CRITICAL \
  --scanners vuln --no-progress "${image}"

sbom_path="$(mktemp)"
trap 'rm -f "${sbom_path}"' EXIT
docker run --rm --volume /var/run/docker.sock:/var/run/docker.sock \
  "${trivy_image}" image --db-repository "${trivy_database_primary}" \
  --db-repository "${trivy_database_fallback}" \
  --format cyclonedx --scanners vuln --no-progress "${image}" \
  > "${sbom_path}"
test -s "${sbom_path}"

printf '%s\n' "${version_output}"
printf 'Trivy gate passed and a temporary CycloneDX SBOM was generated locally.\n'
