# Arquitectura de integraciones

> **Clasificación:** CONDICIONAL


<!-- JAXON-CKP005:START -->
## Instancia J-AXON -- integraciones concretadas

| Sistema | Propósito | Protocolo | Autenticación | Timeout base | Retry / idempotencia | Fallo |
|---|---|---|---|---|---|---|
| Agent <-> SFTPGo/Collector | tareas, snapshots, respuestas | SFTP/SSH + JSON Schema | keypair Ed25519 individual + host-key verification | connect/auth/operation 10/10/60 s | 5 intentos transitorios + exponential backoff/full jitter; `messageId`/`taskId` | spool local + reconciliación; errores permanentes no se reintentan ciegamente |
| Discovery Executor <-> SFTPGo/Discovery | jobs/resultados Discovery | SFTP/SSH + contrato propio | identidad SSH separada del Agent | valores por contrato; probe de red ~2 s | `jobId` estable; no reejecutar job ya procesado | mailbox aislado por executor; un job activo por executor |
| J-AXON -> n8n | iniciar workflow autorizado | HTTPS/API/webhook | credencial exclusiva de integración | connect 5 s / request 15 s | max 3 retries transitorios, exponential backoff+jitter, mismo `executionId` | ejecución `FAILED` tras agotar retry; core continúa |
| n8n -> J-AXON | callback de ejecución | HTTPS webhook | HMAC-SHA-256 + timestamp, anti-replay 5 min | corto, síncrono solo para aceptar callback | `eventId` + `executionId`; duplicados no repiten efectos | callback inválido/replay rechazado |
| J-AXON -> proveedor IA futuro | generación/embeddings | adapter, protocolo según proveedor | credencial separada si aplica | `POR_DECIDIR` con proveedor | solo cuando la operación sea segura; registrar latencia/error/uso | degradación sin bloquear core |

SFTPGo 2.7.x es infraestructura de transporte; Paramiko 5.x cliente Agent. Agent y Discovery Executor usan identidades y buzones separados. n8n no es autoridad de negocio. IA/RAG permanece provider-agnostic.

Eventos internos del monolito **no son una integración externa**: usan contratos in-process y Transactional Outbox PostgreSQL cuando requieren entrega durable. Automation es la frontera que decide qué hecho autorizado puede iniciar una integración; n8n no consume directamente eventos internos y AI no se suscribe globalmente al sistema.
<!-- JAXON-CKP005:END -->

## Propósito

Definir integraciones externas y su comportamiento funcional, contractual y operativo.

## Inventario

| Sistema | Propósito | Protocolo | Autenticación | Timeout | Retry | Rate limit | Estrategia ante fallo |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## Por integración

Definir:

- propietario externo;
- contrato;
- datos intercambiados;
- clasificación de datos;
- autenticación/autorización;
- cifrado;
- timeout;
- retry;
- idempotencia;
- límites/cuotas;
- sandbox;
- versionado;
- deprecación;
- observabilidad;
- soporte/escalamiento.

## Dependencia crítica

Si la integración puede degradar el sistema, activar `RESILIENCIA.md`.

## Asincronía

Si usa webhooks/eventos/colas:

- autenticidad;
- duplicados;
- ordering;
- replay;
- DLQ;
- firma/verificación;
- idempotencia.

## Testing

Definir combinación adecuada de:

- fake/stub;
- contract test;
- sandbox;
- smoke periódico.

No depender exclusivamente del proveedor real en cada PR.
