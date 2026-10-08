# Historial de cambios

> **Instancia J-AXON | CKP-JAXON-005 | 2026-09-29**
> Cumplimentación parcial: solo decisiones aprobadas. Las guías genéricas se conservan; sus ejemplos no son requisitos aprobados del producto.

## Fundación, publicación y CI base del frontend -- TEC-PLT-004a, TEC-GIT-002, TEC-QLT-001a y TEC-DOC-005, 2026-10-08

Se crea y verifica la fundación técnica de `frontend` con Node.js 24.21.0, npm 11.19.0, Next.js 16.3.8, React 19.3.0, TypeScript 5.9.3, App Router, Server Components por defecto, Vitest y React Testing Library. La página mínima identifica J-AXON sin simular funcionalidad de negocio y queda no-indexable mediante metadata y `robots.txt`. La validación local confirma `npm ci`, lint, typecheck, una prueba, build, `verify` y smoke tests de `/` y `/robots.txt`.

ESLint 10.12.0 se intentó según la decisión condicionada, pero `eslint-plugin-react@7.37.5` falló de forma ejecutable. Con autorización expresa de LUIS se fijó temporalmente ESLint 9.39.5, compatible pero EOL. La auditoría de producción queda limpia. La auditoría completa conserva una excepción aprobada el 2026-10-08 exclusivamente para `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, transitiva de herramientas de desarrollo y sin versión corregida disponible; un verificador cerrado falla ante cualquier advisory, paquete, versión, alcance o error diferente.

`FRONTEND` se publica como repositorio privado con commit inicial `ffec9bdd39d5168ed5f7d3bbaa68d5d4bc5860ab` y ramas permanentes `main` y `develop`. `Frontend CI` se integra en `develop` mediante el Pull Request `#1` y squash `0a34d6dadacc95351544996235faf6bcb0b21b65`. El run de PR `37849248403` y el run post-merge `37849379045` terminan verdes con `npm ci`, lint, typecheck, Vitest/RTL, build y las auditorías de producción/completa. Las actions externas quedan fijadas por SHA completo, los permisos se limitan a `contents: read` y no se usan secretos, artefactos ni despliegues.

La documentación canónica continúa en `backend/doc`. No se crean lógica de negocio, APIs, contratos, migraciones, Dockerfile, Compose, Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados ni despliegues. `automation-collector` permanece vacío, J-AXON conserva `DESIGN_READY` y `PRODUCTION_READY = NO`.

---

## CI base del backend -- TEC-PLT-003a, 2026-10-08

Se implementa y verifica `Backend CI` en `.github/workflows/backend-ci.yml`. El workflow ejecuta `./mvnw --batch-mode --no-transfer-progress clean verify` con Java 25 Temurin sobre `ubuntu-24.04` en Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecuciones manuales. La evidencia local y GitHub Actions confirma compilación, tres pruebas sin fallos, verificación Spring Modulith y la integración Testcontainers con PostgreSQL 18.6, pgvector 0.8.6, Flyway y los ocho schemas aprobados.

Las acciones externas están fijadas por SHA completo, los permisos se limitan a `contents: read`, `persist-credentials` está deshabilitado y no se usan secretos ni se publican artefactos o despliegues. Contratos y SFTP permanecen fuera hasta disponer de artefactos ejecutables aplicables. La activación se integra mediante el Pull Request `#1` hacia `develop`, con checks verdes sobre su última revisión y fusión `Squash and merge`.

Los rulesets y protecciones automáticas siguen pendientes por la limitación del plan actual para repositorios privados. La disciplina de Pull Request y checks verdes se aplica operativamente, pero GitHub no impide técnicamente una fusión incorrecta. `main` no se modifica y puede quedar temporalmente detrás de `develop`. No se crean acuerdos, migraciones, contratos, lógica de negocio, tablas, tags, releases, Packages, artefactos publicados ni despliegues; `PRODUCTION_READY = NO`.

