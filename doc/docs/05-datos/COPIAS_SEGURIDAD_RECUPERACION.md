# Backup and Recovery

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.  
> **Estado:** política Production aprobada; baseline lógica PostgreSQL verificada en entorno aislado; RPO/RTO y recuperación Production siguen sin verificar.

## Propósito

Definir backups, restauración, RPO y RTO para J-AXON Production sin confundir backup con archivo histórico de negocio.

## PostgreSQL Production

- estrategia: base backup periódico + archivado continuo de WAL/PITR;
- herramienta concreta: `POR_DECIDIR` según infraestructura;
- no depender únicamente de un dump nocturno para cumplir el RPO;
- migraciones/upgrades con riesgo de datos deben enlazar plan de recuperación.

## Baseline lógica implementada

`TEC-OPS-005a` y `TEC-OPS-006a` proporcionan una regresión técnica reproducible para Local/CI:

- `scripts/backup-postgresql.sh` genera un dump lógico custom mediante PostgreSQL 18 `pg_dump`, sin ownership ni privilegios, con temporales privados, permisos `0600`, publicación mediante rename y rechazo de sobrescritura;
- antes de publicar valida PostgreSQL 18, pgvector 0.8.6, los ocho schemas aprobados y el historial Flyway;
- el manifest cerrado `1.0` registra fecha UTC, identificador opaco de origen, versiones, formato, tamaño, SHA-256, versiones Flyway, schemas, pgvector, duración y resultado, sin hostname ni credenciales;
- `scripts/restore-postgresql.sh` exige archivos regulares `0600` en un directorio privado, congela copias privadas para la validación, requiere manifest íntegro, tamaño y SHA-256 coincidentes, identificadores de origen/destino distintos y una base destino vacía y aislada sin otros escritores;
- la restauración usa `pg_restore --exit-on-error --single-transaction --no-owner --no-privileges`, por lo que un fallo revierte la transacción;
- `scripts/verify-postgresql-backup-restore.sh` crea origen y destino PostgreSQL efímeros aislados, aplica Flyway, incorpora datos sintéticos, verifica la restauración y ejecuta casos negativos de integridad, formato, permisos, sobrescritura, destino y fallos de herramientas.

Esta baseline no implementa ni sustituye base backup, archivado WAL/PITR, cifrado, almacenamiento externo, scheduling, rotación, retención, backup documental, operación Production o demostración de RPO/RTO.

## Documentos permanentes

- origen: filesystem persistente detrás de `DocumentStorage`;
- backup incremental/copia recuperable con intervalo máximo de **1 hora** en Production;
- la copia debe preservar metadata necesaria y permitir verificar SHA-256 tras restauración.

## Dominio de fallo

- al menos una copia de backup debe estar fuera del dominio de fallo primario del servidor/storage Productivo;
- no es obligatorio usar nube: puede ser NAS/servidor/appliance/medio independiente según instalación;
- la aplicación normal no debe tener capacidad irrestricta para borrar backups.

## Cifrado

- todos los backups de Production se cifran en reposo;
- transferencia del backup protegida;
- claves separadas del contenido respaldado y con acceso restringido;
- no almacenar secretos expuestos en texto plano dentro de artefactos de backup.

## Retención de backups

- diarios: **14 días**;
- semanales: **8 semanas**;
- mensuales: **12 meses**.

La retención de backup no sustituye la retención funcional de auditoría/tickets/documentos.

## Pruebas de restauración

- cada backup: comprobar finalización y evidencia/manifiesto/checks aplicables;
- mensual: restaurar PostgreSQL en entorno aislado + muestra representativa de documentos y validar SHA-256;
- trimestral: restauración integral representativa PostgreSQL + storage documental, comprobando referencias DB->archivo e integridad;
- antes de readiness comercial: al menos una restauración integral exitosa y documentada.

El gate sintético de `TEC-OPS-006a` detecta regresiones del procedimiento lógico, pero no equivale al restore mensual representativo ni a la prueba integral trimestral de PostgreSQL y documentos.

Registrar fecha, recovery point, tiempo empleado, errores, objetos verificados y acciones correctivas.

## Objetivos iniciales

- **RPO <= 1 hora** para el conjunto de datos core Production (DB + documentos permanentes);
- **RTO <= 8 horas** para recuperar capacidades core: Identity, Inventory, Collector, Support, PostgreSQL y storage documental;
- IA/n8n/observabilidad pueden recuperarse después si el core opera de forma segura;
- estos valores son objetivos internos iniciales, no SLA contractual, y solo se consideran cumplidos si pruebas representativas lo demuestran.

## Restauración y borrados

Tras restaurar desde un backup que todavía contenga datos ya eliminados legítimamente del sistema primario, ejecutar reconciliación de retención/borrado antes de reabrir el sistema cuando corresponda, para evitar resucitar información fuera de política.
