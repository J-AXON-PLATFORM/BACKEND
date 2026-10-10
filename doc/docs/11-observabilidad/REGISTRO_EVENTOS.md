# Registro de eventos y logs

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Producir registros útiles, estructurados y seguros.

## Formato

Preferir JSON estructurado con, cuando aplique:

- timestamp UTC;
- level;
- module/component;
- version/environment;
- correlationId/traceId;
- operation/event;
- result;
- durationMs;
- error code estable.

## Estado implementado en backend -- TEC-OPS-002a

- salida de consola JSON Logstash mediante soporte nativo de Spring Boot;
- timestamps técnicos en UTC;
- `service.name=jaxon-backend` y entorno configurable como campos comunes;
- `traceId` y `spanId` cuando existe un span activo;
- `correlationId` por solicitud HTTP mediante `X-Correlation-ID`;
- soporte de campos estructurados `operation`, `result`, `durationMs` y `errorCode` cuando aplican.

El backend conserva únicamente un UUIDv4 válido recibido en `X-Correlation-ID`, normalizado a minúsculas; si falta o es inválido genera otro UUIDv4 y siempre devuelve el valor aprobado en la respuesta. El MDC se limpia en `finally` y las pruebas cubren solicitudes secuenciales, concurrentes y excepciones.

La baseline cubre el ciclo servlet síncrono actualmente ejecutable. La propagación del `correlationId` propio hacia trabajo asíncrono o futuros flujos SSE debe resolverse y probarse cuando exista ese flujo funcional; no se asume automáticamente por el MDC de hilo.

## No registrar

- passwords;
- session cookies/tokens;
- SSH private keys;
- HMAC secrets;
- backup keys;
- documentos completos;
- prompts/contexto sensible completos salvo caso expresamente aprobado y minimizado.

## Retención

- Production 30 días;
- Development 7 días;
- Test efímero;
- QA 14 días;
- Local sin centralización obligatoria.

Logs técnicos no sustituyen auditoría. Acciones que requieran 5 años deben existir en `audit_reporting`.
