# Registro de acuerdos de J-AXON

> **Clasificación:** OBLIGATORIO para continuidad.  
> **Estado:** acuerdos aprobados por el usuario; documentación, no ejecución.  
> **Corte:** CKP-JAXON-005 | 2026-09-29.  
> **Framework:** doc 1.0.0.

Los IDs agrupan decisiones aprobadas para trazabilidad. No implican que exista implementación, prueba o publicación remota. Una propuesta, ejemplo, texto del legado o archivo de plantilla no se convierte en acuerdo por existir.

## AC-JAXON-001 -- Propósito y tecnologías base

Rehacer J-AXON conservando la idea y familias base: Next.js/React/TypeScript, Java/Spring Boot, Python, PostgreSQL+pgvector y Flyway. Versiones concretas no están congeladas. El legado es evidencia histórica, no autoridad.

## AC-JAXON-002 -- Ocho módulos

MOD-01 Inventario, MOD-02 Discovery, MOD-03 Collector, MOD-04 Soporte, MOD-05 IA+RAG, MOD-06 Identidad, MOD-07 Automatización, MOD-08 Auditoría/Reportes.

## AC-JAXON-003 -- Ownership y límites

Cada módulo controla sus datos/reglas; sin escritura directa en tablas ajenas. Inventario es ficha oficial; Collector observa/mide; descubrir/incorporar/instalar son operaciones separadas; Soporte valida tickets; IA asiste sin acciones autónomas; Automatización solicita; Auditoría/Reportes no modifica negocio ajeno.

## AC-JAXON-004 -- Arquitectura

Backend central Spring Boot como monolito modular; frontend y componentes Python independientes. Collector central en backend; Agent Python por PC. Discovery coordina en backend y ejecuta solo en equipos designados. No microservicios iniciales. n8n es herramienta externa, no backend de negocio.

## AC-JAXON-005 -- Repositorios/documentación

MULTIREPO: `frontend`, `backend`, `automation-collector`. Documentación general única en `backend/doc/`; README locales pueden referenciarla. No se publicaron cambios remotos en este checkpoint.

## AC-JAXON-006 -- Datos

PostgreSQL por entorno con schemas `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation`, `audit_reporting`; pgvector; Flyway; archivos fuera de tablas. Base nueva, no importación/borrado automático del legado.

## AC-JAXON-007 -- Canales

Escenario base LAN. Web por HTTPS. Agents/snapshots/buzón por SFTP. Se descarta como base vigente la propuesta HTTP/HTTPS general para Agent. Internet caído no equivale a LAN caída.

## AC-JAXON-008 -- Buzón SFTP

Agent inicia conexión/polling, recoge tareas y entrega snapshots/respuestas; backend mantiene estado oficial. Transferencia != validación != resultado de negocio.

## AC-JAXON-009 -- Contratos de Agent

JSON + JSON Schema versionados; definiciones oficiales en backend. Versionado contractual `MAJOR.MINOR`, MINOR compatible y MAJOR breaking. Identidad estable de tareas/jobs y correlación de respuestas; nombres de campos exactos pendientes.

## AC-JAXON-010 -- Web API

REST/HTTPS + OpenAPI como contrato + Swagger UI para visualización/prueba. Definición oficial versionada en backend. SSE selectivo para avisos servidor->navegador; browser->backend sigue REST/HTTPS.

## AC-JAXON-011 -- Forma de trabajo documental

Analizar/proponer/aprobar por lotes; documentación español, código/identificadores inglés; conservar todos los archivos; no rellenar desconocidos; diseño aprobado != permiso de implementación.

## AC-JAXON-012 -- Contratos internos

Operación solicitada y notificación de hecho/evento se distinguen. En monolito modular: interfaces/contratos in-process para operaciones y eventos de aplicación internos para hechos cuando corresponda. No Kafka/RabbitMQ solo por comunicación interna inicial. Contratos propiedad de J-AXON, no del proveedor.

## AC-JAXON-013 -- Rendimiento/capacidad

Referencia: ~350 equipos, crecimiento alrededor de ~1000 sin rediseño completo sujeto a pruebas; ~30 usuarios web concurrentes típicos; miles de documentos RAG. Snapshots escalonados, batch por tareas controladas, medir->optimizar->remeasure->escalar; no cache/Redis preventivo.

## AC-JAXON-014 -- Estrategia de datos ampliada

FKs dentro de módulo; refs cross-module por IDs+contratos. IDs técnicos estables. Índices por uso/medición. Retención por categorías. Backups+restore tests. Binarios en almacenamiento dedicado con metadata DB e integridad verificable. v1 single-tenant por instalación. Sin replicación/partitioning/CDC/ETL inicial sin necesidad. Migraciones destructivas requieren plan de recuperación.

## AC-JAXON-015 -- Seguridad

Autenticación humana separada de Agent; cuentas locales iniciales; Argon2id; cookie segura; RBAC+checks contextuales; keypair SSH único por Agent y host-key verification; mínimo privilegio; secretos externos; rotación/revocación; HTTPS/SFTP; cifrado at-rest según clasificación; auditoría sin secretos; minimización IA; STRIDE ligero; controles web; rate limiting login; SAST/SCA/secret/DAST/SBOM/image scan progresivos.

## AC-JAXON-016 -- Entornos

Local, Development, Test efímero, QA y Production. Preproduction física no obligatoria. Misma build promovida con configuración/secretos separados. QA con datos sintéticos; no copia productiva sin sanitizar.

## AC-JAXON-017 -- Pruebas

TDD donde aporta; pirámide; JUnit5/AssertJ/Mockito/Spring Boot Test/Testcontainers; Vitest+RTL; pytest; Playwright; contract tests OpenAPI/JSON Schema; PostgreSQL real; migraciones; SFTP real controlado; aislamiento Agents; suites fast/integration/complete; seguridad/perf/resiliencia/accesibilidad/RAG; regresión para bugs; no 100% coverage como fin.

## AC-JAXON-018 -- DevOps/entrega

CI GitHub Actions; CD progresivo; Docker y Compose inicialmente; Agent con packaging propio; build once/promote; GHCR; config externa; IaC pendiente; rollback/roll-forward; mantenimiento permitido; health; release traceable; instalación/upgrade parte del producto; Agent versionado/compatible.

## AC-JAXON-019 -- Infraestructura

On-premise/LAN, no exposición pública por defecto, mínima superficie, topología flexible, hardware por medición, sin Kubernetes/autoscaling inicial, HA no v1, storage abstracto, buzón SFTP separado de storage permanente, backup en dominio de fallo independiente, DNS estable, HTTPS LAN, evitar lock-in.

## AC-JAXON-020 -- Observabilidad

Logs estructurados, correlation IDs, métricas J-AXON, Micrometer+Actuator, OpenTelemetry, health liveness/readiness, estado Agent, alertas accionables, dashboards por perspectiva, frontend básico, cardinalidad controlada, UTC, retención diferenciada, SLIs y futuros SLOs.

## AC-JAXON-021 -- Resiliencia

Retry limitado/backoff+jitter; persistencia local acotada; disk-full explícito; transferencias atómicas; validación completa; rechazo/cuarentena; duplicados asumidos; no exactly-once; ID estable/idempotencia/reconciliación; workflow de tareas; expiración posible; recuperación backend/Agent; no retry ciego de acciones peligrosas; timeouts/circuit/bulkhead/rate selective; degradación IA/n8n/SSE; PostgreSQL crítico; sin broker solo por resiliencia.

## AC-JAXON-022 -- Operación

Owner operativo por instalación; no 24/7 inicial; SEV-1..4 conceptual; escalamiento; runbooks; ciclo de incidente; comunicación; postmortems; drills; RTO/RPO probados; restore tests; error budget solo con SLO; dependencias críticas; intervenciones auditables; maintenance windows; post-deploy verification; aislamiento por cliente.

## AC-JAXON-023 -- Integraciones externas

Adapters backend, HTTPS para APIs externas sin reemplazar SFTP, creds separadas, timeout/retry/idempotencia/correlación. IA provider/model interchangeable; autorización antes de retrieval; prompts versionados; output no oficial; graceful degradation. n8n orquesta, backend conserva estado y reglas, sin direct DB writes normales, workflows versionables, QA separado.

## AC-JAXON-024 -- Git y releases

`main` producción, `develop` integración/candidato; ramas cortas desde develop; hotfix desde main y sync a develop; PR/CI, self-review solo / aprobación independiente en equipo; Conventional Commits; SemVer software y MAJOR.MINOR contratos; tags inmutables; changelog; release manifest/matriz compatibilidad; contratos canónicos backend.

## AC-JAXON-025 -- Metodología/backlog

Scrum, sprints 2 semanas, Kanban/GitHub Projects, jerarquía Epic->Feature->HU->Task, Bugs/Spikes/Tech Debt, Story Points Fibonacci, DoR/DoD, WIP bajo, trazabilidad, Planning/Daily ligera/Review/Retro. IA genera backlog en `SOLO_PROPUESTA`; publicación automática no.

## AC-JAXON-026 -- Reglas IA

Proveedor-agnóstico; leer fuentes reales; estados de decisión explícitos; autorización acotada; no scope creep/refactors oportunistas; justificar deps; no inventar contratos; compatibilidad cross-repo; implementación verificada; tests ejecutados; no ocultar fallos; secretos/datos minimizados; trazabilidad/checkpoints; jerarquía de fuentes; acciones destructivas con autorización clara; resumen de evidencia tras cambios.

## AC-JAXON-027 -- Protocolo de decisiones

Definir problema, leer contexto, restricciones, respetar acuerdos, comparar alternativas incluyendo no agregar complejidad, recomendar, declarar supuestos y condiciones de cambio, agrupar por lotes, permitir aprobación parcial, separar diseño/implementación, registrar ADR/RFC, preservar historia y contradicciones, permitir POR_DECIDIR real.

## AC-JAXON-028 -- Alcance v1 y flujos

Los ocho módulos están en v1 con alcance controlado. Flujos E2E principales: activo+Agent+soporte+IA, Discovery->incorporación y tarea->Agent->resultado->SSE. Lista explícita de fuera de v1. Criterio funcional de éxito en QA limpio.

## AC-JAXON-029 -- Requisitos funcionales

