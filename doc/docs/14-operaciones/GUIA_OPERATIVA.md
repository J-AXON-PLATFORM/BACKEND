# Guía operativa

> **Clasificación:** CONDICIONAL

## Propósito

Permitir operar el sistema de forma repetible sin depender de conocimiento oral.

## Contenido

- propietarios;
- dependencias;
- arranque;
- shutdown;
- health;
- despliegue;
- rollback;
- backups;
- restauración;
- tareas programadas;
- rotación de secretos;
- fallos frecuentes;
- diagnóstico;
- escalamiento.

## Procedimiento

Cada runbook debe incluir:

```text
Objetivo:
Precondiciones:
Riesgos:
Pasos:
Verificación:
Rollback:
Cuándo detenerse:
Escalamiento:
```

## Seguridad

No almacenar credenciales reales en el runbook.

## Baseline lógica PostgreSQL

La baseline ejecutable se define en [Backup and Recovery](../05-datos/COPIAS_SEGURIDAD_RECUPERACION.md) y utiliza:

- `scripts/backup-postgresql.sh` para crear el dump custom y su manifest;
- `scripts/restore-postgresql.sh` para validar y restaurar en una base vacía;
- `scripts/verify-postgresql-backup-restore.sh` para la prueba aislada Local/CI.

Precondiciones: clientes PostgreSQL 18, variables `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER` y secreto inyectado fuera de argumentos/logs; identificador opaco de instancia; directorio absoluto, canónico, privado y ajeno al workspace; fuente sin migraciones concurrentes durante el dump. El restore requiere archivos `0600`, una identidad destino distinta de la origen y una base vacía, aislada y sin conexiones o escritores concurrentes.

Después del restore se deben comprobar los ocho schemas, pgvector, historial Flyway y datos esperados. Detener y escalar ante manifest, checksum, tamaño, versión o permisos inválidos, destino no vacío o cualquier fallo de `pg_dump`/`pg_restore`; no ignorar ni publicar artefactos parciales. Este procedimiento no es un runbook Production completo y no sustituye base backup + WAL/PITR, cifrado, almacenamiento independiente o pruebas representativas de RPO/RTO.

## Indexación AI Local/Test

`OLA5-AI-002a` requiere fuera del repositorio el ONNX y tokenizer exactos mediante `JAXON_AI_EMBEDDING_MODEL_PATH` y `JAXON_AI_EMBEDDING_TOKENIZER_PATH`. El arranque verifica archivo regular no symlink, tamaño y SHA-256 streaming; cualquier ausencia o diferencia deja el proveedor no disponible y las solicitudes válidas responden `503` sin crear job. `JAXON_AI_INDEXING_CONCURRENCY`, `JAXON_AI_INDEXING_BATCH_SIZE`, `JAXON_AI_INDEXING_CLAIM_TIMEOUT` y `JAXON_AI_INDEXING_POLL_INTERVAL` conservan defaults acotados del código. No colocar pesos en Git, imagen, logs o artefactos publicados. Production y su dimensionamiento siguen fuera de alcance.

`OLA5-AI-003a` reutiliza el mismo proveedor: si no está disponible, `POST /api/v1/ai/retrieval` responde `503` `AI_RETRIEVAL_UNAVAILABLE` sin efectos. La métrica `jaxon.ai.retrieval.duration` se etiqueta solo con `outcome` (`hit|empty|rejected|unavailable|failed`); `rejected` cubre solo consultas sin tokens o sobre el límite de tokens, porque los `400|422` del body se resuelven antes del servicio y no se miden. La medición local se reproduce con `./mvnw -Dtest=AiRetrievalBenchmarkTest -Djaxon.ai.retrieval.benchmark=true -Djaxon.ai.embedding.model-path=<onnx> -Djaxon.ai.embedding.tokenizer-path=<tokenizer> test`; en el host local de 12 CPU se obtuvo p95 HTTP de 45,4 ms con 20 000 chunks y 137,8 ms con 100 000 chunks. No son cifras Production.
