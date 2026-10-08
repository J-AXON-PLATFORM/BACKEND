# Pendientes y orden de continuación -- J-AXON

> **Clasificación:** CONDICIONAL, activa para continuidad.  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.  
> **Estado:** descubrimiento y refinamiento/diseño v1 cerrados hasta Lote 54; baseline `DESIGN_READY`; implementación iniciada de forma acotada con `TEC-PLT-001a`, `TEC-PLT-002a` y `TEC-PLT-003a`; `PRODUCTION_READY = NO`.

## Punto de reanudación

No reabrir AC-JAXON-001..058 ni los Lotes 30--54 salvo instrucción expresa o contradicción documental real.

`TEC-PLT-001a` y `TEC-PLT-002a` fueron completadas y verificadas el 2026-10-06; `TEC-PLT-003a` fue completada y verificada el 2026-10-08. Existen el bootstrap estructural del backend, la integración JDBC/Flyway probada contra PostgreSQL 18.6 con pgvector 0.8.6 y la CI base que ejecuta `clean verify` en GitHub Actions. La migración inicial habilita `vector` y crea exactamente los ocho schemas aprobados, sin tablas de negocio ni lógica funcional.

El resto de OLA 0 y la primera vertical E2E continúan pendientes. La siguiente tarea técnica no ha sido iniciada y debe analizarse y delimitarse como incremento pequeño antes de ejecutarse. LUIS autorizó la continuidad incremental para que, después de verificar cada tarea, el coordinador determine el siguiente incremento y prepare un alcance concreto; esto no equivale a autorización general para implementar toda OLA 0.

## Decisiones cerradas adicionales en CKP-JAXON-005

- Lote 49: Support/Tickets lifecycle, prioridades, asignación, conversación, adjuntos, cierre/reapertura, permisos e integración IA/Automation.
- Lote 50: Inventory administrativo, tipos, responsable/ubicación, baseline técnica aceptada, Discovery create/link y concurrencia.
- Lote 51: catálogo read-only de tareas Collector, capabilities, lifecycle, límites y permisos.
- Lote 52: AuditEvent, catálogo auditable, acceso/filtros, reportes Assets/Agents/Tickets y CSV.
- Lote 53: contratos públicos in-process + Transactional Outbox PostgreSQL, `at-least-once`, idempotencia y separación SSE/n8n/AI.
- Lote 54: readiness, UUIDv4, baseline v1 controlada y clasificación de pendientes.

## Pendientes legítimos antes de Production

- hardware Production definitivo y dimensionamiento real;
- medio físico definitivo de `DocumentStorage` (disco/volumen/NAS) y storage real de backups;
- reverse proxy, DNS, PKI/certificados y secret storage concretos;
- owner operativo nominal por instalación y ventana real de soporte;
- política/infra final de deployment según la instalación.

## Pendientes `EVIDENCE_DRIVEN`

- tuning PostgreSQL/JVM/pools;
- thresholds adicionales de circuit breaker/bulkhead/rate limiting;
- necesidad real de cache/Redis, réplicas o particionamiento;
- capacidad certificada para ~1000 Agents;
- ajustes de límites/defaults de Agent/SFTP/outbox después de pruebas;
- frecuencia operativa final de ciertos drills.

## Producto/organización todavía abierto

- stakeholders nominales, fecha objetivo, presupuesto y restricciones regulatorias/proveedor concretas;
- SLA comercial solo si existe contrato/necesidad;
- legal hold/retenciones regulatorias específicas si una organización las exige.

## IA/infra futura abierta sin bloquear core

- proveedor/modelo LLM y embeddings, local vs externo y hardware IA;
- OCR avanzado, reranker, búsqueda híbrida, fine-tuning/agentes autónomos;
- futuro object storage/cloud;
- Vault/OpenBao/otro secret manager empresarial si se requiere;
- Terraform/OpenTofu/otra IaC real cuando se conozca infraestructura.