Catálogo inicial aprobado de 90 RF de alto nivel. El conteo 90 corrige una suma errónea en conversación sin cambiar la lista. Ver `docs/02-requisitos/REQUISITOS_FUNCIONALES.md`.

## AC-JAXON-030 -- Riesgos

Registro inicial de 20 riesgos con probabilidad/impacto cualitativos y mitigaciones; permanecen abiertos hasta implementación/verificación.

## AC-JAXON-031 -- Activación documental

Perfil `ESTANDAR`; perfiles `BACKEND_API`, `FRONTEND_WEB`, `BATCH_WORKERS`, `MONOLITO_MODULAR`, `SISTEMA_DISTRIBUIDO`, `ON_PREMISE`, `PRODUCCION_BAJA_CRITICIDAD`. Matriz condicional revisada; no activar complejidad por moda.

## AC-JAXON-032 -- Backlog inicial

11 épicas y 85 elementos iniciales (58 historias funcionales + 27 técnicos). Orden por olas y primera vertical aprobados. El conteo corrige “~70” sin cambiar elementos. No se creó nada remoto.

## AC-JAXON-033 -- Cierre documental

Autorizada consolidación de documentación, actualización de checkpoint, pendientes/matriz y ejecución de `validate_docs.py`, sin implementar código ni modificar GitHub/proyecto legado. CKP-JAXON-002 cierra el descubrimiento inicial documental si no hay errores críticos.


## AC-JAXON-034 -- Stack y versiones base (Lote 30)

Backend: Java 25 LTS, Spring Boot 4.1.x, Maven 3.9.x + Maven Wrapper y Spring Modulith 2.1.x. Frontend: Node.js 24 LTS, npm 11.x, Next.js 16.3.x, React 19.x compatible y TypeScript 5.9.x. `automation-collector`: Python 3.14.x, uv, `pyproject.toml` + `uv.lock`. Series compatibles en documentación; exact patch/locks se fijan en repositorios. SFTP server/library y packaging se resolvieron posteriormente en AC-JAXON-041.

## AC-JAXON-035 -- Estructura de repositorios y fronteras (Lote 31, corregido)

Backend organizado primero por los ocho módulos; capas internas solo cuando aportan valor. Frontend usa `app` para routing/layout y `features` para capacidades de usuario, sin espejo mecánico de módulos backend. `automation-collector` separa `agent` y `discovery_executor`. `.env.example` solo con valores ficticios y secretos reales fuera de Git. Ocho schemas existen desde bootstrap. La fuente canónica de OpenAPI/JSON Schema permanece en backend; cualquier `schemas/` de consumidores es copia/generado. El apartado de entornos del Lote 31 que reducía el modelo a tres entornos queda expresamente sustituido por AC-JAXON-016: Local, Development, Test efímero, QA y Production.

## AC-JAXON-036 -- Configuración por entorno y secretos (Lote 32, corregido)

Configuración en capas: base no sensible -> configuración por entorno -> variables -> secretos inyectados en runtime. Prefijo de variables J-AXON: `JAXON_`. `.env` real solo puede usarse como conveniencia local y se ignora en Git; `.env.example` es versionable. Configuración requerida inválida/faltante provoca fail-fast. `NEXT_PUBLIC_*` solo para datos públicos. Agent y Discovery centralizan configuración por responsabilidad, sin `os.getenv` disperso. No existe perfil local implícito que oculte el entorno. El modelo canónico tiene cinco entornos de AC-JAXON-016 y build-once/promote con configuración/secretos externos separados.

## AC-JAXON-037 -- PostgreSQL y migraciones (Lote 33)

PostgreSQL 18.x y pgvector 0.8.x estable. Flyway usa inicialmente la versión administrada por Spring Boot 4.1.x. Un único historial Flyway para el backend, con scripts organizados por módulo/propietario y bootstrap técnico común. Sin tablas de negocio en `public`. Naming `VYYYYMMDDHHMMSS__module_description.sql`. Migraciones aplicadas no se reescriben; correcciones por nueva migración. Local/Development/Test/QA pueden migrar automáticamente; Production migra como paso explícito/trazable de deployment/upgrade. Separar conceptualmente credenciales DDL de migración y runtime de aplicación.

## AC-JAXON-038 -- Contenedores y promoción entre entornos (Lote 34)

Backend/frontend con Dockerfiles multi-stage y runtime no-root. Una imagen por componente/version, identificada por SemVer + Git SHA y promovida por digest inmutable. Local usa Compose principalmente para infraestructura y permite ejecución nativa; Development integra imágenes CI; Test es efímero; QA es estable con datos sintéticos; Production consume artefactos publicados/promovidos y no compila código. Compose usa base común + overrides donde aporte, sin congelar aún topología final de Production. Agent queda fuera del Compose central y usa packaging propio; DB interna, SFTP solo Agents autorizados, web HTTPS.

## AC-JAXON-039 -- CI/CD concreto (Lote 35)

CI independiente por repositorio. PR a `develop/main` exige `fast + integration`. Merge/push válido a `develop` construye/publica artefacto inmutable para Development; QA y Production reciben el mismo digest sin rebuild. Backend CI cubre compile/unit/modularidad/PostgreSQL/Flyway/contratos/SFTP cuando aplique; frontend `npm ci`/lint/typecheck/Vitest/RTL/Next build; Python `uv --locked`/Ruff/pytest/contratos/SFTP. E2E pertenece al sistema aunque Playwright resida en frontend. Cambios cross-repo validan combinaciones explícitas. GitHub Actions con permisos mínimos y actions fijadas a SHA completo; Dependabot por PR; CodeQL/GHAS cuando esté disponible; sin secretos productivos en PR; cancelación de CI obsoleto del mismo PR cuando sea seguro.

## AC-JAXON-040 -- Identidad humana, sesión y RBAC (Lote 36)

Sesión web opaca server-side persistida en PostgreSQL bajo `identity`; sin JWT access/refresh para humanos v1. Cookie `HttpOnly+Secure+SameSite=Strict`, no persistente, `Path=/`, `__Host-` cuando topología lo permita; CSRF activo. TTL: 30 min inactividad + 12 h absoluto; logout/disable/password/roles revocan sesiones. Roles v1: `SYSTEM_ADMIN`, `IT_OPERATOR`, `SUPPORT_TECHNICIAN`, `END_USER`, `AUDITOR`; múltiples roles por usuario. Roles -> permisos -> checks contextuales; default deny; autoridad backend. Argon2id base 19 MiB/t=2/p=1 ajustable por benchmark; contraseña mínimo 15 caracteres, largas permitidas, sin composición artificial y con blocklist local. Sin autorregistro; bootstrap seguro; reset inicial administrativo; rate limit/backoff base 5 intentos/15 min; estados de usuario `ACTIVE`/`DISABLED`; MFA no obligatorio v1. Identidad Agent permanece separada.

## AC-JAXON-041 -- SFTP, identidad técnica y packaging Agent (Lote 37)

SFTPGo 2.7.x estable como servidor SFTP/infraestructura de transporte, no fuente oficial de negocio. Backend inicialmente sin cliente SFTP Java; Collector trabaja sobre almacenamiento operacional de buzón expuesto por SFTPGo. Agent usa Paramiko 5.x. Un keypair SSH individual por Agent, Ed25519 por defecto; private key generada/almacenada localmente; host key preprovisionada/verificada sin confianza automática. Enrolamiento: pre-registro administrativo + generación local de claves + registro de public key, sin canal HTTP alternativo. Buzón aislado por Agent, `tasks` lectura y `snapshots/responses` escritura controlada. Transferencias `.part` + rename atómico. Revocación por Agent sin borrar historia; rotación controlada de claves. Packaging PyInstaller 6.22.x `onedir`, builds por SO; Agent como servicio de mínimo privilegio; sin self-update autónomo v1. Discovery Executor tiene identidad distinta.

## AC-JAXON-042 -- Contratos ejecutables base (Lote 38)

Web usa OpenAPI 3.1.2; Agent usa JSON Schema Draft 2020-12. Fuente canónica en backend; consumidores no mantienen fuentes paralelas. API `/api/v1`, MAJOR en URL y contratos MAJOR.MINOR independientes de SemVer. JSON `camelCase`, enums `UPPER_SNAKE_CASE`, IDs externos strings opacos, timestamps RFC3339 UTC. Envelope Agent común: `contractVersion`, `messageType`, `messageId`, `agentId`, `createdAt`. Task: `taskId` estable, `taskType`, `expiresAt`, `payload`, con task types explícitos y nunca shell arbitrario. Response correlacionada por `taskId` con `SUCCEEDED|FAILED|REJECTED`. Snapshot con envelope y payload evolucionable. Validación completa antes de persistir. REST errors RFC 9457 + `code/correlationId/errors`; HTTP base 400/401/403/404/409/422/429/500/503. SSE solo avisa; REST conserva la fuente de estado.

## AC-JAXON-043 -- Primera vertical E2E (Lote 39)

Actor `IT_OPERATOR`: login -> crear activo -> pre-registrar/enrolar Agent -> snapshot inicial automático SFTP -> Collector valida/persiste -> SSE avisa -> frontend consulta REST y muestra estado técnico. Activo inicial mínimo `name/assetType/status`, con `ACTIVE|INACTIVE`. Máximo un Agent `ACTIVE` por activo; estados Agent `PENDING|ACTIVE|REVOKED`. Snapshot mínimo: hostname, OS, arquitectura, CPU lógica, RAM y almacenamiento; no ampliar todavía a usuarios/procesos/archivos/software exhaustivo. Separar `capturedAt`/`receivedAt`, conservar historial de snapshots, no copiar observaciones Collector a Inventory. Endpoint técnico `/api/v1/assets/{assetId}/technical-state`; `lastSeenAt` separado de `lastSnapshotAt`; UI distingue oficial vs observado; E2E real cruza browser/backend/PostgreSQL/SFTPGo/Agent/JSON Schema/SSE.

## AC-JAXON-044 -- Resiliencia operacional Agent (Lote 40)

