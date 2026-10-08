# Batch y workers

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- Agent y Discovery executor

- `automation-collector` usa Python 3.14.x, uv/`uv.lock`, pytest y Ruff.
- `agent` y `discovery_executor` son programas distintos y no comparten identidad técnica.
- Agent usa Paramiko 5.x, keypair Ed25519 individual, host-key verification y SFTPGo 2.7.x como transporte.
- Packaging Agent: PyInstaller 6.22.x `onedir`, build por sistema operativo; servicio de mínimo privilegio; no Docker obligatorio ni self-update autónomo v1.
- Poll tareas 60 s ±15 s jitter; snapshot 6 h; una tarea activa; batch 10; spool durable 7 días/100 MB; ledger taskId 30 días.
- Discovery se ejecuta solo en equipos designados y autorizados, no desde todos los Agents.
<!-- JAXON-CKP003:END -->

## Activar si

Existe procesamiento programado, en background o consumido desde una cola.

## Diseño del trabajo

- trigger;
- frecuencia;
- input;
- output;
- límite de tiempo;
- paralelismo;
- particionamiento;
- checkpoints;
- idempotencia;
- reintentos;
- DLQ;
- cancelación;
- reanudación.

## Datos

Definir:

- snapshot/consistencia;
- locks;
- duplicados;
- procesamiento parcial;
- reconciliación.

## Operación

Medir:

- duración;
- throughput;
- backlog;
- edad del mensaje;
- errores;
- retries;
- jobs atascados.

## Pruebas

Validar reejecución, duplicados, fallos parciales y recuperación.
