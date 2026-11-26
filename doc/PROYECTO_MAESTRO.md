# Proyecto Maestro

> **Clasificación:** OBLIGATORIO  
> **Versión del framework:** 1.0.0  
> **Proyecto:** J-AXON  
> **Revisión de continuidad:** CKP-JAXON-005 | `OLA6-AUD-001d`: login fallido y claves públicas técnicas, 2026-11-29
> **Estado documental:** baseline v1 `DESIGN_READY`; OLA 5 y `OLA6-AUD-001a`--`001c` verificadas; `001d` autorizado bajo `AC-JAXON-096`; `PRODUCTION_READY = NO`.
> **Regla:** Los acuerdos aprobados son diseño/forma de trabajo, no evidencia de implementación. Los campos que siguen `POR_DECIDIR` no deben inventarse.

Para continuidad leer, en este orden: [REGISTRO_ACUERDOS_JAXON.md](docs/00-gobierno/REGISTRO_ACUERDOS_JAXON.md), [TRABAJO_ACTUAL.md](docs/16-contexto/TRABAJO_ACTUAL.md), este `PROYECTO_MAESTRO.md` y [PENDIENTES_JAXON.md](docs/16-contexto/PENDIENTES_JAXON.md). El checkpoint conversacional se distribuye aparte del framework como `CHECKPOINT_JAXON_CKP_005.md`.

## 0. Estado de inicialización

- Estado: `DESIGN_READY`: descubrimiento y refinamiento/diseño detallado v1 cerrados documentalmente; la implementación comenzó de forma acotada con las tres fundaciones y sus CI base, sin autorización general para el resto del backlog.
- Implementación técnica de OLA 0: bootstrap backend y persistencia técnica con ocho schemas; health, contenedores y Compose Local; observabilidad base; backup/restore lógico PostgreSQL con manifest SHA-256 y prueba aislada; OpenAPI 3.1.2 contract-first; JSON Schema Agent 1.0, fixtures y pruebas; copia vendorizada y validador Agent Python offline; fundaciones frontend/Python; Dependabot y Gitleaks multirepo; CI ampliadas. `OLA1-IAM-001a` añadió autenticación humana, `OLA1-INV-001a` alta/consulta mínima de activos y `OLA2-AGT-001a` pre-registro, identidad técnica y registro de public key Agent.
- Madurez: baseline documental `DESIGN_READY` e implementación incremental iniciada; `PRE_IMPLEMENTACION` fue el estado anterior al inicio de `TEC-PLT-001a`.
- Modelo de repositorios: `MULTIREPO`: `frontend`, `backend`, `automation-collector`.
- Estado remoto histórico verificado el 2026-10-07: organización `J-AXON-PLATFORM` creada con tres repositorios privados. `backend` fue inicializado y publicado de forma controlada en `https://github.com/J-AXON-PLATFORM/BACKEND` mediante `TEC-GIT-001C`.
- Estado remoto frontend verificado el 2026-10-08: `FRONTEND` fue publicado como privado con ramas `main` y `develop`; `Frontend CI` fue integrado en `develop` mediante el Pull Request `#1`, squash `0a34d6dadacc95351544996235faf6bcb0b21b65`; sus runs de PR y post-merge quedaron verdes.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` fue publicado como privado con ramas `main` y `develop`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1`, squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados ni despliegues.
- Los nombres lógicos canónicos continúan siendo `backend`, `frontend` y `automation-collector`; las mayúsculas visibles en GitHub no cambian la arquitectura MULTIREPO. GitHub trata estos identificadores sin distinción efectiva entre mayúsculas y minúsculas. La organización histórica distinta `j-axon` y sus repositorios preexistentes son ajenos al nuevo inicio controlado y no fueron modificados.
- Ubicación de documentación general: `backend/doc/`.
- Perfil documental: `ESTANDAR`.
- Responsable de aprobar decisiones significativas: usuario promotor de J-AXON; identidad formal `POR_DECIDIR`.
- Fecha de última revisión: 2026-11-28.
- Refinamiento aprobado hasta: **Lote 54**. Lotes 49--54 consolidan Support, Inventory, Collector tasks, Audit/Reporting, contratos/eventos internos y readiness. **No existe próximo lote de refinamiento funcional planificado antes de implementación**; cualquier implementación requiere autorización expresa.
- Estado funcional: Identity, Inventory y Agent/Collector autorizados, Support hasta lifecycle terminal, Discovery hasta creación/vínculo `INCORPORATED`, OLA 5 y `OLA6-AUD-001a`--`001c` están integrados y verificados. `OLA6-AUD-001d` está autorizado para futuros login fallido y registros de clave Agent/Executor 2.0; `429`, backfill, reportes, purga y Production quedan fuera.

## 1. Identidad

- Nombre: J-AXON.
- Descripción: plataforma de gestión tecnológica para una organización, con inventario de activos, descubrimiento de red, Collector/Agents, soporte/tickets, IA+RAG, identidad, automatización/notificaciones y auditoría/reportes.
- Problema: reconstruir de forma controlada un proyecto anterior incompleto, evitando heredar contratos, decisiones o dependencias no verificadas.
- Objetivo v1: administrar activos tecnológicos, recibir información técnica mediante Agents, descubrir equipos de red de forma autorizada, gestionar soporte asociado a esos activos y ofrecer asistencia IA/RAG con trazabilidad y control de acceso.
- Criterio funcional de éxito de v1: un QA limpio puede ejecutar de extremo a extremo identidad, activo, Agent/snapshot, tarea SFTP, descubrimiento, ticket, RAG autorizado y auditoría con datos de prueba y sin depender del proyecto legado.
- Criterios complementarios: tests críticos verdes, contratos validados, migraciones reproducibles, instalación documentada y ausencia de vulnerabilidades bloqueantes conocidas según las políticas aprobadas.
- Roles humanos v1 aprobados: `SYSTEM_ADMIN`, `IT_OPERATOR`, `SUPPORT_TECHNICIAN`, `END_USER`, `AUDITOR`; un usuario puede tener múltiples roles. La matriz exhaustiva permiso->operación se completa por funcionalidad, aplicando default deny y checks contextuales.
- Partes interesadas nominales: `POR_DECIDIR`.
- Fecha objetivo, presupuesto y restricciones regulatorias concretas: `POR_DECIDIR`.

## 2. Alcance y restricciones

### Dentro de v1

Los ocho módulos lógicos participan en v1, con distinta profundidad:

- Núcleo: Identidad y Acceso; Inventario y Activos; Collector/Agents; Soporte/Tickets.
- Capacidades controladas: Descubrimiento de Red; IA+RAG; Automatización/Notificaciones; Auditoría/Reportes.

Capacidades mínimas: autenticación local y RBAC; inventario manual y proveniente de descubrimiento; registro de Agent, snapshots y tareas controladas por SFTP; descubrimiento en rangos autorizados; ciclo básico de tickets; RAG con fuentes autorizadas; automatizaciones/notificaciones básicas; auditoría y reportes esenciales.

### Fuera de v1 inicial

- microservicios de dominio;
- Kubernetes;
- alta disponibilidad activa-activa;
- zero-downtime obligatorio;
- SaaS multi-tenant compartido;
- aplicación móvil;
- aplicación de escritorio para usuarios;
- SSO/AD/LDAP empresarial;
- IA autónoma sobre activos/tickets/comandos;
- shell remota arbitraria;
- despliegue masivo avanzado de Agents;
- motor BPM/workflows complejo;
- BI/report builder avanzado;
- telemetría de altísima frecuencia;
- personalización rígida para un único cliente;
- exposición pública a Internet por defecto;
- SLA comercial rígido antes de disponer de mediciones.

