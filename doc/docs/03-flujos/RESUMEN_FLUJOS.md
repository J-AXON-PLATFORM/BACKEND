# Resumen de flujos

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-005 | 2026-09-29  
> **Estado:** descubrimiento inicial y refinamiento/diseño v1 cerrados hasta Lote 54; implementación incremental iniciada y verificada hasta `OLA2-AGT-001a`, sin autorización general para nuevos incrementos.

## Índice

| ID | Módulos | Tipo | Propósito | Contratos principales | Estado |
|---|---|---|---|---|---|
| FL-E2E-001 | IAM, Inventory, Collector, Support, AI, Audit | E2E | De usuario/activo a soporte asistido y trazado | REST/OpenAPI, SFTP/JSON Schema, SSE | APROBADO conceptual |
| FL-E2E-002 | Discovery, Inventory | E2E | Descubrir y autorizar incorporación | REST/OpenAPI + trabajo SFTP cuando aplique | APROBADO conceptual |
| FL-E2E-003 | Collector, Agent, Inventory | E2E asíncrono | Crear tarea, recogerla, responder y aplicar resultado | SFTP/JSON Schema + SSE | APROBADO conceptual |
| FL-INV-001 | Inventory | Negocio | Alta/consulta/actualización/historial de activo | Operaciones públicas del módulo | APROBADO inicial |
| FL-DIS-001 | Discovery, Inventory | Negocio | Rango autorizado -> detección -> revisión -> incorporación | Contrato entre módulos | APROBADO inicial |
| FL-COL-001 | Agent, Collector, Inventory | Datos/asíncrono | Snapshot -> validación -> observación -> aceptación | SFTP + JSON Schema | APROBADO inicial |
| FL-SUP-001 | Support, Inventory, IAM | Negocio | Crear/asociar/asignar/atender/cerrar ticket | Operaciones públicas | APROBADO inicial |
| FL-AI-001 | AI, Support, Inventory | Datos | Fuente autorizada -> indexación -> retrieval -> respuesta con fuentes | Adapter proveedor IA | APROBADO inicial |
| FL-AUT-001 | Automation + módulos dueños | Entre módulos | Hecho/regla -> solicitud/notification -> seguimiento | Interfaces/eventos internos, SSE/n8n | APROBADO inicial |
| FL-AUD-001 | Audit + módulos | Datos | Evento relevante -> registro -> consulta/reporte | Contratos internos | APROBADO inicial |

## FL-E2E-000 -- Primera vertical ejecutable aprobada

1. `IT_OPERATOR` inicia sesión y obtiene sesión server-side válida.
2. Crea activo mínimo (`name`, `assetType`, `status`); estados iniciales `ACTIVE|INACTIVE`.
3. Pre-registra Agent; máximo un Agent `ACTIVE` por activo; estados `PENDING|ACTIVE|REVOKED`.
4. Agent se instala, genera keypair Ed25519 local y registra solo public key; SFTPGo habilita buzón aislado.
5. Tras enrolamiento, Agent genera snapshot inicial automático con hostname, OS, arquitectura, CPU lógica, RAM y almacenamiento.
6. Sube `.part` y finaliza por rename atómico. Collector valida ruta/JSON/versión/schema/identidad/deduplicación/reglas.
7. Collector persiste historial, separando `capturedAt` y `receivedAt`; Inventory no recibe copias de observaciones técnicas.
8. Tras el commit de la primera aceptación, `GET /api/v1/assets/{assetId}/technical-state/events` emite SSE `ASSET_TECHNICAL_STATE_UPDATED`; duplicados, conflictos, rechazos y reprocesados no emiten.
9. Frontend valida el aviso, invalida solo la query técnica del usuario/activo y consulta `GET /api/v1/assets/{assetId}/technical-state`.
10. UI distingue datos oficiales del activo de datos observados y muestra `lastSeenAt` separado de `lastSnapshotAt`.

Payload inicial no incluye usuarios/procesos/archivos/historial/software exhaustivo.