---

## Publicación controlada del repositorio backend -- TEC-GIT-001C, 2026-10-07

Se inicializa Git exclusivamente en `backend`, se configura el remoto SSH oficial `git@github.com:J-AXON-PLATFORM/BACKEND.git` y se publica la baseline verificada. El historial queda formado por los commits `chore: bootstrap backend repository` y `docs: record backend repository publication`; las ramas permanentes `main` y `develop` apuntan al mismo commit final y siguen sus ramas remotas.

La publicación fue autenticada mediante una clave SSH Ed25519 dedicada y verificación estricta de la huella oficial de `github.com`. Antes de inicializar Git se ejecutaron satisfactoriamente las pruebas Maven con Java 25 y las validaciones documentales normal y estricta.

`frontend` y `automation-collector` permanecen vacíos y sin Git local. No se crearon Issues, Projects, Pull Requests, workflows, rulesets, tags, releases, artefactos ni despliegues. No se modificaron código, pruebas, dependencias, contratos, datos, migraciones, `VERSION` ni el Registro de Acuerdos; `DESIGN_READY` y `PRODUCTION_READY = NO` se conservan.

---

## Corrección del nombre documental REVERSION -- TEC-DOC-003, 2026-10-07

Se corrige un nombre documental afectado por mojibake y se adopta el nombre ASCII `docs/14-operaciones/REVERSION.md`. El contenido documental no cambia y se actualiza únicamente su referencia en `MANIFIESTO_FRAMEWORK.md`.

Esta corrección no crea acuerdo, Lote, checkpoint ni versión. No se modificaron código, Git ni GitHub.

---

## Sincronización documental del estado real de GitHub -- TEC-DOC-002, 2026-10-07

Se registra el estado remoto verificado de la organización nueva `J-AXON-PLATFORM` y sus tres repositorios privados vacíos: `BACKEND` (`https://github.com/J-AXON-PLATFORM/BACKEND`), `FRONTEND` (`https://github.com/J-AXON-PLATFORM/FRONTEND`) y `AUTOMATION-COLLECTOR` (`https://github.com/J-AXON-PLATFORM/AUTOMATION-COLLECTOR`). Los nombres lógicos canónicos permanecen `backend`, `frontend` y `automation-collector`; las mayúsculas visibles no cambian el modelo MULTIREPO, y GitHub trata estos identificadores sin distinción efectiva entre mayúsculas y minúsculas.

Los repositorios remotos no contienen archivos, código, commits, ramas, Issues, Projects, Pull Requests, workflows, rulesets, releases, artefactos ni despliegues. Git no fue inicializado en los repositorios locales y no se configuraron remotos. La organización histórica distinta `j-axon` y sus repositorios preexistentes son independientes, no fueron modificados y no deben confundirse con el nuevo inicio oficial.

La autorización de LUIS cubrió exclusivamente la creación de `J-AXON-PLATFORM` y de los tres repositorios privados vacíos, y quedó consumida. No autoriza publicación posterior, inicialización Git, remotos, commits, ramas, backlog remoto, CI/CD, rulesets ni despliegues. Esta sincronización no cambia `DESIGN_READY`, el alcance implementado por `TEC-PLT-001a` y `TEC-PLT-002a`, ni `PRODUCTION_READY = NO`; no crea acuerdo, Lote, checkpoint o versión documental.

---

## Actualización de continuidad -- TEC-PLT-002a, 2026-10-06

Se completa y verifica el bootstrap de persistencia del backend con PostgreSQL 18.6, pgvector 0.8.6, PostgreSQL JDBC 42.7.13, Flyway 12.4.0 y Testcontainers 2.0.5. La imagen de integración `pgvector/pgvector:0.8.6-pg18-bookworm` aplicó desde una base vacía la migración `V20261006000000__bootstrap_schemas.sql`.

