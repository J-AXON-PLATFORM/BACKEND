# Contratos ejecutables de J-AXON

Esta carpeta es la fuente canónica de los contratos externos de J-AXON.

- `openapi/1.0/openapi.yaml`: contrato REST inicial de la primera vertical.
- `openapi/1.1/openapi.yaml`: minor compatible que conserva 1.0 y añade pre-registro, consulta y clave pública Agent.
- `openapi/2.0/openapi.yaml`: major que añade activación Agent y conexión SFTP pública.
- `openapi/2.1/openapi.yaml`: minor compatible que añade el aviso SSE selectivo de estado técnico.
- `openapi/2.2/openapi.yaml`: minor compatible que añade estado operativo Agent derivado y evidencia temporal.
- `openapi/2.3/openapi.yaml`: minor compatible que añade creación y consulta humana de tareas Agent.
- `openapi/2.4/openapi.yaml`: minor compatible que añade creación mínima y consulta de tickets propios.
- `openapi/2.5/openapi.yaml`: minor compatible que añade listado propio y conversación pública de tickets.
- `openapi/3.0/openapi.yaml`: major que corrige los elementos del listado propio de tickets a resúmenes sin `description`.
- `openapi/3.1/openapi.yaml`: minor compatible que añade logout de la sesión actual y alta administrativa de usuarios humanos.
- `openapi/4.0/openapi.yaml`: major que generaliza tickets asignables y añade bandeja y autoasignación técnica.
- `openapi/4.1/openapi.yaml`: minor compatible que añade conversación técnica `PUBLIC|INTERNAL` para el técnico asignado.
- `openapi/4.2/openapi.yaml`: minor compatible que añade prioridad y ciclo operativo no terminal para el técnico asignado.
- `openapi/4.3/openapi.yaml`: minor compatible que añade resolución, cierre, reapertura e historial terminal público.
- `openapi/4.4/openapi.yaml`: minor compatible que añade administración y consulta de ámbitos autorizados Discovery.
- `openapi/4.5/openapi.yaml`: minor compatible que añade pre-registro, consulta y registro de public key de Discovery Executor.
- `openapi/5.0/openapi.yaml`: major que añade activación y datos públicos del canal SFTP de Discovery Executor.
- `openapi/manifest.tsv`: inventario declarativo fail-closed de integridad y compatibilidad OpenAPI.
- `agent/1.0/`: contratos JSON Schema Draft 2020-12 para snapshot, Task y Result Agent.

Los contratos usan versión `MAJOR.MINOR`, independiente de la versión del software. Un cambio incompatible requiere un nuevo major. Un minor nuevo debe aceptar las instancias válidas del minor anterior y coexistir con él durante la transición. OpenAPI 5.0 es el contrato actual; 1.0--4.5 permanecen históricos e inmutables.

Los identificadores bajo `https://schemas.j-axon.invalid/` no son endpoints. Los validadores deben resolverlos únicamente contra recursos locales. `backend` conserva la fuente canónica; las copias en consumidores se sincronizan y verifican mediante el manifest SHA-256.

OpenAPI 1.0 permanece inmutable y disponible como minor anterior. OpenAPI 1.1 referencia sus operaciones estables y añade únicamente tres operaciones Agent para sesión humana `IT_OPERATOR`; no define un canal HTTP para el ejecutable Agent.

OpenAPI 2.0 permanece inmutable. OpenAPI 2.1 conserva sus operaciones y añade únicamente el stream best-effort `GET /assets/{assetId}/technical-state/events`; REST sigue siendo la fuente oficial del estado técnico.

OpenAPI 2.1 permanece inmutable. OpenAPI 2.2 amplía `AgentResponse` con `operationalStatus`, `lastSeenAt` y `lastSnapshotAt`; conserva separado el estado administrativo y no añade polling, heartbeat ni eventos SSE.

OpenAPI 2.2 permanece inmutable. OpenAPI 2.3 añade únicamente `POST /agents/{agentId}/tasks` y `GET /agents/{agentId}/tasks/{taskId}` para sesión humana `IT_OPERATOR`; no define un canal HTTP para el ejecutable Agent.