## FL-E2E-001 -- Activo, Agent, soporte e IA

1. Administrador/usuario autorizado se autentica.
2. Se registra o consulta un activo.
3. Se registra/vincula un Agent autorizado.
4. Agent envía snapshot JSON por SFTP.
5. Collector valida y procesa.
6. La UI/servicio de aplicación consulta Inventory para datos oficiales y Collector para observaciones; Collector no copia automáticamente el snapshot a tablas de Inventory. Una eventual aceptación oficial futura debe pasar por una operación propia de Inventory.
7. Usuario crea ticket asociado al activo.
8. IA/RAG recupera solo contexto autorizado y ofrece sugerencia con fuentes.
9. Técnico atiende/cierra según reglas de Soporte.
10. Auditoría conserva trazabilidad relevante.

## FL-E2E-002 -- Discovery

1. `SYSTEM_ADMIN` mantiene `AuthorizedScope` `ACTIVE|DISABLED` y asocia `DiscoveryExecutor` permitidos.
2. `IT_OPERATOR` crea `DiscoveryJob` solo sobre scope/executor autorizado; `jobId` estable y TTL inicial 1 h.
3. Backend publica job en buzón SFTP dedicado del Discovery Executor; la identidad/buzón no se comparte con Agent.
4. Executor revalida target dentro del scope y ejecuta perfil `BASIC` no intrusivo (ARP cuando aplique, ICMP, DNS, probes TCP configurados), con máximo un job activo; defaults iniciales `maxConcurrency=32` y probe timeout ~2 s.
5. Job transita `PENDING -> DISPATCHED -> RUNNING -> SUCCEEDED`, o `FAILED|EXPIRED|CANCELLED`.
6. Se registran `DiscoveredDevice` como observaciones con datos opcionales (IP, MAC, hostname, puertos observados); estado `UNREVIEWED`.
7. Usuario revisa resultados: `IGNORED` o solicita incorporación. No fusionar automáticamente basándose solo en IP/MAC.
8. Discovery invoca operación pública de Inventory; si se incorpora, conserva trazabilidad al `assetId` y marca `INCORPORATED`. Discovery nunca escribe tablas de Inventory.
9. Incorporar no instala Agent automáticamente; enrolamiento Agent sigue siendo flujo separado.
10. Resultados no incorporados siguen retención de 30 días. SSE puede avisar cambios de job y frontend vuelve a consultar REST.

`OLA4-DIS-001a` ejecuta solo el paso 1 para scopes: `SYSTEM_ADMIN` crea y cambia `ACTIVE|DISABLED` con versión optimista y outbox atómico; `IT_OPERATOR` consulta únicamente activos. No avanza a executor, job, SFTP o escaneo.

`OLA4-DIS-002a` amplía únicamente el paso 1: `SYSTEM_ADMIN` pre-registra un executor `PENDING`, fija su asociación inicial a scopes activos, inicializa localmente una identidad Ed25519 separada y registra solo la public key. No activa el executor ni avanza a buzón SFTP, jobs o escaneo.

`OLA4-DIS-003a` completa solo la activación administrativa del paso 1 y el canal técnico previo al paso 3: valida public key y al menos un scope asociado activo, reconcilia una cuenta SFTPGo aislada, confirma `ACTIVE` y verifica una conexión puntual con host key fijada. Se detiene antes de buzones `/jobs|/results`, publicación, polling, ejecución, resultados o escaneo.

`OLA4-DIS-004a` ejecuta únicamente la creación y despacho inicial de los pasos 2--3: `IT_OPERATOR` elige una combinación executor/scope activa, backend crea `PENDING` con TTL de una hora, publica atómicamente el mensaje en `/jobs` y confirma `DISPATCHED`. Se detiene antes de pickup, `RUNNING`, ejecución, resultados, dispositivos o incorporación.

