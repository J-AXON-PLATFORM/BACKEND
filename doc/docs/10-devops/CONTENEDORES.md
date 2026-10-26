# Contenedores

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- contenedores

- Backend/frontend: Dockerfiles multi-stage y runtime no-root.
- Una imagen por componente/version; tags SemVer + Git SHA, identidad de promoción por digest inmutable.
- Local: Compose principalmente para infraestructura; Development/QA pueden usar las imágenes CI; Production no compila código durante deployment.
- Compose con base común + overrides donde aporte; la topología final Production sigue abierta.
- Agent no se fuerza a Docker: tiene packaging PyInstaller por SO. SFTPGo sí puede operar como infraestructura containerizada cuando corresponda.
- DB y administración no se exponen innecesariamente; web HTTPS y SFTP solo para Agents autorizados.
<!-- JAXON-CKP003:END -->

## Estado implementado -- TEC-OPS-001a, TEC-PLT-006a, TEC-PLT-006b y TEC-PLT-007a

El 2026-10-08 se verificó la primera baseline ejecutable de contenedores, limitada a backend y frontend:

- backend multi-stage sobre Temurin JDK/JRE 25 por digest, runtime UID/GID `10001`, puerto 8080, OCI labels y artefacto Spring Boot reproducible;
- health general, liveness y readiness sin componentes ni detalles sensibles; readiness incorpora conexión PostgreSQL; shutdown graceful con 20 segundos;
- fixture efímera local PostgreSQL 18.6 Alpine + pgvector 0.8.6 compilado desde commit y checksum fijados, con `su-exec` y filesystem final aplanado para eliminar `gosu` vulnerable del historial OCI;
- frontend Next.js standalone, build Node.js 24.21.0 por digest y runtime Alpine por digest con UID/GID `10001`; la imagen final contiene Node y dependencias runtime, pero no npm, npx, Corepack, yarn o pnpm;
- smoke tests con filesystem read-only, `/tmp` efímero, puertos dinámicos locales, identidad no-root, metadata OCI, ausencia de archivos sensibles, health/contenido, migración Flyway, schemas y pgvector;
- scans Trivy 0.75.0 completos y bloqueantes para severidades HIGH/CRITICAL, sin `--ignore-unfixed` ni excepciones de imagen.

Las imágenes se construyen solo de forma local o efímera en CI. No se publicaron imágenes, Packages, artefactos, tags o releases. Registry, promoción, SBOM, firma, infraestructura Production y despliegue permanecen pendientes y fuera de estos incrementos.

El 2026-10-09 se verificó la baseline Compose Local:

- `compose.yaml` levanta por defecto solo la fixture PostgreSQL/pgvector mediante build local, healthcheck `pg_isready`, volumen nombrado y red dedicada;
- PostgreSQL y el backend opcional publican puertos exclusivamente en `127.0.0.1`;
- el profile `application` añade el backend no-root y read-only, espera a PostgreSQL healthy y recibe `JAXON_DB_URL`, usuario, contraseña y `JAXON_ENVIRONMENT=local`;
- `.env.example` contiene solo configuración ficticia y el `.env` Local real permanece fuera de Git;
- `scripts/verify-compose.sh` usa puertos dinámicos, un proyecto único y cleanup restringido a su volumen efímero propio, sin eliminar datos Local persistentes ajenos;
- la verificación cubre servicio predeterminado, health/probes, Flyway, ocho schemas, pgvector 0.8.6, loopback, persistencia y runtime backend endurecido.

`OLA2-SFTP-001a` añade SFTPGo 2.7.6 al Compose predeterminado: build desde fuente fijada por commit/checksum/firma, runtime distroless UID/GID `65532`, autenticación exclusiva por public key, comandos SSH auxiliares deshabilitados, SFTP limitado a loopback, API administrativa solo interna y volúmenes separados para estado y buzones. El gate ejecuta pruebas upstream, `govulncheck`, Trivy HIGH/CRITICAL y SBOM CycloneDX temporal; no publica la imagen.

`OLA2-SNP-001a` no cambia la imagen ni Compose. Evoluciona solo el aprovisionamiento Local/Test: cada Agent recibe un virtual folder `/snapshots` determinista, aislado y mapeado fuera de su `home_dir`, con límite global de upload de 5 MiB y permisos mínimos para carga, rename, creación de directorios y limpieza de archivos parciales. El perfil anterior exacto puede actualizarse; cualquier otro drift se rechaza.

`OLA4-DIS-003a` tampoco cambia imagen ni Compose. Añade solo un perfil SFTPGo Local/Test separado del Agent: username/home `discovery-executor-{executorId}`, una public key Ed25519, autenticación sin contraseña, permiso mínimo `list` sobre `/`, sin virtual folders, grupos, cuotas funcionales, `/jobs` o `/results`. La reconciliación acepta perfil exacto o creación; cualquier drift se rechaza. Production permanece fuera de alcance.

`OLA4-DIS-004a` amplía esa fixture Local/Test sin añadir servicios: volumen dedicado `/srv/sftpgo/discovery-jobs`, escritura backend y virtual folder `/jobs` read-only para cada executor, mapeado a un directorio determinista aislado. El perfil permite solo `list|download`; conserva sin contraseña, grupos o cuotas funcionales y sigue sin `/results`. Compose smoke verifica montaje y permisos; Production permanece fuera de alcance.

`OLA4-DIS-005a` añade solo el volumen SFTPGo Local/Test `/srv/sftpgo/discovery-results` y el virtual folder aislado `/results`. El executor puede `list|download|upload|rename_files`, sin delete, overwrite o creación de directorios, con máximo 5 MiB; backend no monta ese volumen porque la ingestión no está autorizada. Los perfiles históricos exactos se actualizan y cualquier drift se rechaza. Production permanece fuera de alcance.

Compose no incluye frontend, n8n, observabilidad, reverse proxy, TLS o topología Production.

## Propósito

Definir uso de contenedores cuando aportan aislamiento y reproducibilidad.

## Imagen

- base mínima y mantenida;
- versiones/digests;
- multi-stage build si aporta valor;
- usuario no root;
- filesystem read-only cuando sea viable;
- healthcheck según plataforma;
- sin secretos embebidos.

## Runtime

Definir:

- CPU;
- memoria;
- puertos;
- volúmenes;
- variables;
- señales;
- shutdown;
- logs;
- red.

## Seguridad

- scan;
- SBOM;
- firma si aplica;
- actualización;
- capabilities mínimas;
- no ejecutar privilegiado sin justificación.

## Persistencia

El contenedor debe considerarse reemplazable. Datos persistentes viven en mecanismos explícitos.

## Pruebas

Validar build, arranque, health y shutdown.
