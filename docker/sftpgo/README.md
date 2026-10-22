# J-AXON Local/Test SFTPGo image

This recipe builds a J-AXON-derived SFTPGo image for Local/Test use only. It is
not an official prebuilt SFTPGo image and must not be published or deployed to
Production.

The official `v2.7.6` prebuilt images are not used because the blocking Trivy
scan detected `CVE-2026-78667`, `CVE-2026-78669`, and `CVE-2026-97031` after
their publication. The recipe starts from the exact signed upstream commit,
uses Go 1.26.10, and updates only `golang.org/x/net` from `v0.59.0` to `v0.60.0`.

Supply-chain pins:

- tag: `v2.7.6`;
- commit: `62ae9ba3957e9ed52b44a4f885e805e2d7b35972`;
- source SHA-256: `310a475de6eeb1d3e43a4df0ada0091a042487afb2a749e18c5c2d8c9aedd80f`;
- signer fingerprint: `A7B5CC667C573A1DCD115ECB935D2952DEC4EECF`;
- Go builder: `golang:1.26.10-bookworm@sha256:5c3f3f28a273c9b61698b2d34f18375929790ab1f1d3835d032d21e2b31eae0c`;
- runtime: `gcr.io/distroless/static-debian13:nonroot@sha256:e2e927ec666bae08560abb3c55d0659eceabb657f56b6782ab500a9fc7f555e3`.

`v2.7.6` is a lightweight tag. It has no independent tag-object signature; it
points to the exact commit above, whose PGP signature is verified by
`verify-source.sh` using the pinned maintainer key fingerprint.

Run:

```bash
./docker/sftpgo/build-local.sh
```

The script requires Docker Buildx. CI provides an exact-key local BuildKit
cache through `SFTPGO_BUILD_CACHE_DIR`; restored caches are verified before
use and remain read-only. Cache misses export a complete replacement
atomically. The cache is an availability optimization inside GitHub Actions'
trust boundary, not an authenticity mechanism. Upstream tests,
`govulncheck`, runtime image loading, Trivy, and SBOM generation still run on
every build.

The build disables upstream cloud storage, MySQL, PostgreSQL, SQLite, and
portable-mode features that are not used by this Local/Test SFTP fixture. It
retains the SFTP server, Bolt provider, administrative API, and metrics. The
exact tagged package graph excludes the unsafe, unmaintained OpenPGP packages
reported by `GO-2026-5932`; the module remains recorded transitively but its
vulnerable packages and symbols are not compiled or reachable.

The build verifies provenance and source checksum, runs `go mod verify`, the
applicable upstream Go test suite, `govulncheck`, a blocking Trivy scan, and a
temporary CycloneDX SBOM generation. The runtime contains the binary, static
web/API resources, configuration, `LICENSE`, and `NOTICE`; it contains no
shell, compiler, package manager, Git repository, or build cache and runs as
the distroless `nonroot` user.

SFTPGo is licensed under GNU AGPL v3 with the additional terms in `NOTICE`.
The corresponding modified source is reproducible from this recipe and the
pinned upstream source.
