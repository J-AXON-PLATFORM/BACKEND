# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Última tarea completada: `TEC-PLT-002a`, autorizada, ejecutada y verificada el 2026-10-06.
- Implementado: bootstrap estructural de backend de `TEC-PLT-001a`; integración JDBC/Flyway con PostgreSQL 18.6 y pgvector 0.8.6 verificada mediante Testcontainers; y migración bootstrap que crea exactamente los ocho schemas aprobados, sin tablas de negocio.
- No implementado: lógica funcional, entidades, repositorios de dominio, servicios, APIs, OpenAPI ejecutable, CI, Dockerfile, Compose, frontend y `automation-collector`; tampoco existe infraestructura PostgreSQL Production.
- Estado remoto verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con repositorios privados vacíos `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`; corresponden a los nombres lógicos `backend`, `frontend` y `automation-collector`. No se publicó contenido ni se crearon commits, ramas, remotos locales, Issues, Projects, Pull Requests, workflows, rulesets, releases, artefactos o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: ninguno iniciado.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: diseño aprobado (`IT_OPERATOR -> login -> activo -> Agent -> snapshot SFTP -> Collector -> PostgreSQL -> SSE -> REST -> visualización técnica`), pero no implementada.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: analizar y delimitar exclusivamente el siguiente incremento pequeño de OLA 0; la siguiente tarea técnica todavía no ha sido iniciada.
- Próxima acción remota pendiente: analizar y delimitar una tarea independiente para inicializar Git y publicar controladamente el contenido correspondiente en los tres repositorios, solicitando antes autorización expresa de LUIS para ese alcance.
- Continuidad: LUIS autorizó que, después de verificar cada tarea, el coordinador determine el siguiente incremento pequeño y prepare un alcance concreto. Esto no autoriza de forma ilimitada toda OLA 0 ni permite ejecutar el siguiente incremento sin su delimitación y autorización aplicable.

## Reanudación obligatoria

Antes de proponer una acción o implementación:

1. leer `docs/00-gobierno/REGISTRO_ACUERDOS_JAXON.md`;
2. leer este `TRABAJO_ACTUAL.md`;
3. leer `PROYECTO_MAESTRO.md`;
4. leer `docs/16-contexto/PENDIENTES_JAXON.md`;
5. leer `docs/16-contexto/ENRUTADOR_DOCUMENTACION.md`;
6. ampliar solo con los documentos específicos del trabajo solicitado.

## Regla de continuidad

- No reabrir AC-JAXON-001..058 salvo contradicción documental real o instrucción expresa.
- `DESIGN_READY` != `IMPLEMENTATION_READY` != `PRODUCTION_READY`.
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; `TEC-PLT-001a` y `TEC-PLT-002a` ya fueron autorizadas, ejecutadas y verificadas, sin extender esa autorización al resto del backlog.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de `TEC-PLT-001a` y `TEC-PLT-002a`.
- La autorización ya consumida para crear la organización y los tres repositorios privados vacíos no autoriza inicialización Git, publicación, remotos, commits, ramas, Issues, Projects, Pull Requests, workflows, rulesets, releases, artefactos ni despliegues.
