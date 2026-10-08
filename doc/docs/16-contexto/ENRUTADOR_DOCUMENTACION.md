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

Estado: baseline `DESIGN_READY`; Lotes 30--54 aprobados; implementación incremental iniciada de forma acotada; `PRODUCTION_READY = NO`. `TEC-PLT-001a` y `TEC-PLT-002a` fueron completadas y verificadas el 2026-10-06: existen el bootstrap estructural y la integración PostgreSQL 18.6/pgvector 0.8.6/Flyway con los ocho schemas aprobados, todavía sin tablas de negocio ni lógica funcional.

Estado remoto verificado el 2026-10-07: la organización oficial nueva `J-AXON-PLATFORM` contiene los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos canónicos `backend`, `frontend` y `automation-collector`. `backend` fue inicializado y publicado mediante `TEC-GIT-001C`, con dos commits y ramas `main`/`develop` sincronizadas; `frontend` y `automation-collector` permanecen vacíos y sin Git local. No se crearon backlog remoto, Pull Requests, workflows, rulesets, tags, releases, artefactos o despliegues. La organización histórica distinta `j-axon` no fue modificada.

La siguiente tarea técnica no ha sido iniciada. La ruta inmediata es analizar y delimitar exclusivamente el siguiente incremento pequeño de OLA 0. Para ese análisis, cargar obligatoriamente las cinco fuentes de continuidad y luego:

- `docs/12-agil/GESTION_BACKLOG.md`, `DEFINICION_PREPARADO.md` y `DEFINICION_TERMINADO.md`;
- los documentos específicos de plataforma, arquitectura, contratos, datos, seguridad y pruebas capaces de delimitar el incremento;
- la primera vertical E2E solo si el alcance analizado la afecta.

LUIS autorizó un flujo de continuación incremental: después de verificar cada tarea, el coordinador determina el siguiente incremento pequeño y prepara un alcance concreto. No interpretar ese flujo, `DESIGN_READY` ni `TEC-PLT-001a` como permiso ilimitado para implementar toda OLA 0. Crear o modificar repositorios, ramas, Issues, Projects, workflows, GitHub, código, despliegues o infraestructura Production fuera de un alcance concreto requiere autorización expresa de LUIS.

Para trabajo remoto, cualquier inicialización y publicación de `frontend` o `automation-collector` debe analizarse y delimitarse en una tarea independiente, solicitando antes autorización expresa de LUIS. La autorización consumida por `TEC-GIT-001C` se limitó a `backend` y no cubre CI/CD, backlog, reglas de protección, releases ni despliegue.
