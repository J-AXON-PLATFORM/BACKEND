# Enrutador de documentación

> **Clasificación:** OBLIGATORIO  
> **Estado:** Operativo

## Propósito

Leer únicamente la documentación capaz de cambiar la decisión o implementación actual.

## Rutas

| Trabajo | Empezar por | Ampliar si aplica |
|---|---|---|
| Nueva funcionalidad | Proyecto Maestro + HU + flujo + contexto módulo | arquitectura, datos, seguridad, contratos |
| Decisión técnica | Marco decisiones + NFR/restricciones | ADR/RFC, benchmarks, operación |
| Backend | contexto módulo + guías backend + flujo | API, datos, seguridad, resiliencia |
| Frontend | contexto módulo + arquitectura frontend + flujo | contrato, accesibilidad, rendimiento |
| Móvil | `18-plataformas/MOVIL.md` + flujo | contratos, seguridad, offline |
| CLI | `18-plataformas/CLI.md` | contratos, empaquetado |
| Batch/worker | `18-plataformas/BATCH_WORKERS.md` | datos, resiliencia, observabilidad |
| Serverless | `18-plataformas/SERVERLESS.md` | cloud, resiliencia, costes |
| Microservicios | arquitectura distribuida | contratos, resiliencia, datos, observabilidad |
| Evento/mensaje | flujo asíncrono + contrato | idempotencia, outbox, retry/replay |
| Rendimiento | NFR + rendimiento/capacidad | DB, caché, pruebas |
| Base de datos | arquitectura datos + estrategia DB | migraciones, calidad, escala |
| ETL/analítica | calidad/linaje + arquitectura datos | escalabilidad, privacidad |
| Cloud | arquitectura cloud | IaC, seguridad, costes, DR |
| IaC | IaC + entorno | cloud, seguridad, CI |
| Despliegue | estrategia despliegue + entorno | migraciones, rollback, observabilidad |
| Seguridad | requisitos + flujo | threat model, datos, supply chain |
| Incidente | gestión incidentes + señales | runbook, postmortem, DR |
| DR | recuperación + backups | cloud, datos, runbooks |
| E2E | flujo E2E + aceptación | contratos + capas afectadas |
| Cambio contrato | contrato + consumidores/proveedor | compatibilidad, contract tests |
| Error | evidencia + flujo afectado | regresión, contrato, ADR |

## Regla de contexto

No cargar una carpeta completa por comodidad.

Cada expansión de contexto debe responder:

> ¿Este documento puede cambiar la solución, detectar un riesgo o definir una validación necesaria?

Si la respuesta es no, no cargarlo.

## Instancia J-AXON -- ruta inmediata CKP-JAXON-005

Estado: baseline `DESIGN_READY`; Lotes 30--54 y AC-JAXON-059..094 aprobados; OLA 3 está integrada y verificada hasta `OLA3-SUP-006a`, OLA 4 está cerrada hasta `OLA4-DIS-008b`, OLA 5 está integrada y verificada hasta `OLA5-AI-006a` y `OLA6-AUD-001a`/`001b` están integradas y verificadas. Solo permanece activo el cierre documental de `001b` bajo `AC-JAXON-094`; `PRODUCTION_READY = NO`.

Estado remoto histórico verificado el 2026-10-07: la organización oficial nueva `J-AXON-PLATFORM` contiene los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos canónicos `backend`, `frontend` y `automation-collector`; `backend` fue inicializado y publicado mediante `TEC-GIT-001C`.

Estado remoto histórico al cierre de `TEC-PLT-003a`, verificado el 2026-10-08: `Backend CI` fue integrado en `develop` mediante el Pull Request `#1`, fusionado con `Squash and merge` tras checks verdes de la última revisión. `main` no fue modificado y podía diferir temporalmente de `develop`. En ese corte, `frontend` y `automation-collector` permanecían vacíos y sin Git local. No se crearon backlog remoto, Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues. La organización histórica distinta `j-axon` no fue modificada.

Estado frontend verificado el 2026-10-08: `FRONTEND` fue publicado con `main` y `develop`; `Frontend CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `0a34d6dadacc95351544996235faf6bcb0b21b65`; los runs de PR `37849248403` y post-merge `37849379045` quedaron verdes. La producción npm está limpia y la auditoría completa controla una excepción temporal única para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo. ESLint 9.39.5 permanece como riesgo EOL.

Estado Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` fue publicado con `main` y `develop`; contiene una fundación Python 3.14.8/uv 0.12.24 con Agent y Discovery Executor separados, sin SFTP o discovery funcional. `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs de PR `37852626079` y post-merge `37852683236` quedaron verdes, incluida la auditoría sin vulnerabilidades conocidas. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.

Los bloques técnicos de OLA 0 fueron completados. Identity, Inventory, Agent/Collector autorizado, Support hasta lifecycle terminal, Discovery hasta creación de activo desde dispositivo, AI hasta deshabilitación de fuentes y Auditoría hasta `HUMAN_USER_CREATED` 2.0 están integrados. `AC-JAXON-094` ratifica retrospectivamente la representación escalar canónica de `roles` y autoriza solo su cierre documental; otros eventos auditables, backfill, reportes, purga, generación y Production siguen fuera de alcance. Para un trabajo posterior cargar obligatoriamente las cinco fuentes de continuidad y luego únicamente los documentos específicos capaces de cambiar su delimitación:

- `docs/12-agil/GESTION_BACKLOG.md`, `docs/12-agil/GUIAS_TAREAS.md` y `docs/12-agil/FLUJO_KANBAN.md`;
- los documentos específicos de plataforma, arquitectura, contratos, datos, seguridad y pruebas capaces de delimitar el incremento;
- la primera vertical E2E solo si el alcance analizado la afecta.

LUIS autorizó un flujo de continuación incremental: después de verificar cada tarea, el coordinador determina el siguiente incremento pequeño y prepara un alcance concreto. No interpretar ese flujo, `DESIGN_READY` ni `TEC-PLT-001a` como permiso ilimitado para implementar toda OLA 0. Crear o modificar repositorios, ramas, Issues, Projects, workflows, GitHub, código, despliegues o infraestructura Production fuera de un alcance concreto requiere autorización expresa de LUIS.

Para cambios backend/frontend, los workflows clasifican `DOCS_ONLY|APPLICATION|PLATFORM_FULL` con fallback cerrado; el nivel completo conserva todos los gates históricos. `BACKEND#61` clasificó `APPLICATION`. El cierre de `AC-JAXON-094` modifica exclusivamente `backend/doc/` y debe clasificar `DOCS_ONLY`, salvo nivel fail-closed más estricto. `frontend` y `automation-collector` no cambian. Los rulesets permanecen pendientes por la limitación del plan para repositorios privados; PR y checks verdes se aplican como disciplina operativa, no como bloqueo técnico de GitHub.

Para trabajo remoto, cualquier cambio adicional en los tres repositorios debe analizarse y delimitarse en una tarea independiente, solicitando antes autorización expresa de LUIS. Las autorizaciones consumidas no cubren backlog, nuevas CI, reglas de protección, releases ni despliegue.
