# Trabajo actual

> **Clasificación:** CONDICIONAL  
> **Checkpoint externo:** CKP-JAXON-005 | 2026-09-29.

Mantener este archivo corto: orienta a una IA sin sustituir el Registro de Acuerdos ni el Proyecto Maestro.

- Estado actual: baseline documental v1 `DESIGN_READY`; implementación iniciada de forma acotada; `PRODUCTION_READY = NO`.
- Descubrimiento inicial: cerrado.
- Refinamiento/diseño v1: **Lotes 30--54 aprobados y consolidados**.
- Trabajo activo: `OLA3-SUP-006a` + `TEC-QLT-007a`, autorizado en `backend` y `frontend` bajo `AC-JAXON-078`; frontend está integrado y verificado, backend está implementado/verificado localmente y pendiente de Pull Request.
- Implementado localmente: resolución/cierre/reapertura con `expectedVersion`, historial terminal append-only, outbox mínimo, OpenAPI 4.3, timeline y formularios estrictos; CI `DOCS_ONLY|APPLICATION|PLATFORM_FULL` fail-closed en ambos repositorios.
- No implementado en este corte: reasignación, adjuntos, búsqueda global, SSE de Support, IA, Automation, consumidor outbox o auditoría materializada. No cambian dependencias, `automation-collector` o infraestructura Production.
- Estado remoto histórico verificado el 2026-10-07: existe la organización `J-AXON-PLATFORM` con los repositorios privados `BACKEND`, `FRONTEND` y `AUTOMATION-COLLECTOR`, correspondientes a los nombres lógicos `backend`, `frontend` y `automation-collector`; `backend` fue publicado inicialmente mediante `TEC-GIT-001C`.
- Estado remoto de contenedores verificado el 2026-10-08: `BACKEND#5` fue integrado en `develop` con squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` de PR y `37879749721` post-merge quedaron verdes. `FRONTEND#2` fue integrado con squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` de PR y `37880065517` post-merge quedaron verdes. No se publicaron imágenes ni artefactos.
- Estado remoto Python verificado el 2026-10-08: `AUTOMATION-COLLECTOR` es privado; `main` apunta a `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12`; `Python CI` fue integrado en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`; los runs `37852626079` de PR y `37852683236` post-merge quedaron verdes. No se crearon Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados o despliegues.
- Organización histórica: `j-axon` es distinta, contiene repositorios preexistentes ajenos al nuevo inicio controlado, no fue modificada y no debe confundirse con `J-AXON-PLATFORM`. La diferencia depende del identificador de organización, no solo de mayúsculas, que GitHub trata sin distinción efectiva.
- Autorización general para implementar/modificar GitHub/desplegar: **NO EXISTE**.
- Sprint actual: `OLA3-SUP-006a` + `TEC-QLT-007a` en verificación e integración controlada; cualquier incremento posterior requiere delimitación y autorización propias.
- Backlog: inicial aprobado; no publicado en GitHub.
- Primera vertical: login/logout, alta administrativa de usuarios, activo, Agent/Collector hasta Result resiliente y los cortes Support hasta prioridad/ciclo no terminal están integrados. El lifecycle terminal y su timeline están implementados localmente en ambos repositorios. REST continúa como fuente oficial.
- Dominios refinados: Identity, Discovery, Support, Inventory, Collector tasks, AI/RAG, Automation, Audit/Reporting.
- Contratos internos: commands/queries in-process; eventos durables por Transactional Outbox PostgreSQL `at-least-once`; SSE best-effort y REST fuente de verdad.
- CI backend/frontend local: clasificación determinista por riesgo, fallback `PLATFORM_FULL`, procedencia read-only para push y todos los gates previos intactos en completo. OpenAPI 4.3 es el contrato actual sobre `/api/v1`; 1.0--4.2 y Agent 1.0 permanecen inmutables.
- CI frontend completo conserva Node.js 24.21.0, `npm ci`, lint, typecheck, Vitest/RTL, build, auditorías, standalone, smoke y Trivy. La auditoría completa conserva la excepción cerrada solo para `GHSA-vfj7-8cjw-p6xm` en `braces@3.0.3` de desarrollo.
- CI Python: conserva sync/lock, Ruff, pytest, bundle, build, auditoría y entrypoints; 98 pruebas cubren también intentos, jitter/cap, clasificación, persistencia, reinicio, idempotencia y cleanup de Results sin cambiar el workflow. `OLA3-SUP-004a` y su corrección `004b` no modifican este repositorio.
- Riesgos frontend abiertos: ESLint 9.39.5 está EOL y debe migrarse cuando la cadena oficial soporte ESLint 10; Dependabot confirma como única alerta inicial `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, y la excepción debe retirarse cuando exista corrección oficial o desaparezca la cadena.
- Excepción correctiva activa: el primer run de `FRONTEND#18` (`39922328221`) falló solo en audit Production por advisories nuevas de Next.js; se autorizó actualizar Next.js/`eslint-config-next` 16.3.8 -> 16.4.2 y realizar un segundo push al mismo PR, sin ampliar funcionalidad ni workflows.
- IDs técnicos: UUIDv4; externos como strings opacos.
- Verificación local del bloque activo: 108 pruebas backend y 56 frontend, OpenAPI 4.3/compatibilidad histórica, clasificadores, `actionlint` 1.7.10, builds y E2E Chromium multirepo real verdes. El primer `clean verify` detectó la expectativa de tablas desactualizada y el primer E2E una columna incorrecta solo en su aserción SQL nueva; ambas pruebas se corrigieron y sus ejecuciones completas posteriores quedaron verdes.
- Observación de caché SFTPGo: la clave histórica `c356f7ce...` se reproduce con los bytes de `develop` y la ruta absoluta del runner GitHub, mientras el mismo contenido produce `32775b3d...` en el checkout local porque `cache-key.sh` incorpora al digest la ruta emitida por `sha256sum`. El workflow del bloque rota además la clave actual. La independencia respecto de la ruta queda como deuda conocida fuera de `AC-JAXON-078`; no invalida la evidencia histórica ni se corrige silenciosamente en este corte.
- Próxima acción técnica: integrar backend, verificar su run post-merge, limpiar ramas y emitir el reporte final autorizado.
- Evidencia remota verificada: `AUTOMATION-COLLECTOR#6`, squash `5bec06539b7fa3014e8231cde84b960bf4f74a50`, tuvo runs verdes `38935923442` y `38935999273`; `BACKEND#18`, squash `12c877b9f890e48e47bc338dcee024b155b92e09`, tuvo runs verdes `38936213131` y `38941377804`.
- Evidencia remota de `OLA2-TSK-001a`: `AUTOMATION-COLLECTOR#7`, squash `cb324b498b7e0f13324c7485a167c2999cac9683`, tuvo runs verdes `39192254745` y `39192308664`; `FRONTEND#11`, squash `a173c0816a104851561635831235c08d4bf8f4b7`, tuvo runs verdes `39192380140` y `39192566258`; `BACKEND#23`, squash `478d5cae56c73a28c88783e123b8e635d6c1ff12`, tuvo runs verdes `39192945194` y `39194236599`.
- Evidencia remota de `OLA2-RSL-001a`: `AUTOMATION-COLLECTOR#8`, squash `bc508336af7c2082a2a36e20c1dd180d4507c169`, tuvo runs verdes `39254574671` de PR y `39254685186` post-merge.
- Evidencia remota de `OLA3-SUP-001a`: `FRONTEND#12`, squash `aa1995484e1ce7b45eae2ad4bbccf7847a9547e5`, tuvo runs verdes `39309591894` de PR y `39309969666` post-merge; `BACKEND#26`, squash `10883a944377cc97a8ec2fec34a5d13574f76a40`, tuvo runs verdes `39341186082` de PR y `39343168032` post-merge.
- Evidencia remota de `OLA3-SUP-002a`: `FRONTEND#13`, squash `82ac1bead85a0b660cbbc51ffbd9981bf96c1f31`, tuvo runs verdes `39472237415` de PR y `39472574612` post-merge; `BACKEND#28`, squash `f641e4abcdedc9f332072759489fadd61d91af1d`, tuvo runs verdes `39472965325` de PR y `39475825907` post-merge.
- Evidencia remota de `OLA3-SUP-002b`: `FRONTEND#14`, squash `e4f27bc765ee1446ba0b915c6c94618072b8f820`, tuvo runs verdes `39508796592` de PR y `39509165232` post-merge; `BACKEND#30`, squash `fb0a726725796d7b367567458023fce030d2f305`, tuvo runs verdes `39509548841` de PR y `39511809204` post-merge.
- Evidencia remota de `OLA1-IAM-002a`: `FRONTEND#15`, squash `1347f7f4006dbcbef0eff5d9c03b50ec410f2e76`, tuvo runs verdes `39537607523` de PR y `39537884458` post-merge; `BACKEND#32`, squash `b8c9f354c82a7bc849e112512e7ea2746752ba7f`, tuvo runs verdes `39539823211` de PR y `39541562446` post-merge. El run backend previo `39538137754` falló por `GO-2026-6809` y fue sustituido por la corrección mínima autorizada a Go 1.26.10.
- Evidencia remota de `OLA3-SUP-003a`: `FRONTEND#16`, squash `d39fcbdd38ebdddf04d3a143909e43cc6fb72fdc`, tuvo runs verdes `39589547411` de PR y `39589902859` post-merge; `BACKEND#34`, squash `4fd0137edec6247e2c208cd93b180d5009925527`, tuvo runs verdes `39590317308` de PR y `39593017095` post-merge.
- Evidencia remota de `OLA3-SUP-004a`: `FRONTEND#17`, squash `32db50cce9933d69b0b6ad7c12e871c491c99243`, tuvo runs verdes `39681656581` de PR y `39682015289` post-merge; `BACKEND#36`, squash `ecc497f1b7ab097730efeb80f12aa16aaaea0717`, tuvo run definitivo verde `39682421438` de PR y run `39688305298` post-merge. El primer intento del run backend de PR falló por una aserción temporal preexistente del contador SSE de Collector; la reejecución sin cambios quedó verde.
- Evidencia remota de `OLA3-SUP-004b` + `TEC-QLT-005a`: `BACKEND#38`, squash `f46ba60ba6f5f276e630309958656e07369f2c31`, tuvo runs verdes `39716165236` de PR y `39718160097` post-merge. La prueba SSE corregida pasó 20/20 repeticiones locales consecutivas y `clean verify` pasó 101/101 pruebas.
- Evidencia remota de `TEC-QLT-006a`: `BACKEND#41`, squash `7c3233b5efd8242ac42222154e99070728957422`, tuvo run definitivo de PR `39874341640` y post-merge `39876819453` verdes. La rama local y remota autorizada fue eliminada.
- Evidencia remota frontend de `OLA3-SUP-005a`: `FRONTEND#18`, squash `64dee5fede9f9d7d1abf13e2f115c87b7e2f9260`, tuvo run corregido de PR `39924355831` y post-merge `39924662623` verdes. El run inicial `39922328221` falló únicamente por advisories de Next.js incorporadas durante la ejecución y originó la excepción correctiva autorizada a 16.4.2.
- Evidencia remota backend de `OLA3-SUP-005a`: `BACKEND#42`, squash `cad9f30849d745ca88651c0bc9d8bb618f3d33f9`, tuvo run de PR `39955461750` y post-merge `39958345344` verdes; ramas eliminadas y repositorios sincronizados.
- Evidencia remota frontend de `OLA3-SUP-006a` + `TEC-QLT-007a`: `FRONTEND#19`, squash `65c9a5ecd3b78cd4306ecc281f2f4f65a2b6f5c8`, tuvo runs `40004926261` de PR y `40005341450` post-merge verdes; ambos se clasificaron `PLATFORM_FULL`.
- Los runs previos `39865316279`, `39866621799` y `39872945593` fallaron respectivamente por incompatibilidad del driver, tamaño/confirmación de la exportación `mode=max` y el `cache.lock` transitorio; no fueron relanzados y cada corrección quedó trazable por commit.
- El segundo run de PR `39866621799` completó pruebas upstream, `govulncheck` y la exportación `mode=max`, pero falló antes del build runtime al confirmar la caché, tras 18 min 31 s; no fue relanzado. La corrección usa `mode=min` y diagnósticos explícitos de layout.
- El tercer run de PR `39872945593` completó pruebas upstream, `govulncheck` y la exportación `mode=min`, pero el inventario rechazó el `cache.lock` transitorio dejado por BuildKit; no fue relanzado. La corrección elimina solo ese lock antes de inventariar y conserva el rechazo del resto de contenido adicional.
- Cierre remoto `OLA3-SUP-005a`: completo. `AC-JAXON-078` autoriza únicamente los dos Pull Requests y verificaciones del bloque actual; cualquier otro cambio GitHub requiere autorización independiente.
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

