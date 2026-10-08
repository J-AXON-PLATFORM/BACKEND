# Resumen de arquitectura

> **Clasificación:** OBLIGATORIO  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.


<!-- JAXON-CKP005-DETAIL:START -->
## Refinamiento/diseño v1 aprobado hasta Lote 54

- Stack/runtime, cinco entornos, PostgreSQL/Flyway, contenedores/promoción, CI/CD, identidad humana, SFTP/Agent, contratos, primera vertical y resiliencia Agent: Lotes 30--40.
- Datos/operación: `DocumentStorage` filesystem v1, SHA-256, retenciones, backups/PITR, restores, RPO <=1 h y RTO <=8 h.
- RAG: provider-agnostic, chunking ~800/120, pgvector HNSW, retrieval autorizado, reindexación.
- Automation: estado oficial en J-AXON, n8n async, HMAC-SHA-256 callbacks, idempotencia.
- Infra: LAN/DNS/HTTPS/reverse proxy, TLS 1.3/1.2, runtime secret files protegidos, IaC formal posterior.
- Observabilidad: Grafana/Prometheus/Loki/Tempo, retenciones, SLOs y alertas iniciales.
- Rendimiento: targets p95/p99, k6 + simulador Agent/SFTP y perfil de benchmark.
- Frontend: App Router híbrido ligero + TanStack Query + WCAG 2.2 AA objetivo.
- Discovery: scopes/executors/jobs/devices, mailbox SFTP dedicado, perfil BASIC no intrusivo.
- Support: lifecycle/prioridades/asignación/comentarios/adjuntos/cierre-reapertura y autorización refinados.
- Inventory: ficha administrativa, catálogo de tipos, baseline técnica aceptada, Discovery create/link y concurrencia refinados.
- Collector: catálogo read-only de tareas/capabilities/estados/límites refinado.
- Audit/Reporting: eventos auditables, append-only, reportes Assets/Agents/Tickets y CSV refinados.
- Interno: APIs in-process para commands/queries + Transactional Outbox PostgreSQL `at-least-once` para hechos durables; sin broker.
- Readiness: IDs UUIDv4, baseline v1 controlada y estado `DESIGN_READY` documental; implementación sigue no iniciada ni autorizada.
<!-- JAXON-CKP005-DETAIL:END -->

## Arquitectura vigente

- Backend Java/Spring Boot: monolito modular de ocho módulos.
- Frontend Next.js/React/TypeScript independiente.
- Agent y ejecutor Discovery en Python; Collector central en Spring Boot.
- Tres repositorios: `frontend`, `backend`, `automation-collector`; documentación general en `backend/doc/`.
- PostgreSQL por entorno, schema por módulo, pgvector y Flyway.
- Escenario base on-premise/LAN; v1 single-tenant por instalación.
- Web: HTTPS/REST/OpenAPI/Swagger UI; avisos selectivos SSE.
- Agents: SFTP + JSON/JSON Schema para snapshots, tareas y respuestas.
- Módulo->módulo: operaciones por interfaces internas; hechos por eventos de aplicación cuando aplique; sin broker externo por defecto.
- n8n e IA detrás de adapters/contratos controlados por backend.
- Docker/Compose inicialmente para componentes aplicables; no Kubernetes.

## Restricciones de frontera

Cada módulo posee sus datos. No escritura directa cross-module. Inventory es ficha oficial; Collector observa; Discovery no incorpora/instala automáticamente; IA no ejecuta acciones autónomas; Automation solicita; Audit/Reporting consume sin mutar datos ajenos.

## Documentación activada

Perfil `ESTANDAR`; HLD, ADR, arquitectura distribuida, resiliencia, datos, CI/CD, observabilidad, operación y trazabilidad. Los dominios funcionales principales y contratos internos quedaron cerrados para v1. Siguen abiertos deliberadamente provider/model IA, hardware Productivo definitivo, medio físico storage, reverse proxy/PKI/secret manager concretos, IaC real, SLA comercial y tuning/thresholds que dependen de evidencia.

---
> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla dinámica

## Propósito

Dar una vista corta y canónica de la arquitectura actual.

## Estilo de arquitectura

- ...

## Módulos/servicios

- ...

## Dependencias principales

- ...

## Contratos/fronteras principales

- ...

## Referencias C4

- Contexto:
- Contenedores:
- Componentes:

## Restricciones

- ...

## Decisiones

- Ver `adr/` y `rfc/` cuando apliquen.
