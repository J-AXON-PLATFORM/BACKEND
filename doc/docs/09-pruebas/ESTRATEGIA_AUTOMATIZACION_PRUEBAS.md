# Estrategia de automatización de pruebas


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Automatizar pruebas sin depender de un proveedor de CI específico

- Local commands
- CI execution if selected
- Parallelization
- Flaky test policy
- Reports/artifacts

## OLA5-AI-002a

- Unitarias: chunking exacto `480/72`, límites estructurales/Unicode, hashes deterministas, normalización L2 y rechazo de embeddings inválidos.
- Integración PostgreSQL 18/pgvector: solicitud/autorización/CSRF, concurrencia/versionado, lifecycle, chunks `vector(384)`, HNSW, outbox, rollback atómico, proveedor no disponible, recuperación de claim y descarte de resultado tardío.
- Migración: upgrade desde `OLA5-AI-001a`, preservación de fuentes/eventos históricos y constraints nuevos.
- Contrato: OpenAPI 8.0 válido, operación única nueva, body/respuesta cerrados y hash inmutable de OpenAPI 7.2.
- Preflight externo: artefactos fijados, dimensión/norma/determinismo, offsets Unicode, recuperación semántica básica, latencia y throughput; pesos fuera del repositorio y sin artefactos publicados.
- E2E frontend posterior: aceptación `202`, estados/refetch, errores `403|409|503`, bloqueo de doble envío y backend/PostgreSQL reales; no simular autorización como única evidencia.

## OLA5-AI-003a

- Integración PostgreSQL 18/pgvector real: autorización `401|403`, CSRF, validación cerrada de body/tipos/límites/tokens, solo fuentes `READY` del modelo fijado, ranking y desempates deterministas, comparación con ranking exacto calculado por fuerza bruta, resultados vacíos, `503` sin mutaciones y lectura sin outbox.
- Unitarias: conversión de distancia a similitud acotada y rechazo de valores no finitos.
- Contrato: OpenAPI 8.1 válido, operación única nueva, request/response cerrados, compatibilidad `oasdiff` y hash congelado de 8.0.
- Rendimiento: `AiRetrievalBenchmarkTest` se ejecuta solo con `-Djaxon.ai.retrieval.benchmark=true` y paths externos del modelo; mide p50/p95 HTTP con modelo real, PostgreSQL/pgvector y 20 000 chunks predeterminados.
- Frontend: parser cerrado, estados de carga/vacío/error/resultados, caché por usuario y E2E Chromium multirepo con backend, PostgreSQL y modelo reales.

## OLA5-AI-006a

- Integración PostgreSQL 18/pgvector real: `401|403|404`, CSRF, body cerrado, `READY|FAILED -> DISABLED`, `409` para `PENDING|INDEXING`/versión obsoleta, no-op vigente, concurrencia con un único evento, rollback del outbox sin estado parcial, exclusión inmediata del retrieval, evidencia intacta y rechazo de reindexación posterior.
- Migración: upgrade desde el estado de `OLA5-AI-003a`, eventos históricos preservados y restricciones que aceptan solo el payload cerrado del nuevo evento.
- Contrato: OpenAPI 8.2 válido, operación única nueva, request cerrado, compatibilidad `oasdiff` y hash congelado de 8.1.
- Frontend: parser, visibilidad por estado, confirmación, versión enviada, errores, reconsulta, ausencia de acciones en `DISABLED` y E2E Chromium multirepo real registrar-indexar-recuperar-deshabilitar-recuperar.

## OLA6-AUD-001a