### Restricciones vigentes

- Escenario base LAN/on-premise.
- Web: HTTPS + REST + OpenAPI + Swagger UI.
- Agents: SFTP + archivos JSON/JSON Schema versionados para snapshots, tareas y respuestas.
- No sustituir SFTP por HTTP/HTTPS sin nueva decisión aprobada.
- No eliminar archivos de `doc` ni convertir propuestas/ejemplos/legado en acuerdos.
- Base nueva para el reinicio; no borrar ni importar automáticamente el legado.

## 3. Producto y negocio

### Flujos principales de v1

1. Identidad -> registrar/consultar activo -> registrar Agent -> snapshot SFTP -> Collector valida/persiste observación técnica -> frontend compone ficha oficial de Inventory + estado observado de Collector -> ticket -> IA/RAG asiste -> técnico resuelve -> auditoría registra. Cualquier promoción futura de un dato observado a dato oficial debe ser una operación explícita de Inventory, no una escritura directa de Collector.
2. Usuario autorizado -> trabajo de descubrimiento -> ejecutor designado -> dispositivos encontrados -> revisión humana -> incorporación seleccionada -> Inventario.
3. Usuario autorizado -> backend crea tarea -> buzón SFTP -> Agent recoge/valida -> ejecuta acción permitida -> responde por SFTP -> Collector procesa -> estado actualizado -> SSE avisa al navegador cuando aplique.

### Reglas críticas

- Cada módulo controla sus datos y reglas; no hay escritura directa en tablas ajenas.
- Un activo puede existir sin Agent.
- Agent offline no implica activo averiado.
- Descubrir != incorporar != instalar Agent.
- IA/RAG asiste; no cierra tickets, modifica activos ni ejecuta comandos autónomamente.
- Soporte sigue operativo sin IA.
- Automatización solicita acciones al módulo dueño; n8n no es propietario de reglas centrales.
- Auditoría/Reportes no modifica datos operativos ajenos.

### Escala inicial de diseño

- Aproximadamente 350 dispositivos inicialmente; diseño sin rediseño completo alrededor de 1000.
- Aproximadamente 30 usuarios web concurrentes típicos.
- IA/RAG: miles de documentos inicialmente, con crecimiento.
- SLOs iniciales y targets de performance están aprobados; hardware Production definitivo, picos reales, SLA contractual y límites certificados siguen sujetos a medición/contrato.

## 4. Módulos y dominios

Ocho módulos lógicos aprobados. No son ocho repositorios, bases ni microservicios.

| ID | Módulo | Esquema | Responsabilidad principal |
|---|---|---|---|
| MOD-01 | Inventario y Activos | `inventory` | Ficha oficial, asignaciones, ubicación, estado, configuración aceptada e historial. |
| MOD-02 | Descubrimiento de Red | `discovery` | Rangos autorizados, trabajos y resultados de descubrimiento. |
| MOD-03 | Collector | `collector` | Agents, vínculo con equipos, tareas, snapshots, mediciones y procesamiento. |
| MOD-04 | Soporte / Tickets | `support` | Tickets, conversación, asignaciones, adjuntos e historial de atención. |
| MOD-05 | IA + RAG | `ai` | Fuentes, fragmentos, vectores, indexación y asistencia autorizada. |
| MOD-06 | Identidad y Acceso | `identity` | Usuarios, identidades técnicas, roles, permisos y sesiones. |
| MOD-07 | Automatización y Notificaciones | `automation` | Reglas, ejecuciones, destinatarios y seguimiento de notificaciones. |
| MOD-08 | Auditoría y Reportes | `audit_reporting` | Auditoría y estructuras de consulta/reportes. |

Collector tiene Agent Python por PC y componente central en Spring Boot. Descubrimiento tiene coordinación central y ejecución solo en equipos designados; Lote 48 concretó scopes, executors, jobs y revisión/incorporación. Lotes 49--52 concretaron Support, Inventory, catálogo read-only de tareas Collector y Audit/Reporting. Lote 53 cerró el modelo de contratos/eventos internos y Lote 54 el readiness v1. Los flujos se detallan en `docs/03-flujos/RESUMEN_FLUJOS.md`.

### 4.1 Refinamiento funcional v1 cerrado

**Support (Lote 49).** Estados `OPEN|IN_PROGRESS|WAITING_FOR_REQUESTER|RESOLVED|CLOSED`; prioridades `LOW|NORMAL|HIGH|URGENT`; tipos `INCIDENT|SERVICE_REQUEST|QUESTION|OTHER`; `requester/createdBy/assignee/source` separados; 0..1 assignee; comentarios `PUBLIC|INTERNAL`; adjuntos `DocumentStorage`; resolución/cierre/reapertura trazables; IA sugiere y Automation no salta reglas; concurrencia optimista.

**Inventory (Lote 50).** Ficha oficial administrativa; `AssetType` administrable; `assetTag` opcional/único, serial no necesariamente único, responsable Identity por contrato y `locationLabel` simple. `ACTIVE|INACTIVE`, sin hard-delete ordinario; un inactivo no conserva Agent activo. Configuración técnica oficial solo por aceptación explícita/versionada de observación Collector. Discovery crea o vincula sin merge automático.

**Collector (Lote 51).** Tareas v1 read-only: `COLLECT_SNAPSHOT`, `COLLECT_NETWORK_STATE`, `COLLECT_SOFTWARE_INVENTORY`; capabilities reportadas y doble allowlist. Estados `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`; bulk con targets explícitos y límites configurables. Solo `IT_OPERATOR`; sin dispatch autónomo IA/n8n.

**Audit/Reporting (Lote 52).** `AuditEvent` append-only, `SUCCESS|DENIED|FAILED`, actor/recurso/correlación y metadata allowlisted. Reportes v1 Assets/Agents/Tickets mediante contratos públicos; CSV efímero. Auditoría 5 años desde `recordedAt`; no sustituye observabilidad ni duplica payloads completos.

## 5. Perfiles técnicos

### Canales/plataformas

- [x] `BACKEND_API`
- [x] `FRONTEND_WEB`
- [ ] `MOVIL`
- [ ] `ESCRITORIO`
- [ ] `CLI`
- [x] `BATCH_WORKERS`
- [ ] `DATOS_ETL`

### Arquitectura

- [ ] `MONOLITO`
- [x] `MONOLITO_MODULAR`
- [ ] `MICROSERVICIOS`
- [ ] `ORIENTADO_EVENTOS`
- [ ] `SERVERLESS`
- [x] `SISTEMA_DISTRIBUIDO`
- [ ] `EMBEBIDO`
- [ ] `OTRO`

### Infraestructura

- [ ] `LOCAL_ONLY`
- [x] `ON_PREMISE`
- [ ] `CLOUD`
- [ ] `HIBRIDO`
- [ ] `MULTICLOUD`

### Operación

- [ ] `SIN_PRODUCCION`
- [x] `PRODUCCION_BAJA_CRITICIDAD`
- [ ] `PRODUCCION_24X7`
- [ ] `REGULADO`
- [ ] `ALTA_CRITICIDAD`

Perfil documental: `ESTANDAR`. Ver `docs/00-gobierno/MATRIZ_ACTIVACION.md`.

