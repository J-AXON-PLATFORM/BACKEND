# Diccionario de datos


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Definir campos, tipos, sensibilidad y reglas de datos.


| Entity | Field | Tipo | Required | Sensitive | Rule | Source |
|---|---|---|---|---|---|---|
| `ai.document_sources` | `status` | varchar(16) | sí | no | `PENDING|INDEXING|READY|FAILED|DISABLED`; mutaciones autorizadas por lifecycle AI; `READY|FAILED -> DISABLED` es terminal en v1 y conserva original, chunks y embeddings | `AC-JAXON-089`, `AC-JAXON-091` / migración Flyway |
| `ai.outbox_events` | `event_type`/`payload` | varchar + jsonb | sí | no | eventos AI cerrados; `AI_SOURCE_DISABLED` admite solo `{"previousStatus":"READY|FAILED","newStatus":"DISABLED"}` y los cuatro eventos previos conservan `{"status":...}` | `AC-JAXON-091` / migración `V20261125000000` |
| `ai.source_indexing_jobs` | identidad/lifecycle | UUID + timestamps + estado | sí | interno | un único job por fuente; `PENDING|CLAIMED|SUCCEEDED|FAILED`; claim expirado no se reintenta | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | trazabilidad | texto/hash/rango tokens | sí | sí | ordinal único por fuente, máximo 480 tokens, referencia técnica exacta a fuente/modelo/tokenizer/chunker | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | `embedding` | `vector(384)` | sí | sí | normalizado L2, modelo/revisión fijados, índice HNSW coseno; no es fuente de verdad | `AC-JAXON-089` / migración Flyway |
