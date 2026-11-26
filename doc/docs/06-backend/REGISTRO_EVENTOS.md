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

`OLA6-AUD-001b` versiona `HUMAN_USER_CREATED` sin reinterpretar el histórico: 1.0 conserva `{"status":"ACTIVE"}` y no tiene consumidor Audit; 2.0 añade exclusivamente `roles` como una única cadena canónica separada por `|`, con al menos un rol y orden fijo `AUDITOR|END_USER|IT_OPERATOR|SUPPORT_TECHNICIAN|SYSTEM_ADMIN`, y es el único que reclama el dispatcher Identity. Payload y metadata contienen exactamente `status` y `roles`; se rechazan duplicados, desorden, roles desconocidos, otros separadores, claves adicionales y formatos alternativos. `AC-JAXON-094` ratifica retrospectivamente esta representación escalar ya integrada sin reescribir `AC-JAXON-093`. Ambos eventos comparten el tipo por continuidad semántica, pero el catálogo Audit solo acepta 2.0.

`OLA6-AUD-001c` versiona futuros `HUMAN_LOGIN_SUCCEEDED` y `HUMAN_LOGOUT_SUCCEEDED` a 2.0 sin alterar ni despachar 1.0. Login usa recurso `AUTHENTICATION_ATTEMPT` y el UUIDv4 estable del intento; logout usa `HUMAN_SESSION` y el UUIDv4 de la sesión eliminada. Ambos tienen actor/correlación UUIDv4, `SUCCESS`, `resourceVersion=null` y payload/metadata exactamente `{}`. El dispatcher Identity procesa únicamente esos dos tipos y `HUMAN_USER_CREATED`, siempre 2.0. `HUMAN_LOGIN_FAILED`, username, IP, contraseña, sesión/token, CSRF, hashes, rate-limit keys y metadata adicional quedan excluidos.

`OLA6-AUD-001d` versiona futuros `HUMAN_LOGIN_FAILED`, `AGENT_PUBLIC_KEY_REGISTERED` y `DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED` a 2.0. El fallo usa actor UUIDv4 o nulo solo sin principal, `AUTHENTICATION_ATTEMPT`, `DENIED` y reason exacto; las claves exigen actor humano, recurso técnico exacto, `SUCCESS` y `{}`. El dispatcher procesa los seis tipos Identity allowlisted únicamente 2.0. `429`, no-ops, 1.0 y cualquier dato sensible o identificador de credencial quedan excluidos.

`OLA6-AUD-001e` versiona futuros `ASSET_CREATED`, `AGENT_PRE_REGISTERED` y `AGENT_ACTIVATED` a 2.0. Inventory y Collector despachan por separado solo sus tipos allowlisted, con actor humano, recurso `ASSET|AGENT`, versiones `0|0|1`, `SUCCESS` y `{}`. La activación idempotente no duplica evento; 1.0 no se reclama.

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
