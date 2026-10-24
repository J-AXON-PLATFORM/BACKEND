# Pendientes y orden de continuación -- J-AXON

> **Clasificación:** CONDICIONAL, activa para continuidad.  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.  
> **Estado:** baseline `DESIGN_READY`; implementación técnica de OLA 0 cerrada con supply-chain multirepo; `PRODUCTION_READY = NO`.

## Punto de reanudación

No reabrir AC-JAXON-001..079 ni los Lotes 30--54 salvo instrucción expresa o contradicción documental real.

La baseline técnica de OLA 0 fue completada y verificada. `OLA1-IAM-001a` incorpora la primera funcionalidad: tablas exclusivamente en `identity`, login, `/auth/me`, sesión opaca, bootstrap, límites de intentos, outbox productor, UI mínima y Playwright real. El bloque fue completado, integrado y verificado el 2026-10-11.

La baseline técnica planificada para OLA 0 queda cerrada. `OLA1-INV-001a` completó, integró y verificó el corte mínimo de Inventory: correcciones acotadas de sesión, alta y consulta por ID para `IT_OPERATOR`, persistencia y outbox `ASSET_CREATED`, UI y E2E real. Los incrementos posteriores avanzaron Agent, SFTP, Collector y SSE solo en los cortes descritos a continuación; el resto no autorizado continúa pendiente.

`OLA2-AGT-001a` implementó pre-registro, identidad técnica y clave Ed25519; `OLA2-SFTP-001a` añadió después SFTPGo Local/Test, activación y verificación de conexión; `OLA2-SNP-001a` completó el primer snapshot técnico puntual y su transferencia atómica.

`OLA2-SFTP-001a` está completado, integrado y verificado. Añade únicamente imagen SFTPGo Local/Test reproducible, cuenta y buzón lógico aislados, activación idempotente a `ACTIVE`, host key Ed25519 preprovisionada, configuración/verificación Paramiko y UI/E2E multirepo. No añade snapshots, transferencia funcional, polling, tareas, spool, procesamiento Collector, SSE, revocación, rotación ni Production.

`OLA2-SNP-001a` está completado, integrado y verificado. Añade solo recolección mínima POSIX/Linux, validación Agent 1.0 offline y transferencia `/snapshots/{messageId}.json.part -> .json`, más el virtual folder/perfil SFTPGo requerido. No procesa ni persiste el snapshot en Collector y no añade migraciones, contratos, frontend, workflows, polling, retries, spool, tareas, SSE, revocación, rotación o Production.

`OLA2-COL-001a` está completado, integrado y verificado. Añade montaje read-only del volumen SFTPGo, reconciliación acotada, validación Agent 1.0/identidad, ledger y observaciones en `collector`, deduplicación/conflictos, `GET /assets/{assetId}/technical-state` y UI técnica separada de Inventory. No modifica contratos ni `automation-collector`, y no añade polling Agent, tareas/respuestas, spool, SSE, cuarentena, borrado/purga, revocación, rotación, Discovery o Production.

`OLA2-SSE-001a` está completado, integrado y verificado. Añade el aviso selectivo `ASSET_TECHNICAL_STATE_UPDATED` posterior al commit, stream autenticado por activo, límites/heartbeat/timeout, correlación asíncrona y métricas, más invalidación exacta y refetch REST en frontend. Añade OpenAPI 2.1 compatible y mantiene inmutables 1.0/1.1/2.0 y Agent 1.0. No añade replay, broker, polling Agent, tareas/respuestas, spool, otros eventos SSE, migraciones, cambios en `automation-collector` o Production.

`OLA2-STA-001a` está completado, integrado y verificado. Añade `UNKNOWN|ONLINE|OFFLINE` derivado al consultar un Agent, evidencia `lastSeenAt`/`lastSnapshotAt` solo desde snapshots aceptados, umbral configurable de cinco minutos, gauges agregados y UI estricta segmentada por usuario. Añade OpenAPI 2.2 compatible y mantiene inmutables 1.0/1.1/2.0/2.1 y Agent 1.0. No añade migración, scheduler, polling, heartbeat, evento SSE, cambios en `automation-collector` o Production.

