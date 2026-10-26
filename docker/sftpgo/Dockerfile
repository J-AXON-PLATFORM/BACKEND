ARG GO_IMAGE=docker.io/library/golang@sha256:5c3f3f28a273c9b61698b2d34f18375929790ab1f1d3835d032d21e2b31eae0c
ARG RUNTIME_IMAGE=gcr.io/distroless/static-debian13@sha256:e2e927ec666bae08560abb3c55d0659eceabb657f56b6782ab500a9fc7f555e3

FROM ${GO_IMAGE} AS source

ARG SFTPGO_COMMIT=62ae9ba3957e9ed52b44a4f885e805e2d7b35972
ARG SFTPGO_SOURCE_SHA256=310a475de6eeb1d3e43a4df0ada0091a042487afb2a749e18c5c2d8c9aedd80f
ARG SFTPGO_SOURCE_URL=https://codeload.github.com/drakkan/sftpgo/tar.gz/62ae9ba3957e9ed52b44a4f885e805e2d7b35972

ENV CGO_ENABLED=0 \
    GOFLAGS=-mod=readonly
WORKDIR /src

RUN curl --proto '=https' --tlsv1.2 --fail --location --silent --show-error \
        "${SFTPGO_SOURCE_URL}" --output /tmp/sftpgo.tar.gz \
    && printf '%s  %s\n' "${SFTPGO_SOURCE_SHA256}" /tmp/sftpgo.tar.gz \
        | sha256sum --check --strict \
    && tar --extract --gzip --strip-components=1 --file /tmp/sftpgo.tar.gz \
    && rm /tmp/sftpgo.tar.gz \
    && test "$(sed -n 's/^[[:space:]]*version = "\([^"]*\)"/\1/p' internal/version/version.go)" = "2.7.6"

# This is the only upstream dependency change. It fixes CVE-2026-78669.
RUN GOFLAGS=-mod=mod go get golang.org/x/net@v0.60.0 \
    && test "$(go list -m -f '{{.Version}}' golang.org/x/net)" = "v0.60.0" \
    && go mod verify

FROM source AS upstream-test-cache
RUN useradd --create-home --uid 10001 tester \
    && chown -R tester:tester /src
USER 10001:10001
ENV HOME=/home/tester \
    GOPATH=/home/tester/go
RUN CGO_ENABLED=1 sh -c 'go mod download \
    && go build \
        -buildvcs=false \
        -tags nopgxregisterdefaulttypes,disable_grpc_modules \
        -trimpath -o /tmp/sftpgo-cache-prime . \
    && cd tests/eventsearcher \
    && go build -buildvcs=false -trimpath -ldflags "-s -w" -o /tmp/eventsearcher-cache-prime \
    && cd ../ipfilter \
    && go build -buildvcs=false -trimpath -ldflags "-s -w" -o /tmp/ipfilter-cache-prime'

FROM upstream-test-cache AS upstream-tests
RUN CGO_ENABLED=1 sh -c 'go build \
        -buildvcs=false \
        -tags nopgxregisterdefaulttypes,disable_grpc_modules \
        -trimpath -o sftpgo . \
    && cd tests/eventsearcher \
    && go build -buildvcs=false -trimpath -ldflags "-s -w" -o eventsearcher \
    && cd ../ipfilter \
    && go build -buildvcs=false -trimpath -ldflags "-s -w" -o ipfilter \
    && cd /src \
    && ./sftpgo initprovider \
    && ./sftpgo resetprovider --force \
    && go test \
        -tags nopgxregisterdefaulttypes,disable_grpc_modules \
        -p 1 -timeout 15m ./...'

FROM source AS binary
ARG SFTPGO_COMMIT=62ae9ba3957e9ed52b44a4f885e805e2d7b35972
ARG SFTPGO_BUILD_DATE=2026-09-18T19:12:40Z
ARG SOURCE_DATE_EPOCH=1789758760

RUN mkdir -p \
        /out/etc/sftpgo \
        /out/srv/sftpgo/data \
        /out/srv/sftpgo/tasks \
        /out/srv/sftpgo/discovery-jobs \
        /out/srv/sftpgo/backups \
        /out/usr/local/bin \
        /out/usr/share/sftpgo \
        /out/var/lib/sftpgo \
    && go build \
        -buildvcs=false \
        -tags nopgxregisterdefaulttypes,disable_grpc_modules,nogcs,nos3,noazblob,nomysql,nopgsql,nosqlite,noportable \
        -trimpath \
        -ldflags "-s -w -buildid= -X github.com/drakkan/sftpgo/v2/internal/version.commit=${SFTPGO_COMMIT} -X github.com/drakkan/sftpgo/v2/internal/version.date=${SFTPGO_BUILD_DATE}" \
        -o /out/usr/local/bin/sftpgo . \
    && chmod 0770 /out/srv/sftpgo/tasks \
    && chmod 0770 /out/srv/sftpgo/discovery-jobs \
    && sed 's|"users_base_dir": "",|"users_base_dir": "/srv/sftpgo/data",|' sftpgo.json \
        | sed 's|"backups"|"/srv/sftpgo/backups"|' \
        | sed 's|"sqlite"|"bolt"|' \
        | sed '/"enabled_ssh_commands": \[/,/\],/c\    "enabled_ssh_commands": [],' \
        > /out/etc/sftpgo/sftpgo.json \
    && grep --quiet --fixed-strings '"enabled_ssh_commands": []' /out/etc/sftpgo/sftpgo.json \
    && cp -R openapi static templates /out/usr/share/sftpgo/ \
    && cp LICENSE NOTICE /out/

FROM source AS vulnerability-tool
RUN GOBIN=/tmp/tools go install golang.org/x/vuln/cmd/govulncheck@v1.9.0

FROM vulnerability-tool AS vulnerability-check
RUN /tmp/tools/govulncheck \
        -tags nopgxregisterdefaulttypes,disable_grpc_modules,nogcs,nos3,noazblob,nomysql,nopgsql,nosqlite,noportable \
        ./...

FROM vulnerability-check AS verification
COPY --from=upstream-tests /src/go.mod /tmp/upstream-tests-passed
COPY --from=binary /out/usr/local/bin/sftpgo /tmp/runtime-binary-built

FROM ${RUNTIME_IMAGE} AS runtime

LABEL org.opencontainers.image.title="J-AXON Local/Test SFTPGo" \
      org.opencontainers.image.version="2.7.6-jaxon.1" \
      org.opencontainers.image.revision="62ae9ba3957e9ed52b44a4f885e805e2d7b35972" \
      org.opencontainers.image.source="https://github.com/drakkan/sftpgo" \
      org.opencontainers.image.licenses="AGPL-3.0-only" \
      com.jaxon.environment-scope="local-test-only" \
      com.jaxon.upstream-image="derived-build-not-official-prebuilt"

COPY --from=binary --chown=65532:65532 /out/ /

ENV HOME=/var/lib/sftpgo \
    SFTPGO_LOG_FILE_PATH="" \
    USER=nonroot
WORKDIR /var/lib/sftpgo
USER 65532:65532
EXPOSE 2022 8080
ENTRYPOINT ["/usr/local/bin/sftpgo"]
CMD ["serve"]
