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

## Estado implementado -- TEC-OPS-001a, TEC-PLT-006a y TEC-PLT-006b

El 2026-10-08 se verificó la primera baseline ejecutable de contenedores, limitada a backend y frontend:

- backend multi-stage sobre Temurin JDK/JRE 25 por digest, runtime UID/GID `10001`, puerto 8080, OCI labels y artefacto Spring Boot reproducible;
- health general, liveness y readiness sin componentes ni detalles sensibles; readiness incorpora conexión PostgreSQL; shutdown graceful con 20 segundos;
- fixture efímera local PostgreSQL 18.6 Alpine + pgvector 0.8.6 compilado desde commit y checksum fijados, con `su-exec` y filesystem final aplanado para eliminar `gosu` vulnerable del historial OCI;
- frontend Next.js standalone, build Node.js 24.21.0 por digest y runtime Alpine por digest con UID/GID `10001`; la imagen final contiene Node y dependencias runtime, pero no npm, npx, Corepack, yarn o pnpm;
- smoke tests con filesystem read-only, `/tmp` efímero, puertos dinámicos locales, identidad no-root, metadata OCI, ausencia de archivos sensibles, health/contenido, migración Flyway, schemas y pgvector;
- scans Trivy 0.75.0 completos y bloqueantes para severidades HIGH/CRITICAL, sin `--ignore-unfixed` ni excepciones de imagen.

Las imágenes se construyen solo de forma local o efímera en CI. No se publicaron imágenes, Packages, artefactos, tags o releases. Compose, registry, promoción, SBOM, firma, infraestructura Production y despliegue permanecen pendientes y fuera de este incremento.

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