`OLA2-TSK-001a` está completado, integrado y verificado en los tres repositorios. `AUTOMATION-COLLECTOR#7`, `FRONTEND#11` y `BACKEND#23` tienen CI de PR/post-merge verde. El corte añade solo la vertical explícita `COLLECT_SNAPSHOT`: ejecución de los contratos Task/Result Agent 1.0 existentes, migración y lifecycle Collector, `/tasks` y `/responses` SFTP aislados, runner foreground con ledger/reinicio, REST/UI manual y OpenAPI 2.3 compatible. No añade otros tipos, retries burst, spool general, periodicidad, daemon, nuevo SSE, Discovery o Production.

`OLA2-RSL-001a` está completado, integrado y verificado. `AUTOMATION-COLLECTOR#8`, squash `bc508336af7c2082a2a36e20c1dd180d4507c169`, tiene CI de PR `39254574671` y post-merge `39254685186` verde. El corte añade solo cinco intentos máximos por episodio para entregar un Result terminal durable, con primer intento inmediato, exponential full jitter base 5 s/cap 300 s, clasificación cerrada y reinicio sin reejecutar `COLLECT_SNAPSHOT`. No modifica contratos, dependencias o workflows y no añade spool general, retries de polling/snapshots/capabilities, periodicidad, daemon, otros tipos, nuevos SSE, Discovery o Production.

`OLA3-SUP-001a` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#12`, squash `aa1995484e1ce7b45eae2ad4bbccf7847a9547e5`, tuvo CI verde `39309591894` de PR y `39309969666` post-merge; `BACKEND#26`, squash `10883a944377cc97a8ec2fec34a5d13574f76a40`, tuvo CI verde `39341186082` de PR y `39343168032` post-merge. El corte añade solo alta y consulta individual de tickets propios para `IT_OPERATOR`, validación opcional de activo por contrato Inventory, persistencia/outbox Support, UI estricta y OpenAPI 2.4 compatible. No añade listado, lifecycle, asignación, comentarios, adjuntos, SSE, IA, Automation, consumidor outbox, cambios en `automation-collector` o Production.

`OLA3-SUP-002a` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#13`, squash `82ac1bead85a0b660cbbc51ffbd9981bf96c1f31`, tuvo CI verde `39472237415` de PR y `39472574612` post-merge; `BACKEND#28`, squash `f641e4abcdedc9f332072759489fadd61d91af1d`, tuvo CI verde `39472965325` de PR y `39475825907` post-merge. El corte añade listado paginado de tickets propios y conversación `PUBLIC` append-only, ownership sin enumeración, outbox `TICKET_COMMENT_ADDED` mínimo, UI estricta y OpenAPI 2.5 compatible. No añade búsqueda/listado global, notas internas, lifecycle, asignación, adjuntos, edición/borrado, SSE, IA, Automation, consumidor outbox, cambios en `automation-collector` o Production.

`OLA3-SUP-002b` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#14`, squash `e4f27bc765ee1446ba0b915c6c94618072b8f820`, tuvo CI verde `39508796592` de PR y `39509165232` post-merge; `BACKEND#30`, squash `fb0a726725796d7b367567458023fce030d2f305`, tuvo CI verde `39509548841` de PR y `39511809204` post-merge. Corrige el defecto histórico del listado de `OLA3-SUP-002a`: devuelve resúmenes cerrados sin `description`, no selecciona esa columna, conserva el detalle individual completo y publica OpenAPI 3.0. No añade migraciones, datos, dependencias, cambios de ownership/paginación/comentarios/outbox, lifecycle, búsqueda global, notas internas, `automation-collector` o Production.

`OLA1-IAM-002a` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#15`, squash `1347f7f4006dbcbef0eff5d9c03b50ec410f2e76`, tuvo CI verde `39537607523` de PR y `39537884458` post-merge; `BACKEND#32`, squash `b8c9f354c82a7bc849e112512e7ea2746752ba7f`, tuvo CI verde `39539823211` de PR y `39541562446` post-merge. Añade logout aislado de la sesión actual, alta de usuarios `ACTIVE` exclusiva de `SYSTEM_ADMIN`, roles v1 explícitos, outbox Identity, UI estricta y OpenAPI 3.1 compatible. No añade gestión posterior de usuarios, reset de contraseña, revocación global, MFA, SSO, cambios en `automation-collector` o Production. La fixture SFTPGo conserva 2.7.6 y actualiza únicamente su builder a Go 1.26.10 para cerrar `GO-2026-6809`.

