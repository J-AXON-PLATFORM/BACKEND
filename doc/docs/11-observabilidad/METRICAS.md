# Métricas

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Medir salud, carga, errores, latencia, frescura y saturación.

## Backend/web

- request rate/status/duration;
- active requests;
- JVM CPU/memory/threads/GC;
- 5xx atribuibles al sistema;
- versión/entorno como labels de baja cardinalidad cuando aporte.

## PostgreSQL/pool

- active/idle/pending connections;
- pool saturation;
- query duration agregada;
- errores DB;
- locks/saturación cuando aplique.

No aumentar pools sin medir capacidad del recurso destino.

## Collector / Agents

- Agents por estado y online/offline;
- `lastSeenAt`/`lastSnapshotAt` y snapshot freshness;
- snapshots recibidos/aceptados/rechazados/duplicados;
- tasks creadas/pendientes/exitosas/fallidas/expiradas;
- fallos de procesamiento/SFTP agregados.

Un Agent individual offline no equivale a incidente crítico.

## Integraciones

IA, n8n, SFTPGo y DocumentStorage: requests/operaciones, errores, timeouts, latencia, retries y estado de degradación.

## Cardinalidad

No usar como labels: userId, assetId, ticketId, requestId, taskId, messageId ni texto libre.

## Estado implementado en backend -- TEC-OPS-003a

Actuator expone únicamente `health` y `prometheus`. `/actuator/prometheus` publica métricas estándar de JVM, HTTP y HikariCP con `application` y `environment` como tags comunes de baja cardinalidad. `env`, `metrics` y `loggers` no están expuestos por HTTP.

No se añaden labels con identidades de usuario, activo, tarea, mensaje o correlación. La exportación OTLP de métricas permanece desactivada y no se instala un servidor Prometheus en este bloque.

`/actuator/prometheus` es una interfaz operacional, no una interfaz de usuario. Local publica el puerto completo solo en loopback; en otros entornos su acceso debe restringirse en la red o reverse proxy de la instalación, todavía `POR_DECIDIR`, sin asumir que esta baseline incorpora autenticación de aplicación.

## SLO

Relacionar con `SLO_SLA_SLI.md`.