`OLA4-DIS-005a` ejecuta únicamente la parte técnica local de los pasos 4--5: una invocación manual recoge como máximo un Job, persiste estado local antes de probes, ejecuta `BASIC` dentro del CIDR y entrega un Discovery Result 1.0 atómico en `/results`. La entrega no ingresa al backend ni confirma `RUNNING|SUCCEEDED|FAILED`: el estado oficial permanece `DISPATCHED`. Se detiene antes de polling, daemon, dispositivos, revisión o incorporación.

`OLA4-DIS-006a` completa únicamente la ingestión de los pasos 5--6: backend valida el archivo final y su correlación, registra ledger, termina atómicamente el Job como `SUCCEEDED|FAILED` y, solo para éxito, persiste una observación `UNREVIEWED` por dispositivo. Frontend reconsulta Job y lista la página REST. Se detiene antes de `IGNORED`, incorporación, deduplicación entre Jobs, Inventory, SSE, polling o daemon.

`OLA4-DIS-007a` ejecuta únicamente la alternativa `IGNORED` del paso 7: `IT_OPERATOR` confirma una revisión individual, backend aplica concurrencia optimista y outbox atómico y frontend reconsulta la misma página REST. Se detiene antes de `INCORPORATED`, Inventory, deduplicación, motivo textual, SSE, polling o daemon.

`OLA4-DIS-008a` ejecuta únicamente la alternativa de vínculo del paso 8: `IT_OPERATOR` indica un activo existente y `ACTIVE`, Discovery lo valida mediante la operación pública Inventory y conserva el `assetId` como trazabilidad al marcar `INCORPORATED`. Inventory no se muta, no se crea un activo, no hay merge o deduplicación y el enrolamiento Agent permanece separado.

`OLA4-DIS-008b` añade únicamente la alternativa de creación del paso 8: tras bloquear un dispositivo `UNREVIEWED`, Discovery solicita a la operación pública Inventory crear un activo siempre `ACTIVE`; ambos módulos confirman activo, outboxes, vínculo y transición en una transacción PostgreSQL. Repetir o competir por el mismo dispositivo no crea activos adicionales.

## FL-AI-001 -- Fuente documental mínima

`OLA5-AI-001a` ejecuta solo la entrada del flujo: `SYSTEM_ADMIN` confirma un original `text/plain` UTF-8 de hasta 1 MiB -> backend valida y publica bytes privados mediante `DocumentStorage` -> AI confirma metadata `PENDING` y `AI_SOURCE_REGISTERED` en una transacción -> UI reconsulta listado/detalle REST. Se detiene antes de descarga, extracción, chunks, embeddings, indexación, permisos de retrieval, consulta RAG o generación.

`OLA5-AI-002a` añade solo la indexación: `SYSTEM_ADMIN` confirma fuente/versión -> AI confirma job, `INDEXING` y outbox en una transacción corta -> worker reclama un job -> verifica y lee el original -> tokeniza y divide en chunks `480/72` -> calcula batches de embeddings locales fuera de transacción -> publica atómicamente chunks, vector, estado `READY|FAILED` y outbox. Un claim expirado termina `FAILED` y cualquier resultado tardío se descarta; no existe retry, reindexación, retrieval o generación.

`OLA5-AI-003a` añade solo la recuperación: `SYSTEM_ADMIN` envía consulta/límite con CSRF -> backend autoriza y valida el body cerrado -> verifica proveedor y límite de tokens -> calcula embedding local `query:` -> una única sentencia de lectura filtra fuentes `READY` y modelo fijado, ordena exactamente por coseno, `sourceId` y ordinal -> devuelve fragmentos con referencias o lista vacía. No persiste la consulta, no emite eventos y se detiene antes de generación, chat o asistencia de soporte.

`OLA5-AI-006a` añade solo la desautorización: `SYSTEM_ADMIN` confirma fuente/versión -> backend autoriza, valida el body cerrado y bloquea la fila -> verifica versión y estado `READY|FAILED` -> confirma `DISABLED`, nueva versión y `AI_SOURCE_DISABLED` en una transacción -> la siguiente recuperación ya no la considera por su filtro `READY` -> UI reconsulta REST. No borra evidencia ni reactiva, purga o reindexa.

