# SLI, SLO y SLA

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Conceptos

- **SLI:** indicador medido de confiabilidad.
- **SLO:** objetivo interno para un SLI.
- **SLA:** compromiso contractual/formal, si existe.

## SLOs iniciales aprobados

### Core web

- SLI: porcentaje de solicitudes válidas a capacidades core que terminan correctamente sin indisponibilidad del sistema;
- ventana: rolling 30 días, dentro de la ventana operativa declarada de la instalación;
- SLO: **>=99,5 %**.

### Frescura Agent

- población: Agents `ACTIVE` que están online;
- SLI: porcentaje con snapshot válido de antigüedad <=8 h;
- SLO: **>=99 %**.

### Procesamiento de snapshot recibido

- SLI: porcentaje de snapshots válidos recibidos que se procesan/persisten en <=5 min desde recepción observable por Collector;
- SLO: **>=99 %**.

## Rendimiento

Los targets p95/p99 del Lote 46 son criterios de rendimiento/capacidad iniciales; no se convierten automáticamente en SLA contractual. Relacionar con `RENDIMIENTO_CAPACIDAD.md`.

## SLA

SLA comercial sigue `POR_DECIDIR`; no derivarlo automáticamente de los SLO internos.

## Regla

Los SLO solo se consideran significativos si las fuentes de medición son confiables y se revisan con operación real.

La exposición de métricas Prometheus de la baseline backend habilita observación técnica, pero no constituye por sí sola medición validada de los SLI ni convierte los SLO internos en SLA. Dashboards, alertas, ventanas y evidencia operativa real permanecen pendientes.
