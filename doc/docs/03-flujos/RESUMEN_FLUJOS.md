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

Mutación `AUDIT_REQUIRED` -> cambio de negocio + captura durable `AuditFact` -> commit -> dispatch/retry -> MOD-08 persiste `AuditEvent` append-only. Reportes Assets/Agents/Tickets consultan contratos públicos del módulo dueño; CSV se genera bajo demanda y no se persiste por defecto.

### FL-AUT-001 / eventos internos

Command/query entre módulos -> API pública in-process. Hecho durable -> outbox del productor -> consumidores idempotentes `at-least-once`. Automation solo procesa eventos elegibles y cualquier mutación vuelve al contrato del módulo dueño. n8n no consume el bus interno directamente. SSE es adaptación best-effort y REST conserva verdad.
