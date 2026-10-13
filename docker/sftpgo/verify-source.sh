#!/usr/bin/env bash
set -euo pipefail

readonly tag="v2.7.6"
readonly commit="62ae9ba3957e9ed52b44a4f885e805e2d7b35972"
readonly source_sha256="310a475de6eeb1d3e43a4df0ada0091a042487afb2a749e18c5c2d8c9aedd80f"
readonly signing_fingerprint="A7B5CC667C573A1DCD115ECB935D2952DEC4EECF"
readonly public_key_sha256="fd072457c577bdcd15ea22ee8ecf35c3dad4afe3f6983a796b965549868657a3"
readonly repository="https://github.com/drakkan/sftpgo.git"
readonly source_url="https://codeload.github.com/drakkan/sftpgo/tar.gz/${commit}"
readonly key_url="https://github.com/drakkan.gpg"

work_dir="$(mktemp --directory)"
trap 'rm -rf "${work_dir}"' EXIT
mkdir -m 0700 "${work_dir}/gnupg"

curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
  "${key_url}" --output "${work_dir}/maintainer.gpg"
printf '%s  %s\n' "${public_key_sha256}" "${work_dir}/maintainer.gpg" \
  | sha256sum --check --strict
gpg --homedir "${work_dir}/gnupg" --batch --import "${work_dir}/maintainer.gpg"
actual_fingerprint="$(gpg --homedir "${work_dir}/gnupg" --batch --with-colons \
  --fingerprint "${signing_fingerprint}" | awk -F: '$1 == "fpr" {print $10; exit}')"
test "${actual_fingerprint}" = "${signing_fingerprint}"

git -C "${work_dir}" init --quiet source
git -C "${work_dir}/source" remote add origin "${repository}"
git -C "${work_dir}/source" fetch --quiet --depth 1 origin "refs/tags/${tag}:refs/tags/${tag}"
test "$(git -C "${work_dir}/source" rev-parse "refs/tags/${tag}^{commit}")" = "${commit}"
GNUPGHOME="${work_dir}/gnupg" git -C "${work_dir}/source" verify-commit "${commit}"

curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
  "${source_url}" --output "${work_dir}/source.tar.gz"
printf '%s  %s\n' "${source_sha256}" "${work_dir}/source.tar.gz" \
  | sha256sum --check --strict

printf 'Verified SFTPGo %s commit %s and source SHA-256 %s\n' \
  "${tag}" "${commit}" "${source_sha256}"
