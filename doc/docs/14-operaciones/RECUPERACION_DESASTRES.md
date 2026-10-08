# Recuperación ante desastres

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Recuperar capacidades críticas ante pérdida severa de infraestructura o datos.

## Objetivos iniciales Production

- **RPO <= 1 hora** para PostgreSQL + documentos permanentes;
- **RTO <= 8 horas** para el core;
- servicios core para RTO: Identity, Inventory, Collector, Support, PostgreSQL y `DocumentStorage`;
- IA, n8n y observabilidad pueden permanecer degradados durante recuperación si no bloquean el core;
- los objetivos no son SLA contractual y deben validarse mediante drills/restores reales.

## Prioridad de recuperación

1. infraestructura mínima segura, secretos/certificados necesarios y almacenamiento;
2. PostgreSQL y consistencia de migraciones;
3. `DocumentStorage` y verificación SHA-256;
4. Identity/Inventory/Collector/Support;
5. SFTPGo/flujo Agent si no quedó disponible junto al core;
6. Automation/n8n, IA/RAG y observabilidad según dependencia/impacto.

La secuencia exacta puede variar por escenario, preservando consistencia y seguridad.

## Escenarios

Evaluar como mínimo:

- pérdida/corrupción de PostgreSQL;
- pérdida de filesystem documental;
- pérdida del host primario;
- borrado accidental;
- credenciales comprometidas;
- backup no recuperable;
- SFTPGo/servicio core indisponible;
- dependencia externa IA/n8n indisponible sin confundirla con desastre del core.

## Backups

Ver `docs/05-datos/COPIAS_SEGURIDAD_RECUPERACION.md`.

- PostgreSQL: base backup + WAL/PITR;
- documentos: copia recuperable <=1 h;
- al menos una copia fuera del dominio de fallo primario;
- backups Production cifrados;
- retención 14 diarios/8 semanales/12 mensuales.

## Procedimiento por escenario

Documentar:

- detección;
- autoridad para declarar;
- recovery point elegido;
- restauración de datos;
- secretos/certificados;
- DNS/tráfico;
- verificación de referencias DB->documento y SHA-256;
- reconciliación de retención/borrados si el backup contiene datos ya eliminados;
- post-restore smoke/E2E apropiado;
- comunicación y retorno a normalidad.

## Drills y restore tests

- mensual: PostgreSQL + muestra documental en entorno aislado;
- trimestral: restore integral representativo DB + documentos;
- frecuencia de drill operativo completo más allá de restore: `POR_DECIDIR` según instalación/operación;
- antes de readiness comercial: al menos un restore integral exitoso.

RTO/RPO no se consideran verificados hasta probar recuperación representativa.

## Evidencia

Registrar fecha, versión/configuración, recovery point, resultado, tiempo real, archivos/hash verificados, fallos y acciones de mejora.