La migración habilita la extensión `vector` y crea exactamente los schemas `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation` y `audit_reporting`. La prueba real confirmó la versión del servidor, la versión de pgvector, el historial Flyway, los ocho schemas y la ausencia de tablas de negocio tanto en los schemas modulares como en `public`.

Continúan sin implementarse lógica funcional, entidades, repositorios de dominio, servicios, APIs, OpenAPI ejecutable, frontend y `automation-collector`. No se modificaron contratos, Git, GitHub, despliegues ni infraestructura Production. `DESIGN_READY` se conserva, `IMPLEMENTATION_READY` continúa evaluándose por alcance concreto y `PRODUCTION_READY = NO`; la siguiente tarea técnica no ha sido iniciada.

---

## Restauración de integridad documental -- TEC-DOC-001, 2026-10-06

Se restauran los 11 archivos declarados en `MANIFIESTO_FRAMEWORK.md` que faltaban bajo `.github/` y `.ia/adaptadores/`, mediante copia binariamente idéntica desde la fuente íntegra de solo lectura `doc_J-AXON_1.0.0/doc`. El framework vuelve a contener exactamente los 221 archivos inventariados, sin faltantes ni adicionales.

La restauración fue verificada mediante hashes SHA-256, comparación binaria, contraste de inventarios y validación documental normal y estricta. La validación normal concluye con 0 errores y 0 advertencias; la estricta concluye con 0 errores y conserva la advertencia conocida sobre 14 referencias a `POR_DECIDIR`.

Se mantienen sin cambios la versión 1.0.0, `MANIFIESTO_FRAMEWORK.md` y el Registro de Acuerdos. No se modificaron código, pruebas, dependencias, contratos, datos, migraciones, infraestructura, Git ni GitHub.

---

## Actualización de continuidad -- TEC-PLT-001a, 2026-10-06

Se sincroniza el estado documental después de completar y verificar `TEC-PLT-001a`. El alcance implementado es exclusivamente el bootstrap estructural del backend: Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Maven Wrapper con Maven 3.9.16, clase principal y ocho módulos estructurales.

La verificación ejecutada en la tarea técnica concluyó con dos pruebas satisfactorias: carga del contexto Spring Boot y verificación de los ocho módulos con Spring Modulith. No existe lógica funcional implementada. La primera vertical E2E, PostgreSQL, schemas, Flyway, OpenAPI ejecutable, CI, Docker, Compose, frontend y `automation-collector` continúan sin implementar.

No se afectaron `frontend`, `automation-collector`, contratos, datos, migraciones, despliegues ni infraestructura Production. Git no fue inicializado ni modificado y GitHub no fue modificado. La baseline documental continúa `DESIGN_READY`, el framework `doc` permanece en versión 1.0.0 y `PRODUCTION_READY = NO`.

Esta actualización no crea un acuerdo, Lote ni checkpoint nuevo. CKP-JAXON-005 se conserva como el cierre histórico del diseño/refinamiento hasta el Lote 54. La continuidad posterior es incremental y por alcance concreto; la siguiente tarea técnica no ha sido iniciada.

---



## CKP-JAXON-005 -- cierre de refinamiento/diseño v1 hasta Lote 54, 2026-09-29

Se mantiene el framework `doc` 1.0.0 y la implementación nueva continúa sin iniciarse ni estar autorizada. Se consolidan los Lotes 49--54: Support/Tickets detallado; Inventory/Activos detallado; catálogo de tareas/capabilities Collector; Auditoría/Reportes; contratos/eventos internos con Transactional Outbox; y cierre de readiness v1 con UUIDv4 y baseline controlada.

El proyecto queda documentalmente `DESIGN_READY`, separado de `IMPLEMENTATION_READY` y `PRODUCTION_READY`. Los pendientes restantes son deliberados por Production/instalación, organización, evidencia o scope futuro; no deben rellenarse artificialmente. No se crea/modifica GitHub ni se implementa código.

---

