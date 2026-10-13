#!/usr/bin/env bash
set -euo pipefail

readonly repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly dockerfile="${repository_root}/docker/sftpgo/Dockerfile"
readonly image="jaxon-sftpgo:2.7.6-local"

"${repository_root}/docker/sftpgo/verify-source.sh"
docker build --file "${dockerfile}" --target verification "${repository_root}"
docker build --file "${dockerfile}" --tag "${image}" "${repository_root}"
"${repository_root}/docker/sftpgo/verify-image.sh" "${image}"

printf 'Built %s for J-AXON Local/Test use only; the image was not published.\n' "${image}"
