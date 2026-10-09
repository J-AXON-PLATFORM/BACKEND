# J-AXON Backend

Fundación del monolito modular backend de J-AXON. La baseline actual incluye Spring Boot, PostgreSQL, pgvector, Flyway, health/probes y contenedores; todavía no contiene funcionalidad de negocio.

## Requisitos Local

- Java 25 para ejecución nativa del backend.
- Docker Engine con Docker Compose.

## PostgreSQL Local

La configuración versionada contiene únicamente valores ficticios. Cree su archivo Local antes de usar Compose:

```bash
cp .env.example .env
docker compose up --detach --wait
```

El comando predeterminado levanta únicamente PostgreSQL 18.6 con pgvector 0.8.6, publica el puerto en `127.0.0.1:5432` y conserva los datos en un volumen nombrado. Ajuste los valores ficticios de `.env` si lo necesita; `.env` está ignorado por Git y no debe contener credenciales de QA o Production.

Para ejecutar el backend de forma nativa contra esa base de datos:

```bash
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

## Backend Containerizado Opcional

El profile `application` construye y ejecuta también el backend, espera a que PostgreSQL esté healthy y publica HTTP solo en loopback:

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
