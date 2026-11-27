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
| `ai.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega `AUDIT` de disabled 1.0 y cuatro hechos del lifecycle AI 2.0: `PENDING|DELIVERED|FAILED`, intentos 0..10, próximo intento y error cerrado | `AC-JAXON-092`, `AC-JAXON-102` / migración `V20261126000000` |
| `identity.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega `AUDIT` de los seis eventos Identity allowlisted exclusivamente 2.0: `PENDING|DELIVERED|FAILED`, 0..10 intentos, próximo intento y error cerrado | `AC-JAXON-093`, `AC-JAXON-095`, `AC-JAXON-096` / migraciones `V20261127000000`, `V20261128000000`, `V20261129000000` |
| `inventory.outbox_deliveries` / `collector.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entregas `AUDIT` propietarias de sus eventos allowlisted 2.0: `PENDING|DELIVERED|FAILED`, 0..10 intentos, próximo intento y error cerrado | `AC-JAXON-097` / migraciones `V20261130000000`, `V20261130010000` |
| `discovery.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega `AUDIT` propietaria de nueve tipos Discovery 2.0: `PENDING|DELIVERED|FAILED`, 0..10 intentos, próximo intento y error cerrado | `AC-JAXON-098`..`AC-JAXON-100` / migración `V20261130030000` |
| `support.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega `AUDIT` propietaria de ocho tipos Ticket 2.0: `PENDING|DELIVERED|FAILED`, 0..10 intentos, próximo intento y error cerrado | `AC-JAXON-101` / migración `V20261130090000` |
| `audit_reporting.audit_events` | envelope + `metadata` | uuid + varchar + jsonb | sí | no | append-only e idempotente; catálogo cerrado cinco eventos AI, seis Identity, tres Inventory/Collector, nueve Discovery, ocho Support y `REPORT_EXPORTED` 1.0 con metadata `format|reportType|rowCount` | `AC-JAXON-056`, `AC-JAXON-092`..`AC-JAXON-103` / migraciones `V20261126010000`..`V20261201000000` |
| `identity.outbox_events` | `HUMAN_USER_CREATED` payload/version | varchar + jsonb | sí | no | 1.0 histórico conserva solo `status=ACTIVE`; altas futuras emiten exclusivamente 2.0 con `status=ACTIVE` y roles no vacíos, únicos, ordenados y separados por `|` | `AC-JAXON-093` / migración `V20261127000000` |
| `identity.outbox_events` | login/logout exitoso payload/version | varchar + jsonb | sí | no | 1.0 histórico inmutable y excluido del dispatcher; futuros éxitos emiten 2.0 con payload exactamente `{}`; login usa `AUTHENTICATION_ATTEMPT` y logout `HUMAN_SESSION` | `AC-JAXON-095` / migración `V20261128000000` |
| `identity.outbox_events` | login fallido y claves técnicas payload/version | varchar + jsonb | sí | no | 1.0 histórico inmutable y excluido; futuros fallos emiten 2.0 con reason cerrado y futuros registros efectivos de clave emiten 2.0 con `{}`; `429` y no-ops no emiten | `AC-JAXON-096` / migración `V20261129000000` |
| `inventory.outbox_events` / `collector.outbox_events` | evento/version/recurso | varchar + bigint | sí | no | 1.0 histórico inmutable y excluido; futuros alta de activo, pre-registro y activación Agent emiten 2.0 con versiones de recurso `0|0|1` y `{}` | `AC-JAXON-097` / migraciones `V20261130000000`, `V20261130010000` |
| `discovery.outbox_events` | evento/version/recurso/payload | varchar + bigint + jsonb | sí | no | 1.0 histórico inmutable y excluido; cuatro hechos administrativos, tres de lifecycle Job y dos de revisión humana futuros emiten 2.0 con actor, versión, resultado y metadata cerrados | `AC-JAXON-098`..`AC-JAXON-100` / migraciones `V20261130030000`, `V20261130050000`, `V20261130070000` |
| `support.outbox_events` | evento/version/recurso/payload | varchar + bigint + jsonb | sí | no | 1.0 histórico inmutable y excluido; futuras emisiones de los ocho hechos Ticket existentes usan 2.0 con actor, versión, resultado, metadata y transiciones cerrados | `AC-JAXON-101` / migración `V20261130090000` |
| `ai.outbox_events` | `event_type`/`payload` | varchar + jsonb | sí | no | 1.0 histórico inmutable; futuras emisiones de registered/requested/indexed/failed usan 2.0 con actor/version/resultado/estado cerrados; disabled conserva 1.0 | `AC-JAXON-091`, `AC-JAXON-102` / migraciones `V20261125000000`, `V20261130110000` |
| `ai.source_indexing_jobs` | identidad/lifecycle | UUID + timestamps + estado | sí | interno | un único job por fuente; `PENDING|CLAIMED|SUCCEEDED|FAILED`; claim expirado no se reintenta | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | trazabilidad | texto/hash/rango tokens | sí | sí | ordinal único por fuente, máximo 480 tokens, referencia técnica exacta a fuente/modelo/tokenizer/chunker | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | `embedding` | `vector(384)` | sí | sí | normalizado L2, modelo/revisión fijados, índice HNSW coseno; no es fuente de verdad | `AC-JAXON-089` / migración Flyway |