Defaults configurables: poll tareas 60 s ±15 s jitter; startup jitter 0--60 s; snapshot periódico 6 h con ventana estable 60 min; máximo 5 retries consecutivos con exponential backoff + full jitter, base 5 s y cap 5 min; connect/auth/operation timeout 10/10/60 s; spool local 7 días o 100 MB; ledger de taskId 30 días; TTL task default 1 h y `COLLECT_SNAPSHOT` 15 min; 1 tarea activa por Agent; batch máximo 10/ciclo; reconciliación backend cada 5 min; Agent offline >5 min sin contacto. Solo errores transitorios se reintentan; spool durable, responses prioritarias; periódicos pueden compactarse bajo presión; `messageId`/`taskId` para deduplicación/idempotencia; no exactly-once; taskId procesado no se reejecuta; PostgreSQL down nunca produce éxito falso; SSE/IA/n8n degradan sin bloquear core; `OFFLINE` y `ERROR` son distintos.

## AC-JAXON-045 -- Datos, storage, retención y recuperación (Lote 41)

Storage documental v1 mediante filesystem persistente abstraído por una frontera tipo `DocumentStorage`, separado de PostgreSQL y del buzón SFTP. El medio físico concreto (disco/volumen/NAS) queda para infraestructura posterior; cloud/object storage/S3-compatible sigue `POR_DECIDIR`. SHA-256 es el hash canónico de integridad de archivos relevantes. Retenciones iniciales: snapshots Collector 180 días; Discovery no incorporado 30 días; tickets+adjuntos 3 años desde cierre; auditoría relevante 5 años; fuentes RAG mientras sigan activas/autorizadas y chunks/embeddings heredan su ciclo de vida. Production: PostgreSQL con base backup + WAL/PITR; documentos respaldados como máximo cada 1 h; al menos una copia fuera del dominio de fallo primario; backups cifrados; retención 14 diarios/8 semanales/12 mensuales; restore mensual y prueba integral trimestral. Objetivos iniciales: RPO <=1 h y RTO <=8 h para el core, verificables mediante pruebas reales.

## AC-JAXON-046 -- Arquitectura RAG y retrieval autorizado (Lote 42)

IA/RAG es provider-agnostic mediante abstracciones equivalentes a `EmbeddingProvider` y `GenerationProvider`. Fuentes originales usan `DocumentStorage`; metadata/chunks/embeddings pertenecen al schema `ai`. Estados de fuente: `PENDING|INDEXING|READY|FAILED|DISABLED`. Chunking estructural con defaults iniciales aproximados de 800 tokens y overlap 120. PostgreSQL+pgvector continúa como vector store y HNSW es el índice inicial. Cada chunk mantiene trazabilidad a la fuente. La autorización se aplica antes/durante retrieval; AI consume otros módulos por contratos públicos, nunca por lectura directa de sus tablas. Respuestas RAG conservan referencias; contexto insuficiente no se rellena artificialmente. Documentos recuperados son contenido no confiable frente a prompt injection. Reindexación explícita ante cambios de modelo/chunking/parser; borrar/desautorizar fuente afecta también chunks/embeddings. Proveedor/modelo concreto, cloud vs local, hardware IA, OCR avanzado, reranking, búsqueda híbrida, agentes autónomos y fine-tuning siguen `POR_DECIDIR`.

## AC-JAXON-047 -- Automatización y n8n (Lote 43)

MOD-07 Automation conserva reglas y estado oficial; n8n solo orquesta integraciones cuando aporta valor. Hechos internos pueden disparar reglas. Cada ejecución posee `executionId` estable y estado durable `PENDING|DISPATCHED|SUCCEEDED|FAILED|EXPIRED`. Integración asíncrona: J-AXON inicia y n8n reporta por callback. J-AXON->n8n usa credencial exclusiva; callbacks n8n->J-AXON se autentican con HMAC-SHA-256, timestamp y ventana anti-replay inicial de 5 min. `eventId` + `executionId` soportan idempotencia/deduplicación sin prometer exactly-once. n8n no escribe tablas ni ejecuta operaciones arbitrarias; toda acción vuelve a operaciones explícitas del módulo dueño. Defaults de dispatch: connect timeout 5 s, request timeout 15 s, máximo 3 reintentos transitorios con exponential backoff+jitter. Automatizaciones internas simples no requieren n8n; caída de n8n degrada sin bloquear core; workflows relevantes deben poder exportarse/versionarse y QA permanece separado de Production.

## AC-JAXON-048 -- Infraestructura LAN, TLS, secretos e IaC mínimo (Lote 44)

J-AXON v1 sigue on-premise/LAN y no público por defecto. Web con nombre DNS estable y un único origen HTTPS detrás de reverse proxy; frontend y rutas backend/SSE se enrutan internamente, evitando CORS en topología normal y favoreciendo cookies `__Host-`. Production usa certificado confiable de la PKI/CA de la organización; TLS 1.3 preferido y TLS 1.2 permitido por compatibilidad; TLS 1.0/1.1 deshabilitados. Acceso web normal por 443; 80 solo puede redirigir a HTTPS. PostgreSQL/backend/admin endpoints no se exponen normalmente a usuarios; SFTP conserva endpoint separado. Local puede usar `.env`; QA/Production no lo usan como almacén permanente de secretos. v1 usa archivos protegidos/runtime secrets fuera de Git, separados por entorno y propósito; Vault/OpenBao no son obligatorios. No se adopta Terraform/OpenTofu aún; Compose, configuración y scripts de deployment/backup/restore sí son versionados y reproducibles. Reverse proxy concreto, PKI concreta, hardware, secret manager empresarial e IaC de proveedor siguen abiertos.

## AC-JAXON-049 -- Observabilidad, SLI/SLO y alertas (Lote 45)

Observabilidad on-premise desacoplada de PostgreSQL de negocio. Instrumentación: logs JSON estructurados, Micrometer y OpenTelemetry. Production/QA usan inicialmente Grafana, Prometheus, Loki y Tempo sin HA inicial. Retención: Production logs 30 d, métricas 90 d, trazas 7 d; Development 7 d, Test efímero, QA 14 d; Local no exige centralización. Sampling: operaciones normales 10 %, errores 100 %. Dashboards iniciales: overview, Collector/Agents, backend/DB, integraciones y operaciones. SLOs iniciales: disponibilidad core >=99,5 % en ventana operativa sobre 30 d; >=99 % de Agents ACTIVE online con snapshot válido <=8 h; >=99 % de snapshots válidos recibidos procesados/persistidos <=5 min. Alertas iniciales incluyen core/readiness >5 min, disco >85/95 %, riesgo de incumplir RPO, degradación colectiva de Agents, snapshot freshness, error rate y fallos agregados de SFTP; un Agent offline individual no genera alerta crítica. Fallo del stack de observabilidad no bloquea core. SLA y algunos objetivos operativos finales siguen abiertos.

## AC-JAXON-050 -- Rendimiento y capacidad verificable (Lote 46)

Carga inicial de referencia: ~350 Agents + 30 usuarios web concurrentes; objetivo de crecimiento: ~1000 Agents + 30 usuarios sin rediseño arquitectónico, permitiendo escalado vertical. Targets iniciales: API interactiva p95 <=500 ms/p99 <=1 s; login p95 <=1 s/p99 <=2 s; operaciones síncronas pesadas p95 <=2 s/p99 <=4 s; emisión SSE tras persistencia p95 <=2 s/p99 <=5 s; retrieval vectorial interno p95 <=500 ms; 5xx atribuibles a J-AXON <1 % en carga normal. Headroom de referencia: CPU sostenida <70 %, RAM <80 %, sin saturación sostenida de pools/I/O. Nodo de benchmark inicial: 8 vCPU, 32 GiB RAM, SSD/NVMe y red 1 Gbps; no es hardware mínimo definitivo de Production. k6 será la herramienta inicial HTTP y existirá simulador Python específico Agent/SFTP. Pruebas: baseline, load, capacity, spike y soak; load 350+30/60 min, capacity 1000+30/60 min, headroom 1000+60 y soak 8 h. PostgreSQL/SFTPGo/contratos reales en pruebas representativas. No Redis/réplicas/particionamiento/cambios arquitectónicos preventivos; capacidad de 1000 solo se declara verificada tras prueba representativa aprobada.

## AC-JAXON-051 -- Arquitectura frontend y estado (Lote 47)

Next.js App Router con estrategia híbrida ligera: Server Components por defecto para layout/composición sin interactividad y Client Components donde se requiere interacción. Datos autenticados de negocio se consumen normalmente desde navegador por REST directo a Spring Boot; Next.js no es BFF/segundo backend por defecto. TanStack Query es la herramienta inicial de server-state; estado local queda en React, filtros/paginación compartibles en URL y Context solo para estado global cliente pequeño; no Redux/Zustand inicial. SSE invalida/refresca y REST conserva la verdad. GET puede tener 1 retry transitorio inicial; mutations no se reintentan automáticamente por defecto. No persistir credenciales/datos sensibles en browser storage. SEO no es requisito v1 y la app será no-indexable. Soporte oficial: dos versiones estables mayores más recientes de Chrome, Edge, Firefox y Safari; IE fuera. Playwright principal Chromium y flujos críticos cross-browser Chromium/Firefox/WebKit. Web responsive con desktop como experiencia principal y WCAG 2.2 AA como objetivo de diseño, sin afirmar certificación formal.

## AC-JAXON-052 -- Discovery detallado (Lote 48)

Discovery v1 separa `AuthorizedScope`, `DiscoveryExecutor`, `DiscoveryJob` y `DiscoveredDevice`. `SYSTEM_ADMIN` administra scopes/ejecutores; `IT_OPERATOR` lanza trabajos solo sobre scopes y ejecutores autorizados. Estados: scope `ACTIVE|DISABLED`; executor `PENDING|ACTIVE|REVOKED`; job `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|EXPIRED|CANCELLED`; dispositivo `UNREVIEWED|INCORPORATED|IGNORED`. Discovery Executor mantiene identidad distinta del Agent y usa buzón SFTP dedicado por executor sobre SFTPGo, separado de buzones Agent; conexión iniciada por el executor. `jobId` estable, TTL inicial 1 h y máximo un job activo por executor. Perfil `BASIC` no intrusivo: ARP cuando aplique, ICMP, DNS y conjunto pequeño/configurable de probes TCP; default `maxConcurrency=32`, probe timeout aproximado 2 s, configurables. Resultados son observaciones, no identidad oficial; incorporación requiere revisión humana y operación pública de Inventory, nunca instala Agent automáticamente. Executor revalida el alcance autorizado. Resultados no incorporados retienen 30 días. SSE solo notifica cambios y REST conserva la verdad.

