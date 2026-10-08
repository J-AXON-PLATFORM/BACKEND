# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Última tarea completada: `TEC-PLT-003a`, autorizada, ejecutada y verificada el 2026-10-07.
- Implementado: bootstrap estructural de backend de `TEC-PLT-001a`; integración JDBC/Flyway con PostgreSQL 18.6 y pgvector 0.8.6 verificada mediante Testcontainers; migración bootstrap que crea exactamente los ocho schemas aprobados, sin tablas de negocio; y CI base `Backend CI` de `TEC-PLT-003a` en GitHub Actions.
- No implementado: lógica funcional, entidades, repositorios de dominio, servicios, APIs, OpenAPI ejecutable, Dockerfile, Compose, frontend y `automation-collector`; tampoco existe infraestructura PostgreSQL Production.
- Estado remoto verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`. `backend` contiene `Backend CI` en `develop`, integrado mediante el Pull Request `#1` con `Squash and merge` después de checks verdes sobre la última revisión; `main` no fue modificado y puede quedar temporalmente detrás de `develop`. Los otros dos repositorios permanecen vacíos y sin Git local. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: ninguno iniciado.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: diseño aprobado (`IT_OPERATOR -> login -> activo -> Agent -> snapshot SFTP -> Collector -> PostgreSQL -> SSE -> REST -> visualización técnica`), pero no implementada.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend: PR hacia `develop/main`, push a `develop` y ejecución manual; Java 25; `clean verify`; acciones fijadas por SHA; permisos `contents: read`; sin secretos ni despliegues. Contratos/SFTP se incorporarán cuando sean aplicables. Rulesets pendientes por la limitación del plan para repositorios privados; los checks verdes se exigen operativamente, no por bloqueo técnico de GitHub.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: analizar y delimitar exclusivamente el siguiente incremento pequeño de OLA 0; la siguiente tarea técnica todavía no ha sido iniciada.
- Próxima acción remota pendiente: cualquier inicialización y publicación de `frontend` o `automation-collector` debe analizarse, delimitarse y autorizarse de forma independiente.
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
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; `TEC-PLT-001a`, `TEC-PLT-002a` y `TEC-PLT-003a` ya fueron autorizadas, ejecutadas y verificadas, sin extender esa autorización al resto del backlog.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de `TEC-PLT-001a`, `TEC-PLT-002a` y `TEC-PLT-003a`.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
