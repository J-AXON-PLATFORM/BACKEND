FROM eclipse-temurin:25-jdk-noble@sha256:589ff4cc3f71aab462e7048a47a0d10edf57fbccde3fceea2281e610bf5880b4 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
COPY doc/contracts/agent/1.0/ doc/contracts/agent/1.0/
COPY doc/contracts/discovery/1.0/ doc/contracts/discovery/1.0/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package && \
    cp target/jaxon-backend-*.jar /workspace/application.jar

FROM eclipse-temurin:25-jre-noble@sha256:d9a39a23634650173f1e2bbc176227af9728587ecf0f4b62d53e9355cd7a19ab

ARG VCS_REF=unknown

LABEL org.opencontainers.image.title="J-AXON Backend" \
      org.opencontainers.image.description="J-AXON modular backend" \
      org.opencontainers.image.version="0.1.0-SNAPSHOT" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/J-AXON-PLATFORM/BACKEND"

RUN groupadd --system --gid 10001 jaxon && \
    useradd --system --uid 10001 --gid jaxon --home-dir /app --shell /usr/sbin/nologin jaxon && \
    mkdir -p /app && \
    chown jaxon:jaxon /app

WORKDIR /app
COPY --from=build --chown=jaxon:jaxon /workspace/application.jar application.jar

ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp"

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