`OLA3-SUP-003a` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#16`, squash `d39fcbdd38ebdddf04d3a143909e43cc6fb72fdc`, tuvo CI verde `39589547411` de PR y `39589902859` post-merge; `BACKEND#34`, squash `4fd0137edec6247e2c208cd93b180d5009925527`, tuvo CI verde `39590317308` de PR y `39593017095` post-merge. Añade bandeja paginada para `SUPPORT_TECHNICIAN`, detalle y conversación `PUBLIC` de solo lectura, claim concurrente/idempotente con `expectedVersion`, outbox `TICKET_ASSIGNED`, UI estricta y OpenAPI 4.0. No añade comentarios técnicos, notas internas, cambios de estado/prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE, IA, Automation, consumidor outbox, cambios en `automation-collector` o Production.

`OLA3-SUP-004a` está completado, integrado y verificado en `frontend` y `backend`. `FRONTEND#17`, squash `32db50cce9933d69b0b6ad7c12e871c491c99243`, tuvo CI verde `39681656581` de PR y `39682015289` post-merge; `BACKEND#36`, squash `ecc497f1b7ab097730efeb80f12aa16aaaea0717`, tuvo CI definitiva verde `39682421438` de PR y `39688305298` post-merge. Añade conversación combinada `PUBLIC|INTERNAL` solo para el técnico actualmente asignado, mantiene requester y lectura pre-claim exclusivamente `PUBLIC`, persiste `TICKET_COMMENT_ADDED` con payload mínimo, incorpora migración segura, UI estricta y OpenAPI 4.1 compatible. No añade cambios de estado/prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE, IA, Automation, consumidor outbox, cambios en `automation-collector` o Production.

`OLA3-SUP-004b` + `TEC-QLT-005a` está completado, integrado y verificado exclusivamente en `backend`. `BACKEND#38`, squash `f46ba60ba6f5f276e630309958656e07369f2c31`, tuvo CI verde `39716165236` de PR y `39718160097` post-merge. Corrige `TICKET_COMMENT_ADDED.resource_version` requester mediante lectura bloqueante de la versión vigente sin mutar el ticket, y estabiliza solo la prueba SSE con una espera observable acotada; 20/20 repeticiones SSE y 101/101 pruebas de `clean verify` quedaron verdes. No cambia contratos, migraciones, payloads, dependencias, lógica productiva SSE, `frontend`, `automation-collector` o Production.

`TEC-QLT-006a` está completado, integrado y verificado bajo `AC-JAXON-076`. `BACKEND#41`, squash `7c3233b5efd8242ac42222154e99070728957422`, tuvo run definitivo de PR `39874341640` y post-merge `39876819453` verdes; la rama local y remota fue eliminada. Añade exclusivamente una caché BuildKit exacta para SFTPGo Local/Test y conserva pruebas upstream, `govulncheck`, runtime, Trivy, SBOM y gates posteriores. No cambia producto, contratos, datos, otros repositorios o Production.

`OLA3-SUP-005a` está completado, integrado y verificado. `FRONTEND#18`, squash `64dee5fede9f9d7d1abf13e2f115c87b7e2f9260`, tuvo runs verdes `39924355831` y `39924662623`; `BACKEND#42`, squash `cad9f30849d745ca88651c0bc9d8bb618f3d33f9`, tuvo runs verdes `39955461750` y `39958345344`. Añade prioridad oficial y solo las tres transiciones no terminales autorizadas, concurrencia optimista/no-op, eventos mínimos, OpenAPI 4.2 y UI estricta.

`OLA3-SUP-006a` + `TEC-QLT-007a` está completado, integrado y verificado. `FRONTEND#19`, squash `65c9a5ecd3b78cd4306ecc281f2f4f65a2b6f5c8`, tuvo runs verdes `40004926261` y `40005341450`; `BACKEND#43`, squash `8593e02c4a9b69be5121d953a24834ba93e10dd1`, tuvo runs verdes `40005994234` y `40008967710`. Los cuatro runs se clasificaron `PLATFORM_FULL`.

