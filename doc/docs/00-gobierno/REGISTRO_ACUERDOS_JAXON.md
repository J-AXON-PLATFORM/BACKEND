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

## Referencias

- [Proyecto Maestro](../../PROYECTO_MAESTRO.md)
- [Pendientes](../16-contexto/PENDIENTES_JAXON.md)
- [Trabajo actual](../16-contexto/TRABAJO_ACTUAL.md)