## 6. Tecnologías

### Backend

- Lenguaje: **Java 25 LTS**.
- Framework: **Spring Boot 4.1.x**; exact patch fijado en el repositorio cuando se implemente.
- Modularidad: **Spring Modulith 2.1.x** sobre monolito modular de ocho módulos.
- Build: **Maven 3.9.x + Maven Wrapper**.
- Arquitectura: monolito modular; frontera primaria por módulo, sin carpetas globales que diluyan ownership.
- API web: REST sobre HTTPS; **OpenAPI 3.1.2** como contrato canónico y Swagger UI para visualización/prueba.
- Persistencia: **PostgreSQL 18.x + pgvector 0.8.x**; migraciones Flyway usando inicialmente la versión administrada por Spring Boot.
- Integración Agent: SFTP + JSON/JSON Schema; el backend no necesita inicialmente cliente SFTP Java porque Collector procesa el almacenamiento operacional de buzón expuesto por SFTPGo.

### Frontend

- Runtime/build: **Node.js 24 LTS + npm 11.x**.
- Framework: **Next.js 16.3.x + React 19.x compatible + TypeScript 5.9.x**.
- Estado ejecutable verificado: Node.js 24.21.0, npm 11.19.0, Next.js 16.4.2, React/React DOM 19.3.0, TypeScript 5.9.3 y ESLint 9.39.5 temporalmente EOL.
- Estructura: `src/app/` para routing/layout y `src/features/` para capacidades de usuario/negocio; no copiar mecánicamente los ocho módulos backend.
- Actualizaciones selectivas servidor->navegador mediante SSE sobre HTTPS; navegador->backend permanece REST/HTTPS.
- `NEXT_PUBLIC_*` solo para información que puede ser pública en navegador; casi ningún secreto debe existir en frontend.
- Frontend Lote 47: App Router híbrido ligero; Server Components por defecto para composición no interactiva, Client Components para interacción; TanStack Query para server-state; REST directo a Spring Boot como patrón normal; SEO no requerido/no-indexable; soporte de las dos versiones estables mayores más recientes de Chrome/Edge/Firefox/Safari; IE fuera.

### Python / Agent y Discovery executor

- Runtime: **Python 3.14.x**.
- Gestión de proyecto/dependencias: **uv**, `pyproject.toml` y `uv.lock`.
- Estado ejecutable verificado: Python 3.14.8, uv 0.12.24, Hatchling 1.32.4, Ruff 0.16.10, pytest 9.1.1 y pip-audit 2.10.1; dependencias runtime vacías y auditoría sin vulnerabilidades conocidas.
- Cliente SFTP Agent: **Paramiko 5.x**.
- Packaging Agent: **PyInstaller 6.22.x `onedir`**, con builds por sistema operativo soportado; el Agent no se fuerza a Docker.
- `agent` y `discovery_executor` son programas distintos dentro de `automation-collector`; no comparten identidad SSH.
- Polling de tareas: **60 s ± 15 s de jitter**, configurable; jitter inicial 0--60 s.
- pytest para pruebas y Ruff para calidad estática.

### Infraestructura y calidad

- Servidor SFTP: **SFTPGo 2.7.x** estable, exact patch fijado/escaneado en implementación; es transporte, no fuente oficial de negocio.
- Docker para backend/frontend e infraestructura reproducible donde aplique; Docker Compose inicialmente; no Kubernetes.
- Imágenes backend/frontend multi-stage y runtime no-root.
- GitHub Actions por repositorio como CI; GHCR para imágenes Docker; una build inmutable se promueve por digest entre entornos.
- JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers; Vitest + React Testing Library; Playwright; pytest.
- OpenTelemetry como estándar de instrumentación; Micrometer + Spring Boot Actuator como base backend.

## 7. Arquitectura

- Backend Spring Boot como monolito modular con ocho módulos: `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation`, `audit_reporting`.
- Frontera primaria por módulo; capas internas (`domain`, `application`, `infrastructure`, `web`) solo cuando aportan valor. Evitar arquitectura global `controller/service/repository/entity/dto` que pierda ownership.
- Frontend y componentes Python independientes.
- Tres repositorios: `frontend`, `backend`, `automation-collector`.
- Documentación general única en `backend/doc/`.
- OpenAPI y JSON Schemas tienen **fuente canónica en backend**. Frontend y `automation-collector` consumen/copias generadas o sincronizadas, nunca mantienen una segunda fuente independiente.
- Commands/queries entre módulos mediante APIs públicas in-process; hechos confirmados mediante eventos internos cuando corresponda.
- Eventos durables internos usan Transactional Outbox PostgreSQL dentro del schema productor, entrega `at-least-once`, `eventId` estable e idempotencia por consumidor.
- No se introduce Kafka/RabbitMQ/NATS/Pulsar ni broker externo solo para comunicación interna v1; SSE no es el bus interno.
- Cada módulo es propietario de sus datos; referencias cruzadas por IDs estables y contratos públicos, evitando FKs cross-module.
- Inventory conserva datos oficiales del activo; Collector conserva observaciones técnicas. Collector no sobrescribe la ficha oficial.
- IA/RAG coordinada por backend y abstraída de proveedor.
- n8n como integración/orquestación externa, no backend paralelo de negocio.
- HLD y ADR activos por perfil documental; SDD/LLD/RFC se usan cuando la complejidad/decisión lo justifique.

### 7.1 Distribución

- Varias unidades desplegables: sí.
- Comunicación remota interna: sí (web/backend, Agents/SFTP, integraciones).
- Agent inicia conexión; no se habilita escaneo de red desde todos los Agents.
- SFTPGo expone buzones aislados; Collector/backend mantiene el estado oficial.
- Fallos parciales: sí; se aplican políticas de resiliencia.
- Consistencia eventual: existe en flujos asíncronos de Agent/tareas.
- No se promete entrega exactamente una vez; se favorece identificación estable, idempotencia, deduplicación y reconciliación.

### 7.2 Contratos

- Operación solicitada y notificación de hecho/evento se distinguen conceptualmente.
- Contratos internos pertenecen a J-AXON, no a APIs de proveedores.
- Versionado contractual: `MAJOR.MINOR`; MINOR compatible, MAJOR breaking.
- Web: **OpenAPI 3.1.2 / REST / HTTPS** con MAJOR en URL (`/api/v1/...`).
- Agent: **JSON Schema Draft 2020-12 / SFTP**.
- Fuente canónica de ambos contratos: backend, bajo `backend/doc/contracts/` o estructura equivalente aprobada.
- Estado ejecutable inicial: `doc/contracts/openapi/1.0/openapi.yaml` y `doc/contracts/agent/1.0/`; Agent se copia byte a byte a `automation-collector/contracts/agent/1.0/` mediante manifest determinista y script explícito.
- JSON usa `camelCase`; enums `UPPER_SNAKE_CASE`; IDs externos son strings opacos; timestamps RFC3339 UTC.
- Envelope común Agent: `contractVersion`, `messageType`, `messageId`, `agentId`, `createdAt`.
- Tareas: `taskId` estable, `taskType`, `expiresAt`, `payload`; catálogo v1 `COLLECT_SNAPSHOT|COLLECT_NETWORK_STATE|COLLECT_SOFTWARE_INVENTORY`; Agent reporta capabilities; nunca shell/comando arbitrario.
- Lifecycle backend: `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`; respuesta Agent correlacionada por `taskId` con `SUCCEEDED|FAILED|REJECTED`; errores estructurados mediante `error.code` estable.
- Snapshots: envelope común + `snapshotId`, `capturedAt`, `payload`; el contenido técnico evoluciona por contrato.
- Validación de frontera obligatoria antes de mutar estado oficial: archivo/ruta -> JSON -> versión -> schema -> identidad -> deduplicación -> negocio -> persistencia.
- Se distinguen éxito de transporte, recepción/validación y resultado de negocio.
- REST usa RFC 9457 `application/problem+json` con `code`, `correlationId` y `errors` cuando aplique.
- Convención HTTP base: 400/401/403/404/409/422/429/500/503.
- SSE transporta avisos pequeños; REST continúa siendo fuente de estado para navegador.