- No reabrir AC-JAXON-001..078 salvo contradicción documental real o instrucción expresa.
- `DESIGN_READY` != `IMPLEMENTATION_READY` != `PRODUCTION_READY`.
- `IMPLEMENTATION_READY` se evalúa por alcance concreto; `OLA3-SUP-006a` + `TEC-QLT-007a` autorizan únicamente el lifecycle terminal y CI escalonada aquí descritos, no el resto de OLA 3.
- Decidido/documentado != implementado != verificado.
- Los `POR_DECIDIR` restantes son deliberados y están ligados a Production, organización, evidencia o scope futuro; no rellenarlos artificialmente.
- No asumir autorización de código, Git, GitHub, despliegues o infraestructura Production a partir de la aprobación de los Lotes 49--54 ni de la finalización de los incrementos backend/frontend ya verificados.
- La autorización consumida por `TEC-GIT-001C` cubrió únicamente la publicación controlada de `backend`; no autoriza publicar `frontend` o `automation-collector`, operar Issues/Projects/Pull Requests, crear workflows o rulesets, publicar tags/releases/artefactos ni desplegar.
- Las autorizaciones posteriores y consumidas cubrieron únicamente las fundaciones, publicaciones, CI, health/contenedores, Compose Local y sincronizaciones documentales expresamente indicadas; no autorizan nuevos incrementos, workflows, Pull Requests, releases, publicación de imágenes ni despliegues.