OpenAPI 2.3 permanece inmutable. OpenAPI 2.4 añade únicamente `POST /tickets` y `GET /tickets/{ticketId}` para que una sesión humana `IT_OPERATOR` cree y consulte sus propios tickets; no añade gestión del ciclo de vida de Support.

OpenAPI 2.4 permanece inmutable. OpenAPI 2.5 añade únicamente `GET /tickets`, `POST /tickets/{ticketId}/comments` y `GET /tickets/{ticketId}/comments` para listar tickets propios y mantener su conversación `PUBLIC`; no añade búsqueda global, notas internas ni gestión del ciclo de vida de Support.

OpenAPI 2.5 permanece byte a byte inmutable. OpenAPI 3.0 conserva las operaciones y el servidor `/api/v1`, pero `TicketPage.items` usa el schema cerrado `TicketListItem` sin `description`; `GET /tickets/{ticketId}` conserva `TicketResponse` completo.

OpenAPI 3.0 permanece inmutable. OpenAPI 3.1 añade únicamente `POST /auth/logout` para invalidar la sesión humana presentada y `POST /users` para alta administrativa por `SYSTEM_ADMIN`; no añade gestión posterior de usuarios ni revocación global de sesiones.

OpenAPI 3.1 permanece byte a byte inmutable. OpenAPI 4.0 generaliza la respuesta de ticket para asignación/versionado y añade la bandeja, su detalle/conversación `PUBLIC` de solo lectura y `POST /tickets/{ticketId}/claim` para `SUPPORT_TECHNICIAN`; los endpoints requester permanecen exclusivos de `IT_OPERATOR`. No añade comentarios técnicos, cambios de estado, reasignación, notas internas ni búsqueda global.

OpenAPI 4.0 permanece byte a byte inmutable. OpenAPI 4.1 conserva `GET /tickets/technician-queue/{ticketId}/comments` exclusivamente `PUBLIC` y añade la colección separada `GET|POST /tickets/technician-queue/{ticketId}/conversation` para conversación `PUBLIC|INTERNAL` del técnico actualmente asignado. Los endpoints requester continúan exponiendo solo `PUBLIC`.

OpenAPI 4.1 permanece byte a byte inmutable. OpenAPI 4.2 añade únicamente `PUT /tickets/{ticketId}/priority` y `PUT /tickets/{ticketId}/status`, con CSRF y `expectedVersion`, para prioridad y transiciones `OPEN -> IN_PROGRESS`, `IN_PROGRESS -> WAITING_FOR_REQUESTER` y `WAITING_FOR_REQUESTER -> IN_PROGRESS` del técnico asignado. No añade resolución, cierre, reapertura o reasignación.

OpenAPI 4.2 permanece byte a byte inmutable. OpenAPI 4.3 añade únicamente resolución, cierre y reapertura especializadas, más lectura paginada del historial terminal para requester propietario y técnico asignado. Los requests de mutación son cerrados, exigen `expectedVersion` y no modifican las respuestas históricas de ticket.

OpenAPI 4.4 permanece byte a byte inmutable. OpenAPI 4.5 añade únicamente pre-registro, listado, consulta y registro de public key de `DiscoveryExecutor`; no añade activación, ejecución Discovery, jobs, dispositivos, SFTP o incorporación a Inventory.

OpenAPI 4.5 permanece byte a byte inmutable. OpenAPI 5.0 es breaking porque amplía el schema cerrado de `DiscoveryExecutor` desde solo `PENDING`/versión `0` a `PENDING|ACTIVE`/versión `0|1`; añade únicamente `POST /discovery/executors/{executorId}/activate` y la respuesta pública `host|port|username|hostKey|hostKeyFingerprint`. No añade jobs, resultados, polling, escaneo, dispositivos o incorporación a Inventory.

Agent 1.0, incluidos `task.schema.json`, `result.schema.json`, manifest y copia vendorizada, permanece byte a byte inmutable. Backend empaqueta los cuatro schemas existentes para validación runtime.