`OLA4-DIS-001a` + `TEC-QLT-008a` está completado, integrado y verificado. `FRONTEND#21`, squash `3624adab3bf8f418e6c1e46e5820b83fc73f7b07`, tuvo runs verdes `40050022669` y `40050263740`; `BACKEND#44`, squash `72a1deab9a0fd7685fdc9da266c7cb4d68bdb5fc`, tuvo runs verdes `40061731474` y `40063098808`. Los cuatro runs definitivos se clasificaron `PLATFORM_FULL`.

`OLA4-DIS-002a` está implementado y verificado localmente en `automation-collector`, `frontend` y `backend`, pendiente de integración remota. Añade solo pre-registro/listado/consulta `PENDING`, asociación inicial inmutable con scopes activos, identidad técnica separada y public key Ed25519, inicializador local endurecido, OpenAPI 4.5 y UI estricta. Activación, revocación, modificación de asociaciones, SFTP Discovery, jobs, escaneo, dispositivos e incorporación a Inventory permanecen pendientes.

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
- Riesgo temporal frontend: las auditorías npm de producción y completa están limpias tras el refresh correctivo autorizado a `undici@8.12.2` y `zod@4.6.6`; el verificador conserva fallo cerrado. ESLint 9.39.5 sigue EOL y debe migrarse cuando la cadena oficial sea compatible con ESLint 10.
- OpenAPI 4.5 es el contrato web local actual; añade únicamente Discovery Executors conservando 4.4. OpenAPI 1.0--4.4 permanecen inmutables. El bundle Agent 1.0, su copia vendorizada y manifest tampoco cambian. Activación/SFTP/jobs/dispositivos Discovery, gestión posterior de usuarios, MFA/SSO, reasignación/adjuntos/búsqueda/SSE de Support, otros tipos de tarea, spool general, retries, purga y PyInstaller siguen pendientes.
- La observabilidad base backend produce logs JSON con correlación, métricas Prometheus y trazas W3C/OpenTelemetry opcionales. Continúan pendientes la infraestructura centralizada, dashboards, alertas, validación operativa de SLI/SLO y una estrategia que pueda aproximar la aspiración de conservar trazas de error sin prometerla mediante head sampling.
- La reconciliación Collector genera un `correlationId` UUIDv4 explícito por candidato, lo instala de forma acotada en MDC y lo transporta al aviso SSE posterior al commit; no reutiliza contexto servlet ni asume propagación implícita entre hilos.
- La baseline de `TEC-OPS-005a`/`TEC-OPS-006a` crea dumps custom y manifests verificables, restaura únicamente sobre un destino vacío y separado, y prueba escenarios positivos/negativos en recursos efímeros. Backup/DR Production continúa pendiente.
- La baseline supply-chain final de OLA 0 usa `develop` como default branch sin cambiar el rol productivo de `main`; habilita Dependency Graph, Dependabot alerts y security updates agrupados; deshabilita version updates ordinarios; y añade Gitleaks bloqueante con historial completo y redacción a los tres jobs existentes. No habilita auto-merge, Secret Protection, CodeQL, SBOM, firma o publicación.
- `TEC-QLT-006a` cerró `BACKEND#41` y no requiere acción remota adicional. La corrección documental retrospectiva queda incluida en el alcance backend de `OLA3-SUP-005a`.
- Las autorizaciones de los bloques backend, frontend, `automation-collector`, health/contenedores, Compose Local, observabilidad y backup/restore lógico fueron consumidas. La excepción posterior cubre únicamente este cierre documental. No operar Issues/Projects, crear otros workflows o rulesets, tags, releases, imágenes o artefactos publicados, ni desplegar sin autorización expresa de LUIS.
- No asumir autorización general para nuevas tareas de implementación a partir de los incrementos ya verificados; cada incremento requiere alcance concreto.
- Siguiente paso remoto pendiente: integrar y verificar exclusivamente `OLA4-DIS-002a` en orden `automation-collector`, `frontend`, `backend`, y limpiar ramas.
- Próxima acción técnica posterior: emitir el reporte final autorizado y delimitar por separado cualquier incremento nuevo.
- La primera vertical E2E ya fue alcanzada mediante incrementos autorizados; la continuación mantiene el mismo enfoque incremental y no implementa “todo J-AXON de una vez”.

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
