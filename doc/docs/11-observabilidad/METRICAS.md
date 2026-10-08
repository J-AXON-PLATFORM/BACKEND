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

## SLO

Relacionar con `SLO_SLA_SLI.md`.