## Implementación y Git

- Estado remoto verificado el 2026-10-07: organización `J-AXON-PLATFORM` creada y repositorios privados `BACKEND` (`https://github.com/J-AXON-PLATFORM/BACKEND`), `FRONTEND` (`https://github.com/J-AXON-PLATFORM/FRONTEND`) y `AUTOMATION-COLLECTOR` (`https://github.com/J-AXON-PLATFORM/AUTOMATION-COLLECTOR`) creados. Los nombres lógicos canónicos siguen siendo `backend`, `frontend` y `automation-collector`.
- GitHub trata estos identificadores sin distinción efectiva entre mayúsculas y minúsculas. La organización histórica distinta `j-axon` y sus repositorios preexistentes no fueron modificados y no forman parte del nuevo inicio oficial.
- `backend` fue inicializado y publicado mediante `TEC-GIT-001C`, con dos commits y ramas permanentes `main`/`develop` sincronizadas. `frontend` y `automation-collector` continúan vacíos y sin Git local. No existen Issues, Projects, Pull Requests, workflows, rulesets, tags, releases, artefactos o despliegues creados por esta publicación.
- Estado remoto con el cierre de `TEC-PLT-003a` verificado el 2026-10-08: `Backend CI` fue integrado en `develop` mediante el Pull Request `#1`, fusionado con `Squash and merge` tras verificar en verde la última revisión. El workflow corre en PR hacia `develop/main`, push a `develop` y manualmente; usa Java 25, Maven Wrapper, acciones fijadas por SHA completo y permisos `contents: read`, sin secretos ni despliegues. `main` no fue modificado y puede quedar temporalmente detrás de `develop`.
- Contratos y SFTP siguen pendientes en CI hasta que existan artefactos ejecutables aplicables. Rulesets y protecciones automáticas continúan pendientes por la limitación del plan actual para repositorios privados; la disciplina de PR y checks verdes es operativa, no forzada técnicamente por GitHub.
- La autorización específica de `TEC-GIT-001C` fue consumida y se limitó a `backend`. No publicar los otros repositorios, operar Issues/Projects/PRs, crear workflows o rulesets, tags, releases o artefactos, ni desplegar sin autorización expresa de LUIS.
- No asumir autorización general para nuevas tareas de implementación a partir de `TEC-PLT-001a`, `TEC-PLT-002a` o `TEC-PLT-003a`; cada incremento requiere alcance concreto.
- Siguiente paso remoto pendiente: analizar y delimitar por separado cualquier inicialización y publicación de `frontend` o `automation-collector`, solicitando antes autorización expresa de LUIS para ese alcance.
- Próxima acción técnica: analizar y delimitar exclusivamente el siguiente incremento pequeño de OLA 0, sin iniciar todavía su ejecución.
- El objetivo sigue siendo avanzar por OLA 0 y alcanzar temprano la primera vertical E2E, no implementar “todo J-AXON de una vez”.

## Reglas de continuidad

- Registro de Acuerdos es la primera autoridad.
- Mantener cinco entornos: Local, Development, Test efímero, QA, Production.
- SFTP sigue siendo canal Agent/Discovery; no sustituir por HTTP/HTTPS sin nueva decisión.
- Inventory conserva ficha oficial; Collector conserva observaciones; aceptación oficial es explícita.
- Support conserva reglas de tickets; IA/n8n/Automation no las saltan.
- Eventos internos durables no convierten al sistema en arquitectura event-driven ni introducen broker.
- Diseño aprobado != implementado/verificado.
- Documentación español; código/identificadores inglés.

## Referencias

- [Acuerdos](../00-gobierno/REGISTRO_ACUERDOS_JAXON.md)
- [Proyecto Maestro](../../PROYECTO_MAESTRO.md)
- [Trabajo actual](TRABAJO_ACTUAL.md)
- [Enrutador documental](ENRUTADOR_DOCUMENTACION.md)
