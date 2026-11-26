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
| `ai.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega por consumidor (`AUDIT`) de eventos AI: `PENDING|DELIVERED|FAILED`, intentos 0..10, próximo intento y error cerrado; cascada al purgar el evento | `AC-JAXON-092` / migración `V20261126000000` |
| `identity.outbox_deliveries` | `event_id`/`consumer`/`status` | uuid + varchar | sí | no | entrega `AUDIT` de `HUMAN_USER_CREATED|HUMAN_LOGIN_SUCCEEDED|HUMAN_LOGOUT_SUCCEEDED` exclusivamente 2.0: `PENDING|DELIVERED|FAILED`, 0..10 intentos, próximo intento y error cerrado | `AC-JAXON-093`, `AC-JAXON-095` / migraciones `V20261127000000`, `V20261128000000` |
| `audit_reporting.audit_events` | envelope + `metadata` | uuid + varchar + jsonb | sí | no | append-only e idempotente; catálogo cerrado `AI_SOURCE_DISABLED` 1.0 y tres eventos Identity 2.0; login/logout exigen metadata `{}`, actor UUIDv4 y `resource_version=null` | `AC-JAXON-056`, `AC-JAXON-092`, `AC-JAXON-093`, `AC-JAXON-095` / migraciones `V20261126010000`, `V20261127010000`, `V20261128010000` |
| `identity.outbox_events` | `HUMAN_USER_CREATED` payload/version | varchar + jsonb | sí | no | 1.0 histórico conserva solo `status=ACTIVE`; altas futuras emiten exclusivamente 2.0 con `status=ACTIVE` y roles no vacíos, únicos, ordenados y separados por `|` | `AC-JAXON-093` / migración `V20261127000000` |
| `identity.outbox_events` | login/logout exitoso payload/version | varchar + jsonb | sí | no | 1.0 histórico inmutable y excluido del dispatcher; futuros éxitos emiten 2.0 con payload exactamente `{}`; login usa `AUTHENTICATION_ATTEMPT` y logout `HUMAN_SESSION` | `AC-JAXON-095` / migración `V20261128000000` |
| `ai.outbox_events` | `event_type`/`payload` | varchar + jsonb | sí | no | eventos AI cerrados; `AI_SOURCE_DISABLED` admite solo `{"previousStatus":"READY|FAILED","newStatus":"DISABLED"}` y los cuatro eventos previos conservan `{"status":...}` | `AC-JAXON-091` / migración `V20261125000000` |
| `ai.source_indexing_jobs` | identidad/lifecycle | UUID + timestamps + estado | sí | interno | un único job por fuente; `PENDING|CLAIMED|SUCCEEDED|FAILED`; claim expirado no se reintenta | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | trazabilidad | texto/hash/rango tokens | sí | sí | ordinal único por fuente, máximo 480 tokens, referencia técnica exacta a fuente/modelo/tokenizer/chunker | `AC-JAXON-089` / migración Flyway |
| `ai.source_chunks` | `embedding` | `vector(384)` | sí | sí | normalizado L2, modelo/revisión fijados, índice HNSW coseno; no es fuente de verdad | `AC-JAXON-089` / migración Flyway |