## AC-JAXON-053 -- Support/Tickets detallado (Lote 49)

Support es owner de `Ticket`, conversación, adjuntos e historial funcional. Estados v1: `OPEN|IN_PROGRESS|WAITING_FOR_REQUESTER|RESOLVED|CLOSED`; reapertura es transición a `OPEN`, no estado. `RESOLVED` exige `resolutionSummary`; cierre conserva razón `RESOLVED|CANCELLED|DUPLICATE|NOT_APPLICABLE`; reapertura exige motivo y la retención de 3 años se cuenta desde el último cierre válido. Tipos iniciales: `INCIDENT|SERVICE_REQUEST|QUESTION|OTHER`. Prioridades: `LOW|NORMAL|HIGH|URGENT`, con `NORMAL` como default configurable y Support como autoridad de la prioridad oficial; no equivalen a `SEV-1..4` ni implican SLA. Separar `requester`, `createdBy`, `assignee` y `source`; máximo un assignee actual. Comentarios `PUBLIC|INTERNAL`, append-only en flujo normal. `assetId` es opcional en el formulario general y obligatorio cuando el ticket nace desde contexto de activo; se valida por contrato público de Inventory. Adjuntos usan `DocumentStorage`, SHA-256 y autorización por acceso; defaults configurables 25 MiB/archivo y 100 MiB/ticket; ejecutables/scripts se rechazan y el motor antimalware concreto sigue abierto. `END_USER` opera sus tickets; `IT_OPERATOR` participa como requester sin autoridad de Support; `SUPPORT_TECHNICIAN` gestiona la atención; `SYSTEM_ADMIN` no obtiene autoridad funcional de tickets automáticamente; `AUDITOR` es lectura controlada. IA solo sugiere. Automation/n8n puede solicitar creación/notificaciones autorizadas pero no cerrar, reasignar o repriorizar autónomamente. REST conserva verdad, SSE solo avisa. Mutaciones sensibles usan concurrencia optimista y conflicto 409 ante versión obsoleta.

## AC-JAXON-054 -- Inventory/Activos detallado (Lote 50)

Inventory conserva la ficha oficial. `Asset` v1 usa ID técnico estable, `name`, `assetType`, `status`, `assetTag` opcional, `serialNumber`, `manufacturer`, `model`, `responsibleUserId`, `locationLabel`, timestamps y versión. Estados siguen `ACTIVE|INACTIVE`; no hay hard-delete ordinario v1 y el cambio de estado conserva motivo. `assetTag`, si existe, es único normalizado/case-insensitive; serial no impone unicidad global. `AssetType` es catálogo administrable con valores iniciales configurables `DESKTOP|LAPTOP|SERVER|NETWORK_DEVICE|PRINTER|OTHER`, y estados `ACTIVE|DISABLED`. Responsable se referencia a Identity por contrato, no FK cross-module; ubicación v1 es `locationLabel`, sin jerarquía formal todavía. Un Asset `INACTIVE` no puede conservar Agent `ACTIVE`: se bloquea la transición hasta resolverlo mediante Collector; reactivar Asset no reactiva Agent. La promoción de una observación técnica a configuración oficial requiere acción explícita de `IT_OPERATOR`; crea una `AcceptedTechnicalConfiguration` inmutable/versionada con procedencia del snapshot, inicialmente para hostname/OS/arquitectura/CPU lógica/RAM/storage. Discovery puede crear un activo o vincular el dispositivo a uno existente mediante operación idempotente; no fusiona activos automáticamente. Support/Audit/AI solo consultan según autorización. Mutaciones usan concurrencia optimista; SSE solo avisa.

## AC-JAXON-055 -- Collector: catálogo de tareas y capacidades (Lote 51)

Catálogo v1 read-only: `COLLECT_SNAPSHOT`, `COLLECT_NETWORK_STATE`, `COLLECT_SOFTWARE_INVENTORY`. No `RUN_COMMAND`, `RUN_SCRIPT`, reboot/shutdown, gestión de servicios, instalación/desinstalación, modificación de configuración ni acceso arbitrario a archivos. El Agent reporta `agentVersion` y capabilities; backend valida Agent `ACTIVE` + capability + autorización + límites y el Agent valida de nuevo localmente. Estados de tarea: `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`; `TASK_STARTED` confirma inicio cuando llega, pero un resultado final válido puede reconciliar una transición desde `DISPATCHED`. `expiresAt` limita el inicio, no necesariamente la finalización. Defaults configurables: máximo 100 targets explícitos por solicitud masiva, 10 tareas no terminales por Agent, una por `taskType`, una `RUNNING`, task JSON 64 KiB, result JSON 5 MiB y timeout de ejecución 5 min por capability; se mantienen polling/batch/TTL/retries aprobados. Solo `IT_OPERATOR` solicita tareas v1; Automation/n8n e IA no disparan tareas Agent autónomamente. Resultados `REJECTED` significan no ejecución por contrato/capability/vigencia; `FAILED` significa ejecución válida que falló. Recepción del archivo no equivale a éxito: Collector valida/quarantina antes de persistir. Observaciones de estas tareas pertenecen a Collector y retienen 180 días; no se vuelven datos oficiales de Inventory automáticamente. El Agent usa mínimo privilegio y no eleva privilegios automáticamente.

## AC-JAXON-056 -- Auditoría y Reportes detallado (Lote 52)

`AuditEvent` v1 es append-only en flujo normal y contiene como mínimo IDs/tiempos, `eventType`, módulo, `actorType`, `actorId`, `resourceType`, `resourceId`, `result`, `correlationId` y metadata pequeña allowlisted. `actorType`: `HUMAN_USER|SYSTEM|AGENT|DISCOVERY_EXECUTOR|AUTOMATION`; resultado `SUCCESS|DENIED|FAILED`. No guardar secretos, bodies completos de comentarios, adjuntos, prompts/respuestas completos ni payloads arbitrarios. El catálogo auditable cubre cambios relevantes de Identity, Inventory, Discovery, Collector, Support, RAG y Automation; no cada GET/poll/lastSeen/evento técnico. Una operación `AUDIT_REQUIRED` exitosa no responde éxito hasta capturar durablemente la intención de auditoría; denegaciones/fallos auditables se registran sin convertir un fallo de auditoría en autorización. Raw Audit se consulta por `AUDITOR`; `SYSTEM_ADMIN` no recibe `audit:read` automáticamente. Filtros mínimos: fechas, módulo, eventType, actor, recurso, resultado y correlationId; page size default 50, máximo 200. Retención de Audit = 5 años desde `recordedAt`. Reportes v1: Assets, Agents y Tickets; consumen contratos públicos de los módulos, no tablas ajenas. Roles: `AUDITOR` puede ver los tres; `IT_OPERATOR` Assets/Agents; `SUPPORT_TECHNICIAN` Tickets; otros no por defecto. Exportación v1 CSV bajo demanda, no persistida; `REPORT_EXPORTED` es auditable. Sin SSE propio, BI/report builder, PDF/XLSX, warehouse ni tamper-proof criptográfico obligatorios v1.

## AC-JAXON-057 -- Contratos/eventos internos (Lote 53)

Entre módulos, commands/queries usan contratos públicos síncronos in-process; hechos ya confirmados usan eventos internos. No acceso a repositorios/entidades/tablas de otro módulo y se evitan dependencias circulares. Puede existir transacción común selectiva dentro del monolito cuando una operación cross-module requiera atomicidad y no cruce un sistema externo, siempre invocando operaciones del módulo dueño. Los eventos durables se capturan mediante Transactional Outbox en PostgreSQL dentro del schema del productor; publicación después del commit, semántica `at-least-once`, `eventId` estable y consumidores idempotentes. Envelope conceptual: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `producerModule`, `resourceType/resourceId`, `resourceVersion` cuando aplique, `correlationId`, `causationId` opcional, actor cuando aplique y payload pequeño. Versionado MAJOR.MINOR; payload durable default <=64 KiB. Eventos iniciales cubren cambios relevantes de Identity/Inventory/Discovery/Collector/Support/AI/Automation. `AuditFact` allowlisted también via outbox. Automation solo consume eventos permitidos por regla; n8n no consume el bus interno directamente y AI no es consumidor global. SSE no es el bus: es best-effort y REST conserva verdad. Delivery durable se controla por consumidor; defaults de retry: ~1 s, exponential backoff+jitter, cap 5 min, 10 intentos; al agotar, el evento permanece fallido para reproceso controlado. Eventos entregados pueden purgarse del outbox tras 7 días como default; `PENDING/FAILED` no se autoborran. Sin broker, DLQ externa, replay histórico general, orden global, exactly-once, AsyncAPI obligatorio, sagas framework ni event sourcing v1.

## AC-JAXON-058 -- Cierre de refinamiento y readiness v1 (Lote 54)

El refinamiento/diseño detallado v1 queda conceptualmente cerrado y J-AXON pasa a `DESIGN_READY` una vez consolidada/validada esta documentación. Se distinguen `DESIGN_READY`, `IMPLEMENTATION_READY` y `PRODUCTION_READY`; ninguno implica el siguiente. IDs técnicos canónicos v1 = UUIDv4, almacenados como `uuid` nativo PostgreSQL cuando aplique y expuestos como strings opacos. El alcance v1 se congela como baseline controlada: cambios materiales nuevos requieren decisión explícita. Los pendientes se clasifican en bloqueantes antes de implementación, bloqueantes antes de Production o `EVIDENCE_DRIVEN`; tras esta consolidación no se conocen bloqueos arquitectónicos críticos para empezar, pero hardware/medio físico DocumentStorage/reverse proxy/PKI/secret store/DNS/recursos/backup storage/owner operativo deben resolverse antes de Production según instalación, y tuning/thresholds/capacidad certificada dependen de evidencia. Provider/model IA y SLA comercial siguen abiertos sin bloquear el core. Riesgos permanecen abiertos hasta evidencia. DoR/DoD, olas 0--7 y primera vertical temprana se conservan. La capacidad ~1000 Agents no puede declararse verificada hasta benchmark representativo. `doc` permanece 1.0.0. `DESIGN_READY` no autoriza implementación, creación/modificación de GitHub, repositorios, ramas, Issues, Projects, workflows ni despliegues; cualquier inicio de implementación requiere autorización expresa de LUIS.