## 8. Rendimiento y capacidad

- Referencia inicial: ~350 Agents ACTIVE + ~30 usuarios web concurrentes; objetivo de crecimiento ~1000 Agents + ~30 usuarios sin rediseño arquitectónico, sujeto a pruebas.
- Headroom de prueba: 1000 Agents + 60 usuarios.
- RAG: miles de documentos y crecimiento.
- No certificar soporte para 1000 antes de pruebas representativas.
- Poll de tareas Agent: 60 s ±15 s jitter; startup jitter 0--60 s.
- Snapshot periódico: 6 h, distribuido en ventana estable de 60 min.
- Una tarea `RUNNING` por Agent; batch máximo 10/ciclo y ejecución secuencial. Lote 51 añade queue no terminal default 10, máximo una no terminal por `taskType`, bulk máximo 100 Agent IDs explícitos, task JSON 64 KiB, result JSON 5 MiB y timeout de ejecución 5 min como defaults configurables.
- Targets iniciales bajo carga representativa: API normal p95 <=500 ms/p99 <=1 s; login p95 <=1 s/p99 <=2 s; operación síncrona pesada p95 <=2 s/p99 <=4 s; SSE post-commit p95 <=2 s/p99 <=5 s; retrieval vectorial p95 <=500 ms; 5xx atribuibles a J-AXON <1 %.
- Headroom orientativo: CPU sostenida <70 %, RAM <80 %, sin saturación sostenida de pools/I/O.
- Perfil de benchmark inicial: 8 vCPU, 32 GiB RAM, SSD/NVMe, 1 Gbps. No es hardware mínimo definitivo de Production.
- Herramientas: k6 para HTTP/REST y simulador Python Agent/SFTP con identidad/jitter/contratos reales.
- Escenarios mínimos: baseline; load 350+30/60 min; capacity 1000+30/60 min; headroom 1000+60; spike; soak 8 h.
- Estrategia: medir -> localizar cuello -> optimizar -> volver a medir -> escalar verticalmente antes de complejidad distribuida.
- No introducir Redis/cache, réplicas, particionamiento, broker o microservicios preventivamente.

## 9. Datos

- **PostgreSQL 18.x** por entorno; **pgvector 0.8.x** estable.
- Ocho schemas: `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation`, `audit_reporting`; sin tablas de negocio en `public`.
- FKs normalmente dentro del módulo; referencias cross-module por IDs estables + contratos públicos.
- Flyway: un historial backend, scripts por propietario/módulo, naming `VYYYYMMDDHHMMSS__module_description.sql`; migraciones aplicadas no se reescriben.
- Local/Development/Test/QA pueden migrar automáticamente; Production ejecuta migraciones como paso explícito y trazable.
- Credencial DDL/migración separada conceptualmente de runtime de mínimo privilegio.
- PostgreSQL real con Testcontainers; no H2 como sustituto de compatibilidad.
- IDs técnicos canónicos v1 = **UUIDv4**; PostgreSQL `uuid` nativo cuando aplique y representación externa como string opaco.
- pgvector usa **HNSW** como índice vectorial inicial; parámetros finos se ajustan con evaluación.
- Binarios fuera de PostgreSQL mediante `DocumentStorage`; implementación v1 = filesystem persistente. Medio físico final (disco/volumen/NAS) y futuro object storage/cloud siguen abiertos.
- Buzón SFTP operacional separado del storage documental permanente.
- SHA-256 como hash canónico de integridad de archivos relevantes.
- Retenciones iniciales: Collector snapshots/observaciones/resultados de tareas v1 180 días; Discovery no incorporado 30 días; Support tickets+adjuntos 3 años desde el último cierre válido; auditoría 5 años desde `recordedAt`; fuente RAG mientras esté activa/autorizada y chunks/embeddings heredan ciclo; telemetría según Lote 45; outbox entregado 7 días como default y `PENDING/FAILED` sin autoborrado.
- Production backups: PostgreSQL base backup + WAL/PITR; documentos recuperables con intervalo <=1 h; al menos una copia fuera del dominio de fallo primario; todos los backups cifrados; retención 14 diarios/8 semanales/12 mensuales.
- Restore mensual (DB+muestra documental) y prueba integral trimestral; al menos un restore integral exitoso antes de readiness comercial.
- Objetivos iniciales: **RPO <=1 h**, **RTO <=8 h** para core; se consideran cumplidos solo mediante pruebas representativas.
- `TEC-OPS-005a`/`TEC-OPS-006a` implementan únicamente dump lógico custom, manifest SHA-256 y restore aislado automatizado. No sustituyen base backup + WAL/PITR ni cubren cifrado, storage externo, scheduling, retención, documentos o verificación Production de RPO/RTO.
- v1 single-tenant por instalación; sin replicación/particionamiento/CDC/ETL inicial sin evidencia.
- Migraciones destructivas requieren plan de preservación/transformación y recuperación.

## 10. Seguridad

- Autenticación humana separada de Agent.
- Humanos: cuentas locales inicialmente; SSO/federación futuro.
- Sesión web v1: **sesión opaca server-side persistida en PostgreSQL bajo `identity`**, sin JWT access/refresh para humanos.
- Cookie: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, no persistente; usar prefijo `__Host-` cuando la topología lo permita.
- CSRF activo para operaciones mutables; autorización siempre en backend.
- TTL sesión: 30 min de inactividad + 12 h máximo absoluto. Logout, disable, cambio de contraseña o roles invalidan sesiones correspondientes.
- Password hashing: Argon2id, base mínima `19 MiB / t=2 / p=1`, ajustable mediante benchmark.
- Política contraseña v1: mínimo 15 caracteres, permitir al menos 128, sin reglas artificiales de composición, con blocklist local; sin autorregistro.
- Roles humanos v1: `SYSTEM_ADMIN`, `IT_OPERATOR`, `SUPPORT_TECHNICIAN`, `END_USER`, `AUDITOR`; múltiples roles por usuario.
- Modelo de autorización: roles -> permisos -> checks contextuales; default deny. La UI oculta acciones por UX, no por seguridad.
- Bootstrap inicial seguro de un administrador mediante instalación/configuración externa; sin credenciales por defecto. Reset inicial de contraseña es administrativo.
- Protección login: backoff/rate limit base 5 fallos/15 min, configurable; mensajes genéricos para evitar enumeración. MFA no obligatorio en v1.
- Cada Agent con identidad técnica propia y par SSH individual; algoritmo por defecto Ed25519. Private key se genera y permanece en el equipo.
- Verificación previa de host key SFTP; no TOFU/auto-accept.
- Revocación y rotación de clave Agent por identidad; no compartir claves entre Agent y Discovery Executor.
- Agent con mínimo privilegio, aislamiento y validación de destinatario/tarea.
- Secretos fuera de repositorio y separados por entorno/propósito; `.env.example` solo ficticio; `.env` real solo conveniencia Local. QA/Production usan runtime secret files protegidos fuera de Git; secret manager empresarial no es obligatorio v1.
- HTTPS web y SFTP/SSH para Agents/Discovery. Production usa DNS estable y certificado confiable de PKI/CA organizacional; TLS 1.3 preferido, TLS 1.2 permitido, TLS 1.0/1.1 deshabilitados. Reverse proxy/PKI/secret manager concretos por instalación siguen abiertos.
- Cifrado en reposo según clasificación; **todos los backups Production se cifran**. SHA-256 de archivos y HMAC-SHA-256 de callbacks n8n no deben confundirse con cifrado.
- Auditoría de eventos relevantes mediante `AuditEvent` append-only; raw Audit reservado a `AUDITOR`, metadata allowlisted y secretos/payloads completos prohibidos.
- IA con minimización de datos y core funcional sin proveedor IA.
- Threat model ligero con STRIDE para login, Agent/SFTP, buzón, snapshots, archivos, RAG y administración.
- CI/CD de seguridad progresiva: SAST, SCA, secret scan, DAST, SBOM e image scan cuando aplique.

