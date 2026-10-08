# Backup and Recovery

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.  
> **Estado:** política inicial aprobada; valores deben verificarse mediante restore tests.

## Propósito

Definir backups, restauración, RPO y RTO para J-AXON Production sin confundir backup con archivo histórico de negocio.

## PostgreSQL Production

- estrategia: base backup periódico + archivado continuo de WAL/PITR;
- herramienta concreta: `POR_DECIDIR` según infraestructura;
- no depender únicamente de un dump nocturno para cumplir el RPO;
- migraciones/upgrades con riesgo de datos deben enlazar plan de recuperación.

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

Registrar fecha, recovery point, tiempo empleado, errores, objetos verificados y acciones correctivas.

## Objetivos iniciales

- **RPO <= 1 hora** para el conjunto de datos core Production (DB + documentos permanentes);
- **RTO <= 8 horas** para recuperar capacidades core: Identity, Inventory, Collector, Support, PostgreSQL y storage documental;
- IA/n8n/observabilidad pueden recuperarse después si el core opera de forma segura;
- estos valores son objetivos internos iniciales, no SLA contractual, y solo se consideran cumplidos si pruebas representativas lo demuestran.

## Restauración y borrados

Tras restaurar desde un backup que todavía contenga datos ya eliminados legítimamente del sistema primario, ejecutar reconciliación de retención/borrado antes de reabrir el sistema cuando corresponda, para evitar resucitar información fuera de política.
