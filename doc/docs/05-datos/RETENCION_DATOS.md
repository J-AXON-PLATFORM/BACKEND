# Retención de datos

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Definir cuánto tiempo se conserva cada categoría de datos, por qué y cómo se elimina o anonimiza.

| Categoría | Retención inicial | Motivo / regla | Eliminación / relación | Estado |
|---|---:|---|---|---|
| Datos maestros/estado operacional vigente | sin expiración temporal automática | ciclo de vida del módulo | operación explícita del módulo | APROBADO |
| Snapshots y observaciones técnicas Collector, incluidos resultados de tareas v1 | 180 días | historia técnica acotada | purga según política; revisar con volumen real | APROBADO L41/L51 |
| Resultados Discovery no incorporados | 30 días | evidencia temporal de descubrimiento | `UNREVIEWED/IGNORED` elegibles para purga | APROBADO |
| Tickets cerrados + adjuntos | 3 años desde el último cierre válido | soporte/historial operativo; una reapertura reinicia el punto de cómputo al volver a cerrar | purga según reglas de Support y excepciones legales | APROBADO L41/L49 |
| Auditoría relevante | 5 años desde `recordedAt` | trazabilidad de seguridad/operación | eliminación controlada; no usar logs técnicos como sustituto; desde `OLA6-AUD-001a` vive en `audit_reporting.audit_events`, independiente del outbox, y su purga queda bloqueada hasta un corte autorizado | APROBADO L41/L52 |
| Fuente documental RAG | mientras esté activa/autorizada | propósito documental | al retirar/eliminar, retirar original conforme a política | APROBADO |
| Chunks/embeddings RAG | mismo ciclo de la fuente | derivados, no fuente de verdad | borrar/deshabilitar junto con fuente | APROBADO |
| Logs técnicos Production | 30 días | diagnóstico | backend de observabilidad | APROBADO L45 |
| Métricas Production | 90 días | tendencia/capacidad | backend de observabilidad | APROBADO L45 |
| Trazas Production | 7 días | diagnóstico distribuido | backend de observabilidad | APROBADO L45 |
| Backups | 14 diarios / 8 semanales / 12 mensuales | recuperación, no archivo legal | rotación independiente | APROBADO L41 |
| Outbox interno entregado | 7 días por default configurable | diagnóstico/reconciliación de entrega | solo entregados; `PENDING/FAILED` no se autoborran | APROBADO L53 |

Development conserva telemetría 7 días, Test es efímero, QA 14 días y Local no requiere centralización obligatoria.

## Factores

- obligación legal/regulatoria concreta por organización: `POR_DECIDIR`;
- necesidad operativa;
- auditoría;
- privacidad;
- backups;
- analítica;
- litigio/legal hold si aplica: `POR_DECIDIR`.

## Borrado

Definir para cada categoría:

- sistema fuente;
- referencias/derivados;
- caches/índices;
- backups;
- exports;
- downstream.

Una fuente RAG eliminada/desautorizada no debe dejar chunks/embeddings recuperables. Un resultado Discovery incorporado no convierte Discovery en owner de la ficha oficial del activo.

## Backups

Un dato eliminado del sistema primario puede permanecer temporalmente en backups hasta que roten. La política de backup no se usa como retención histórica de negocio. Tras restore, reconciliar eliminaciones vigentes cuando corresponda.

## Verificación

Cuando la eliminación sea un requisito, debe existir evidencia verificable, no solo una política escrita.
