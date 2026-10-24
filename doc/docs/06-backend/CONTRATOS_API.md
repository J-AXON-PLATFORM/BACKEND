# Contratos de API

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-003 | 2026-09-29.


<!-- JAXON-CKP003:START -->
## Refinamiento aprobado CKP-JAXON-003

- OpenAPI 3.1.2 canónico versionado en backend; Swagger UI solo visualiza/prueba.
- Prefijo MAJOR: `/api/v1`. Recursos en plural; acciones especiales solo cuando no encajen limpiamente como recursos.
- IDs externos se tratan como strings opacos; timestamps RFC3339 UTC.
- Errores: RFC 9457 `application/problem+json` con `code`, `correlationId` y `errors` cuando aplique.
- HTTP base: 400, 401, 403, 404, 409, 422, 429, 500, 503.
- Respuestas exitosas no usan wrapper universal `success/data/error`.
- SSE emite avisos ligeros; el frontend vuelve a consultar REST.
- Primera vertical define `/api/v1/auth/login`, `/api/v1/auth/me`, recursos `/api/v1/assets...` y `/api/v1/assets/{assetId}/technical-state` a nivel de diseño; detalle final de DTOs se implementa contra OpenAPI aprobado.
<!-- JAXON-CKP003:END -->

## API web aprobada

- REST sobre HTTPS, principalmente JSON.
- OpenAPI como contrato oficial versionado en backend; Swagger UI para visualizar/probar.
- Contrato `MAJOR.MINOR`; MINOR compatible, MAJOR breaking.
- Frontend y backend verifican compatibilidad contra la misma definición.
- Validación de entrada antes de mutar estado oficial.
- SSE se usa selectivamente para avisos servidor->browser; browser->backend sigue REST/HTTPS.

## Contrato ejecutable inicial -- AC-JAXON-059

`doc/contracts/openapi/1.0/openapi.yaml` implementa OpenAPI 3.1.2 con `info.version: 1.0` y server relativo `/api/v1`. Incluye exclusivamente `POST /auth/login`, `GET /auth/me`, `POST /assets`, `GET /assets/{assetId}` y `GET /assets/{assetId}/technical-state`.

Modela sesión opaca por cookie, CSRF en `SessionResponse` y header `X-CSRF-Token`, `LoginRequest`, `SessionResponse`, `AssetCreateRequest`, `AssetResponse`, `TechnicalStateResponse`, RFC 9457 `Problem` y error de campo. Los ejemplos no contienen credenciales o tokens. OLA1-IAM-001a implementa solo los controladores de login y `/auth/me`; el contrato por sí solo no acredita las operaciones restantes.

## Contrato compatible 1.1 -- OLA2-AGT-001a

`doc/contracts/openapi/1.1/openapi.yaml` conserva las cinco operaciones 1.0 y añade solo `POST /agents`, `GET /agents/{agentId}` y `PUT /agents/{agentId}/ssh-key`. OpenAPI 1.0 conserva checksum fijo y continúa disponible como minor anterior; `oasdiff` bloquea incompatibilidad 1.0 -> 1.1.

Las tres operaciones nuevas pertenecen al canal web humano, requieren `IT_OPERATOR`, y POST/PUT requieren CSRF. La public key se acepta únicamente como OpenSSH Ed25519, se normaliza sin comentario y se responde solo mediante indicador y huella `SHA256:...`; nunca se expone material privado. Repetir la misma key es idempotente, una key diferente o duplicada produce `409`, y el estado permanece `PENDING`.

## Contrato major 2.0 -- OLA2-SFTP-001a

`doc/contracts/openapi/2.0/openapi.yaml` conserva las ocho operaciones 1.1 y añade solo `POST /agents/{agentId}/activate`. La mutación requiere sesión `IT_OPERATOR` y CSRF; devuelve exclusivamente `host`, `port`, `username` y public host key Ed25519. La activación exige activo `ACTIVE` y public key registrada, reconcilia idempotentemente la cuenta lógica SFTPGo, limita a un Agent `ACTIVE` por activo y no expone credenciales administrativas o material privado.

OpenAPI 1.0 y 1.1 permanecen byte a byte inmutables; `oasdiff` mantiene el gate de compatibilidad minor 1.0 -> 1.1 y valida estructuralmente 2.0. El salto major es necesario porque `AgentResponse.status` deja de ser constante `PENDING` y admite `ACTIVE`. Este contrato web no crea un canal HTTP para el ejecutable Agent ni autoriza snapshots o tareas.

## Contrato compatible 2.1 -- OLA2-SSE-001a

`doc/contracts/openapi/2.1/openapi.yaml` conserva las nueve operaciones 2.0 y añade solo `GET /assets/{assetId}/technical-state/events`. La operación requiere sesión `IT_OPERATOR`, produce `text/event-stream`, valida activo antes de abrir el stream y declara `401`, `403`, `404` y `429` como RFC 9457. El evento `ASSET_TECHNICAL_STATE_UPDATED` contiene exclusivamente `eventId`, `eventType`, `occurredAt`, `assetId` y `correlationId`; el `id` SSE coincide con `eventId`.

El contrato no promete replay ni estado oficial dentro del stream: el cliente vuelve a consultar `GET /assets/{assetId}/technical-state`. `oasdiff` bloquea incompatibilidad 2.0 -> 2.1 y los checksums fijados protegen byte a byte OpenAPI 1.0/1.1/2.0. JSON Schema Agent 1.0 no cambia.

