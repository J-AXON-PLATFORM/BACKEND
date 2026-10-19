# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Último bloque: `OLA2-RSL-001a`, completado, integrado y verificado en `automation-collector` el 2026-10-19. Añade exclusivamente resiliencia acotada para entregar Results terminales de `COLLECT_SNAPSHOT`, autorizada por `AC-JAXON-068`.
- Implementado: máximo cinco intentos por episodio de entrega Result, primer intento inmediato, exponential backoff con full jitter de base 5 s y cap 300 s, clasificación cerrada de fallos transitorios y recuperación desde el ledger sin reejecutar la capability. El Result durable conserva bytes e IDs entre intentos/reinicios; la transferencia sigue siendo atómica e idempotente y solo limpia su `.part` propio.
- No implementado: otros tipos de tarea, `TASK_STARTED`, spool general, retries de polling/snapshots/capabilities, snapshots periódicos, polling del navegador, eventos SSE nuevos, cuarentena, borrado/purga, alertas individuales, daemon/servicio, revocación, rotación, Discovery, PyInstaller o infraestructura Production. No cambian contratos, dependencias, migraciones, SFTPGo, Compose o workflows.
- Estado remoto histórico verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`; `backend` fue publicado inicialmente mediante `TEC-GIT-001C`.
- Estado remoto de contenedores verificado el 2026-10-08: `BACKEND#5` fue integrado en `develop` con squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge quedaron verdes. `FRONTEND#2` fue integrado con squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge quedaron verdes. No se publicaron imágenes ni artefactos.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` es privado; `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: `OLA2-RSL-001a` cerrado; cualquier incremento posterior requiere delimitación y autorización propias.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: login, activo, pre-registro, activación, conexión SFTP, envío puntual, procesamiento Collector, aviso SSE, refetch REST, visualización técnica, estado operativo Agent derivado y tarea explícita `COLLECT_SNAPSHOT` están integrados. REST continúa como fuente oficial.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend: conserva los gates existentes sin cambiar el workflow. OpenAPI 2.3 es el contrato actual y mantiene compatibilidad con 2.2; OpenAPI 1.0/1.1/2.0/2.1/2.2 y Agent 1.0 permanecen inmutables. La CI de PR y post-merge de `OLA2-TSK-001a` quedó verde.
- CI frontend: PR hacia `develop/main`, push a `develop` y ejecución manual; Node.js 24.21.0; `npm ci`, lint, typecheck, Vitest/RTL, build, auditorías, build standalone, smoke no-root/read-only y scan Trivy bloqueante HIGH/CRITICAL. La auditoría completa conserva la excepción cerrada solo para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo.
- CI Python: conserva sync/lock, Ruff, pytest, bundle, build, auditoría y entrypoints; 98 pruebas cubren también intentos, jitter/cap, clasificación, persistencia, reinicio, idempotencia y cleanup de Results sin cambiar el workflow.
- Riesgos frontend abiertos: ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial soporte ESLint 10; Dependabot confirma como única alerta inicial `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, y la excepción debe retirarse cuando exista corrección oficial o desaparezca la cadena.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: delimitar y autorizar por separado un nuevo incremento pequeño; no iniciar implementación sin esa autorización.
- Evidencia remota verificada: `AUTOMATION-COLLECTOR#6`, squash `5bec06539b7fa3014e8231cde84b960bf4f74a50`, tuvo runs verdes `38935923442` y `38935999273`; `BACKEND#18`, squash `12c877b9f890e48e47bc338dcee024b155b92e09`, tuvo runs verdes `38936213131` y `38941377804`.
- Evidencia remota de `OLA2-TSK-001a`: `AUTOMATION-COLLECTOR#7`, squash `cb324b498b7e0f13324c7485a167c2999cac9683`, tuvo runs verdes `39192254745` y `39192308664`; `FRONTEND#11`, squash `a173c0816a104851561635831235c08d4bf8f4b7`, tuvo runs verdes `39192380140` y `39192566258`; `BACKEND#23`, squash `478d5cae56c73a28c88783e123b8e635d6c1ff12`, tuvo runs verdes `39192945194` y `39194236599`.
- Evidencia remota de `OLA2-RSL-001a`: `AUTOMATION-COLLECTOR#8`, squash `bc508336af7c2082a2a36e20c1dd180d4507c169`, tuvo runs verdes `39254574671` de PR y `39254685186` post-merge.
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

- No reabrir AC-JAXON-001..068 salvo contradicción documental real o instrucción expresa.
- `DESIGN_READY` != `IMPLEMENTATION_READY` != `PRODUCTION_READY`.
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; los incrementos hasta `OLA2-RSL-001a` consumen únicamente sus cortes expresos y no autorizan el resto de OLA 2.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de los incrementos backend/frontend ya verificados.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
- Las autorizaciones posteriores y consumidas cubrieron únicamente las fundaciones, publicaciones, CI, health/contenedores, Compose Local y sincronizaciones documentales expresamente indicadas; no autorizan nuevos incrementos, workflows, Pull Requests, releases, publicación de imágenes ni despliegues.
