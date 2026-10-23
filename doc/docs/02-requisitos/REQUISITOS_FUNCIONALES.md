# Requisitos funcionales

> **Clasificación:** OBLIGATORIO  
> **Instancia:** J-AXON | CKP-JAXON-005 | 2026-09-29  
> **Estado:** catálogo funcional inicial aprobado para v1; endpoints, campos, tablas y estados exactos se refinan después.

## MOD-06 -- Identidad y Acceso

- **RF-IAM-001:** autenticar usuarios humanos mediante credenciales locales.
- **RF-IAM-002:** cerrar sesión autenticada.
- **RF-IAM-003:** usuarios autorizados pueden crear, consultar, modificar, habilitar y deshabilitar usuarios.
- **RF-IAM-004:** gestionar roles y permisos.
- **RF-IAM-005:** cada operación protegida valida autorización en backend.
- **RF-IAM-006:** gestionar identidades técnicas de Agents separadas de identidades humanas.
- **RF-IAM-007:** revocar credenciales de un Agent sin afectar a otros.

## MOD-01 -- Inventario y Activos

- **RF-INV-001:** registrar manualmente un activo.
- **RF-INV-002:** consultar la ficha completa de un activo.
- **RF-INV-003:** modificar datos administrativos permitidos.
- **RF-INV-004:** asignar/cambiar responsable y ubicación.
- **RF-INV-005:** gestionar estado administrativo.
- **RF-INV-006:** consultar configuración técnica aceptada.
- **RF-INV-007:** consultar historial de cambios relevantes.
- **RF-INV-008:** permitir activos sin Agent.
- **RF-INV-009:** recibir observaciones validadas de Collector sin escritura directa de Collector en tablas de Inventario.
  - **Refinamiento Lote 39:** en la primera vertical las observaciones permanecen en Collector y se consultan/componen con la ficha de Inventory; no se copian automáticamente.
- **RF-INV-010:** recibir propuestas de Discovery y decidir incorporación.
- **Refinamiento Lote 50:** `Asset` conserva ficha oficial con `ACTIVE|INACTIVE`, sin hard-delete ordinario v1; `assetTag` opcional/único normalizado, serial no necesariamente único, `AssetType` como catálogo administrable y `locationLabel` simple.
- **Refinamiento Lote 50:** un activo `INACTIVE` no puede conservar Agent `ACTIVE`; la transición se bloquea hasta resolver el Agent mediante Collector.
- **Refinamiento Lote 50:** observaciones Collector solo pasan a configuración oficial mediante aceptación humana explícita de `IT_OPERATOR`; la baseline aceptada es inmutable/versionada y conserva procedencia del snapshot.
- **Refinamiento Lote 50:** Discovery puede crear activo nuevo o vincular a existente de forma idempotente; no existe merge automático v1. Mutaciones sensibles usan concurrencia optimista.

## MOD-02 -- Descubrimiento de Red

- **RF-DIS-001:** solicitar trabajo de descubrimiento sobre rangos autorizados.
- **RF-DIS-002:** registrar cada trabajo y resultado.
- **RF-DIS-003:** ejecutar solo mediante ejecutor autorizado/designado.
- **RF-DIS-004:** mostrar dispositivos detectados.
- **RF-DIS-005:** revisar individualmente dispositivos descubiertos.
- **RF-DIS-006:** solicitar incorporación a Inventario.
- **RF-DIS-007:** descubrir no incorpora automáticamente.
- **RF-DIS-008:** incorporar no instala Agent automáticamente.
- **Refinamiento Lote 48:** Discovery separa `AuthorizedScope`, `DiscoveryExecutor`, `DiscoveryJob` y `DiscoveredDevice`; scopes `ACTIVE|DISABLED`, executors `PENDING|ACTIVE|REVOKED`, jobs `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|EXPIRED|CANCELLED`, dispositivos `UNREVIEWED|INCORPORATED|IGNORED`.
- **Refinamiento Lote 48:** `SYSTEM_ADMIN` administra scopes/ejecutores; `IT_OPERATOR` lanza trabajos solo sobre combinaciones autorizadas.
- **Refinamiento Lote 48:** Discovery Executor usa identidad y buzón SFTP propios, separados del Agent; un job activo por executor, TTL inicial 1 h, `jobId` estable.
- **Refinamiento Lote 48:** perfil `BASIC` no intrusivo con ARP cuando aplique, ICMP, DNS y probes TCP pequeños/configurables; `maxConcurrency=32` y probe timeout aproximado 2 s como defaults configurables.
- **Refinamiento Lote 48:** resultados son observaciones; incorporación requiere revisión humana y operación pública de Inventory; el executor revalida que cada target esté dentro del scope autorizado.

## MOD-03 -- Collector