## Contrato compatible 2.2 -- OLA2-STA-001a

`doc/contracts/openapi/2.2/openapi.yaml` conserva las diez operaciones 2.1 y amplía `AgentResponse` con `operationalStatus`, `lastSeenAt` y `lastSnapshotAt`. El estado administrativo `PENDING|ACTIVE` permanece separado; el operativo se deriva como `UNKNOWN|ONLINE|OFFLINE` de snapshots aceptados y los timestamps opcionales se expresan en UTC. `OFFLINE` indica únicamente que se superó el umbral de contacto.

`oasdiff` bloquea incompatibilidad 2.1 -> 2.2. Checksums fijados protegen OpenAPI 1.0/1.1/2.0/2.1 y el manifest conserva JSON Schema Agent 1.0. No se crea otro endpoint, canal HTTP Agent, heartbeat o evento SSE.

## Contrato compatible 4.2 -- OLA3-SUP-005a

`doc/contracts/openapi/4.2/openapi.yaml` conserva todas las operaciones 4.1 y añade solo `PUT /tickets/{ticketId}/priority` y `PUT /tickets/{ticketId}/status`. Ambas operaciones requieren `SUPPORT_TECHNICIAN` actualmente asignado, CSRF y `expectedVersion`; devuelven `409` ante versión obsoleta o transición inválida. Los no-op con versión vigente no mutan ni emiten evento.

La prioridad admite `LOW|NORMAL|HIGH|URGENT`; el ciclo operativo se limita a `OPEN -> IN_PROGRESS`, `IN_PROGRESS -> WAITING_FOR_REQUESTER` y `WAITING_FOR_REQUESTER -> IN_PROGRESS`. `oasdiff` bloquea incompatibilidad 4.1 -> 4.2 y los checksums fijados protegen byte a byte todas las versiones anteriores. No se incorporan resolución, cierre, reapertura o reasignación.

## Contrato compatible 4.3 -- OLA3-SUP-006a

`doc/contracts/openapi/4.3/openapi.yaml` conserva todas las operaciones 4.2 y añade únicamente `POST /tickets/{ticketId}/resolve`, `POST /tickets/{ticketId}/close`, `POST /tickets/{ticketId}/reopen`, `GET /tickets/{ticketId}/lifecycle` y `GET /tickets/technician-queue/{ticketId}/lifecycle`. Las mutaciones requieren CSRF y `expectedVersion`, devuelven el `TicketResponse` histórico sin campos nuevos y usan `409` para versión obsoleta o transición inválida.

El historial público es cerrado, paginado y ordenado; puede contener resumen de resolución, motivo cerrado de cierre o motivo de reapertura según el evento. La autorización permanece contextual al requester propietario o técnico asignado. `oasdiff` bloquea incompatibilidad 4.2 -> 4.3 y el checksum fijado protege 4.2 byte a byte.

## Contrato compatible 4.4 -- OLA4-DIS-001a

`doc/contracts/openapi/4.4/openapi.yaml` conserva todas las operaciones 4.3 y añade únicamente `POST|GET /discovery/scopes`, `GET /discovery/scopes/{scopeId}` y `PUT /discovery/scopes/{scopeId}/status`. Las mutaciones son exclusivas de `SYSTEM_ADMIN`, requieren CSRF y, para estado, `expectedVersion`; `IT_OPERATOR` solo obtiene scopes `ACTIVE` y recibe `404` uniforme para inexistente o deshabilitado.

El manifiesto `doc/contracts/openapi/manifest.tsv` declara cada versión, checksum histórico y predecesor compatible. El verificador rechaza entradas mal formadas, duplicadas, archivos ausentes/no declarados, más de un contrato actual e incompatibilidades. OpenAPI 1.0--4.3 permanece byte a byte inmutable.

## Pendiente de diseño detallado

Permanecen pendientes Swagger UI runtime, generación de cliente y contratos posteriores a 4.4. SFTP/JSON Schema permanece separado para los ejecutables; SSE solo notifica al navegador y no crea un canal HTTP Agent/Discovery.

---
> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica

## Propósito

Definir contratos públicos de API cuando el proyecto exponga interfaces HTTP, GraphQL, gRPC u otra forma equivalente.

Este documento es específico de APIs. La política general de contratos está en `docs/04-arquitectura/CONTRATOS_SISTEMA.md`.

## Por cada API

Documentar según aplique:

- propósito;
- consumidores;
- versión;
- autenticación/autorización;
- request/input;
- response/output;
- tipos y schemas;
- validaciones;
- errores y códigos/estados;
- paginación/orden/filtros;
- idempotencia;
- concurrencia;
- límites;
- timeouts/reintentos cuando correspondan;
- compatibilidad y breaking changes;
- deprecación;
- pruebas asociadas.

## Especificación

Preferir una especificación legible por máquinas cuando la tecnología lo permita. No fijar OpenAPI, GraphQL SDL, Protobuf u otra herramienta hasta que el proyecto seleccione el estilo de interfaz.

## Regla

El contrato aprobado guía tanto al proveedor como a sus consumidores. La UI no debe convertirse en la fuente implícita del contrato del backend.
