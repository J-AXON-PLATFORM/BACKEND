# Modelo de amenazas


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Identificar activos, límites de confianza, amenazas y mitigaciones

- Assets
- Entry points
- Trust boundaries
- Threats
- Mitigations
- Residual risks

## OLA5-AI-002a

- Activos: originales privados, chunks, embeddings, artefactos ONNX/tokenizer y estados/outbox AI.
- Entradas: petición humana autenticada y original ya registrado; no existe endpoint de contenido, vector o retrieval.
- Mitigaciones: `SYSTEM_ADMIN` + CSRF + versión optimista; lectura no-follow con tamaño/SHA-256; modelo/tokenizer externos fijados por tamaño y hash streaming; límites de 1 MiB, 480 tokens, batch y concurrencia; inferencia fuera de transacción; publicación atómica; claims expirados terminales y resultados tardíos descartados; logs/métricas sin contenido ni IDs de alta cardinalidad.
- Riesgos residuales: capacidad/RSS del runtime local, protección operativa de los paths externos de modelo y tokenizer, y tuning HNSW/hardware Production. No se presentan como resueltos por este corte.

## OLA5-AI-003a

- Activos: fragmentos indexados, metadata pública de fuentes y consultas humanas.
- Entradas: `POST /ai/retrieval` autenticado con CSRF; el contenido recuperado es dato no confiable.
- Amenazas: acceso no autorizado, fuga de fuentes no elegibles, enumeración por mensajes de error, consumo excesivo por consultas largas, inyección de contenido en la UI y confusión entre evidencia y respuesta generada.
- Mitigaciones: `SYSTEM_ADMIN` + CSRF antes del embedding; body cerrado, 2000 caracteres y 480 tokens; `limit` <= 10; filtro `READY` y de modelo dentro de la misma sentencia; respuesta allowlisted sin rutas, claves ni vectores; errores RFC 9457 sin eco; logs y métricas sin consulta ni identificadores; lectura sin persistencia; frontend renderiza texto escapado y rotula los fragmentos como evidencia recuperada.
- Riesgos residuales: sin rate limiting específico para la operación; el escaneo exacto escala linealmente con el número de chunks y su p95 solo está medido localmente hasta 100 000 chunks; dimensionamiento Production pendiente.

## OLA5-AI-006a

- Activos: estado oficial de fuentes, elegibilidad para recuperación y evidencia histórica (original, chunks, embeddings, outbox).
- Entradas: `POST /ai/sources/{sourceId}/disable` autenticado con CSRF.
- Amenazas: desautorización no autorizada, carreras que dupliquen eventos o pierdan versiones, enumeración de fuentes y borrado accidental de evidencia.
- Mitigaciones: `SYSTEM_ADMIN` + CSRF; `404` uniforme; body cerrado; bloqueo de fila y versión optimista; transacción única para estado y outbox; payload sin nombre, contenido ni rutas; ninguna operación borra datos; la recuperación conserva su filtro `READY` dentro de la sentencia.
- Riesgos residuales: no hay reactivación ni purga en v1, por lo que un error humano exige un corte futuro; la retención física de fuentes deshabilitadas sigue pendiente de una política de purga aprobada.

## OLA6-AUD-001a

- Activos: integridad y completitud de `audit_reporting.audit_events`, estado de entrega por consumidor y confidencialidad de la consulta.
- Entradas: dispatcher in-process AI -> `AuditFactRecorder`; `GET /audit/events` autenticado.
- Amenazas: pérdida o duplicación de hechos, sobrescritura o borrado de auditoría, lectura cross-module, exfiltración por metadata, acceso de roles no autorizados y enumeración por filtros.
- Mitigaciones: intención durable en el outbox del productor; entrega idempotente por `source_event_id`; conflicto sin sobrescritura; trigger append-only; catálogo y metadata cerrados en Java y en la base; prueba estática sin SQL ajeno; `AUDITOR` exclusivo; `422` sin eco; respuesta sin secretos ni contenido.
- Riesgos residuales: rol único de base de datos; `SYSTEM_ADMIN` puede crear una cuenta `AUDITOR`; sin tamper-proof criptográfico ni alertas individuales de `FAILED` en v1.

## OLA6-AUD-001b