## CKP-JAXON-004 -- refinamiento aprobado hasta Lote 48, 2026-09-29

Se mantiene el framework `doc` 1.0.0 y la implementación nueva continúa sin iniciarse. Se consolidan los Lotes 41--48: storage documental/retención/backups/RPO-RTO; arquitectura RAG; Automation+n8n; infraestructura LAN/TLS/secretos; observabilidad/SLOs/alertas; rendimiento/capacidad; arquitectura frontend; y Discovery detallado. No se modifica GitHub ni se implementa código.

El punto de continuación pasa al **Lote 49 -- MOD-04 Support/Tickets**. La continuidad obliga a leer primero Registro de Acuerdos, Trabajo Actual, Proyecto Maestro, Pendientes y Enrutador, y luego requisitos/flujos/autorización/retención/contratos específicos del lote.

---

## CKP-JAXON-003 -- refinamiento aprobado hasta Lote 40, 2026-09-29

Se mantiene el framework `doc` 1.0.0. Se consolidan los Lotes 30--40: versiones/runtime/build; estructura de repositorios y configuración por cinco entornos; PostgreSQL/Flyway; contenedores/promoción; CI/CD; identidad humana/RBAC; SFTP/identidad técnica/packaging Agent; contratos OpenAPI/JSON Schema; primera vertical E2E; y resiliencia operacional Agent con valores iniciales configurables. No se implementa código ni se modifica GitHub.

Se depura la continuidad: `CHECKPOINT_JAXON.md`, `CONTROL_CAMBIOS_CHECKPOINT.md` e `INVENTARIO_ARCHIVOS_CHECKPOINT.json` dejan de formar parte de `doc/` porque eran artefactos de checkpoint del proyecto y no del framework reutilizable. El checkpoint CKP-JAXON-003 se distribuye como archivo externo junto al ZIP. `docs/16-contexto/` se conserva porque sí es el mecanismo del framework para contexto dinámico de proyecto.

---

## CKP-JAXON-002 -- cierre de descubrimiento inicial, 2026-09-29

Se mantiene el framework `doc` 1.0.0. Se consolidan los lotes de decisiones aprobados posteriores a CKP-JAXON-001: contratos, capacidad, datos, seguridad, entornos, pruebas, DevOps, infraestructura, observabilidad, resiliencia, operación, integraciones, Git, metodología, reglas IA, protocolo de decisiones, alcance v1, requisitos, riesgos, activación y backlog. Se actualizan checkpoint, Maestro, acuerdos, pendientes y documentos funcionales principales. No se implementa código ni se modifica GitHub.

Los conteos aprobados se recalculan: la lista de requisitos contiene 90 elementos y el backlog inicial 85 elementos; solo se corrige aritmética, no contenido.

La validación estructural de ese corte quedó registrada en el artefacto histórico externo de control de CKP-JAXON-002.

---

## CKP-JAXON-001 -- consolidación de la instancia, 2026-09-29

Se mantiene la versión del framework 1.0.0. Se cumplimentan campos aprobados del Proyecto Maestro y se agregan contexto de continuidad, acuerdos, pendientes, evidencia histórica y control de conservación de archivos.

No se implementaron decisiones nuevas ni se alteró el proyecto legado, la versión del framework o el validador. En ese corte todavía se conservaban los artefactos raíz de checkpoint, retirados del paquete `doc/` a partir de CKP-JAXON-003.

---


## 1.0.0

Versión estable consolidada de `doc` como framework documental dinámico para proyectos de software de distinta escala y arquitectura.

### Activación dinámica

- Separación entre **perfil documental** (`LIGERO`, `ESTANDAR`, `EMPRESARIAL`) y **perfiles técnicos**.
- Nueva `MATRIZ_ACTIVACION.md` para activar documentación según arquitectura, plataforma, riesgo y operación.
- Perfiles para backend, frontend, móvil, escritorio, CLI, batch/workers, datos/ETL, monolito, monolito modular, microservicios, eventos, serverless, cloud, híbrido y multicloud.
- Regla explícita contra activar complejidad por moda o por la mera existencia de una plantilla.

