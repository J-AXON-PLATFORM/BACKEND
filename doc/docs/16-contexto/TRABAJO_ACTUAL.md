# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Último bloque: `OLA3-SUP-002b`, completado, integrado y verificado en `frontend` y `backend` el 2026-10-20. Corrige el listado resumido de tickets autorizado por `AC-JAXON-071`, conservando la historia de `OLA3-SUP-002a` y `AC-JAXON-070`.
- Implementado: alta, consulta individual completa y listado paginado resumido sin `description` para tickets propios de `IT_OPERATOR`; alta y consulta paginada de comentarios propios `PUBLIC`; ownership aplicado en SQL; orden estable; `support.ticket_comments`; outbox transaccional `TICKET_COMMENT_ADDED` sin contenido sensible; UI estricta y OpenAPI 3.0.
- No implementado: búsqueda/listado global, notas `INTERNAL`, asignación, cambio de prioridad o estado, adjuntos, edición/borrado, resolución/cierre/reapertura, SSE de Support, IA, Automation, consumidor outbox o auditoría materializada. Tampoco cambian `automation-collector`, workflows, SFTPGo, Compose o infraestructura Production.
- Estado remoto histórico verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`; `backend` fue publicado inicialmente mediante `TEC-GIT-001C`.
- Estado remoto de contenedores verificado el 2026-10-08: `BACKEND#5` fue integrado en `develop` con squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge quedaron verdes. `FRONTEND#2` fue integrado con squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge quedaron verdes. No se publicaron imágenes ni artefactos.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` es privado; `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: `OLA3-SUP-002b` cerrado; cualquier incremento posterior requiere delimitación y autorización propias.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: login, activo, pre-registro, activación, conexión SFTP, envío puntual, procesamiento Collector, aviso SSE, refetch REST, visualización técnica, estado operativo Agent derivado, tarea explícita `COLLECT_SNAPSHOT`, alta/consulta/listado propio de tickets y conversación pública están integrados. REST continúa como fuente oficial.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend: conserva los gates existentes sin cambiar el workflow. OpenAPI 3.0 es el contrato actual sobre `/api/v1`; OpenAPI 1.0/1.1/2.0/2.1/2.2/2.3/2.4/2.5 y Agent 1.0 permanecen inmutables. La CI de PR y post-merge de `OLA3-SUP-002b` quedó verde.
- CI frontend: PR hacia `develop/main`, push a `develop` y ejecución manual; Node.js 24.21.0; `npm ci`, lint, typecheck, Vitest/RTL, build, auditorías, build standalone, smoke no-root/read-only y scan Trivy bloqueante HIGH/CRITICAL. La auditoría completa conserva la excepción cerrada solo para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo.
- CI Python: conserva sync/lock, Ruff, pytest, bundle, build, auditoría y entrypoints; 98 pruebas cubren también intentos, jitter/cap, clasificación, persistencia, reinicio, idempotencia y cleanup de Results sin cambiar el workflow. `OLA3-SUP-002b` no modifica este repositorio.
- Riesgos frontend abiertos: ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial soporte ESLint 10; Dependabot confirma como única alerta inicial `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, y la excepción debe retirarse cuando exista corrección oficial o desaparezca la cadena.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Próxima acción técnica: delimitar y autorizar por separado un nuevo incremento pequeño; no iniciar implementación sin esa autorización.
- Evidencia remota verificada: `AUTOMATION-COLLECTOR#6`, squash `5bec06539b7fa3014e8231cde84b960bf4f74a50`, tuvo runs verdes `38935923442` y `38935999273`; `BACKEND#18`, squash `12c877b9f890e48e47bc338dcee024b155b92e09`, tuvo runs verdes `38936213131` y `38941377804`.
- Evidencia remota de `OLA2-TSK-001a`: `AUTOMATION-COLLECTOR#7`, squash `cb324b498b7e0f13324c7485a167c2999cac9683`, tuvo runs verdes `39192254745` y `39192308664`; `FRONTEND#11`, squash `a173c0816a104851561635831235c08d4bf8f4b7`, tuvo runs verdes `39192380140` y `39192566258`; `BACKEND#23`, squash `478d5cae56c73a28c88783e123b8e635d6c1ff12`, tuvo runs verdes `39192945194` y `39194236599`.
- Evidencia remota de `OLA2-RSL-001a`: `AUTOMATION-COLLECTOR#8`, squash `bc508336af7c2082a2a36e20c1dd180d4507c169`, tuvo runs verdes `39254574671` de PR y `39254685186` post-merge.
- Evidencia remota de `OLA3-SUP-001a`: `FRONTEND#12`, squash `aa1995484e1ce7b45eae2ad4bbccf7847a9547e5`, tuvo runs verdes `39309591894` de PR y `39309969666` post-merge; `BACKEND#26`, squash `10883a944377cc97a8ec2fec34a5d13574f76a40`, tuvo runs verdes `39341186082` de PR y `39343168032` post-merge.
- Evidencia remota de `OLA3-SUP-002a`: `FRONTEND#13`, squash `82ac1bead85a0b660cbbc51ffbd9981bf96c1f31`, tuvo runs verdes `39472237415` de PR y `39472574612` post-merge; `BACKEND#28`, squash `f641e4abcdedc9f332072759489fadd61d91af1d`, tuvo runs verdes `39472965325` de PR y `39475825907` post-merge.
- Evidencia remota de `OLA3-SUP-002b`: `FRONTEND#14`, squash `e4f27bc765ee1446ba0b915c6c94618072b8f820`, tuvo runs verdes `39508796592` de PR y `39509165232` post-merge; `BACKEND#30`, squash `fb0a726725796d7b367567458023fce030d2f305`, tuvo runs verdes `39509548841` de PR y `39511809204` post-merge.
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

- No reabrir AC-JAXON-001..071 salvo contradicción documental real o instrucción expresa.
- `DESIGN_READY` != `IMPLEMENTATION_READY` != `PRODUCTION_READY`.
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; los incrementos hasta `OLA3-SUP-002b` consumen únicamente sus cortes expresos y no autorizan el resto de OLA 3 ni el lifecycle completo de Support.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de los incrementos backend/frontend ya verificados.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
- Las autorizaciones posteriores y consumidas cubrieron únicamente las fundaciones, publicaciones, CI, health/contenedores, Compose Local y sincronizaciones documentales expresamente indicadas; no autorizan nuevos incrementos, workflows, Pull Requests, releases, publicación de imágenes ni despliegues.