## AC-JAXON-059 -- Contratos ejecutables mínimos de OLA 0

Backend es la fuente canónica bajo `doc/contracts/`; `automation-collector` consume una copia vendorizada bajo `contracts/`, sincronizada explícitamente y protegida por manifest determinista con SHA-256 por archivo y por bundle, sin registry, Package, submódulo, subtree ni checkout cross-repo en CI. Web inicia con OpenAPI 3.1.2, `info.version: 1.0`, server relativo `/api/v1` y exclusivamente `POST /auth/login`, `GET /auth/me`, `POST /assets`, `GET /assets/{assetId}` y `GET /assets/{assetId}/technical-state`; es contract-first y no implica controladores. Sesión humana opaca server-side por cookie segura; CSRF se entrega en `SessionResponse` y vuelve por `X-CSRF-Token`; errores RFC 9457. Agent 1.0 usa Draft 2020-12 e identificadores locales `https://schemas.j-axon.invalid/agent/1.0/...`; envelope exacto `contractVersion/messageType/messageId/agentId/createdAt`, sin `correlationId` obligatorio. Snapshot usa `snapshotId/capturedAt/payload` y solo hostname, OS, arquitectura, CPU lógica, RAM y almacenamiento acotado. Task conserva tres tipos read-only con payload vacío/cerrado. Result emite solo `SUCCEEDED|FAILED|REJECTED`; `EXPIRED` permanece en lifecycle backend. La validación es offline, estricta, con UUIDv4/RFC3339 UTC, fixtures positivos/negativos, manifest y compatibilidad OpenAPI. Política inicial: versión actual y minor anterior del mismo major cuando exista; sin plazo temporal de deprecación inventado. Este acuerdo no autoriza endpoints, tablas, migraciones, SFTP, frontend, despliegues ni funcionalidad de negocio.

## AC-JAXON-060 -- Decisiones ejecutables del primer incremento Identity de OLA 1

`OLA1-IAM-001a` crea un único usuario bootstrap `ACTIVE` con asignaciones explícitas `SYSTEM_ADMIN` e `IT_OPERATOR`; `SYSTEM_ADMIN` no adquiere por ello permisos funcionales de Inventory. El bootstrap está desactivado por defecto, exige configuración runtime completa, contraseña conforme a política y un store de usuarios vacío, y falla de forma cerrada ante cualquier incumplimiento.

Como excepción mínima a `AC-JAXON-056`, únicamente `HUMAN_LOGIN_FAILED` sin principal verificable admite `actorType=HUMAN_USER` y `actorId=null`; usa `resourceType=AUTHENTICATION_ATTEMPT`, `resourceId` UUIDv4 propio, resultado `DENIED`, correlación y metadata allowlisted sin username, IP cruda, credenciales o secretos. Si la identidad existe, el evento conserva su `actorId`. Esta excepción no modifica el resto del contrato de auditoría.

La protección inicial permite cinco fallos durante una ventana móvil de 15 minutos; el sexto y posteriores devuelven `429` hasta salir de la ventana. Un login correcto elimina el contador aplicable. La clave combina de forma hash el username normalizado y la dirección remota observada directamente por la aplicación; no confía en headers de forwarding mientras no exista un proxy confiable aprobado.

## AC-JAXON-061 -- Decisiones ejecutables del primer incremento Agent de OLA 2

`OLA2-AGT-001a` autoriza pre-registro humano de Agent, identidad técnica separada y generación local Ed25519 sin conectividad. `collector` conserva Agent/vínculo/estado `PENDING`; `identity` conserva identidad técnica, public key normalizada y huella OpenSSH SHA-256; `inventory` valida existencia mediante contrato público in-process. No existen FKs ni escrituras directas cross-module. La transacción local común invoca exclusivamente operaciones públicas de los módulos dueños.

OpenAPI 3.1.2 versión 1.1 conserva todas las operaciones 1.0 y añade solo `POST /agents`, `GET /agents/{agentId}` y `PUT /agents/{agentId}/ssh-key` para sesión humana `IT_OPERATOR` con CSRF en mutaciones. La misma clave es idempotente; sustitución, rotación o clave asignada a otro Agent producen conflicto. El comentario OpenSSH no integra la huella. OpenAPI 1.0 y JSON Schema Agent 1.0 permanecen inmutables.

El inicializador `jaxon-agent initialize` valida UUIDv4, genera y conserva localmente la private key, muestra únicamente clave pública/huella/ruta pública, no sobrescribe estado y falla cerrado ante estado parcial o incompatible. Registrar la public key no activa el Agent: `ACTIVE` requiere provisionamiento SFTPGo futuro. Este acuerdo no implementa SFTPGo, buzón, SFTP, host-key verification, snapshots, tareas, polling, spool, SSE, revocación, rotación, PyInstaller ni servicio del sistema.

## AC-JAXON-062 -- Enrolamiento SFTP seguro Local/Test de OLA 2

`OLA2-SFTP-001a` autoriza exclusivamente SFTPGo Local/Test reproducible, cuenta y buzón lógico aislados, activación humana idempotente `PENDING -> ACTIVE`, máximo un Agent `ACTIVE` por activo y verificación puntual SFTP con host key Ed25519 preprovisionada. Collector conserva el estado oficial y emite `AGENT_ACTIVATED`; Identity entrega la public key mediante su contrato público e Inventory confirma que el activo está `ACTIVE`. El aprovisionamiento usa la API administrativa de SFTPGo, reconcilia de forma estricta y falla cerrado ante drift o indisponibilidad.

OpenAPI 1.0 y 1.1 permanecen inmutables. OpenAPI 2.0 registra el cambio major de `AgentResponse.status` y añade `POST /agents/{agentId}/activate`. El Agent usa Paramiko 5.0.0 con private key local exacta, `RejectPolicy`, `allow_agent=false` y `look_for_keys=false`; la configuración pública se persiste con controles POSIX y no admite sustitución silenciosa.

La fixture deriva de SFTPGo 2.7.6, commit firmado `62ae9ba3957e9ed52b44a4f885e805e2d7b35972`, compila con Go 1.26.9 y actualiza únicamente `golang.org/x/net` a 0.60.0 para cerrar los hallazgos bloqueantes conocidos. Usa runtime distroless no-root y gates de pruebas upstream, `govulncheck`, Trivy y SBOM temporal. No se publica ni despliega. Este acuerdo no autoriza snapshots o transferencias funcionales, polling, tareas, spool, procesamiento Collector, estado técnico, SSE, revocación, rotación, Discovery, PyInstaller, servicio del sistema o infraestructura Production.

El 2026-10-15 LUIS ratificó de forma limitada la ampliación ya integrada del workflow backend existente que construye/verifica la fixture SFTPGo y elimina exclusivamente su caché de construcción después de consumirla. La ratificación no autoriza nuevos workflows, jobs, relajación de gates, publicaciones, despliegues ni cambios GitHub posteriores.

## AC-JAXON-063 -- Snapshot técnico puntual y transferencia SFTP atómica

`OLA2-SNP-001a` autoriza exclusivamente `jaxon-agent send-snapshot --state-dir <ruta>` en POSIX/Linux para recoger una vez hostname, familia/nombre/versión del sistema operativo, arquitectura, CPU lógicas, memoria total y total/disponible del volumen raíz `/`. El Agent genera `messageId` y `snapshotId` UUIDv4 independientes, `capturedAt` y `createdAt` RFC3339 UTC, serializa JSON UTF-8 compacto/determinista y valida offline los bytes contra Agent 1.0 antes de cualquier conexión. El mensaje no puede superar 5 MiB y no se persiste localmente.

La transferencia conserva private key Ed25519 local exacta, host key fijada, `allow_agent=false`, `look_for_keys=false` y timeouts connect/banner/auth/operación `10/10/10/60` segundos. Solo crea de forma exclusiva `/snapshots/{messageId}.json.part`, verifica su tamaño, renombra atómicamente a `.json`, verifica final presente y parcial ausente, y ante fallo solo intenta borrar su propio `.part`. La salida segura se limita a `agentId`, `messageId`, `snapshotId`, `remotePath`, `status` y timestamps.

SFTPGo Local/Test crea server-side `/snapshots` mediante un virtual folder determinista, aislado por Agent y mapeado fuera de su `home_dir`; mantiene `/` con `list`, `/tasks` sin cambios y `/responses` sin funcionalidad nueva. `/snapshots` recibe únicamente `list`, `upload`, `rename_files`, `create_dirs` y `delete_files`; `delete_files` no se concede en otra ruta. `filters.max_upload_file_size=5242880` es un límite global de la cuenta. El aprovisionamiento solo crea, acepta el perfil objetivo exacto o actualiza el perfil anterior exacto de `OLA2-SFTP-001a`; cualquier otro drift falla cerrado.

OpenAPI 1.0/1.1/2.0, JSON Schema Agent 1.0, manifest y bundle vendorizado permanecen inmutables. No se crean migraciones ni se modifican frontend o workflows. No se autorizan polling, retries automáticos, spool, tareas, daemon/servicio, procesamiento Collector, persistencia de observaciones, `technical-state`, SSE, revocación, rotación, Discovery, PyInstaller, infraestructura Production, publicación de artefactos o despliegues.

## AC-JAXON-064 -- Ingestión mínima Collector y estado técnico consultable

`OLA2-COL-001a` autoriza exclusivamente en `backend` y `frontend` la recepción indirecta del snapshot final ya transferido por `OLA2-SNP-001a`: Collector monta en solo lectura el almacenamiento operacional de SFTPGo, reconcilia como máximo 1000 mensajes nuevos por ciclo con el intervalo predeterminado de cinco minutos aprobado en `AC-JAXON-044` e ignora siempre `.part`. Solo admite buzones y nombres UUIDv4 canónicos, archivos regulares sin symlinks, estables, UTF-8 estricto y de hasta 5 MiB; valida offline contra la fuente canónica Agent 1.0 empaquetada desde `backend/doc/contracts/agent/1.0/`.

