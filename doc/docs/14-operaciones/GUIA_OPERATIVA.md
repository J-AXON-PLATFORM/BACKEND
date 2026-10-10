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