- Unitarias: catálogo cerrado de `AuditFact` y backoff exponencial con jitter y tope.
- Migración: upgrade desde `V20261125000000`, restricciones de entregas por consumidor, cascada y catálogo/metadata de Audit.
- Integración PostgreSQL 18/pgvector real: entrega exacta, idempotencia, conflicto sin sobrescritura hasta `FAILED`, indisponibilidad sin afectar la deshabilitación y recuperación, aislamiento de un evento defectuoso, concurrencia de tres dispatchers, append-only (`UPDATE`, `DELETE`, `TRUNCATE`) y propagación obligatoria del recorder.
- API: `401`, `403` para `SYSTEM_ADMIN` y otros roles sin `AUDITOR`, filtros, paginación, orden y `422` sin eco.
- Arquitectura y contrato: `ModularityTests`, prueba estática de ownership SQL y OpenAPI 8.3 con 8.2 congelado.
- Frontend: Vitest/RTL de visibilidad, parser, filtros, paginación y caché; E2E Chromium multirepo deshabilitar -> materializar -> consultar como `AUDITOR`, con `403` para `SYSTEM_ADMIN`.

## OLA6-AUD-001b

- Unitarias: catálogo `HUMAN_USER_CREATED` 2.0 cerrado, orden/separador/duplicados/roles desconocidos y backoff con jitter/tope.
- Migración PostgreSQL 18/pgvector: upgrade con 1.0 histórico preservado, constraints 2.0, entrega Identity, cascada y ampliación Audit sin regresión de `AI_SOURCE_DISABLED`.
- Integración: alta -> outbox 2.0 -> Audit exacto, 1.0 ignorado, redelivery idempotente, conflicto hasta `FAILED` en 10 intentos, indisponibilidad sin revertir usuario, aislamiento de evento defectuoso y tres dispatchers concurrentes.
- Arquitectura/regresión: ModularityTests, AuditOwnershipTest, suite AI Audit, `clean verify`, OpenAPI 8.3 byte a byte, documentación, Gitleaks, backup/restore y clasificador `APPLICATION`.

## OLA6-AUD-001c

- Integración: login/logout real -> outbox 2.0 -> Audit exacto `{}`; 1.0 ignorado; `HUMAN_LOGIN_FAILED` excluido; redelivery, conflicto, indisponibilidad, aislamiento y tres dispatchers conservados por la suite compartida.
- Migración PostgreSQL 18/pgvector: upgrade con eventos 1.0 byte a byte, constraints Identity 2.0 y catálogo Audit ampliado sin regresión de `AI_SOURCE_DISABLED` ni `HUMAN_USER_CREATED`.
- Catálogo/seguridad: combinaciones cerradas, UUIDv4, `resourceVersion=null`, metadata vacía y rechazo de claves prohibidas/adicionales.
- Regresión: Identity/Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `clean verify` Java 25, OpenAPI 8.3 byte a byte, documentación, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001d

- Productores: fallo desconocido/conocido/deshabilitado 2.0, actor nulo o UUIDv4, cinco eventos antes de `429`, cero filas adicionales después; claves Agent/Executor 2.0 atómicas, no-op sin duplicado y rechazos sin evento.
- Dispatcher/Audit: materialización exacta de los tres tipos, datos prohibidos rechazados, 1.0 ignorado, redelivery, conflicto, indisponibilidad, aislamiento y concurrencia compartidos.
- Migración PostgreSQL 18/pgvector: upgrade desde `20261128010000`, histórico 1.0 byte a byte y catálogos Identity/Audit cerrados sin regresión de los cuatro tipos ya auditados.
- Regresión: suites Identity, Agent, Discovery y Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `clean verify` Java 25, OpenAPI 8.3 byte a byte, documentación, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001e

- Productores: alta de activo, pre-registro Agent y activación efectiva emiten 2.0 atómico con versiones `0|0|1`; la activación idempotente conserva un solo evento.
- Dispatcher/Audit: entregas Inventory y Collector separadas, 1.0 ignorado, redelivery, conflicto hasta 10 intentos, indisponibilidad, concurrencia `SKIP LOCKED`, payload vacío y catálogo cerrado.
- Migración PostgreSQL 18/pgvector: upgrade desde `20261129010000`, filas 1.0 byte a byte, tablas de entrega propias y catálogo Audit ampliado sin regresión AI/Identity ni append-only.
- Regresión: Inventory, Agent, AI, Identity y Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `clean verify` Java 25, OpenAPI 8.3 byte a byte, documentación, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001f

