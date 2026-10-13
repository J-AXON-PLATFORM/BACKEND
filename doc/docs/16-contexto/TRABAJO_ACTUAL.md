# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Bloque actual: `OLA2-SFTP-001a`, implementado y verificado localmente, añade exclusivamente SFTPGo Local/Test, aprovisionamiento lógico, transición idempotente a `ACTIVE`, host-key verification y verificación puntual del subsistema SFTP. `AUTOMATION-COLLECTOR#5` y `FRONTEND#7` ya están integrados; el cierre backend depende del estado real del PR que contiene este documento y de su run post-merge.
- Implementado localmente en el candidato: OpenAPI major 2.0; `POST /api/v1/agents/{agentId}/activate`; máximo un Agent `ACTIVE` por activo; evento `AGENT_ACTIVATED`; cuenta SFTPGo sin contraseña y con public key única; buzón lógico aislado con ACLs; imagen reproducible no-root; configuración Agent POSIX y Paramiko 5.0.0; UI con datos de conexión solo en memoria; y E2E Chromium real contra frontend/backend/PostgreSQL/SFTPGo/Agent.
- No implementado: revocación Agent, rotación de clave, snapshots o transferencias funcionales, procesamiento Collector, estado técnico, SSE, polling, tareas, spool, PyInstaller, servicio del sistema, listado/búsqueda Agent, consumidores outbox, logout, gestión de usuarios o Discovery. Tampoco existe infraestructura Production. La baseline lógica no implementa base backup, WAL/PITR, cifrado, storage externo, scheduling, retención, documentos ni validación Production de RPO/RTO.
- Estado remoto histórico verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`; `backend` fue publicado inicialmente mediante `TEC-GIT-001C`.
- Estado remoto de contenedores verificado el 2026-10-08: `BACKEND#5` fue integrado en `develop` con squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge quedaron verdes. `FRONTEND#2` fue integrado con squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge quedaron verdes. No se publicaron imágenes ni artefactos.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` es privado; `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: `OLA2-SFTP-001a` con validación local cerrada e integración multirepo en curso controlado.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: login, activo y pre-registro Agent están integrados; el candidato `OLA2-SFTP-001a` alcanza activación y conexión SFTP verificada. Snapshot, transferencia, procesamiento Collector, SSE y visualización técnica siguen pendientes.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend candidata: conserva los gates existentes y añade OpenAPI 2.0, build/verificación de SFTPGo fijado, prueba contra SFTPGo real y smoke Compose. OpenAPI 1.0/1.1 y JSON Schema Agent 1.0 permanecen inmutables. No publica imágenes, artefactos o despliegues.
- CI frontend: PR hacia `develop/main`, push a `develop` y ejecución manual; Node.js 24.21.0; `npm ci`, lint, typecheck, Vitest/RTL, build, auditorías, build standalone, smoke no-root/read-only y scan Trivy bloqueante HIGH/CRITICAL. La auditoría completa conserva la excepción cerrada solo para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo.
- CI Python: conserva sync/lock, Ruff, pytest, build, auditoría y ambos entrypoints; añade Paramiko 5.0.0 fijado para verificación SFTP estricta, sin snapshots, tareas, secretos cross-repo, artefactos o despliegues.
- Riesgos frontend abiertos: ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial soporte ESLint 10; Dependabot confirma como única alerta inicial `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, y la excepción debe retirarse cuando exista corrección oficial o desaparezca la cadena.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: tramitar y verificar el PR backend de `OLA2-SFTP-001a`; no considerar cerrado el incremento antes de confirmar su run post-merge. No ampliar el alcance a snapshots, polling, tareas o procesamiento Collector.
- Evidencia remota parcial verificada el 2026-10-12: `AUTOMATION-COLLECTOR#5`, squash `61805a7822d32db6dd1d99f2d5f15d26ee7474f1`, tuvo runs verdes `38335720613` y `38335774137`; `FRONTEND#7`, squash `4937641729f0efa36d8c0ac735d1d3e4ac661006`, tuvo runs verdes `38335837986` y `38336042216`.
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

- No reabrir AC-JAXON-001..060 salvo contradicción documental real o instrucción expresa.
- `DESIGN_READY` != `IMPLEMENTATION_READY` != `PRODUCTION_READY`.
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; `OLA1-IAM-001a`, `OLA1-INV-001a` y `OLA2-AGT-001a` consumen únicamente sus cortes expresos y no autorizan el resto de OLA 2.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de los incrementos backend/frontend ya verificados.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
- Las autorizaciones posteriores y consumidas cubrieron únicamente las fundaciones, publicaciones, CI, health/contenedores, Compose Local y sincronizaciones documentales expresamente indicadas; no autorizan nuevos incrementos, workflows, Pull Requests, releases, publicación de imágenes ni despliegues.
