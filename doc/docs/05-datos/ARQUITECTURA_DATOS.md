# Arquitectura de datos

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Definir dominios, ownership, almacenamiento, movimiento y ciclo de vida.

## Instancia J-AXON -- mapa de almacenamiento aprobado

- PostgreSQL 18.x conserva datos estructurados y metadata, con un schema por módulo.
- Archivos/binarios relevantes permanecen fuera de PostgreSQL y se acceden mediante una abstracción de backend equivalente a `DocumentStorage`.
- Implementación v1 de `DocumentStorage`: filesystem persistente. El medio físico concreto (disco/volumen/NAS) se decide con infraestructura; cloud/object storage/S3-compatible sigue futuro.
- El buzón operacional de SFTPGo está separado del storage documental permanente.
- Ownership de documento y metadata permanece en el módulo dueño: Support para adjuntos de tickets, AI para fuentes RAG, etc.
- Los nombres/rutas físicas no se convierten en contratos de dominio; usar identificadores opacos y metadata persistida.
- SHA-256 es el hash canónico de integridad de archivos relevantes.
- Fuente RAG, chunks y embeddings comparten ciclo de vida; borrar/desautorizar una fuente debe retirar sus derivados recuperables.
- Discovery produce observaciones temporales; incorporar a Inventory es una operación explícita y no transfiere ownership de tablas.
- En `OLA4-DIS-002a`, Discovery posee `discovery_executors`, la asociación N:M `discovery_executor_scopes` y su outbox; Identity posee `technical_identities`, public key, huella y outbox de identidad. No existen FK cross-schema ni acceso directo de Discovery a tablas Identity: el alta coordinada usa contrato público in-process y una única transacción PostgreSQL.
- En `OLA4-DIS-003a`, Discovery añade sobre su agregado la transición `PENDING`/versión `0` a `ACTIVE`/versión `1`, username SFTP determinista y timestamp de activación, más el evento propio `DISCOVERY_EXECUTOR_ACTIVATED`. La clave se consulta mediante el contrato público Identity; SFTPGo se reconcilia antes de la transacción PostgreSQL, de forma idempotente y estricta, pero no forma una transacción atómica cross-system.
- En `OLA4-DIS-004a`, Discovery posee `discovery_jobs` y sus eventos. La FK compuesta garantiza asociación executor/scope, un índice parcial impide más de un job no terminal por executor y los checks fijan perfil, estados, TTL y coherencia estado/versión/timestamps. La publicación filesystem ocurre después del alta transaccional; una segunda transacción confirma `DISPATCHED`, por lo que los `PENDING` se reconcilian por identidad estable sin escribir datos de otros módulos.
- En `OLA4-DIS-006a`, Discovery posee `discovery_result_ingestions`, `accepted_discovery_results` y `discovered_devices`. Ledger, resultado aceptado, transición terminal, dispositivos y outbox se confirman en una transacción; FK compuesta impide asociar un dispositivo a otro Job o a un resultado `FAILED`. Inventory no recibe escrituras y el archivo SFTP retenido no sustituye la evidencia PostgreSQL.
- En `OLA4-DIS-007a`, Discovery amplía su propio `discovered_devices` con `UNREVIEWED|IGNORED`, versión y revisión coherentes. La identidad humana revisora se conserva como UUID estable sin FK cross-schema y no se expone por API; dispositivo, metadatos de revisión y outbox se confirman en una transacción. Inventory permanece intacto.
- En `OLA4-DIS-008a`, Discovery añade `INCORPORATED` e `inventory_asset_id` nullable a su propia tabla, sin FK cross-schema ni unicidad entre Jobs. La existencia y estado `ACTIVE` se validan y protegen con bloqueo compartido mediante la frontera pública Inventory antes de confirmar dispositivo, revisión, vínculo y outbox Discovery; Inventory no se muta.
- En `OLA4-DIS-008b`, la estructura persistente de `008a` se reutiliza sin migración. Discovery bloquea primero su dispositivo e invoca `AssetCreator`; Inventory crea en sus tablas el activo `ACTIVE` y `ASSET_CREATED`, y Discovery conserva vínculo, revisión y evento propio. La única transacción PostgreSQL abarca ambas operaciones públicas sin escritura cross-module directa.
- En `OLA5-AI-001a`, AI posee `document_sources` y `outbox_events`; PostgreSQL conserva metadata, hash, clave opaca y estado `PENDING`, mientras el original permanece fuera de DB bajo `DocumentStorage`. El archivo se publica y verifica antes de la transacción metadata/outbox. Un fallo DB activa retirada compensatoria y nunca publica metadata; una compensación también fallida solo puede dejar un huérfano no publicado, cuya reconciliación queda fuera del corte.
- En `OLA6-AUD-001b`, Identity posee `identity.outbox_deliveries` y despacha únicamente sus `HUMAN_USER_CREATED` 2.0; Audit posee `audit_reporting.audit_events` y amplía su catálogo mediante `AuditFactRecorder`. No existe SQL cross-schema entre módulos. El usuario y su evento 2.0 se confirman atómicamente; la materialización posterior conserva idempotencia y no altera la operación ya confirmada.
- En `OLA6-AUD-001c`, el mismo dispatcher y tabla de entregas Identity incluyen solo login/logout exitosos 2.0. La sesión y su evento se confirman atómicamente en login; la eliminación de sesión y su evento se confirman atómicamente en logout. Los eventos 1.0 no se mutan ni reclaman.
- En `OLA6-AUD-001d`, esa infraestructura incluye solo login fallido y registros efectivos de claves Agent/Executor 2.0. Intento/evento o clave/evento se confirman atómicamente; `429`, no-ops y rechazos no crean hechos adicionales. Los eventos 1.0 no se mutan ni reclaman.
- En `OLA6-AUD-001e`, Inventory y Collector poseen tablas de entrega y dispatchers separados para sus futuros `ASSET_CREATED`, `AGENT_PRE_REGISTERED` y `AGENT_ACTIVATED` 2.0. Cada agregado y evento se confirma atómicamente; Audit recibe solo `AuditFactRecorder`, sin SQL cross-module. 1.0 permanece byte a byte y sin claim.
- En `OLA6-AUD-001f`, Discovery posee una tabla de entrega y un dispatcher para cuatro hechos futuros 2.0 del lifecycle administrativo inicial. Cada agregado y evento se confirma atómicamente; Audit recibe solo `AuditFactRecorder`, sin SQL cross-module. Los no-ops no emiten y 1.0 permanece byte a byte y sin claim.
- En `OLA6-AUD-001g`, la infraestructura Discovery existente incluye tres hechos futuros 2.0 del Job. Created/dispatched conservan actor y correlación humanos; la transición terminal y completion con actor executor se confirman en una transacción y mapean `SUCCEEDED|FAILED` a `SUCCESS|FAILED`. Duplicados, conflictos, rechazos y finales tardíos no emiten; 1.0 permanece byte a byte y sin claim.