- **RF-COL-001:** registrar Agent y relacionarlo con equipo autorizado.
- **RF-COL-002:** cada Agent tiene identidad técnica propia.
- **RF-COL-003:** Agent recolecta información técnica autorizada.
- **RF-COL-004:** Agent genera snapshots según JSON Schema aprobado.
- **RF-COL-005:** Agent entrega snapshots mediante SFTP.
- **RF-COL-006:** Collector valida estructura, versión, identidad y contenido antes de procesar.
- **RF-COL-007:** snapshot inválido no modifica estado oficial.
- **RF-COL-008:** conservar Agent, versión, último contacto, último snapshot y estado de procesamiento.
- **RF-COL-009:** usuario autorizado solicita tareas controladas a uno o varios Agents.
- **RF-COL-010:** backend crea tareas y las deja en el buzón SFTP correspondiente.
- **RF-COL-011:** Agent consulta solo tareas que le corresponden.
- **RF-COL-012:** Agent valida identidad, vigencia, versión y tipo antes de ejecutar.
- **RF-COL-013:** Agent devuelve respuesta/resultado por SFTP.
- **RF-COL-014:** backend distingue recepción, validación, procesamiento y resultado final.
- **RF-COL-015:** procesamiento tolera duplicados mediante identidad e idempotencia.
- **RF-COL-016:** v1 no ofrece shell remota arbitraria como capacidad general.
- **Refinamiento Lote 51:** catálogo read-only v1 = `COLLECT_SNAPSHOT|COLLECT_NETWORK_STATE|COLLECT_SOFTWARE_INVENTORY`; Agent reporta capabilities y backend+Agent validan allowlist/capability.
- **Refinamiento Lote 51:** estados de tarea `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`; `expiresAt` limita el inicio; `TASK_STARTED` es evidencia de inicio pero el resultado final puede reconciliar la transición.
- **Refinamiento Lote 51:** solicitud masiva usa targets explícitos; defaults configurables: 100 targets, queue 10/Agent, una tarea no terminal por `taskType`, 1 `RUNNING`, 64 KiB task JSON, 5 MiB result JSON, timeout 5 min por capability.
- **Refinamiento Lote 51:** solo `IT_OPERATOR` solicita tareas v1; Automation/n8n/IA no disparan tareas Agent autónomamente; observaciones resultantes pertenecen a Collector y retienen 180 días.

## MOD-04 -- Soporte / Tickets

- **RF-SUP-001:** crear ticket.
- **RF-SUP-002:** asociar ticket a activo cuando corresponda.
- **RF-SUP-003:** consultar tickets.
- **RF-SUP-004:** clasificar y priorizar tickets.
- **RF-SUP-005:** asignar responsable de atención.
- **RF-SUP-006:** modificar estado según ciclo permitido.
- **RF-SUP-007:** registrar comentarios/conversación.
- **RF-SUP-008:** adjuntar archivos.
- **RF-SUP-009:** mantener historial de atención/cambios relevantes.
- **RF-SUP-010:** cerrar ticket según reglas de Soporte.
- **RF-SUP-011:** solicitud de Automatización se valida en Soporte antes de crear ticket.
- **RF-SUP-012:** Soporte sigue funcionando sin IA/RAG.
- **Corte ejecutable OLA3-SUP-005a:** materializa `RF-SUP-004` para `LOW|NORMAL|HIGH|URGENT` y el subconjunto de `RF-SUP-006` formado por `OPEN -> IN_PROGRESS`, `IN_PROGRESS -> WAITING_FOR_REQUESTER` y retorno a `IN_PROGRESS`; el lifecycle terminal permanece pendiente.
- **Corte ejecutable OLA3-SUP-006a:** materializa el subconjunto terminal de `RF-SUP-006` para resolución, cierre y reapertura con autoridad contextual, y solo la porción de `RF-SUP-009` correspondiente al historial terminal público append-only; no implementa todo el lifecycle, reasignación, adjuntos o auditoría materializada.
- **Refinamiento Lote 49:** estados `OPEN|IN_PROGRESS|WAITING_FOR_REQUESTER|RESOLVED|CLOSED`; resolución exige `resolutionSummary`, cierre conserva razón y reapertura vuelve a `OPEN` con motivo.
- **Refinamiento Lote 49:** tipos `INCIDENT|SERVICE_REQUEST|QUESTION|OTHER`; prioridades `LOW|NORMAL|HIGH|URGENT`, `NORMAL` default configurable; no equivalen a severidades operativas ni SLA.
- **Refinamiento Lote 49:** separar `requester`, `createdBy`, `assignee` y `source`; máximo un assignee actual; comentarios `PUBLIC|INTERNAL` append-only en flujo normal.
- **Refinamiento Lote 49:** adjuntos usan `DocumentStorage` y SHA-256; defaults 25 MiB/archivo y 100 MiB/ticket; autorización se revalida al acceder.
- **Refinamiento Lote 49:** IA solo sugiere; Automation/n8n puede solicitar creación/notificaciones autorizadas pero no cerrar, reasignar o repriorizar autónomamente; concurrencia optimista para mutaciones sensibles.

