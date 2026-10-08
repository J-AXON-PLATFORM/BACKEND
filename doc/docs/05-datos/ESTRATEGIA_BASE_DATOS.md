# Estrategia de base de datos

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.


<!-- JAXON-CKP004:START -->
## Refinamiento aprobado hasta CKP-JAXON-005

- PostgreSQL **18.x** por entorno; pgvector **0.8.x** estable.
- Ocho schemas por módulo; `public` sin tablas de negocio.
- Una base por entorno y v1 single-tenant por instalación.
- PostgreSQL real en integración/Testcontainers; no H2 como sustituto de compatibilidad.
- IDs técnicos siguen estables pero su tipo exacto continúa abierto.
- pgvector usa **HNSW** como índice vectorial inicial; parámetros finos quedan sujetos a pruebas.
- Binarios fuera de PostgreSQL mediante `DocumentStorage`; v1 filesystem persistente, medio físico concreto futuro.
- SHA-256 para integridad de archivos relevantes.
- Retenciones, backups, restore tests y RPO/RTO concretados en Lote 41.
<!-- JAXON-CKP004:END -->

## Estrategia aprobada

- PostgreSQL por entorno con schemas `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation`, `audit_reporting`.
- Ownership por módulo; FKs normalmente dentro de módulo; evitar FKs cross-module y usar IDs estables + contratos.
- pgvector para RAG; vector index no es source-of-truth.
- Base nueva para reinicio; no borrar/importar legado automáticamente.
- Files/binaries fuera de tablas; DB guarda metadata/referencia; acceso mediante `DocumentStorage`, con filesystem persistente como implementación v1. Medio físico definitivo y futuro object storage/cloud siguen abiertos.
- Índices desde restricciones/queries reales y medición; pgvector usa HNSW inicialmente y sus parámetros finos se ajustan mediante evaluación.
- Retención por categorías según `RETENCION_DATOS.md`; no telemetría infinita por defecto.
- Production: base backup + WAL/PITR, backup documental como máximo cada 1 h, restore tests mensuales/integrales trimestrales; RPO <=1 h y RTO <=8 h iniciales, sujetos a verificación.
- Integridad de archivos relevantes mediante SHA-256.
- v1 single-tenant por instalación; no hardcodear datos institucionales; SaaS multi-tenant compartido es futuro.
- Validación de calidad antes de estado oficial; dato externo inválido != error del sistema.
- Sin replicación/partitioning/CDC/ETL inicial sin evidencia.

---
> **Clasificación:** CONDICIONAL

## Propósito

Elegir y operar persistencia según necesidades reales.

## Antes de elegir tecnología

Definir:

- modelo de acceso;
- relaciones;
- transacciones;
- consistencia;
- volumen;
- crecimiento;
- latencia;
- disponibilidad;
- consultas;
- operación;
- experiencia del equipo.

## Alternativas

No asumir relacional, documental, key-value, grafo o series temporales.

Comparar por caso.

## Diseño

- ownership;
- esquema;
- claves;
- índices;
- constraints;
- transacciones;
- concurrencia;
- pooling;
- backups;
- migraciones.

## Operación

- monitoreo;
- slow queries;
- capacidad;
- mantenimiento;
- failover;
- restore.

## Regla

No introducir varias bases por “polyglot persistence” sin una necesidad que compense operación adicional.
