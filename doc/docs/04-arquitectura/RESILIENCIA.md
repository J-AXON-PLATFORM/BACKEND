# Resiliencia

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.  
> **Activada:** sí, por Agent/SFTP, integraciones externas y fallos parciales.


<!-- JAXON-CKP003:START -->
## Valores aprobados CKP-JAXON-003

- Poll tareas: 60 s ±15 s jitter; startup jitter 0--60 s.
- Snapshot periódico: 6 h, ventana escalonada 60 min.
- Retry: máximo 5 intentos consecutivos; exponential backoff + full jitter; base 5 s; cap 5 min.
- SFTP timeout: connect 10 s, auth 10 s, operación 60 s.
- Spool local durable: 7 días o 100 MB; responses > snapshots bajo demanda > periódicos.
- Ledger taskId local: 30 días. TTL default 1 h; `COLLECT_SNAPSHOT` 15 min.
- 1 tarea activa; batch máximo 10/ciclo; reconciliación backend 5 min.
- `OFFLINE`: >5 min sin contacto; distinto de `ERROR`.
- PostgreSQL caído nunca produce éxito falso; SSE/IA/n8n pueden degradar sin bloquear persistencia core.
<!-- JAXON-CKP003:END -->

## Políticas aprobadas

- Distinguir Internet caído, LAN/servidor inaccesible, PC apagado, SFTP caído y backend/procesador caído.
- Errores transitorios vs permanentes.
- Retry limitado + backoff apropiado + jitter; nunca infinito.
- Agent conserva pendientes importantes en almacenamiento local durable, acotado y observable.
- Disco lleno tiene manejo explícito; no confirmar éxito falso.
- No procesar archivos parciales: transferencia temporal y finalización atómica.
- Validar JSON, JSON Schema, versión, identidad/destino antes de estado oficial.
- Archivo inválido/corrupto -> rechazado/cuarentena con motivo; sin retry infinito.
- Duplicados son posibles; no prometer exactly-once. Usar ID estable, idempotencia y reconciliación.
- Workflow de tarea distingue disponibilidad/pickup/proceso/resultado; nombres exactos se refinan.
- Tareas expiran por clase; default aprobado 1 h y `COLLECT_SNAPSHOT` 15 min; otros tipos pueden definir TTL específico al diseñarse.
- Agent valida destino/versión/tipo/vigencia/parámetros.
- Backend y Agent recuperan/reconcilian estado tras restart.
- `OLA4-DIS-005a` aplica al Discovery Executor un ledger separado `RECEIVED|RUNNING|RESULT_READY|DELIVERED`: persiste `RUNNING` antes del primer probe, un restart produce `EXECUTION_INTERRUPTED` sin reejecución y `RESULT_READY` conserva bytes/IDs hasta una invocación manual posterior. No añade retry automático.
- Acciones no idempotentes/peligrosas no se reintentan ciegamente.
- Timeouts externos; circuit breaker, bulkhead y rate limit solo selectivamente.
- IA, n8n y SSE degradan sin corromper core; PostgreSQL down hace core degradado/not-ready y no permite éxito falso de escrituras críticas.
- Sin broker externo solo por resiliencia inicialmente.

## Valores todavía pendientes

Los valores base Agent/SFTP anteriores están aprobados y son configurables. Siguen `POR_DECIDIR` hardware, thresholds de circuit breaker/bulkhead/rate limiting adicionales y cualquier ajuste derivado de pruebas representativas.

---
> **Clasificación:** CONDICIONAL  
> **Activar si:** hay dependencias remotas, recursos limitados, procesamiento asíncrono o requisitos de disponibilidad.


## Instancia J-AXON -- refinamientos Lotes 43 y 48

- Dispatch J-AXON->n8n: connect timeout 5 s, request timeout 15 s, máximo 3 reintentos transitorios con exponential backoff+jitter y `executionId` estable. 401/403/payload inválido/workflow inexistente no se reintentan ciegamente.
- Callback n8n: duplicados asumidos, `eventId`+`executionId`, HMAC-SHA-256 y ventana anti-replay 5 min.
- Discovery: máximo un job activo por executor, TTL inicial 1 h, `jobId` estable, cancelación cooperativa y revalidación de target dentro del `AuthorizedScope`.
- Fallo de n8n, IA u observabilidad degrada capacidades auxiliares sin tumbar el core.

## Propósito

Definir cómo se comporta el sistema cuando las dependencias se ralentizan, fallan, duplican operaciones o recuperan servicio.

## Regla

No aplicar todos los patrones. Cada mecanismo debe resolver un modo de fallo identificado.

## Plantilla por dependencia

```text
Dependencia:
Criticidad:
Modo de fallo:
Impacto:
Timeout:
Retry:
Backoff:
Jitter:
Idempotencia:
Circuit breaker:
Bulkhead:
Rate limit:
Fallback:
Degradación permitida:
Cola/DLQ:
Observabilidad:
Recuperación:
```

## Timeouts

Toda llamada remota con posibilidad de bloqueo debe tener una política explícita.

Definir timeout según:

- presupuesto de latencia;
- comportamiento del proveedor;
- SLA/SLO;
- cascadas;
- experiencia del usuario.

## Reintentos

Reintentar solo operaciones seguras o idempotentes.

Definir:

- errores reintentables;
- cantidad;
- backoff;
- jitter;
- límite total de tiempo;
- interacción con timeouts.

Evitar retry storms.

## Idempotencia

Necesaria cuando una operación puede repetirse por:

- retry;
- redelivery;
- timeout ambiguo;
- usuario;
- recuperación.

Definir clave, ventana, almacenamiento y respuesta repetida.

## Circuit breaker

Considerarlo cuando llamadas repetidas a una dependencia caída agravan el problema.

No usarlo si un timeout/retry simple resuelve el caso sin estado adicional.

## Bulkheads

Separar pools/recursos cuando una dependencia pueda agotar recursos compartidos y afectar funciones no relacionadas.

## Rate limiting y backpressure

Definir:

- dónde limitar;
- por quién;
- respuesta;
- cola;
- rechazo;
- degradación;
- métricas.

## Colas y DLQ

Si existe mensajería:

- reintentos;
- poison messages;
- DLQ;
- replay;
- alertas;
- ownership;
- retención.

## Pruebas

Validar modos de fallo relevantes:

- timeout;
- conexión rechazada;
- latencia alta;
- error 5xx;
- duplicado;
- dependencia parcial;
- broker no disponible;
- saturación;
- recuperación.

## Resultado

Una estrategia de resiliencia debe demostrar que el sistema:

- falla de manera controlada;
- no multiplica el incidente;
- preserva integridad;
- recupera servicio;
- produce señales operables.