## 11. Entornos

- Local: máquina de desarrollo; datos ficticios/recreables.
- Development: integración de componentes.
- Test: efímero/automatizado con DB desechable.
- QA: validación/demo estable con datos sintéticos/preparados.
- Production: datos/configuración reales, aislado de otros entornos.
- Preproduction física separada no obligatoria inicialmente; `develop` representa código candidato, no un entorno físico.
- Misma build/artefacto promovido con configuración externa por entorno.
- Secretos separados por entorno.
- No copiar datos productivos a QA sin sanitización; preferir sintéticos.
- Dependencias externas y observabilidad identifican/configuran el entorno.

## 12. Pruebas

- TDD para comportamiento ejecutable nuevo cuando aporte valor: Red/Green/Refactor; no trivialidades.
- Pirámide: mayoría unitarias/integración; menos E2E.
- Backend: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers y verificación de fronteras Spring Modulith.
- Frontend: Vitest + React Testing Library; Playwright principal en Chromium y flujos críticos cross-browser en Chromium/Firefox/WebKit antes de releases relevantes.
- Python/Agent: pytest + Ruff.
- Contratos JSON Schema: válidos, inválidos, compatibilidad/versionado y rechazo de MAJOR incompatible.
- OpenAPI: verificación de compatibilidad y divergencia implementación/especificación.
- Integración PostgreSQL 18 real con Testcontainers y Flyway; migraciones desde DB vacía y upgrades relevantes.
- SFTP crítico con servidor SFTPGo real controlado en pruebas, no solo mocks; aislamiento por Agent.
- Suites: `fast`, `integration`, `complete`. PR a `develop/main` bloquea con fast+integration; release ejecuta evidencia completa apropiada.
- E2E con Playwright pertenece al sistema J-AXON aunque el harness pueda residir físicamente en `frontend`.
- Primera vertical E2E aprobada: IT_OPERATOR login -> activo -> enrolamiento Agent -> snapshot SFTP -> Collector -> PostgreSQL -> SSE -> REST -> visualización técnica.
- Cambios cross-repo deben validar combinaciones explícitas de backend/Agent/contratos.
- Seguridad, rendimiento, resiliencia, accesibilidad y RAG tienen pruebas específicas.
- Datos QA sintéticos/reproducibles y escalables para carga; pruebas representativas usan PostgreSQL/SFTPGo/contratos reales y datasets coherentes con 350/1000 Agents.
- Bug importante deja regresión.
- Release no ignora fallos críticos; cobertura 100% no es objetivo.
- Rendimiento: k6 para HTTP/REST + simulador Python Agent/SFTP; baseline/load/capacity/spike/soak y targets del Lote 46.

## 13. DevOps y entrega

- CI obligatorio con GitHub Actions por repo: `backend`, `frontend`, `automation-collector`.
- PR a `develop/main`: suites `fast + integration` como checks obligatorios.
- Push/merge válido a `develop`: construir/publicar artefacto inmutable para Development.
- CD progresivo: Development -> QA -> Production usando **el mismo digest**, sin rebuild; Production requiere aprobación explícita.
- GitHub Actions con permisos mínimos; actions externas fijadas por SHA completo. Dependabot mediante PR; CodeQL/GHAS donde esté disponible sin dependencia obligatoria de licencia.
- Estado supply-chain OLA 0: `develop` es la default branch de los tres repositorios y `main` conserva su rol de producción. Dependency Graph, Dependabot alerts y security updates agrupados están habilitados; `.github/dependabot.yml` bloquea version updates ordinarios. Los jobs existentes ejecutan Gitleaks 8.30.1 sobre historial completo, fijado por SHA-256, bloqueante y con redacción total.
- CI de PR no usa secretos productivos; cancelar CI obsoleto del mismo PR cuando sea seguro.
- Backend CI: compile, unit, verificación modular, PostgreSQL/Flyway, contratos y SFTP cuando aplique.
- Estado backend: `Backend CI` ejecuta `clean verify`, incluidas pruebas OpenAPI 1.0--2.3, JSON Schema/fixtures/manifest, PostgreSQL/Flyway, SFTPGo y modularidad, y conserva Compose, Testcontainers, builds, smoke y scans. El backup/restore lógico incluye Agent, identidad técnica y outboxes. `oasdiff` 1.33.0 se descarga por versión exacta y SHA-256 para bloquear breaking changes entre versiones consecutivas. No hay secretos, publicación o despliegue.
- Los rulesets y protecciones automáticas del repositorio privado no están aplicados por la limitación del plan actual. PR y checks verdes se exigen operativamente, pero GitHub no bloquea técnicamente una fusión incorrecta. `main` y `develop` pueden quedar temporalmente en commits distintos.
- Frontend CI: `npm ci`, lint, typecheck, Vitest/RTL y `next build`.
- Estado frontend: `Frontend CI` ejecuta esos gates y auditorías de producción/completa en PR hacia `develop/main`, push a `develop` y manualmente. Ambas auditorías están limpias tras el refresh puntual autorizado de `undici` y `zod` transitivos de desarrollo; el verificador permanece cerrado ante cualquier hallazgo no reconocido.
- Python CI: `uv sync --locked`, Ruff, pytest, contratos/SFTP y packaging cuando aplique.
- Estado Python: `Python CI` ejecuta sincronización estricta, lock, Ruff, pytest, build, auditoría, verificación local del bundle y ambos entrypoints. `cryptography` 50.0.2 genera la identidad Ed25519 local; `jsonschema` 4.26.0 y `referencing` 0.37.0 validan contratos Agent offline; Paramiko 5.0.0 implementa SFTP con host key fijada. PyInstaller permanece pendiente.
- Dockerfiles backend/frontend multi-stage; runtime no-root.
- Una imagen por componente/version; tags SemVer + Git SHA y promoción por digest inmutable. `latest` no es identidad de deployment.
- Local usa Compose principalmente para infraestructura y permite ejecución nativa de backend/frontend; Development integra imágenes de CI; Test es efímero; QA estable con datos sintéticos; Production consume artefactos publicados/promovidos y no compila código fuente.
- Compose con base común + overrides donde aporte; topología final de Production no queda congelada todavía.
- Agent queda fuera del Compose central; packaging propio PyInstaller por SO. SFTPGo se integra como infraestructura donde corresponda.
- GHCR como registro inicial de imágenes; paquetes Agent por releases separadas.
- Configuración externa; archivos/scripts/migraciones/config no secreta versionados; secretos separados de CI/runtime.
- No se adopta todavía Terraform/OpenTofu: infraestructura física/virtualización/proveedor no están definidos. Compose, configuración no secreta y scripts de deployment/backup/restore sí deben ser versionados/reproducibles. IaC real se reevaluará con infraestructura conocida.
- Rollback/roll-forward plan; backup antes de upgrades con riesgo de datos.
- Zero-downtime no requerido v1; ventanas de mantenimiento controladas permitidas.
- Health/readiness para despliegue; release traceable a commit/tests/artifacts/contracts/migrations/SBOM/version.