## MOD-05 -- IA + RAG

- **RF-AI-001:** usuarios autorizados incorporan fuentes documentales.
- **RF-AI-002:** indexar contenido autorizado con embeddings/pgvector.
- **RF-AI-003:** mantener relación fragmentos-fuente original.
- **RF-AI-004:** usuario realiza consultas asistidas por IA.
- **RF-AI-005:** comprobar permisos antes de recuperar contexto.
- **RF-AI-006:** RAG recupera solo información autorizada.
- **RF-AI-007:** respuestas pueden presentar referencias a fuentes.
- **RF-AI-008:** IA usa contexto autorizado de tickets, activos y observaciones.
- **RF-AI-009:** IA sugiere procedimientos/soluciones a soporte.
- **RF-AI-010:** IA no cierra tickets autónomamente.
- **RF-AI-011:** IA no modifica activos autónomamente.
- **RF-AI-012:** IA no ejecuta comandos en Agents autónomamente.
- **RF-AI-013:** indisponibilidad IA no bloquea Inventario, Collector o Soporte.

## MOD-07 -- Automatización y Notificaciones

- **RF-AUT-001:** definir reglas automáticas sobre hechos autorizados.
- **RF-AUT-002:** ejecutar acción de automatización cuando se cumpla regla.
- **RF-AUT-003:** solicitar operaciones al módulo propietario, sin modificar sus datos directamente.
- **RF-AUT-004:** generar notificaciones.
- **RF-AUT-005:** mantener estado básico de ejecuciones.
- **RF-AUT-006:** integrar n8n sin transferirle reglas centrales.
- **RF-AUT-007:** fallo de n8n no elimina/revierte operaciones válidas del core.
- **RF-AUT-008:** cambios relevantes pueden notificarse al frontend por SSE.

## MOD-08 -- Auditoría y Reportes

- **RF-AUD-001:** registrar acciones auditables relevantes.
- **RF-AUD-002:** permitir identificar quién/qué/cuándo/recurso/resultado conceptualmente.
- **RF-AUD-003:** no guardar secretos/credenciales en auditoría.
- **RF-AUD-004:** usuarios autorizados consultan auditoría.
- **RF-AUD-005:** generar reportes básicos de activos.
- **RF-AUD-006:** reportes básicos de Agents.
- **RF-AUD-007:** reportes básicos de tickets.
- **RF-AUD-008:** Auditoría/Reportes no modifica datos operativos ajenos.
- **Refinamiento Lote 52:** `AuditEvent` append-only con actor/recurso/resultado/correlación y metadata allowlisted; resultados `SUCCESS|DENIED|FAILED`; secretos/payloads completos prohibidos.
- **Refinamiento Lote 52:** raw Audit es consulta de `AUDITOR`; retención 5 años desde `recordedAt`; filtros por tiempo/módulo/evento/actor/recurso/resultado/correlationId, page size 50 y máximo 200 como defaults.
- **Refinamiento Lote 52:** reportes v1 Assets/Agents/Tickets mediante contratos públicos, con exportación CSV no persistente; sin BI/report builder, PDF/XLSX ni SSE propio v1.
- **Refinamiento Lote 52:** una mutación `AUDIT_REQUIRED` exitosa exige captura durable de su intención de auditoría antes de responder éxito.

## Requisitos transversales

- **RF-TRV-001:** interfaz web consume backend mediante REST/HTTPS.
- **RF-TRV-002:** cambios seleccionados pueden actualizar interfaz mediante SSE.
- **RF-TRV-003:** operaciones respetan permisos del usuario autenticado.
- **RF-TRV-004:** cada módulo modifica solo datos de su propiedad.
- **RF-TRV-005:** contratos Web/API documentados mediante OpenAPI.
- **RF-TRV-006:** contratos Agent/backend usan JSON + JSON Schema versionado.
- **RF-TRV-007:** procesos asíncronos exponen estado consultable y no fingen ejecución inmediata.
- **RF-TRV-008:** errores externos se distinguen de errores propios del dominio.

## Conteo

El catálogo contiene **90 requisitos funcionales de alto nivel**. En la conversación se indicó por error “82”; la lista aprobada ya contenía 90 y aquí solo se corrige la suma, sin añadir ni eliminar requisitos.

## Pendiente de refinamiento

Los dominios funcionales principales quedaron refinados hasta el Lote 54. Siguen `POR_DECIDIR` únicamente detalles ejecutables que deben fijarse durante implementación (endpoints/campos físicos/tablas/schemas exactos donde no estén contractualizados) y decisiones deliberadamente diferidas por organización, Production o evidencia. Diseño aprobado no equivale a implementación.
