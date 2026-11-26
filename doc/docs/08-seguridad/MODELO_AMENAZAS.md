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