## Consistencia

- Transacciones fuertes dentro del módulo/DB cuando corresponda.
- Flujos SFTP, Automation y Discovery pueden ser eventualmente consistentes y se reconcilian por identidad estable/idempotencia.
- DB metadata y archivo permanente deben alcanzar un estado coherente antes de exponer el documento como disponible.
- Backups de DB y documentos deben poder restaurarse de manera coherente y verificarse por hash.

## Retención aprobada

La fuente canónica es `RETENCION_DATOS.md`. Resumen: Collector 180 días; Discovery no incorporado 30 días; Support 3 años desde cierre; auditoría 5 años; fuentes RAG según autorización/actividad; telemetría de observabilidad tiene política independiente.

## Movimiento

Documentar por flujo:

- APIs/REST;
- eventos internos;
- SFTP Agent y Discovery Executor;
- exports/imports futuros;
- no introducir CDC/ETL/replicación sin necesidad demostrada.

## Escala

Relacionar con `ESCALABILIDAD_DATOS.md` y `docs/04-arquitectura/RENDIMIENTO_CAPACIDAD.md`.

## Calidad

Relacionar con `CALIDAD_LINAJE_DATOS.md`.

## Gobierno

Relacionar con `GOBIERNO_DATOS.md`.

## Seguridad

Clasificación, cifrado, acceso, residencia y privacidad deben enlazar con seguridad. Backups Production se cifran en reposo y al menos una copia reside fuera del dominio de fallo primario.
