# Pendientes y orden de continuación -- J-AXON

> **Clasificación:** CONDICIONAL, activa para continuidad.  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.  
> **Estado:** baseline `DESIGN_READY`; implementación técnica de OLA 0 cerrada con supply-chain multirepo; `PRODUCTION_READY = NO`.

## Punto de reanudación

No reabrir AC-JAXON-001..067 ni los Lotes 30--54 salvo instrucción expresa o contradicción documental real.

La baseline técnica de OLA 0 fue completada y verificada. `OLA1-IAM-001a` incorpora la primera funcionalidad: tablas exclusivamente en `identity`, login, `/auth/me`, sesión opaca, bootstrap, límites de intentos, outbox productor, UI mínima y Playwright real. El bloque fue completado, integrado y verificado el 2026-10-11.

La baseline técnica planificada para OLA 0 queda cerrada. `OLA1-INV-001a` completó, integró y verificó el corte mínimo de Inventory: correcciones acotadas de sesión, alta y consulta por ID para `IT_OPERATOR`, persistencia y outbox `ASSET_CREATED`, UI y E2E real. La funcionalidad Agent posterior al pre-registro, Collector, SFTP, SSE y el resto no autorizado continúan pendientes.

`OLA2-AGT-001a` implementó pre-registro, identidad técnica y clave Ed25519; `OLA2-SFTP-001a` añadió después SFTPGo Local/Test, activación y verificación de conexión; `OLA2-SNP-001a` completó el primer snapshot técnico puntual y su transferencia atómica.

`OLA2-SFTP-001a` está completado, integrado y verificado. Añade únicamente imagen SFTPGo Local/Test reproducible, cuenta y buzón lógico aislados, activación idempotente a `ACTIVE`, host key Ed25519 preprovisionada, configuración/verificación Paramiko y UI/E2E multirepo. No añade snapshots, transferencia funcional, polling, tareas, spool, procesamiento Collector, SSE, revocación, rotación ni Production.

`OLA2-SNP-001a` está completado, integrado y verificado. Añade solo recolección mínima POSIX/Linux, validación Agent 1.0 offline y transferencia `/snapshots/{messageId}.json.part -> .json`, más el virtual folder/perfil SFTPGo requerido. No procesa ni persiste el snapshot en Collector y no añade migraciones, contratos, frontend, workflows, polling, retries, spool, tareas, SSE, revocación, rotación o Production.

`OLA2-COL-001a` está completado, integrado y verificado. Añade montaje read-only del volumen SFTPGo, reconciliación acotada, validación Agent 1.0/identidad, ledger y observaciones en `collector`, deduplicación/conflictos, `GET /assets/{assetId}/technical-state` y UI técnica separada de Inventory. No modifica contratos ni `automation-collector`, y no añade polling Agent, tareas/respuestas, spool, SSE, cuarentena, borrado/purga, revocación, rotación, Discovery o Production.

`OLA2-SSE-001a` está completado, integrado y verificado. Añade el aviso selectivo `ASSET_TECHNICAL_STATE_UPDATED` posterior al commit, stream autenticado por activo, límites/heartbeat/timeout, correlación asíncrona y métricas, más invalidación exacta y refetch REST en frontend. Añade OpenAPI 2.1 compatible y mantiene inmutables 1.0/1.1/2.0 y Agent 1.0. No añade replay, broker, polling Agent, tareas/respuestas, spool, otros eventos SSE, migraciones, cambios en `automation-collector` o Production.

`OLA2-STA-001a` está completado, integrado y verificado. Añade `UNKNOWN|ONLINE|OFFLINE` derivado al consultar un Agent, evidencia `lastSeenAt`/`lastSnapshotAt` solo desde snapshots aceptados, umbral configurable de cinco minutos, gauges agregados y UI estricta segmentada por usuario. Añade OpenAPI 2.2 compatible y mantiene inmutables 1.0/1.1/2.0/2.1 y Agent 1.0. No añade migración, scheduler, polling, heartbeat, evento SSE, cambios en `automation-collector` o Production.

