#!/usr/bin/env bash
set -euo pipefail

if (($# != 1)) || [[ ! -f "$1" ]]; then
  printf 'Usage: %s <changed-paths-file>\n' "$0" >&2
  exit 2
fi

level="DOCS_ONLY"
found=false
while IFS= read -r path || [[ -n "${path}" ]]; do
  [[ -z "${path}" ]] && continue
  found=true
  case "${path}" in
    .github/*|.mvn/*|mvnw|mvnw.cmd|pom.xml|Dockerfile|docker/*|compose*.yml|compose*.yaml|.env.example|scripts/*)
      level="PLATFORM_FULL"
      break
      ;;
    src/*|doc/contracts/*)
      level="APPLICATION"
      ;;
    doc/*|README.md|AGENTS.md)
      ;;
    *)
      level="PLATFORM_FULL"
      break
      ;;
  esac
done < "$1"

if [[ "${found}" != "true" ]]; then
  level="PLATFORM_FULL"
fi

printf '%s\n' "${level}"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'level=%s\n' "${level}" >> "${GITHUB_OUTPUT}"
fi