Antes de persistir, Collector comprueba coincidencia de Agent/message entre ruta y payload, Agent `ACTIVE`, usuario SFTP esperado e identidad técnica registrada. Una única migración crea exclusivamente en schema `collector` el ledger de ingestiones y las observaciones aceptadas; la deduplicación/conflicto cubre ruta+fingerprint, `messageId` y `snapshotId`, con locks transaccionales e idempotencia. Collector no borra, renombra, mueve ni reescribe archivos y no escribe tablas ajenas; consulta Inventory e Identity por contratos públicos in-process.

Se implementa el `GET /api/v1/assets/{assetId}/technical-state` ya aprobado en OpenAPI 1.0 y heredado por 1.1/2.0, exclusivo para `IT_OPERATOR`: `200` con estado nulo para un activo existente sin observaciones y `404` para UUID inválido o activo inexistente. Inventory conserva la ficha oficial y Collector la observación técnica; frontend presenta ambas separadas, mantiene estado sensible solo en memoria y segmenta la caché por usuario. OpenAPI 1.0/1.1/2.0, JSON Schema Agent 1.0, fixtures, manifest y copia vendorizada permanecen byte a byte inmutables; `automation-collector` no cambia.

El corte exige pruebas unitarias, integración PostgreSQL/Flyway real, autorización, idempotencia, conflictos, batch retenido y un E2E Chromium real Agent -> SFTPGo -> Collector -> PostgreSQL -> REST -> UI, incluyendo `.part`, ciclos repetidos, aislamiento y rechazo de atribución cruzada. Autoriza una rama, un push inicial y un Pull Request por `frontend` y `backend`, integración squash en ese orden y verificación post-merge. No autoriza SSE, polling Agent, tareas/respuestas, retries automáticos, spool, cuarentena, borrado/purga, revocación/rotación, Discovery, cambios de contratos, workflows, `automation-collector`, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-065 -- Aviso SSE selectivo de estado técnico

`OLA2-SSE-001a` autoriza exclusivamente en `backend` y `frontend` el stream autenticado `GET /api/v1/assets/{assetId}/technical-state/events` para `IT_OPERATOR`. Usa `text/event-stream`, valida UUIDv4 canónico y activo existente antes de abrir la conexión, responde errores `401`, `403`, `404` y `429` como RFC 9457, y emite solo `ASSET_TECHNICAL_STATE_UPDATED` después del primer commit exitoso de un snapshot aceptado. Duplicados, conflictos, rechazos y reprocesados no emiten. El `id` SSE coincide con `eventId`; el payload se limita a `eventId`, `eventType`, `occurredAt`, `assetId` y `correlationId`, sin datos técnicos ni material sensible.

SSE es una adaptación best-effort en memoria, sin replay durable, broker, Redis, WebSocket ni outbox propio; REST continúa como única fuente oficial. Las suscripciones quedan aisladas por activo y usuario. Los defaults configurables son 100 conexiones globales, 3 por usuario, heartbeat comentario cada 15 segundos y timeout de 60 segundos. La conexión pasiva autentica sin renovar la inactividad de 30 minutos ni alterar el máximo absoluto de 12 horas. Collector genera correlación UUIDv4 explícita en el origen asíncrono y expone métricas de baja cardinalidad para conexiones activas, eventos enviados, fallos y rechazos por límite.

Frontend abre una sola `EventSource` con credenciales por activo seleccionado, la cierra al cambiar de activo o desmontar la sesión, valida el evento y únicamente invalida `['collector', userId, 'asset-technical-state', assetId]`; los datos se recuperan de nuevo por REST. Conserva actualización manual y degradación segura si SSE falla. OpenAPI 2.1 añade solo esta operación y mantiene compatibilidad 2.0 -> 2.1; OpenAPI 1.0/1.1/2.0 y JSON Schema Agent 1.0 permanecen byte a byte inmutables.

El corte exige pruebas de contrato, post-commit/silencio para resultados no aceptados, aislamiento, autorización, límites, sesión pasiva, métricas, lifecycle frontend y un E2E Chromium Agent -> SFTPGo -> Collector -> PostgreSQL -> SSE -> REST -> UI sin refresh manual. Autoriza una rama, un push inicial y un Pull Request por `frontend` y `backend`, integración squash en ese orden y verificación post-merge. No crea migraciones ni modifica workflows o `automation-collector`; no autoriza polling Agent, tareas/respuestas, retries automáticos, spool, cuarentena, borrado/purga, revocación/rotación, Discovery, PyInstaller, servicios del sistema, infraestructura Production, otros eventos SSE, artefactos publicados o despliegues.

## AC-JAXON-066 -- Estado operativo derivado del Agent

`OLA2-STA-001a` autoriza exclusivamente en `backend` y `frontend` derivar `UNKNOWN|ONLINE|OFFLINE` al consultar un Agent, separado del estado administrativo persistido `PENDING|ACTIVE`. `UNKNOWN` significa ausencia de contacto válido; `ONLINE` significa que `now - lastSeenAt` es menor o igual al umbral configurable, predeterminado en cinco minutos; `OFFLINE` se alcanza solo al superar el umbral y no significa error o avería. El cálculo usa `Clock`, UTC y no persiste el resultado ni crea scheduler.

La única evidencia válida procede de `collector.accepted_snapshots`: `lastSeenAt` es el máximo `received_at` y `lastSnapshotAt` el máximo `captured_at`, calculados de forma independiente. `.part`, mensajes inválidos, rechazos, conflictos y duplicados no renuevan el contacto. Las tablas existentes ya contienen la evidencia necesaria, por lo que el corte no crea migración ni escribe datos de Inventory, Identity u otro módulo.

`GET /api/v1/agents/{agentId}` conserva autorización `IT_OPERATOR` y errores RFC 9457, y amplía `AgentResponse` con `operationalStatus`, `lastSeenAt` y `lastSnapshotAt`. OpenAPI 2.2 es compatible con 2.1; OpenAPI 1.0/1.1/2.0/2.1 y JSON Schema Agent 1.0 permanecen byte a byte inmutables. Frontend valida estrictamente la respuesta en runtime, segmenta la caché por usuario y muestra por separado estado administrativo, estado operativo, último contacto y último snapshot en UTC, con etiquetas `Sin contacto`, `En línea` y `Fuera de línea`. Una respuesta incompleta no produce un estado falso.

Collector expone gauges agregados de baja cardinalidad por estados administrativos y operativos, sin IDs. El corte exige pruebas con tiempo controlado para el límite exacto, evidencia aceptada y resultados no aceptados, seguridad `401/403/404`, contrato, métricas y un E2E Chromium determinista `UNKNOWN -> ONLINE -> OFFLINE` que mantiene `ACTIVE` y demuestra que un rechazo no renueva contacto. Autoriza una rama, un push inicial y un Pull Request por `frontend` y `backend`, integración squash en ese orden y verificación post-merge. No modifica workflows ni `automation-collector` y no autoriza polling, heartbeat nuevo, tareas/respuestas, retries, spool, eventos SSE adicionales, alertas individuales, cuarentena/purga, revocación/rotación, Discovery, daemon, PyInstaller, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-067 -- Primera vertical controlada de tareas COLLECT_SNAPSHOT

`OLA2-TSK-001a` autoriza exclusivamente una vertical de tareas `COLLECT_SNAPSHOT` en `backend`, `frontend` y `automation-collector`. Solo `IT_OPERATOR` puede crear una tarea explícita para un Agent `ACTIVE`; el payload es exactamente `{}`, el TTL es de 15 minutos y el lifecycle es `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`. El corte permite como máximo una tarea no terminal por Agent/tipo y una ejecución por Agent. No habilita solicitudes masivas ni los otros tipos del catálogo v1.

Backend persiste tareas y Results en tablas propias de Collector, publica de forma durable y atómica `/tasks/{taskId}.json.part -> .json` y reconcilia en solo lectura `/responses/{messageId}.json`. Los límites son 64 KiB por Task, 5 MiB por Result y batch Agent de 10. La recepción no implica éxito: se validan contrato, identidad, correlación, deduplicación, conflictos y transiciones terminales inmutables. OpenAPI 2.3 es compatible con 2.2. El bundle Agent 1.0, su manifest y la copia vendorizada permanecen byte a byte inmutables; backend empaqueta para runtime sus schemas Task/Result ya existentes.

El Agent añade `jaxon-agent run --state-dir <ruta> [--once]` en foreground, con polling de 60 s más jitter de ±15 s, jitter inicial de 0--60 s solo en modo continuo, lock único, ledger POSIX privado, recuperación sin reejecución y Result determinista retenido hasta transferencia atómica. `COLLECT_SNAPSHOT` usa la capability puntual existente y solo devuelve `SUCCEEDED` después del upload atómico del snapshot. El timeout aislado de capability es de cinco minutos. Frontend permite crear una tarea y reconsultarla manualmente, sin polling del navegador ni evento SSE nuevo.

El corte exige pruebas unitarias, integración PostgreSQL/Flyway y SFTPGo reales, contratos, permisos, reinicio, deduplicación, timeout, expiración, aislamiento y un E2E Chromium real `UI -> REST -> PostgreSQL -> /tasks -> Agent -> /snapshots + /responses -> Collector -> REST -> UI`. Autoriza una rama, un push inicial y un Pull Request por `automation-collector`, `frontend` y `backend`, integración squash en ese orden y verificación post-merge. No autoriza retries burst, spool general, ejecución periódica, daemon/servicio/PyInstaller, otros tipos de tarea, `TASK_STARTED`, nuevos SSE, broker, cuarentena/purga, revocación/rotación, Discovery, infraestructura Production, otros workflows, artefactos publicados o despliegues.

## AC-JAXON-068 -- Resiliencia acotada de entrega de Results Agent

`OLA2-RSL-001a` autoriza exclusivamente en `automation-collector` reintentar la entrega SFTP de un Result terminal ya persistido por `OLA2-TSK-001a`. Cada episodio realiza como máximo cinco intentos totales, el primero inmediato; después de los fallos 1--4 aplica exponential backoff con full jitter uniforme entre cero y `min(300 s, 5 s * 2^(intento fallido - 1))`. Solo son transitorios los timeouts, EOF/cierre de transporte y errores de red explícitamente clasificados; autenticación, host key, apertura de canal rechazada, permisos, conflictos y errores desconocidos son permanentes y fallan cerrados sin retry.