- Activos: roles asignados en altas humanas, integridad del outbox Identity, entrega por consumidor y evidencia Audit.
- Entradas: alta administrativa existente y dispatcher in-process Identity -> `AuditFactRecorder`.
- Amenazas: omitir roles sensibles, ambigüedad o formatos alternativos de roles, despacho accidental de 1.0, duplicación/conflicto, bloqueo por evento inválido y SQL cross-module.
- Mitigaciones: evento 2.0 atómico; catálogo completo y orden canónico fijo con separador `|`; constraints y validación Java cerradas; dispatcher filtra tipo+versión; entrega por consumidor con `SKIP LOCKED`, 10 intentos e idempotencia; Audit permanece append-only y sin SQL ajeno.
- Riesgos residuales: rol runtime único de base de datos, sin reproceso operativo de `FAILED`, sin tamper-proof criptográfico y sin auditoría de edición posterior de roles.

## OLA6-AUD-001c

- Activos: evidencia de autenticación humana exitosa, identidad de intento/sesión y privacidad de credenciales/contexto.
- Amenazas: fuga de username, IP, contraseña, token/sesión, CSRF, hashes o rate-limit keys; despacho de fallos o 1.0; metadata arbitraria; duplicación/conflicto.
- Mitigaciones: eventos 2.0 con payload/metadata exactamente `{}`; allowlist tipo+versión; catálogo Java/SQL cerrado; UUIDv4; dispatcher y entrega existentes; idempotencia y conflicto sin sobrescritura.
- Riesgos residuales: los fallos/denegaciones no se auditan en este corte; permanecen los riesgos de rol runtime único, `FAILED` sin reproceso y ausencia de tamper-proof criptográfico.

## OLA6-AUD-001d

- Activos: evidencia de denegaciones de login y de registro de credenciales técnicas, sin material de credencial ni identificadores de red.
- Amenazas: amplificación por peticiones `429`, enumeración, fuga de username/IP/hash/clave/fingerprint/comentario, actor anónimo sintético, metadata arbitraria, despacho de 1.0 o duplicación.
- Mitigaciones: ningún evento por `429`; máximo cinco fallos por clave/ventana; reason único sin distinguir causa; actor nulo solo sin principal; claves con metadata vacía; allowlist 2.0 y catálogos Java/SQL cerrados; idempotencia y conflicto sin sobrescritura.
- Riesgos residuales: un atacante puede rotar usernames/IP y aumentar volumen antes del umbral; Audit no acredita una respuesta `429` ni agrupa episodios; permanecen rol runtime único, `FAILED` sin reproceso y ausencia de tamper-proof criptográfico.

## OLA6-AUD-001e

- Activos: integridad de altas de activos, pre-registros/activaciones Agent, outboxes/entregas propietarios y evidencia Audit.
- Amenazas: despacho accidental de 1.0, versión de recurso falsa, duplicación por activación idempotente, conflicto sobrescrito, evento defectuoso bloqueante o SQL cross-module.
- Mitigaciones: eventos 2.0 atómicos con versiones `0|0|1`; allowlists Java/SQL; dispatchers separados con `SKIP LOCKED`, 10 intentos e idempotencia; conflicto sin sobrescritura; Audit append-only y acceso solo por `AuditFactRecorder`.
- Riesgos residuales: rol runtime único, `FAILED` sin reproceso operativo y ausencia de tamper-proof criptográfico.

## OLA6-AUD-001f

- Activos: integridad del lifecycle administrativo inicial de scopes/executors, outbox/entrega Discovery y evidencia Audit.
- Amenazas: despacho de 1.0 o tipos no autorizados, actor/recurso/versión falsos, metadata adicional, duplicación por no-op, conflicto sobrescrito, evento defectuoso bloqueante o SQL cross-module.
- Mitigaciones: allowlist 2.0 y catálogos Java/PostgreSQL cerrados; UUIDv4; metadata mínima; `SKIP LOCKED`, 10 intentos y aislamiento; idempotencia y conflicto sin sobrescritura; Audit append-only accesible solo mediante `AuditFactRecorder`.
- Riesgos residuales: rol runtime único, `FAILED` sin reproceso operativo y ausencia de tamper-proof criptográfico.

## OLA6-AUD-001g

- Activos: integridad de creación, despacho y finalización oficial de Discovery Jobs, identidad del executor, outbox/entrega Discovery y evidencia Audit.
- Amenazas: despacho de 1.0, actor humano/executor falso, correlación sustituida, resultado terminal incorrecto, completion sin transición oficial, duplicación por reingesta o metadata arbitraria.
- Mitigaciones: productor 2.0 atómico; actor/correlación originales; executor validado contra Job/resultado; mapping cerrado `SUCCEEDED->SUCCESS|FAILED->FAILED`; payload `{}`; allowlists Java/PostgreSQL; idempotencia, conflicto sin sobrescritura y silencio de rechazos/finales tardíos.
- Riesgos residuales: rol runtime único, `FAILED` de entrega sin reproceso operativo y ausencia de tamper-proof criptográfico.
