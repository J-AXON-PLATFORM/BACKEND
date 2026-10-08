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

## Pendiente de diseño detallado

Operaciones/rutas, campos, errores, auth exacta de Swagger, prefijo de versión, generación de cliente y librería concreta. SFTP/JSON Schema permanece separado para Agent; no crear endpoint HTTP de snapshot por herencia del legado.

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
