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
- `agent/1.0/`: contratos JSON Schema Draft 2020-12 para snapshot, Task y Result Agent.

Los contratos usan versión `MAJOR.MINOR`, independiente de la versión del software. Un cambio incompatible requiere un nuevo major. Un minor nuevo debe aceptar las instancias válidas del minor anterior y coexistir con él durante la transición. OpenAPI 3.1 es el contrato actual y conserva compatibilidad con 3.0; 2.5 permanece como histórico inmutable y no se exige compatibilidad minor entre ambos majors.

Los identificadores bajo `https://schemas.j-axon.invalid/` no son endpoints. Los validadores deben resolverlos únicamente contra recursos locales. `backend` conserva la fuente canónica; las copias en consumidores se sincronizan y verifican mediante el manifest SHA-256.

OpenAPI 1.0 permanece inmutable y disponible como minor anterior. OpenAPI 1.1 referencia sus operaciones estables y añade únicamente tres operaciones Agent para sesión humana `IT_OPERATOR`; no define un canal HTTP para el ejecutable Agent.

OpenAPI 2.0 permanece inmutable. OpenAPI 2.1 conserva sus operaciones y añade únicamente el stream best-effort `GET /assets/{assetId}/technical-state/events`; REST sigue siendo la fuente oficial del estado técnico.

OpenAPI 2.1 permanece inmutable. OpenAPI 2.2 amplía `AgentResponse` con `operationalStatus`, `lastSeenAt` y `lastSnapshotAt`; conserva separado el estado administrativo y no añade polling, heartbeat ni eventos SSE.

OpenAPI 2.2 permanece inmutable. OpenAPI 2.3 añade únicamente `POST /agents/{agentId}/tasks` y `GET /agents/{agentId}/tasks/{taskId}` para sesión humana `IT_OPERATOR`; no define un canal HTTP para el ejecutable Agent.

OpenAPI 2.3 permanece inmutable. OpenAPI 2.4 añade únicamente `POST /tickets` y `GET /tickets/{ticketId}` para que una sesión humana `IT_OPERATOR` cree y consulte sus propios tickets; no añade gestión del ciclo de vida de Support.

OpenAPI 2.4 permanece inmutable. OpenAPI 2.5 añade únicamente `GET /tickets`, `POST /tickets/{ticketId}/comments` y `GET /tickets/{ticketId}/comments` para listar tickets propios y mantener su conversación `PUBLIC`; no añade búsqueda global, notas internas ni gestión del ciclo de vida de Support.

OpenAPI 2.5 permanece byte a byte inmutable. OpenAPI 3.0 conserva las operaciones y el servidor `/api/v1`, pero `TicketPage.items` usa el schema cerrado `TicketListItem` sin `description`; `GET /tickets/{ticketId}` conserva `TicketResponse` completo.

OpenAPI 3.0 permanece inmutable. OpenAPI 3.1 añade únicamente `POST /auth/logout` para invalidar la sesión humana presentada y `POST /users` para alta administrativa por `SYSTEM_ADMIN`; no añade gestión posterior de usuarios ni revocación global de sesiones.

Agent 1.0, incluidos `task.schema.json`, `result.schema.json`, manifest y copia vendorizada, permanece byte a byte inmutable. Backend empaqueta los cuatro schemas existentes para validación runtime.
