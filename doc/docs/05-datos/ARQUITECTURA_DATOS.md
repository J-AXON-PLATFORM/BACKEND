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