## 14. Cloud / infraestructura

- Modelo base **on-premise/LAN**, no público por defecto; cloud/híbrido sigue evolución futura.
- Web con nombre DNS estable y único origen HTTPS detrás de reverse proxy; topología normal evita CORS entre frontend/backend.
- Production: certificado confiable PKI/CA organización; TLS 1.3 preferido y TLS 1.2 permitido; 1.0/1.1 deshabilitados.
- 443 es acceso web normal; 80 solo puede redirigir a HTTPS. PostgreSQL/backend/admin endpoints no se exponen normalmente a usuarios.
- SFTPGo 2.7.x como transporte separado para Agents/Discovery Executors autorizados; no fuente de verdad.
- `DocumentStorage` v1 = filesystem persistente abstraído; medio físico final (disco/volumen/NAS) `POR_DECIDIR`; mailbox SFTP separado.
- Local puede usar `.env`; QA/Production no usan `.env` como secret store permanente. Runtime secrets protegidos fuera de Git, separados por entorno/propósito. Vault/OpenBao no obligatorios.
- Docker+Compose inicialmente; sin Kubernetes/autoscaling; HA no requerida v1.
- Topología flexible: puede iniciar agrupada y separar app/DB/storage con evidencia.
- Perfil de benchmark 8 vCPU/32 GiB/SSD-NVMe/1 Gbps; hardware Production definitivo se decide por medición.
- Al menos un backup fuera del dominio de fallo primario; RPO <=1 h/RTO <=8 h iniciales.
- Reverse proxy concreto, PKI concreta, virtualización, hardware definitivo, secret manager empresarial, IaC y cloud siguen `POR_DECIDIR` donde corresponda.

## 15. Observabilidad

- Logs JSON estructurados, Micrometer, Actuator y OpenTelemetry.
- Production/QA usan inicialmente stack on-premise **Grafana + Prometheus + Loki + Tempo**, sin HA inicial obligatoria y separado de PostgreSQL de negocio.
- Correlation ID y trace ID separados de IDs de negocio (`taskId/jobId/executionId`).
- Retención: Production logs 30 d, métricas 90 d, trazas 7 d; Development 7 d; Test efímero; QA 14 d; Local sin centralización obligatoria. Auditoría separada = 5 años.
- Sampling inicial: operaciones normales 10 %, errores 100 % cuando sea posible/seguro.
- Dashboards: overview, Collector/Agents, backend/DB, integraciones y operaciones.
- Liveness no falla por dependencia degradable; readiness core sí depende de PostgreSQL.
- SLOs iniciales: disponibilidad core >=99,5 % en ventana operativa/30 d; >=99 % de Agents ACTIVE online con snapshot válido <=8 h; >=99 % de snapshots válidos recibidos procesados/persistidos <=5 min.
- Alertas iniciales: readiness core >5 min; disco >85/95 %; backup/RPO; degradación colectiva Agents; snapshot freshness; API 5xx; fallos agregados SFTP. Un offline individual no es alarma crítica.
- Fallo de observabilidad degrada diagnóstico pero no bloquea core. SLA comercial sigue `POR_DECIDIR`.

## 16. Resiliencia

- Distinguir Internet de LAN: sin Internet puede operar J-AXON local; sin LAN/servidor el Agent no intercambia SFTP; IA externa puede fallar sin Internet.
- Errores transitorios vs permanentes; solo los transitorios se reintentan automáticamente.
- Poll de tareas 60 s ± 15 s jitter; startup jitter 0--60 s.
- Snapshot periódico cada 6 h con ventana escalonada de 60 min; `COLLECT_SNAPSHOT` permite actualización bajo demanda.
- Retry por ráfaga: máximo 5 intentos consecutivos, exponential backoff + full jitter, base 5 s, cap 5 min.
- Estado ejecutable acotado: `OLA2-RSL-001a` aplica esa política únicamente a la entrega de un Result terminal durable; el primer intento es inmediato, solo se reintentan fallos explícitamente transitorios y el agotamiento conserva el Result para un episodio posterior tras reinicio manual.
- Timeouts SFTP iniciales: connect 10 s, auth 10 s, operación 60 s; configurables por flujo.
- Agent con spool durable local; límite inicial 7 días o 100 MB. Prioridad: responses > snapshots bajo demanda > periódicos. Bajo presión, suspender/compactar periódicos antes de perder respuestas.
- Ledger local de `taskId` procesados: 30 días; reaparición del mismo `taskId` no reejecuta la operación y puede republicar respuesta previa.
- TTL default de tarea 1 h; `COLLECT_SNAPSHOT` 15 min. Tarea expirada -> `REJECTED/TASK_EXPIRED` sin ejecutar.
- Una tarea activa por Agent; batch máximo 10 por ciclo, secuencial inicialmente.
- Transferencia completa mediante `.part` + rename/move atómico; Collector ignora parciales.
- Validar JSON, JSON Schema, versión, identidad/destino antes del estado oficial.
- Archivos inválidos/corruptos a rechazo/cuarentena con motivo; sin loop infinito.
- Duplicados asumidos; no `exactly once`; `messageId` + `taskId`, deduplicación persistente e idempotencia/reconciliación.
- Agent valida destino/versión/tipo/vigencia/parámetros.
- Backend recupera estado oficial desde DB durable; Agent recupera/reconcilia pendientes locales.
- Reconciliación backend inicial cada 5 min para archivos/mensajes pendientes e inconsistencias recuperables, solo mediante operaciones idempotentes conocidas.
- `lastSeenAt` y `lastSnapshotAt` son distintos; offline inicial >5 min sin contacto. `OFFLINE` != `ERROR`.
- PostgreSQL down: no éxito falso de escrituras; archivos permanecen pendientes hasta reconciliación.
- SSE, IA y n8n pueden degradar sin impedir persistencia del core.
- Sin Kafka/RabbitMQ solo por resiliencia inicialmente.
- Circuit breaker/bulkhead/rate limit adicionales solo con evidencia; thresholds siguen `POR_DECIDIR`.

## 17. Operación