## FL-E2E-003 -- Tarea a Agent

1. Usuario autorizado solicita operación permitida.
2. Backend valida y crea identidad estable de tarea.
3. Archivo de tarea se publica en buzón SFTP del Agent.
4. Agent recoge, valida destino/versión/tipo/vigencia/parámetros.
5. Ejecuta solo acción permitida.
6. Envía respuesta/resultado por SFTP.
7. Collector valida/procesa y actualiza estado oficial.
8. Frontend puede recibir aviso SSE y vuelve a consultar estado REST.

## Reglas de flujo

- Transferencia correcta != procesamiento exitoso != resultado de negocio aplicado.
- Pickup != tarea completada.
- No `exactly once`; se usan ID estable, idempotencia, deduplicación y reconciliación.
- Descubrir != incorporar != instalar.
- IA no ejecuta acciones autónomas.

## Refinamientos cerrados Lotes 49--53

### FL-SUP-001 -- Support/Tickets

`requester/createdBy` crea -> Support valida activo cuando aplique -> clasifica/prioriza -> asigna 0..1 técnico -> `OPEN|IN_PROGRESS|WAITING_FOR_REQUESTER|RESOLVED|CLOSED` -> resolución/cierre -> posible reapertura a `OPEN`. Comentarios `PUBLIC|INTERNAL`; adjuntos en `DocumentStorage`; REST verdad oficial, SSE aviso. IA solo sugiere y Automation/n8n no salta las reglas de Support.

`OLA3-SUP-005a` ejecuta solo el tramo de técnico asignado: prioridad oficial y `OPEN -> IN_PROGRESS -> WAITING_FOR_REQUESTER -> IN_PROGRESS`, con versión optimista y outbox atómico. Resolución, cierre y reapertura continúan fuera de ese corte.

`OLA3-SUP-006a` añade el tramo terminal especializado: el técnico asignado resuelve o cierra según la allowlist, y requester propietario o técnico asignado reabren un ticket terminal a `OPEN` conservando assignee. Cada transición confirma ticket, historial y outbox en una sola transacción; los actores autorizados reconsultan el timeline público y no se exponen notas internas ni textos sensibles en eventos.

### FL-INV-001 -- Inventory

Alta manual o incorporación Discovery -> ficha administrativa oficial -> responsable/ubicación/tipo/estado -> historial. Un `INACTIVE` no puede conservar Agent `ACTIVE`. Snapshot/observación Collector puede revisarse y aceptarse explícitamente como `AcceptedTechnicalConfiguration` versionada; nunca promoción automática. Discovery puede crear o vincular a activo existente, sin merge automático.

### FL-E2E-003 -- tareas Collector

`IT_OPERATOR` solicita uno o varios Agent IDs explícitos -> Collector valida `ACTIVE` + capability + autorización + límites -> publica task SFTP -> Agent valida destino/versión/tipo/vigencia/capability -> opcional `TASK_STARTED` -> ejecuta capability read-only -> respuesta SFTP -> Collector valida/quarantina/deduplica -> estado terminal. Catálogo v1: `COLLECT_SNAPSHOT`, `COLLECT_NETWORK_STATE`, `COLLECT_SOFTWARE_INVENTORY`.

### FL-AUD-001 -- Audit/Reporting

Mutación `AUDIT_REQUIRED` -> cambio de negocio + captura durable `AuditFact` -> commit -> dispatch/retry -> MOD-08 persiste `AuditEvent` append-only. `OLA6-AUD-001a` lo materializa para `AI_SOURCE_DISABLED`: dispatcher AI reclama su outbox con `SKIP LOCKED` -> `AuditFactRecorder` en la misma transacción -> entrega `DELIVERED` por consumidor o reintento/`FAILED` -> `AUDITOR` consulta REST. Reportes Assets/Agents/Tickets consultan contratos públicos del módulo dueño; CSV se genera bajo demanda y no se persiste por defecto.

