# Mensaje para iniciar el siguiente chat

> **Clasificación:** OPCIONAL, ayuda de continuidad.  
> **Checkpoint externo:** CKP-JAXON-005.  
> **Estado:** instrucción de lectura; no autorización de implementación.

Adjunta al nuevo chat:

1. `doc_JAXON_1.0.0_checkpoint_005.zip`;
2. `CHECKPOINT_JAXON_CKP_005.md`.

No hace falta adjuntar el legado salvo que una decisión concreta necesite contrastar evidencia histórica. Si se adjunta, recordar que **no es fuente de decisiones del reinicio**.

## Texto para copiar en el siguiente chat

Continuaremos **J-AXON** desde **CKP-JAXON-005**. Mi nombre es **LUIS** y debes mencionarlo de forma natural en cada respuesta. El descubrimiento inicial y el refinamiento/diseño detallado v1 están cerrados hasta el **Lote 54** y el proyecto está documentalmente `DESIGN_READY`. El nuevo sistema todavía **NO se ha implementado** y no tienes autorización general para programar, modificar GitHub, crear repositorios/branches/Issues/Projects/PRs/workflows ni desplegar.

Antes de responder o proponer una acción, **lee y contrasta los archivos reales; no uses solo memoria ni el checkpoint**. Orden obligatorio:

1. `doc/docs/00-gobierno/REGISTRO_ACUERDOS_JAXON.md` -- autoridad de decisiones aprobadas.
2. `doc/docs/16-contexto/TRABAJO_ACTUAL.md` -- estado exacto.
3. `doc/PROYECTO_MAESTRO.md` -- consolidación vigente.
4. `doc/docs/16-contexto/PENDIENTES_JAXON.md` -- solo decisiones todavía abiertas.
5. `doc/docs/16-contexto/ENRUTADOR_DOCUMENTACION.md` y documentos específicos del trabajo solicitado.

Confirma primero que Lotes 30--54 están cerrados, que `DESIGN_READY != IMPLEMENTATION_READY != PRODUCTION_READY`, y que los `POR_DECIDIR` restantes son deliberados (Production/organización/evidencia/futuro). No inventes un Lote 55 funcional solo por continuar la numeración.

Reglas críticas:
- Inventory = ficha oficial; Collector = observaciones técnicas y tareas read-only v1;
- Support = owner de tickets; IA solo sugiere y Automation/n8n no salta sus reglas;
- AuditEvent append-only, raw Audit para `AUDITOR`, reportes básicos por contratos públicos;
- commands/queries módulo->módulo = APIs in-process; hechos durables = Transactional Outbox PostgreSQL `at-least-once` e idempotencia;
- SSE = best-effort aviso; REST conserva verdad; n8n no consume el bus interno directamente;
- IDs técnicos v1 = UUIDv4, externos como strings opacos;
- cinco entornos: Local, Development, Test efímero, QA, Production;
- SFTP + JSON Schema sigue siendo canal de Agent/Discovery;
- `doc` permanece 1.0.0;
- diseño/documentación aprobados != implementación/verificación.

Si LUIS autoriza expresamente iniciar implementación, respeta el alcance exacto autorizado. La recomendación de readiness es empezar por **OLA 0 + mínimo necesario para alcanzar temprano la primera vertical E2E**, pero no ejecutes esa recomendación sin autorización.