- Cada instalación tendrá un propietario operativo definido por instalación (cliente TI/proveedor/compartido).
- No se promete soporte 24/7 inicialmente; ventana según instalación/contrato.
- Cuatro severidades conceptuales `SEV-1..SEV-4`; tiempos/SLA `POR_DECIDIR`.
- Severidad por impacto de negocio, con escalamiento.
- Runbooks para incidentes repetibles sin secretos.
- Ciclo: detectar -> clasificar -> contener -> diagnosticar -> recuperar -> verificar -> cerrar.
- Comunicación de incidentes controlada; postmortem para incidentes relevantes, con acciones verificables.
- Drills periódicos en operación real; frecuencia `POR_DECIDIR`.
- RTO/RPO deben probarse; restauración requerida antes de readiness comercial.
- Error budget solo cuando existan SLOs reales.
- Catálogo de dependencias críticas, propietarios y recuperación.
- Intervenciones críticas/auditables; ventanas de mantenimiento permitidas.
- Verificación post-deploy y plan de rollback/roll-forward.
- Aislamiento single-tenant incluye logs, backups, incidentes, config y credenciales.

## 18. Integraciones externas

- Integraciones detrás de interfaces/adapters controlados por backend; contratos, credenciales, timeouts, retry/idempotencia y correlación explícitos.
- HTTPS para APIs externas no reemplaza SFTP de Agents/Discovery.

### IA/RAG

- Provider-agnostic mediante abstracciones equivalentes a `EmbeddingProvider` y `GenerationProvider`.
- Fuentes originales en `DocumentStorage`; metadata/chunks/embeddings en schema `ai`.
- Estados de fuente: `PENDING|INDEXING|READY|FAILED|DISABLED`.
- Chunking estructural con defaults ~800 tokens + overlap ~120, configurables/medibles.
- pgvector + HNSW inicial; cada chunk mantiene trazabilidad a fuente.
- Autorización antes/durante retrieval; AI consume otros módulos por contratos públicos, no leyendo sus tablas directamente.
- Respuesta RAG conserva referencias; contexto insuficiente puede responder que no hay evidencia suficiente.
- Documentos son contenido no confiable frente a prompt injection; reglas del sistema prevalecen.
- Reindexación explícita ante cambios de modelo/chunking/parser; desautorizar/eliminar fuente afecta chunks/embeddings.
- Proveedor/modelo LLM/embedding concreto, local vs externo, hardware IA, OCR avanzado, reranker, búsqueda híbrida, fine-tuning y agentes autónomos siguen `POR_DECIDIR`.
- Datos sensibles no se envían a proveedor externo hasta aprobar tratamiento/configuración. Fallo IA no bloquea core.

### n8n / Automation

- Automation conserva reglas y estado oficial; n8n solo orquesta integraciones cuando aporta valor.
- Ejecuciones durables con `executionId` y estados `PENDING|DISPATCHED|SUCCEEDED|FAILED|EXPIRED`.
- Modelo asíncrono: J-AXON inicia, n8n devuelve aceptación y reporta resultado por callback.
- J-AXON->n8n: credencial exclusiva; connect 5 s/request 15 s; máximo 3 retries transitorios con exponential backoff+jitter.
- n8n->J-AXON: HMAC-SHA-256 + timestamp, ventana anti-replay 5 min; `eventId`+`executionId` para deduplicación/idempotencia.
- n8n no escribe tablas ni ejecuta operación arbitraria; toda mutación pasa por operación explícita del módulo propietario.
- Automatizaciones internas simples no requieren n8n. Workflows relevantes exportables/versionables; QA separado de Production; fallo n8n no bloquea core.

## 19. Git y colaboración