`OLA6-AUD-001b` aplica el mismo flujo solo a altas administrativas futuras: Identity confirma usuario + `HUMAN_USER_CREATED` 2.0 con roles canónicos -> dispatcher Identity reclama solo 2.0 -> `AuditFactRecorder` materializa con `resourceVersion=null` -> entrega `DELIVERED` o retry/`FAILED`. 1.0 histórico no se despacha y la consulta REST/OpenAPI 8.3 no cambia.

`OLA6-AUD-001c` aplica el mismo flujo a login/logout humanos exitosos futuros: Identity confirma sesión + login 2.0, o elimina sesión + logout 2.0 -> el dispatcher existente reclama solo tipos allowlisted 2.0 -> Audit materializa metadata `{}` -> entrega o retry/`FAILED`. Fallos y 1.0 no se reclaman.

`OLA6-AUD-001d` amplía el flujo a tres hechos futuros: Identity confirma intento denegado + `HUMAN_LOGIN_FAILED` 2.0, o registro efectivo de clave + evento técnico 2.0 -> el mismo dispatcher reclama la allowlist 2.0 -> Audit materializa reason cerrado o metadata `{}` -> entrega o retry/`FAILED`. El camino `429`, no-ops y eventos 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001e` amplía el flujo a alta de activo, pre-registro y activación Agent futuros: Inventory o Collector confirman agregado + evento 2.0 -> su dispatcher y entrega propios reclaman solo la allowlist 2.0 -> `AuditFactRecorder` materializa recurso/versión exactos y metadata `{}` -> entrega o retry/`FAILED`. El no-op de activación y los eventos 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001f` amplía el flujo al lifecycle administrativo inicial de Discovery: ámbito o executor + evento 2.0 se confirman atómicamente -> la única entrega/dispatcher Discovery reclama solo cuatro tipos allowlisted -> `AuditFactRecorder` materializa recurso, versión y metadata exactos -> entrega o retry/`FAILED`. Los no-ops y todo 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001g` amplía la misma entrega al lifecycle de Discovery Jobs: creación y despacho efectivo emiten 2.0 con actor/correlación humanos originales; la aceptación terminal confirma atómicamente Job y completion 2.0 con actor executor y resultado `SUCCESS|FAILED`. Duplicados, conflictos, rechazos, finales tardíos, reprocesamientos y todo 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001h` amplía la misma entrega a la revisión humana de dispositivos: ignore e incorporación efectivos confirman dispositivo + evento 2.0; el camino de activo nuevo conserva además `ASSET_CREATED` 2.0 de Inventory en la misma transacción pública. No-ops, conflictos, activos inválidos, concurrencia perdedora, rollbacks y todo 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001i` aplica el flujo a los ocho hechos existentes de Ticket: Support confirma mutación + evento 2.0 -> su dispatcher reclama exclusivamente la allowlist 2.0 -> `AuditFactRecorder` materializa envelope y metadata cerrados -> entrega o retry/`FAILED`. No-ops, errores de autorización/versión/transición, rollbacks y todo 1.0 permanecen silenciosos para Audit.

`OLA6-AUD-001j` completa el lifecycle existente de fuentes AI: registro/solicitud humanos emiten 2.0 -> el worker terminaliza con `SYSTEM`, actor nulo y correlación humana original -> el dispatcher AI existente reclama esos cuatro tipos 2.0 además de disabled 1.0 -> `AuditFactRecorder` materializa metadata exclusiva de estado. Históricos 1.0, resultados tardíos, provider unavailable, conflictos y rollbacks permanecen sin claim o sin emisión.

### FL-AUT-001 / eventos internos

Command/query entre módulos -> API pública in-process. Hecho durable -> outbox del productor -> consumidores idempotentes `at-least-once`. Automation solo procesa eventos elegibles y cualquier mutación vuelve al contrato del módulo dueño. n8n no consume el bus interno directamente. SSE es adaptación best-effort y REST conserva verdad.
