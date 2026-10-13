# J-AXON Backend

Monolito modular backend de J-AXON. Además de la baseline técnica, implementa login humano, Inventory mínimo, pre-registro Agent y la activación SFTP acotada de `OLA2-SFTP-001a`.

## Requisitos Local

- Java 25 para ejecución nativa del backend.
- Docker Engine con Docker Compose.

## Infraestructura Local

La configuración versionada contiene únicamente valores ficticios. Cree su archivo Local antes de usar Compose:

```bash
cp .env.example .env
docker compose up --detach --wait
```

El comando predeterminado levanta PostgreSQL 18.6 con pgvector 0.8.6 y SFTPGo 2.7.6. Publica PostgreSQL y SFTP únicamente en loopback, no publica la API administrativa SFTPGo y conserva ambos stores en volúmenes nombrados. Ajuste los valores ficticios de `.env` si lo necesita; `.env` está ignorado por Git y no debe contener credenciales de QA o Production.

La imagen SFTPGo se construye desde fuente fijada y verificada, ejecuta como UID/GID `65532`, admite solo autenticación por public key y deshabilita comandos SSH auxiliares. `docker/sftpgo/build-local.sh` valida firma, commit, checksum, pruebas upstream, vulnerabilidades alcanzables, imagen y SBOM temporal. La imagen es exclusivamente Local/Test y no se publica.

El bootstrap Identity está desactivado por defecto. Para una base vacía Local, configure explícitamente `JAXON_IDENTITY_BOOTSTRAP_ENABLED=true`, `JAXON_IDENTITY_BOOTSTRAP_USERNAME` y `JAXON_IDENTITY_BOOTSTRAP_PASSWORD` en `.env`. La contraseña debe tener entre 15 y 256 caracteres y no pertenecer a la blocklist local. El arranque falla si falta configuración, la política no se cumple o ya existe cualquier usuario; no hay credenciales predeterminadas.

Para ejecutar el backend de forma nativa contra esa base de datos:

```bash
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

## Backend Containerizado Opcional

El profile `application` construye y ejecuta también el backend, espera a que PostgreSQL y SFTPGo estén healthy y publica HTTP solo en loopback:

```bash
docker compose --profile application up --detach --build
for attempt in {1..90}; do
  curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness && break
  test "${attempt}" -lt 90 || exit 1
  sleep 1
done
```

Detenga los servicios sin eliminar el volumen persistente Local:

```bash
docker compose --profile application down
```

## Verificación

La verificación automatizada usa puertos dinámicos, un nombre de proyecto único y un volumen efímero propio. Su cleanup no elimina el volumen persistente del proyecto Local:

```bash
./scripts/verify-compose.sh
```

La regresión completa del backend se ejecuta con:

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```