- Organización oficial nueva verificada: `J-AXON-PLATFORM`. Repositorios privados verificados: `BACKEND` (`https://github.com/J-AXON-PLATFORM/BACKEND`), `FRONTEND` (`https://github.com/J-AXON-PLATFORM/FRONTEND`) y `AUTOMATION-COLLECTOR` (`https://github.com/J-AXON-PLATFORM/AUTOMATION-COLLECTOR`).
- Los nombres lógicos canónicos permanecen `backend`, `frontend` y `automation-collector`; GitHub no distingue efectivamente mayúsculas y minúsculas en estos identificadores. La separación respecto de la organización histórica `j-axon` se debe al identificador distinto `J-AXON-PLATFORM`; `j-axon` y sus repositorios preexistentes no fueron modificados y no son los repositorios oficiales de este nuevo inicio.
- Los tres repositorios tienen Git local y remoto SSH `origin`. En `frontend`, `main` apunta a `ffec9bdd39d5168ed5f7d3bbaa68d5d4bc5860ab` y `develop` al squash de CI `0a34d6dadacc95351544996235faf6bcb0b21b65`. En `automation-collector`, `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12` y `develop` al squash de CI `a040c5fba34fae22903088b82191e8355deaab26`. Las ramas siguen sus remotos.
- Las autorizaciones específicas consumidas se limitaron a las fundaciones, publicaciones, CI y sincronizaciones documentales ejecutadas. No autorizan operar backlog remoto, crear otros Pull Requests o workflows, configurar rulesets, crear tags/releases/Packages/artefactos ni desplegar.
- Ramas permanentes: `main` (producción) y `develop` (integración/candidato a producción, no entorno físico).
- Rama predeterminada remota: `develop` en los tres repositorios; esta selección alinea Dependency Graph, Dependabot y PR normales con la integración vigente sin promover cambios a `main`.
- Ramas cortas desde `develop`: `feature/*`, `fix/*`, `refactor/*`, `test/*`, `docs/*`, `chore/*`, `ci/*`.
- Hotfix: nace de `main`, PR a `main`, luego sincronizar a `develop`.
- Ramas cortas se eliminan tras merge; `main`/`develop` permanentes.
- No commits funcionales directos a `main`; proteger `main` y coherentemente `develop` cuando permisos lo permitan; no force-push/deletion.
- Solo: PR + CI + self-review. Equipo: al menos una aprobación independiente; más según riesgo.
- PRs pequeños/enfocados con plantilla: qué/por qué/tests/riesgos/contratos/migraciones/evidencia.
- Conventional Commits: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`.
- Commits lógicos, no artificialmente microscópicos.
- Software: SemVer `MAJOR.MINOR.PATCH`; contratos: `MAJOR.MINOR` independiente.
- Cada repo/componente puede versionarse independientemente; release de producto registra combinación y compatibilidad backend-Agent/contracts.
- Tags inmutables (`vX.Y.Z`); changelog y releases reproducibles desde CI/tag.
- CODEOWNERS recomendado al crecer el equipo; no obligatorio solo.
- Cambios cross-repo enlazan dependencias/PRs; contratos canónicos permanecen en backend; pruebas conjuntas verifican combinaciones.

## 20. Metodología y backlog

- Método: Scrum.
- Sprint inicial: 2 semanas.
- Objetivo de sprint explícito.
- Visualización: Kanban en GitHub Projects.
- Jerarquía: Épica -> Funcionalidad -> Historia de Usuario -> Tarea.
- Tipos adicionales: Bug/Error, Spike, Deuda Técnica.
- Estimación: Story Points Fibonacci `1,2,3,5,8,13`; relativos, no horas. Historia 13 normalmente se divide o requiere Spike.
- Tareas internas no requieren estimación separada obligatoria.
- Spikes timeboxed con hallazgos/opciones/recomendación/pendientes.
- Definition of Ready: problema/usuario/valor, criterios aceptación, dependencias, riesgos, contratos/datos, pruebas esperadas y sin arquitectura crítica sin resolver.
- Definition of Done: criterios, código, tests, CI, revisión, seguridad aplicable, contratos, migraciones, docs y sin errores críticos conocidos aplicables.
- HU Done al integrarse y validarse en `develop`; no significa producción. Release tiene readiness adicional.
- Flujo conceptual: Backlog -> Ready -> In Progress -> In Review -> QA/Validation -> Done; Blocked como estado/campo.
- WIP bajo sin número universal todavía; backlog continuamente priorizado.
- Trazabilidad Epic -> Feature -> HU/Bug -> PR -> commits -> tests -> release.
- Decisiones arquitectónicas importantes se registran explícitamente/ADR-RFC cuando aplique.
- IA puede generar backlog: sí; publicación automática: no. Modo inicial `SOLO_PROPUESTA`.
- Automatización delegada no inicialmente; acciones remotas requieren autorización explícita.
- Eventos Scrum: Planning, daily ligera, Review y Retrospective.
- Backlog inicial: 11 épicas y 85 elementos iniciales (58 historias funcionales + 27 elementos técnicos). El conteo corrige la aritmética de la conversación sin cambiar elementos. Ver `docs/12-agil/GESTION_BACKLOG.md`.

## 20.1 Readiness v1 -- Lote 54

- Estados conceptuales separados: `DESIGN_READY`, `IMPLEMENTATION_READY`, `PRODUCTION_READY`; alcanzar uno no implica el siguiente.
- CKP-JAXON-005 dejó J-AXON en `DESIGN_READY`; la implementación comenzó después mediante incrementos expresamente autorizados y verificados, sin autorización general para el resto.
- Baseline v1 controlada: nuevas capacidades materiales requieren nueva decisión; no usar “ya que estamos implementando” para ampliar scope.
- No quedan bloqueos arquitectónicos críticos conocidos antes de implementación tras esta consolidación.
- Antes de Production deben resolverse por instalación: hardware, medio físico `DocumentStorage`, reverse proxy, PKI/certificados, secret storage, DNS, recursos, storage de backups, owner operativo y ventana real.
- `EVIDENCE_DRIVEN`: tuning DB/JVM/pools, thresholds de circuit breaker/bulkhead/rate limiting, particionamiento/cache/replicas y capacidad certificada. Medir antes de complejizar.
- Provider/model IA y SLA comercial permanecen abiertos sin bloquear el core.
- Riesgos continúan `ABIERTO` hasta evidencia de implementación/prueba; capacidad ~1000 Agents solo puede declararse verificada tras benchmark representativo.
- DoR/DoD y olas 0--7 permanecen vigentes. La primera vertical E2E ya fue alcanzada por incrementos acotados; las capacidades restantes requieren su propia delimitación y autorización.
- Aprobar diseño/documentación no autoriza repositorios, ramas, Issues, Projects, workflows, código ni despliegues. La autorización debe ser expresa y con alcance.

## 21. Reglas para IA

- Proveedor-agnóstico: ChatGPT, Claude, OpenCode u otra IA pueden seguir el mismo proceso.
- Leer fuentes reales; analizar antes de ejecutar; distinguir `APROBADO`, `PROPUESTO`, `POR_DECIDIR`, `HISTORICO`, `IMPLEMENTADO`, `VERIFICADO`.
- Decisiones significativas requieren aprobación; decisión aprobada != autorización de implementación.
- Autorización tiene alcance; no ampliar silenciosamente ni hacer refactors oportunistas.
- Dependencias nuevas relevantes requieren justificación/aprobación.
- No inventar contratos para completar código.
- Cambios multi-repo evalúan compatibilidad.
- Implementación generada por IA debe verificarse; no declarar `debería funcionar` como evidencia.
- Tests generados por IA no valen por autoridad: deben ejecutarse y cubrir comportamiento/negativos cuando aplique.
- No deshabilitar/debilitar tests para obtener verde sin justificación.
- No exponer secretos; minimización de datos también durante desarrollo asistido.
- Decisiones aprobadas deben quedar trazables; checkpoints permiten continuidad entre IAs.
- Jerarquía ante conflicto: instrucción explícita nueva > acuerdo aprobado vigente > documentación actual > evidencia del código nuevo > legado histórico > propuestas/ejemplos.
- IA puede preparar cambios; publicar depende del modo autorizado. Inicialmente `SOLO_PROPUESTA`.
- Acciones destructivas requieren autorización especialmente clara.
- Tras implementación futura: resumir cambios, componentes/archivos, tests, resultados, pendientes y decisiones nuevas.

## 22. Protocolo para decisiones abiertas

Cuando exista `POR_DECIDIR` o más de un camino razonable:

1. Definir exactamente el problema.
2. Leer solo contexto relevante.
3. Identificar requisitos/restricciones.
4. Respetar acuerdos anteriores.
5. Generar alternativas reales e incluir no agregar complejidad cuando sea viable.
6. Comparar simplicidad, coste, rendimiento, seguridad, operación, mantenibilidad, portabilidad, reversibilidad y compatibilidad según corresponda.
7. Dar recomendación concreta, aún no aprobación.
8. Declarar supuestos.
9. Indicar qué podría cambiar la recomendación.
10. Agrupar decisiones relacionadas en lotes manejables; no mezclar dominios arbitrariamente.
11. Permitir aprobación total o parcial del lote.
12. Diseño aprobado != implementación autorizada.
13. Registrar acuerdo/ADR/RFC cuando la importancia lo justifique.
14. Si cambia una decisión, conservar historia, motivo y sustitución.
15. Señalar contradicciones en vez de resolverlas silenciosamente.
16. Permitir que una decisión permanezca abierta hasta disponer de evidencia.

## 23. Salida de descubrimiento

> Descubrimiento inicial cerrado documentalmente en CKP-JAXON-002. **CKP-JAXON-005 consolida el refinamiento/diseño v1 aprobado hasta el Lote 54 y deja el proyecto en `DESIGN_READY` documental.** Esto no certifica implementación ni Production readiness.

- [x] Objetivo y alcance claros.
- [x] Perfil documental seleccionado.
- [x] Perfiles técnicos seleccionados.
- [x] Módulos/dominios iniciales.
- [x] Flujos principales.
- [x] Requisitos funcionales.
- [x] NFR críticos.
- [x] Riesgos.
- [x] Arquitectura decidida o pendientes explícitos.
- [x] Contratos/fronteras identificados.
- [x] Estrategia de datos.
- [x] Seguridad inicial.
- [x] Rendimiento/capacidad inicial.
- [x] Pruebas.
- [x] Entrega.
- [x] Infraestructura/cloud cuando aplica.
- [x] Observabilidad/operación cuando aplica.
- [x] Backlog inicial.
- [x] `MATRIZ_ACTIVACION.md` revisada.
- [x] CKP-JAXON-005: `python tools/validate_docs.py` = 0 errores/0 advertencias; modo `--strict` = 0 errores/1 advertencia esperada por 14 referencias a `POR_DECIDIR` (incluye marcadores/reglas y pendientes legítimos clasificados).

### Siguiente fase

Baseline `DESIGN_READY`, con OLA 5 verificada mediante `BACKEND#54..59` y `FRONTEND#29..34`, `OLA6-AUD-001a` mediante `BACKEND#60`/`FRONTEND#35`, `OLA6-AUD-001b` mediante `BACKEND#61`/`#62` y `OLA6-AUD-001c` mediante `BACKEND#63`, squash `f3fed0a567eae8bbddd3b2495eb1b750c5e40bbb`, runs verdes `45756261224`/`45756542597`. `AC-JAXON-096` autoriza `OLA6-AUD-001d` solo para login fallido y registros de claves públicas técnicas 2.0 en backend. `PRODUCTION_READY = NO`.