- Productores: scope created/status changed y executor pre-registered/activated emiten 2.0 atómico con recurso, versión, actor, correlación y payload exactos; no-ops no duplican.
- Dispatcher/Audit: entrega Discovery única, 1.0 ignorado, redelivery, conflicto hasta 10 intentos, indisponibilidad, aislamiento, `SKIP LOCKED`, metadata cerrada y regresión de productores previos.
- Migración PostgreSQL 18/pgvector: upgrade desde `20261130020000`, filas 1.0 byte a byte, tabla de entrega propia y catálogo Audit ampliado mediante exactamente dos migraciones.
- Regresión: Discovery, AI, Identity, Inventory, Collector y Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `clean verify` Java 25, OpenAPI 8.3 byte a byte, documentación, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001g

- Productores: creación/despacho real emiten 2.0 con actor/correlación humanos originales; completion `SUCCEEDED|FAILED` emite actor executor, versión `2`, resultado `SUCCESS|FAILED` y `{}`.
- Atomicidad/silencio: fallo forzado del outbox revierte aceptación terminal; duplicados, conflictos, rechazos, finales tardíos y reprocesamientos no emiten otro completion.
- Dispatcher/Audit: la entrega Discovery existente reclama solo siete tipos 2.0, materializa metadata cerrada, conserva redelivery, conflicto, 10 intentos, indisponibilidad, aislamiento y `SKIP LOCKED`; 1.0 queda excluido.
- Migración PostgreSQL 18/pgvector: upgrade desde `20261130040000`, filas Job 1.0 comparadas antes/después sin mutación y catálogos Discovery/Audit ampliados mediante exactamente dos migraciones.
- Regresión: Discovery/Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `DatabaseBootstrapIntegrationTest`, `clean verify` Java 25, OpenAPI 8.3 y Job/Result 1.0 byte a byte, documentación normal/estricta, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001h

- Productores: ignore, incorporación a activo existente y creación de activo prueban envelope/metadata exactos, ambos hechos independientes, no-op, versión obsoleta, activo inválido/inactivo, concurrencia y rollback.
- Dispatcher/Audit: la entrega Discovery existente reclama solo nueve tipos 2.0, valida UUID/metadata cerrados y conserva redelivery, conflicto, 10 intentos, indisponibilidad, aislamiento y `SKIP LOCKED`; 1.0 queda excluido.
- Migración PostgreSQL 18/pgvector: upgrade desde `20261130060000`, ambos eventos históricos 1.0 comparados antes/después y catálogos Discovery/Audit ampliados mediante exactamente las dos migraciones autorizadas.
- Regresión: Discovery/Audit dirigidas, `ModularityTests`, `AuditOwnershipTest`, `DatabaseBootstrapIntegrationTest`, `clean verify` Java 25, contratos byte a byte, documentación normal/estricta, whitespace, Gitleaks y clasificador `APPLICATION`.

## OLA6-AUD-001i

- Productores: creación, comentarios requester/técnico, claim, prioridad, estados no terminales y lifecycle terminal emiten 2.0 con envelope, versiones y metadata exactos; no-ops, denegaciones, conflictos y rollback permanecen silenciosos.
- Dispatcher/Audit: Support reclama solo ocho tipos 2.0 y cubre redelivery idempotente, conflicto, 10 intentos, `FAILED`, indisponibilidad, rollback, aislamiento y `SKIP LOCKED`; 1.0 queda excluido.
- Migración PostgreSQL 18/pgvector: upgrade con filas Ticket 1.0 intactas; constraints rechazan extras, metadata incompleta, UUID no canónico y transiciones/combinaciones inválidas; instalación limpia y bootstrap incluyen `support.outbox_deliveries`.
- Regresión: Support/Audit dirigidas, ownership, modularidad, `clean verify` Java 25, OpenAPI 8.3 y Agent/Discovery byte a byte, documentación, whitespace, Gitleaks y clasificador `APPLICATION`.