El ledger local continúa como única fuente durable. Todos los intentos reutilizan byte a byte el mismo Result y conservan `messageId`, `resultId` y `taskId`; nunca reejecutan `COLLECT_SNAPSHOT`. Al agotar el episodio, el registro permanece `TERMINAL`; un reinicio manual inicia otro episodio y vuelve a intentar ese Result antes de recoger tareas. La transferencia conserva `.part -> .json`, limpia únicamente su parcial propio y acepta idempotentemente un final remoto idéntico; un final distinto es conflicto permanente. Los errores expuestos son seguros y no contienen detalles sensibles.

El corte exige pruebas deterministas de primer intento, recuperación y agotamiento, jitter/cap, clasificación transitoria/permanente/desconocida, identidad de bytes e IDs, no reejecución, persistencia, reinicio, idempotencia y limpieza aislada. Autoriza una rama, un push y un Pull Request técnico en `automation-collector`, seguidos solo tras su verificación por un Pull Request documental en `backend`. No modifica contratos Agent 1.0 u OpenAPI 2.3, dependencias, migraciones, SFTPGo, Compose, workflows, backend funcional o frontend; no autoriza spool general, retries de polling/snapshots/capabilities, periodicidad, daemon/servicio/PyInstaller, otros tipos de tarea, `TASK_STARTED`, nuevos SSE, cuarentena/purga, revocación/rotación, Discovery, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-069 -- Alta y consulta mínima de tickets propios

`OLA3-SUP-001a` autoriza exclusivamente en `backend` y `frontend` el primer corte ejecutable de Support para una sesión humana `IT_OPERATOR`: `POST /api/v1/tickets` crea un ticket y `GET /api/v1/tickets/{ticketId}` consulta únicamente un ticket cuyo `requesterUserId` coincide con el usuario autenticado. La creación acepta `type`, `summary`, `description` y `assetId` opcional; fija `status=OPEN`, `priority=NORMAL`, `source=WEB`, `requesterUserId=createdByUserId` al usuario de sesión, `assigneeUserId=null` y `version=0`. Un ticket ajeno o inexistente se presenta como `404`, sin revelar su existencia.

Support persiste exclusivamente en `support.tickets` y captura en la misma transacción un evento `TICKET_CREATED` 1.0 en `support.outbox_events`. Su payload allowlisted contiene solo `type`, `status`, `priority` y `source`, sin resumen ni descripción. El `assetId` opcional se valida mediante el contrato público síncrono `AssetLookup` de Inventory; no existe FK, lectura o escritura directa cross-schema. OpenAPI 2.4 conserva las operaciones de 2.3 y añade solo estos dos endpoints; las versiones 1.0/1.1/2.0/2.1/2.2/2.3 y JSON Schema Agent 1.0 permanecen inmutables.

Frontend mantiene CSRF solo en memoria, valida estrictamente el DTO y su pertenencia al usuario autenticado, y segmenta la caché TanStack Query por `userId` y `ticketId`. El corte exige pruebas de contrato, PostgreSQL/Flyway real, autenticación, rol, CSRF, ownership, validación de activo, atomicidad ticket/outbox, payload mínimo, parser frontend y un E2E Chromium real `login -> activo -> ticket asociado -> PostgreSQL -> reconsulta REST -> UI`. Autoriza una rama, un push inicial y un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. No modifica workflows ni `automation-collector` y no autoriza listado/búsqueda general, asignación, cambio de prioridad o estado, comentarios, adjuntos, resolución/cierre/reapertura, SSE, IA, Automation, consumidores outbox, auditoría materializada, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-070 -- Listado propio y conversación pública de tickets

`OLA3-SUP-002a` autoriza exclusivamente en `backend` y `frontend` listar de forma paginada los tickets cuyo `requesterUserId` coincide con el usuario `IT_OPERATOR` autenticado y añadir/consultar su conversación `PUBLIC`. `GET /api/v1/tickets` usa `page=0`, `size=20`, máximo 50 y orden estable `createdAt DESC, ticketId DESC`. `POST /api/v1/tickets/{ticketId}/comments` crea un comentario append-only normalizado; `GET /api/v1/tickets/{ticketId}/comments` usa `page=0`, `size=50`, máximo 100 y orden `createdAt ASC, commentId ASC`. Ticket inexistente y ajeno producen el mismo `404`.

Support persiste los comentarios exclusivamente en `support.ticket_comments`, con FK interna a `support.tickets` y sin FK o acceso directo a Identity. Cada alta captura atómicamente `TICKET_COMMENT_ADDED` 1.0 en `support.outbox_events`; el payload allowlisted contiene solo `commentId` y `visibility`, nunca body, resumen o descripción. Las lecturas exponen únicamente comentarios `PUBLIC`. OpenAPI 2.5 conserva 2.4 y añade solo estas tres operaciones y sus envelopes cerrados; las versiones anteriores y Agent 1.0 permanecen inmutables.

Frontend mantiene CSRF solo en memoria, segmenta las cachés TanStack Query por usuario, ticket y página, valida estrictamente envelopes, ownership y autor, y no usa retry u optimistic update en mutations. El corte exige pruebas de contrato, PostgreSQL/Flyway real, autorización, CSRF, ownership, paginación/orden, atomicidad y payload mínimo, parsers frontend y E2E Chromium real con comprobación directa de comentario/outbox. Autoriza un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. No modifica workflows ni `automation-collector` y no autoriza búsqueda/listado global, notas `INTERNAL`, asignación, cambio de prioridad o estado, adjuntos, edición/borrado, resolución/cierre/reapertura, SSE, IA, Automation, consumidores outbox, auditoría materializada, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-071 -- Corrección del listado resumido de tickets

La revisión posterior de `OLA3-SUP-002a` confirma un defecto histórico: `GET /api/v1/tickets` reutiliza `TicketResponse` y transmite `description`, aunque el listado debía ser resumido. `OLA3-SUP-002b` autoriza exclusivamente corregirlo en `backend` y `frontend`: el listado devuelve un DTO cerrado con todos los campos vigentes salvo `description`, y su SQL no selecciona esa columna; `GET /api/v1/tickets/{ticketId}` conserva la respuesta completa. No cambian ownership, rol, errores, paginación, orden, comentarios, outbox, datos o migraciones.

Frontend separa `TicketListItem` de `Ticket`, rechaza cerradamente `description` o cualquier campo inesperado en el listado y consulta el detalle individual antes de mostrar la descripción. Las cachés continúan segmentadas por usuario, página y ticket; CSRF, retries, optimistic updates y conversación pública no cambian. OpenAPI 2.5 permanece byte a byte inmutable y OpenAPI 3.0 documenta el breaking change sin cambiar `/api/v1`; Agent 1.0 y el resto de contratos históricos permanecen inmutables.

El corte exige pruebas de ausencia de propiedad y texto sensible en listado, detalle completo del mismo ticket, ownership/paginación/orden, schema cerrado, parser frontend fail-closed, fetch individual y E2E Chromium multirepo. Autoriza un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. No modifica workflows, dependencias ni `automation-collector` y no autoriza lifecycle, notas internas, búsqueda global, otros cambios funcionales, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-072 -- Logout y alta administrativa de usuarios humanos

`OLA1-IAM-002a` autoriza exclusivamente en `backend` y `frontend` cerrar la sesión humana presentada y crear usuarios humanos mediante `SYSTEM_ADMIN`. `POST /api/v1/auth/logout` exige sesión y CSRF, elimina solo el registro de sesión identificado por la cookie presentada, captura atómicamente `HUMAN_LOGOUT_SUCCEEDED` con payload `{}` y responde `204` expirando la cookie después del commit. No cierra otras sesiones del usuario. `POST /api/v1/users` exige sesión `SYSTEM_ADMIN` y CSRF, normaliza el username mediante NFKC, trim y lowercase, aplica la política de contraseña vigente y crea un usuario `ACTIVE` con uno o más roles explícitos del catálogo v1, sin duplicados ni autoridad implícita.

Identity persiste exclusivamente en sus tablas y captura `HUMAN_USER_CREATED` con payload allowlisted `{"status":"ACTIVE"}` en la misma transacción. Frontend mantiene credenciales y CSRF solo en memoria, no reintenta las mutaciones, limpia el estado sensible únicamente tras logout `204` y muestra el alta solo a `SYSTEM_ADMIN`; un usuario exclusivamente `SUPPORT_TECHNICIAN` no recibe acceso visual a administración, Inventory, Agent ni al workspace requester de Support. OpenAPI 3.1 conserva 3.0 y añade solo ambas operaciones; todas las versiones anteriores y Agent 1.0 permanecen inmutables.

El corte exige pruebas unitarias, PostgreSQL/Flyway real desde instalación limpia y upgrade histórico, autorización, CSRF, aislamiento de sesiones, normalización, unicidad, política de contraseña, roles cerrados, atomicidad/outbox, parsers frontend y E2E Chromium multirepo. Autoriza un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. Como excepción mínima de supply chain, el hallazgo `GO-2026-6809` autoriza sustituir exclusivamente el digest oficial del builder Go 1.26.9 de la fixture SFTPGo por Go 1.26.10; SFTPGo 2.7.6, su commit, módulos Go, runtime, workflows y funcionalidad permanecen iguales. No autoriza edición, deshabilitación, borrado, reset de contraseña, revocación global de sesiones, MFA, SSO, gestión de permisos, `automation-collector`, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-073 -- Bandeja técnica y autoasignación concurrente de tickets

`OLA3-SUP-003a` autoriza exclusivamente en `backend` y `frontend` una bandeja paginada para `SUPPORT_TECHNICIAN` con tickets no cerrados sin asignar o asignados al técnico autenticado, detalle completo y conversación `PUBLIC` de solo lectura. Las lecturas técnicas usan rutas explícitas bajo `GET /api/v1/tickets/technician-queue`; los endpoints requester existentes permanecen exclusivos de `IT_OPERATOR`. `POST /api/v1/tickets/{ticketId}/claim` exige sesión `SUPPORT_TECHNICIAN`, CSRF y `expectedVersion`; asigna atómicamente solo un ticket no cerrado y sin assignee, incrementa su versión una vez y devuelve `409` ante versión obsoleta o asignación ajena. La repetición del mismo técnico con la versión actual es idempotente y no vuelve a mutar.