### Arquitectura

- Arquitectura distribuida con límites, comunicación síncrona/asíncrona, consistencia, fallos parciales, mensajería y pruebas.
- Arquitectura cloud independiente de proveedor.
- Resiliencia: timeouts, retries, backoff, jitter, idempotencia, circuit breakers, bulkheads, rate limiting, backpressure y DLQ.
- Rendimiento y capacity planning con latencia, throughput, concurrencia, pools, DB, caché y escalado.
- Perfiles técnicos reutilizables y combinables.
- Guía específica para comparar monolito, monolito modular, microservicios, eventos y serverless.

### Datos

- Gobierno y ownership de datos.
- Calidad y linaje.
- Schema evolution.
- CDC.
- ETL/ELT.
- Replicación.
- Particionamiento.
- Multi-tenancy.
- Escalabilidad y archivado.
- Refuerzo de estrategia de base de datos y migraciones compatibles.

### Frontend

- Arquitectura frontend ampliada.
- Renderizado CSR/SSR/SSG/híbrido.
- Gestión explícita de estado local, compartido, server-state, URL y persistido.
- Rendimiento frontend.
- Internacionalización.
- Observabilidad de cliente.
- Accesibilidad, responsive, routing, componentes, errores y sistema de diseño reforzados.

### Plataformas

Nueva área `docs/18-plataformas/` para:

- móvil;
- escritorio;
- CLI;
- batch/workers;
- serverless.

Esto evita tratar todo proyecto como frontend web + backend.

### Seguridad

- Seguridad de dependencias ampliada.
- Nueva guía de cadena de suministro de software.
- SAST, DAST, secret scanning, dependency scanning, container/IaC scanning, SBOM, firma y procedencia como controles condicionales.

### DevOps y cloud

- CI y CD ampliados.
- Infraestructura como código.
- Gestión de estado y drift.
- Estrategias de despliegue: recreate, rolling, blue/green, canary, feature flags y shadow traffic.
- Compatibilidad de base de datos con expand-contract.
- Rollback y roll-forward.
- Entornos, contenedores, infraestructura y quality gates reforzados.

### Observabilidad y operación

- Logs, métricas, trazas, health checks, alertas y SLI/SLO/SLA ampliados.
- Runbooks operativos.
- Gestión de incidentes.
- Postmortems.
- Recuperación ante desastres con RTO/RPO y drills.
- Prácticas SRE y error budgets cuando correspondan.

### Testing

- Pruebas de rendimiento ampliadas con percentiles, throughput, carga y diagnóstico.
- Pruebas de integración y unitarias reforzadas.
- Nuevas guías de pruebas de componente, sistema, resiliencia, migraciones y accesibilidad.
- Se conserva estrategia de validación integral basada en riesgo en lugar de imponer todas las capas.

### IA y contexto

- Enrutador documental ampliado para nuevas plataformas y arquitecturas.
- Reglas especializadas para IA en arquitectura, DevOps, datos y operaciones.
- Lectura progresiva para reducir consumo de contexto.
- Proceso: análisis → alternativas → recomendación → aprobación → implementación → pruebas → documentación → verificación.

### Validación documental

- Nuevo `tools/validate_docs.py`.
- Verifica versión, archivos esenciales, clasificación, enlaces locales, marcadores heredados y activación de perfiles seleccionados.
- Modo `--strict` para advertir decisiones pendientes.
- La propia versión 1.0.0 se entrega con el validador estructural sin errores ni advertencias en modo normal.

### Gestión y automatización

- Scrum + Kanban.
- Jerarquía Épica → Funcionalidad → Historia de Usuario → Tarea.
- Automatización de backlog opcional.
- Publicación remota únicamente con permisos explícitos.