`OLA2-TSK-001a` está implementado y verificado localmente en los tres repositorios. `AUTOMATION-COLLECTOR#7` y `FRONTEND#11` están integrados con CI de PR/post-merge verde; backend sigue pendiente de publicación e integración. El corte añade solo la vertical explícita `COLLECT_SNAPSHOT`: ejecución de los contratos Task/Result Agent 1.0 existentes, migración y lifecycle Collector, `/tasks` y `/responses` SFTP aislados, runner foreground con ledger/reinicio, REST/UI manual y OpenAPI 2.3 compatible. No añade otros tipos, retries burst, spool general, periodicidad, daemon, nuevo SSE, Discovery o Production.

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
- Los tres repositorios fueron inicializados y publicados en sus alcances autorizados. En `backend`, `develop` apunta al squash de contenedores `90c550714b861beb7d6388cf571744efcda4af8d`; en `frontend`, `main` conserva `ffec9bdd39d5168ed5f7d3bbaa68d5d4bc5860ab` y `develop` apunta al squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`. En `automation-collector`, `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12` y `develop` a `a040c5fba34fae22903088b82191e8355deaab26`.
- Estado remoto con el cierre de `TEC-PLT-003a` verificado el 2026-10-08: `Backend CI` fue integrado en `develop` mediante el Pull Request `#1`, fusionado con `Squash and merge` tras verificar en verde la última revisión. El workflow corre en PR hacia `develop/main`, push a `develop` y manualmente; usa Java 25, Maven Wrapper, acciones fijadas por SHA completo y permisos `contents: read`, sin secretos ni despliegues. `main` no fue modificado y puede quedar temporalmente detrás de `develop`.
- Estado remoto frontend verificado el 2026-10-08: `Frontend CI` fue integrado en `develop` mediante el Pull Request `#1`, squash `0a34d6dadacc95351544996235faf6bcb0b21b65`; el run de PR `37849248403` y el post-merge `37849379045` quedaron verdes. Ejecuta `npm ci`, lint, typecheck, Vitest/RTL, build y auditorías bloqueantes, con acciones fijadas por SHA, permisos `contents: read`, sin secretos ni despliegues.
- Estado remoto Python verificado el 2026-10-08: `Python CI` fue integrado en `develop` mediante el Pull Request `#1`, squash `a040c5fba34fae22903088b82191e8355deaab26`; el run de PR `37852626079` y el post-merge `37852683236` quedaron verdes. Ejecuta sync/lock, Ruff, seis pruebas, build, auditoría y ambos entrypoints, con acciones fijadas por SHA, permisos `contents: read`, sin secretos ni despliegues.
- Estado remoto de health/contenedores verificado el 2026-10-08: `BACKEND#5`, squash `90c550714b861beb7d6388cf571744efcda4af8d`, tuvo runs verdes `37879426717` y `37879749721`; `FRONTEND#2`, squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`, tuvo runs verdes `37879426584` y `37880065517`. Backend valida health/probes, fixture PostgreSQL/pgvector, build/smoke y scans; frontend valida standalone, runtime sin gestores JavaScript, smoke y scan. No se publicaron imágenes, Packages ni artefactos.
- Estado Compose Local verificado el 2026-10-09: `compose.yaml`, `.env.example` y `scripts/verify-compose.sh` implementan PostgreSQL/pgvector predeterminado y backend opcional, puertos loopback, volumen/red dedicados, configuración ficticia y smoke aislado. `Backend CI` conserva sus gates y añade validación/smoke Compose sin publicación ni despliegue.
- Riesgo temporal frontend: la producción npm está limpia, pero la auditoría completa acepta solo `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, transitiva de desarrollo sin parche oficial al 2026-10-08. Cerrar al publicar corrección oficial, desaparecer la cadena o sustituir la herramienta mediante decisión aprobada. ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial sea compatible con ESLint 10.
- OpenAPI 2.3 es el contrato web local actual y mantiene compatibilidad con 2.2. OpenAPI 1.0, 1.1, 2.0, 2.1 y 2.2 permanecen inmutables. El bundle Agent 1.0, su copia vendorizada y manifest tampoco cambian; backend incorpora sus schemas Task/Result existentes al empaquetado runtime. Otros tipos de tarea, retries burst, spool general, otros eventos SSE, purga y PyInstaller siguen pendientes.
- La observabilidad base backend produce logs JSON con correlación, métricas Prometheus y trazas W3C/OpenTelemetry opcionales. Continúan pendientes la infraestructura centralizada, dashboards, alertas, validación operativa de SLI/SLO y una estrategia que pueda aproximar la aspiración de conservar trazas de error sin prometerla mediante head sampling.
- La reconciliación Collector genera un `correlationId` UUIDv4 explícito por candidato, lo instala de forma acotada en MDC y lo transporta al aviso SSE posterior al commit; no reutiliza contexto servlet ni asume propagación implícita entre hilos.
- La baseline de `TEC-OPS-005a`/`TEC-OPS-006a` crea dumps custom y manifests verificables, restaura únicamente sobre un destino vacío y separado, y prueba escenarios positivos/negativos en recursos efímeros. Backup/DR Production continúa pendiente.
- La baseline supply-chain final de OLA 0 usa `develop` como default branch sin cambiar el rol productivo de `main`; habilita Dependency Graph, Dependabot alerts y security updates agrupados; deshabilita version updates ordinarios; y añade Gitleaks bloqueante con historial completo y redacción a los tres jobs existentes. No habilita auto-merge, Secret Protection, CodeQL, SBOM, firma o publicación.
- Las autorizaciones de los bloques backend, frontend, `automation-collector`, health/contenedores, Compose Local, observabilidad y backup/restore lógico fueron consumidas. La excepción posterior cubre únicamente este cierre documental. No operar Issues/Projects, crear otros workflows o rulesets, tags, releases, imágenes o artefactos publicados, ni desplegar sin autorización expresa de LUIS.
- No asumir autorización general para nuevas tareas de implementación a partir de los incrementos ya verificados; cada incremento requiere alcance concreto.
- Siguiente paso remoto pendiente: analizar y delimitar por separado cualquier cambio GitHub adicional, solicitando antes autorización expresa de LUIS.
- Próxima acción técnica: completar la integración y verificación remota backend de `OLA2-TSK-001a`; después, delimitar por separado cualquier incremento nuevo.
- El objetivo siguiente es alcanzar temprano la primera vertical E2E mediante incrementos autorizados, no implementar “todo J-AXON de una vez”.

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
