# Registro de eventos backend

> **Clasificación:** CONDICIONAL


## Instancia J-AXON -- eventos internos v1

Este documento no convierte logs en bus de eventos. J-AXON separa:

- logs/trazas/métricas: observabilidad técnica;
- `AuditEvent`: trazabilidad durable de seguridad/negocio en MOD-08;
- eventos internos de aplicación: hechos intermodulares, con entrega durable solo cuando corresponda.

Reglas Lote 53:

- commands/queries entre módulos usan contratos públicos síncronos in-process;
- hechos confirmados pueden emitir eventos internos;
- eventos durables se capturan con Transactional Outbox PostgreSQL dentro del schema productor;
- semántica `at-least-once`, `eventId` estable, idempotencia por consumidor y sin orden global;
- payloads pequeños/allowlisted, default <=64 KiB; nunca secretos ni objetos completos por comodidad;
- `correlationId` permite unir request/operación/evento; `causationId` es opcional;
- SSE es best-effort hacia navegador y REST conserva el estado oficial;
- n8n no consume directamente el mecanismo interno; pasa por MOD-07 Automation.

## Propósito

Definir logging útil para diagnóstico, auditoría técnica y correlación.

## Campos sugeridos

Cuando aplique:

- timestamp;
- nivel;
- servicio/módulo;
- versión;
- request/correlation/trace ID;
- operación;
- duración;
- resultado;
- categoría de error.

## Datos prohibidos

No registrar:

- contraseñas;
- tokens;
- claves;
- secretos;
- números completos sensibles;
- payloads completos sin análisis.

## Auditoría

Separar logging operativo de auditoría de negocio/seguridad cuando tengan requisitos distintos.

## Excepciones

Registrar contexto suficiente para diagnóstico, pero nunca depender de logs para preservar la única copia de un dato crítico.

## Relación

Seguir `docs/11-observabilidad/REGISTRO_EVENTOS.md`.
