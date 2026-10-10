# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Último bloque técnico implementado y verificado localmente: `TEC-OPS-005a` y `TEC-OPS-006a`, con dump lógico PostgreSQL custom, manifest SHA-256, restore transaccional aislado y gate positivo/negativo. La evidencia remota se registrará después del cierre del único Pull Request autorizado.
- Implementado: bootstrap estructural y de persistencia del backend; health/liveness/readiness y shutdown graceful; logs JSON y `X-Correlation-ID`; métricas Actuator/Prometheus; tracing OpenTelemetry W3C opcional; scripts y gate de backup/restore lógico; Dockerfiles multi-stage no-root para backend/frontend; fixture local endurecida PostgreSQL 18.6 + pgvector 0.8.6; Compose Local con PostgreSQL predeterminado y backend opcional; OpenAPI contract-first de cinco operaciones sin controladores; cuatro JSON Schemas Agent, fixtures y manifest; validador Agent Python y sincronización vendorizada; smoke tests, scans y CI ampliadas; fundación de `automation-collector` con Agent y Discovery Executor separados.
- No implementado: lógica funcional, entidades, repositorios de dominio, servicios, controladores/APIs de negocio ejecutables, funcionalidades frontend de negocio, SFTP, generación real de snapshots, polling, ejecución real de tareas, discovery de red, enrolamiento, claves SSH y PyInstaller; tampoco existen tablas de negocio o infraestructura PostgreSQL Production. La baseline lógica no implementa base backup, WAL/PITR, cifrado, storage externo, scheduling, retención, documentos ni validación Production de RPO/RTO.
- Estado remoto histórico verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`; `backend` fue publicado inicialmente mediante `TEC-GIT-001C`.
- Estado remoto de contenedores verificado el 2026-10-08: `BACKEND#5` fue integrado en `develop` con squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge quedaron verdes. `FRONTEND#2` fue integrado con squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge quedaron verdes. No se publicaron imágenes ni artefactos.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` es privado; `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: ninguno iniciado.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: diseño aprobado (`IT_OPERATOR -> login -> activo -> Agent -> snapshot SFTP -> Collector -> PostgreSQL -> SSE -> REST -> visualización técnica`), pero no implementada.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend: conserva los gates existentes, integra seis pruebas contractuales en `clean verify` y ejecuta el harness aislado de backup/restore lógico reutilizando las imágenes locales. Valida OpenAPI 3.1.2, JSON Schema, ejemplos, manifest y descarga `oasdiff` 1.33.0 fijado con SHA-256 para validación y comparación contra baseline cuando exista. No incorpora SFTP, secretos, artifacts de backups o despliegues.
- CI frontend: PR hacia `develop/main`, push a `develop` y ejecución manual; Node.js 24.21.0; `npm ci`, lint, typecheck, Vitest/RTL, build, auditorías, build standalone, smoke no-root/read-only y scan Trivy bloqueante HIGH/CRITICAL. La auditoría completa conserva la excepción cerrada solo para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo.
- CI Python: conserva sync/lock, Ruff, build, auditoría y ambos entrypoints; ejecuta 33 pruebas y verificación local del bundle Agent, sin clonar backend, secretos cross-repo, artefactos o despliegues.
- Riesgos frontend abiertos: ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial soporte ESLint 10; retirar la excepción de `braces` cuando exista corrección oficial o desaparezca la cadena.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: cerrar remotamente el único Pull Request autorizado para `TEC-OPS-005a`/`TEC-OPS-006a`; después, analizar y delimitar exclusivamente el siguiente incremento pequeño de OLA 0.
- Próxima acción remota pendiente: cualquier cambio GitHub adicional fuera de los bloques consumidos debe analizarse, delimitarse y autorizarse de forma independiente.
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
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; las fundaciones, health/contenedores, Compose Local, AC-JAXON-059, observabilidad base y la baseline lógica de backup/restore fueron autorizados y verificados dentro de su alcance, sin extender esa autorización al resto del backlog.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de los incrementos backend/frontend ya verificados.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
- Las autorizaciones posteriores y consumidas cubrieron únicamente las fundaciones, publicaciones, CI, health/contenedores, Compose Local y sincronizaciones documentales expresamente indicadas; no autorizan nuevos incrementos, workflows, Pull Requests, releases, publicación de imágenes ni despliegues.
