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