Support persiste exclusivamente en sus tablas y captura `TICKET_ASSIGNED` 1.0 en `support.outbox_events` dentro de la transacción del primer claim exitoso. El payload allowlisted contiene solo `assigneeUserId`; actor y versión del recurso quedan en el envelope. No existe FK, lectura o escritura directa hacia Identity. OpenAPI 4.0 documenta el cambio incompatible que generaliza `assigneeUserId` y `version`; OpenAPI 3.1 y todas las versiones anteriores permanecen byte a byte inmutables. Frontend mantiene CSRF solo en memoria, segmenta cachés por usuario/página/ticket, valida cerradamente que la bandeja contenga solo tickets disponibles para el técnico y ejecuta claim sin retry ni optimistic update.

El corte exige pruebas de contrato, PostgreSQL/Flyway real desde instalación limpia y upgrade histórico, autorización, CSRF, paginación/orden, exclusión de cerrados/asignados a terceros, concurrencia de claim, idempotencia, atomicidad/outbox, payload mínimo, parsers frontend y E2E Chromium multirepo con comprobación SQL. Autoriza un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. No modifica workflows, dependencias ni `automation-collector` y no autoriza comentarios técnicos, notas `INTERNAL`, cambios de estado o prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE, IA, Automation, consumidores outbox, auditoría materializada, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-074 -- Conversación técnica pública e interna del ticket asignado

`OLA3-SUP-004a` autoriza exclusivamente en `backend` y `frontend` que el `SUPPORT_TECHNICIAN` actualmente asignado a un ticket no cerrado consulte y amplíe su conversación combinada `PUBLIC|INTERNAL`. La colección separada `GET|POST /api/v1/tickets/technician-queue/{ticketId}/conversation` exige asignación al actor; ticket inexistente, no asignado, cerrado o asignado a otro técnico producen el mismo `404`. `GET /api/v1/tickets/technician-queue/{ticketId}/comments` y todos los endpoints requester conservan exposición exclusivamente `PUBLIC`. El alta exige CSRF, visibilidad explícita y body no vacío de hasta 10 000 caracteres, sin retry automático ni actualización optimista.

Support persiste exclusivamente en `support.ticket_comments` y captura atómicamente `TICKET_COMMENT_ADDED` 1.0 en `support.outbox_events`. El payload allowlisted contiene solo `commentId` y `visibility`; nunca body, resumen, descripción u otros datos del comentario. La operación no cambia estado, prioridad, assignee o versión del ticket. OpenAPI 4.1 conserva 4.0 byte a byte y añade únicamente la colección y schemas técnicos; todas las versiones anteriores y Agent 1.0 permanecen inmutables. Frontend separa los parsers requester y técnico, rechaza cerradamente cualquier visibilidad no pública en la frontera requester, mantiene CSRF solo en memoria y segmenta cachés por usuario, ticket, colección y página.

El corte exige pruebas de contrato, PostgreSQL/Flyway real desde instalación limpia y upgrade histórico, autorización contextual, CSRF, orden/paginación, privacidad requester, atomicidad/rollback, payload mínimo, parsers fail-closed, cachés y E2E Chromium multirepo con comprobación SQL de comentarios y outbox. Autoriza un Pull Request técnico por `frontend` y `backend`, integración squash en ese orden, verificación post-merge y después un único Pull Request documental en `backend`. No modifica workflows, dependencias ni `automation-collector` y no autoriza cambios de estado o prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE, IA, Automation, consumidores outbox, auditoría materializada, infraestructura Production, artefactos publicados o despliegues.

## AC-JAXON-075 -- Corrección de versión outbox Support y estabilización de prueba SSE

El bloque agrupado `OLA3-SUP-004b` + `TEC-QLT-005a` autoriza exclusivamente en `backend` corregir dos hallazgos acotados posteriores a `OLA3-SUP-004a`. El comentario requester debe bloquear y leer en su misma transacción el ticket propio, conservar el `404` indistinguible y publicar `TICKET_COMMENT_ADDED.resource_version` con la versión vigente observada; no cambia estado, prioridad, assignee o versión del ticket ni amplía el payload mínimo. El hallazgo SSE corresponde a una carrera de observación de la prueba entre la recepción del evento y el incremento asíncrono de `jaxon.collector.sse.events.sent`; se autoriza sustituir la aserción inmediata por una espera observable, corta y acotada, sin cambiar lógica productiva SSE.

La verificación exige casos Support antes y después de claim, privacidad y payload mínimo, rollback, orden concurrente claim/comentario, prueba SSE aislada, veinte repeticiones consecutivas, pruebas que comparten contexto/registry y un único `clean verify` completo. `TEC-QLT-005a` autoriza además medir las duraciones por paso de los runs backend de `OLA3-SUP-004a` y documentar oportunidades futuras; no autoriza modificar el workflow. Se autoriza la rama técnica exacta `fix/ola3-sup-004b-tec-qlt-005a`, un Pull Request técnico backend con squash y verificación post-merge, y después una rama y un único Pull Request documental backend con el mismo cierre controlado.

No cambia OpenAPI, migraciones, dependencias, lockfiles, workflows, frontend, `automation-collector`, infraestructura Production, artefactos publicados o despliegues. La posible optimización reproducible del build SFTPGo queda como trabajo futuro que deberá conservar build, verificación y scans y requerirá delimitación y autorización propias. La autorización queda consumida al completar ambos Pull Requests y sus verificaciones post-merge.

## AC-JAXON-076 -- Caché reproducible del build SFTPGo en CI

`TEC-QLT-006a` autoriza exclusivamente en `backend` optimizar el coste dominante medido por `TEC-QLT-005a` mediante una caché BuildKit local para la fixture SFTPGo Local/Test. La clave debe ser exacta, sin prefijos de restauración, incluir versión de esquema, plataforma y todas las entradas efectivas del build, y usar únicamente `actions/cache` oficial fijada por SHA. La caché restaurada se valida mediante inventario canónico y SHA-256, rechaza symlinks, contenido incompleto, adicional o corrupto, y se consume en modo read-only; solo un miss exporta de forma atómica una caché completa. Una restauración no disponible o una caché rechazada degradan a build completo y nunca omiten el gate.

La optimización puede reutilizar etapas preparatorias, pero debe reejecutar en cada run las pruebas upstream, `govulncheck`, el target final de verificación, la carga de la imagen runtime, Trivy y la generación temporal de SBOM. Permanecen todos los gates posteriores de Gitleaks, PostgreSQL/pgvector, Maven/Testcontainers/Flyway, OpenAPI, contenedor backend, Compose y backup/restore. La caché opera dentro de la frontera de confianza de GitHub Actions y sus hashes detectan corrupción o incompletitud; no se presenta como firma ni como mecanismo independiente de autenticidad.

Se autoriza la rama exacta `ci/tec-qlt-006a-sftpgo-cache`, un único Pull Request técnico y documental hacia `develop`, integración por squash y verificación post-merge. El corte exige pruebas de determinismo e invalidación de clave, integridad positiva y negativa, miss, hit, caché rechazada y ejecución observable de los gates no cacheables. No cambia SFTPGo 2.7.6, su commit o módulos, contratos, migraciones, lógica funcional, dependencias Maven, `frontend`, `automation-collector`, infraestructura Production, publicación de imágenes/Packages/artefactos o despliegues. La autorización queda consumida al completar el Pull Request y su verificación post-merge.

## AC-JAXON-077 -- Triaje y ciclo operativo no terminal de Support

`OLA3-SUP-005a` autoriza exclusivamente en `backend` y `frontend` que el `SUPPORT_TECHNICIAN` actualmente asignado modifique la prioridad oficial `LOW|NORMAL|HIGH|URGENT` y ejecute solo `OPEN -> IN_PROGRESS`, `IN_PROGRESS -> WAITING_FOR_REQUESTER` y `WAITING_FOR_REQUESTER -> IN_PROGRESS`. `PUT /api/v1/tickets/{ticketId}/priority` y `PUT /api/v1/tickets/{ticketId}/status` exigen sesión técnica, CSRF y `expectedVersion`; una versión obsoleta o transición inválida produce `409`. Un no-op con versión vigente devuelve el ticket sin incrementar versión ni emitir evento, mientras un no-op con versión obsoleta conserva el `409`.

Cada cambio efectivo bloquea el ticket, incrementa su versión una vez y captura atómicamente `TICKET_PRIORITY_CHANGED` o `TICKET_STATUS_CHANGED` 1.0 en `support.outbox_events`. Los payloads cerrados contienen solo el valor anterior y el nuevo; actor, recurso, versión posterior y correlación permanecen en el envelope. OpenAPI 4.2 conserva 4.1 byte a byte y añade únicamente ambas operaciones y sus requests cerrados. Frontend no aplica retry automático ni estado optimista, conserva CSRF solo en memoria, segmenta cachés por usuario y reconsulta detalle y bandeja tras éxito.

El corte exige pruebas de contrato, PostgreSQL/Flyway real desde instalación limpia y upgrade histórico, autorización contextual, CSRF, no-op, versión obsoleta, transiciones válidas e inválidas, atomicidad/rollback, payload mínimo, parsers frontend y E2E Chromium multirepo con comprobación SQL. Autoriza exclusivamente la rama `feature/ola3-sup-005a`, un push técnico y un Pull Request por `frontend` y `backend`, integración squash en ese orden y verificación post-merge. El Pull Request backend corrige además el cierre documental retrospectivo autorizado de `TEC-QLT-006a`. No modifica workflows, dependencias ni `automation-collector` y no autoriza resolución, cierre, reapertura, reasignación, adjuntos, búsqueda global, SSE de Support, IA, Automation, consumidores outbox, auditoría materializada, infraestructura Production, artefactos publicados o despliegues.

## Referencias

- [Proyecto Maestro](../../PROYECTO_MAESTRO.md)
- [Pendientes](../16-contexto/PENDIENTES_JAXON.md)
- [Trabajo actual](../16-contexto/TRABAJO_ACTUAL.md)
