FROM postgres:18.6-alpine3.24@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873 AS pgvector-build

ARG PGVECTOR_COMMIT=8ee86c96f0fd72390f890aa8a336fda6d3ab4c6c
ARG PGVECTOR_SHA256=d076a3098010905fd60256649327809651f6288327db6413f0938305f62ea299

USER root

RUN apk add --no-cache build-base clang21 curl llvm21-dev tar && \
    curl --fail --location --silent --show-error \
      --output /tmp/pgvector.tar.gz \
      "https://github.com/pgvector/pgvector/archive/${PGVECTOR_COMMIT}.tar.gz" && \
    echo "${PGVECTOR_SHA256}  /tmp/pgvector.tar.gz" | sha256sum -c - && \
    mkdir /tmp/pgvector && \
    tar --extract --gzip --file /tmp/pgvector.tar.gz --directory /tmp/pgvector --strip-components=1 && \
    make --directory /tmp/pgvector OPTFLAGS="" && \
    make --directory /tmp/pgvector install DESTDIR=/opt/pgvector

FROM postgres:18.6-alpine3.24@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873 AS postgres-runtime

USER root

RUN apk add --no-cache brotli-libs=1.2.1-r0 libcurl=8.23.0-r0 krb5-libs=1.22.3-r0 libxml2=2.13.9-r3 libxslt=1.1.43-r4 musl=1.2.6-r3 musl-utils=1.2.6-r3 zlib=1.4-r0 su-exec && \
    rm /usr/local/bin/gosu && \
    install -m 0755 /sbin/su-exec /usr/local/bin/gosu

COPY --from=pgvector-build /opt/pgvector/usr/local/lib/postgresql/ /usr/local/lib/postgresql/
COPY --from=pgvector-build /opt/pgvector/usr/local/share/postgresql/extension/ /usr/local/share/postgresql/extension/

FROM scratch

LABEL org.opencontainers.image.title="J-AXON PostgreSQL pgvector test fixture" \
      org.opencontainers.image.description="Ephemeral PostgreSQL 18.6 and pgvector 0.8.6 test fixture" \
      org.opencontainers.image.version="18.6-0.8.6" \
      org.opencontainers.image.source="https://github.com/J-AXON-PLATFORM/BACKEND"

COPY --from=postgres-runtime / /

ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=en_US.utf8 \
    PG_MAJOR=18 \
    PG_VERSION=18.6 \
    PG_SHA256=555610c24d53e4316da5b7d3fc25c279d96856d5e0e23ee308c328c5fa881d9f \
    PGDATA=/var/lib/postgresql/18/docker

USER root
WORKDIR /
VOLUME /var/lib/postgresql
EXPOSE 5432
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["postgres"]
STOPSIGNAL SIGINT
