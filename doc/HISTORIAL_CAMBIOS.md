# Historial de cambios

> **Instancia J-AXON | CKP-JAXON-005 | 2026-09-29**
> Cumplimentación parcial: solo decisiones aprobadas. Las guías genéricas se conservan; sus ejemplos no son requisitos aprobados del producto.

## Creación, consulta y despacho SFTP de Discovery Jobs -- OLA4-DIS-004a, 2026-10-25

`automation-collector` incorpora y valida offline una copia byte a byte del contrato Discovery Job 1.0. Backend añade persistencia y exclusión no terminal por executor, TTL de una hora, outboxes mínimos, creación/consulta `IT_OPERATOR`, publicación atómica y reconciliación de pendientes, además del virtual folder SFTPGo `/jobs` aislado y read-only para el executor. Frontend añade selección de executors/scopes activos, creación sin retry y consulta puntual; OpenAPI 5.1 conserva 5.0 byte a byte.

La verificación local queda verde con 74 pruebas frontend, lint, typecheck y build; un E2E Chromium multirepo real; y 141 pruebas backend `clean verify` con SFTPGo real, 18 migraciones y contratos. Una primera repetición final del E2E falló solo en `TestEventRuleProviderEvents` de la suite upstream SFTPGo por una lectura JSON truncada; la repetición exacta, sin cambios, pasó la suite upstream completa, `govulncheck`, Trivy y el recorrido Chromium. La revisión final añadió correlación por `Location` para el `PENDING` conservado tras `503`, expiración terminal si el scope pierde vigencia antes del despacho, retirada durable de cualquier archivo final encontrado fuera de TTL y prueba SFTP real de descarga propia con denegación de upload, borrado, rename y acceso cruzado. `AUTOMATION-COLLECTOR#11`, squash `82d1c5e47e19448a785c9ccb14cc4bd565e6fe34`, tuvo runs verdes `40202744473`/`40202798955`; `FRONTEND#24`, squash `456c9484f6b4903d0940d9dcc6133d907ed41fe0`, tuvo runs verdes `40266786130`/`40268393897` y clasificación `PLATFORM_FULL` autorizada puntualmente por LUIS debido al cambio necesario del arnés E2E. Backend permanece pendiente. Polling, ejecución, resultados, escaneo, dispositivos, incorporación y Production continúan fuera de alcance. `PRODUCTION_READY = NO`.

---

## Activación y canal SFTP Local/Test de Discovery Executor -- OLA4-DIS-003a, 2026-10-25

Los tres repositorios incorporaron activación `PENDING -> ACTIVE` exclusiva de `SYSTEM_ADMIN`, precondiciones de public key y al menos un scope asociado aún activo, reconciliación exacta de una cuenta SFTPGo aislada, evento `DISCOVERY_EXECUTOR_ACTIVATED` mínimo, configuración/verificación puntual Paramiko y respuesta pública conservada solo en memoria frontend. OpenAPI 5.0 es un major breaking porque el schema cerrado 4.5 solo admitía `PENDING` y versión `0`; las versiones 1.0--4.5 permanecen inmutables.

`AUTOMATION-COLLECTOR#10`, squash `9e52ecb12fe65fdb6bc8e9ecf8572a3b605a0d13`, tuvo runs verdes `40162404647`/`40162455936`; `FRONTEND#23`, squash `2da12a78c8961f179e549adc3190e2eda41ed726`, `40174799817`/`40174893425`; y `BACKEND#46`, squash `b0ea80a9f2514581dda3dcd86c12aa9c12786aee`, `40175030319`/`40176438498`. `AC-JAXON-081` está cerrado. `PRODUCTION_READY = NO`.

---

## Pre-registro e identidad local de Discovery Executor -- OLA4-DIS-002a, 2026-10-24

Los tres repositorios incorporaron el corte autorizado: inicializador Ed25519 sin red y estado separado del Agent; pre-registro `PENDING`, asociación inicial N:M con scopes activos, ownership Discovery/Identity sin acceso cross-schema, outboxes mínimos y cuatro operaciones REST exclusivas de `SYSTEM_ADMIN`; frontend responsive con parsers cerrados, estado sensible en memoria e instrucciones que nunca solicitan private key. OpenAPI 4.5 conserva 4.4 byte a byte.

La verificación dirigida y el E2E Chromium multirepo real quedaron verdes, incluida persistencia, privacidad de outboxes, idempotencia de clave y conservación de asociación tras deshabilitar el scope. `AUTOMATION-COLLECTOR#9`, squash `ba98aa904ce6d3deaaa91d27ec7752828f641ac3`, tuvo runs verdes `40113410669`/`40113466098`; `FRONTEND#22`, squash `800554785970c262eaee3effde7a8d34888c5ae1`, `40133522732`/`40133626229`; y `BACKEND#45`, squash `920cefbff0977d886a2447796846adf076556955`, `40133928890`/`40134154144`. `AC-JAXON-080` está cerrado. `PRODUCTION_READY = NO`.

---

## Ámbitos autorizados Discovery y calidad de contratos/caché -- OLA4-DIS-001a + TEC-QLT-008a, 2026-10-23

Backend incorpora `discovery.authorized_scopes` y outbox propio, normalización cerrada de nombre/CIDR, autoridad diferenciada `SYSTEM_ADMIN|IT_OPERATOR`, concurrencia optimista y cuatro operaciones REST. OpenAPI 4.4 conserva 4.3 byte a byte; un manifest declarativo sustituye el inventario imperativo del verificador. La clave SFTPGo deja de depender de la ruta absoluta del checkout.

Frontend añade administración/consulta responsive de scopes, parsers cerrados, caché por usuario/rol/página y mutaciones sin retry ni optimistic update. `e2e/**` pasa a `APPLICATION`, pero los PR del corte son `PLATFORM_FULL` por cambiar mecanismos CI. `FRONTEND#21` quedó integrado mediante squash `3624adab3bf8f418e6c1e46e5820b83fc73f7b07`, con runs verdes `40050022669`/`40050263740`; `BACKEND#44` quedó integrado mediante squash `72a1deab9a0fd7685fdc9da266c7cb4d68bdb5fc`, con runs verdes `40061731474`/`40063098808`. `AC-JAXON-079` está cerrado. Discovery Executor, jobs, SFTP, escaneo, dispositivos e incorporación a Inventory no formaron parte de ese corte. `PRODUCTION_READY = NO`.

El run inicial backend `40060796361` falló antes de los gates largos porque Trivy incorporó `CVE-2026-111497` para `brotli-libs 1.2.0-r1` de la fixture PostgreSQL. La excepción correctiva autorizada fijó únicamente `brotli-libs=1.2.1-r0`; el run definitivo y post-merge quedaron verdes.

El cierre retrospectivo autorizado registra `FRONTEND#19` (`65c9a5ecd3b78cd4306ecc281f2f4f65a2b6f5c8`, runs `40004926261`/`40005341450`) y `BACKEND#43` (`8593e02c4a9b69be5121d953a24834ba93e10dd1`, runs `40005994234`/`40008967710`), todos verdes y `PLATFORM_FULL`.

---

## Lifecycle terminal de Support y CI escalonada -- OLA3-SUP-006a + TEC-QLT-007a, 2026-10-23

Backend incorpora resolución, cierre y reapertura especializados con bloqueo, `expectedVersion`, historial `support.ticket_lifecycle_events` append-only y eventos mínimos `TICKET_RESOLVED|TICKET_CLOSED|TICKET_REOPENED`. OpenAPI 4.3 conserva 4.2 byte a byte y añade cinco operaciones. Frontend presenta timeline público, acciones contextuales sin retry/optimistic update y mantiene acceso técnico a cerrados asignados; los comentarios requester se deshabilitan en cerrado y vuelven tras reapertura.

Los workflows backend/frontend incorporan clasificación `DOCS_ONLY|APPLICATION|PLATFORM_FULL`, pruebas de tabla, fallback cerrado y procedencia read-only de PR para pushes a `develop`. El nivel completo conserva todos los gates previos y ambos PR del bloque se clasifican como `PLATFORM_FULL`. La verificación local concluye con 108 pruebas backend, 56 frontend, contratos OpenAPI, clasificadores, `actionlint` 1.7.10, builds y un E2E Chromium multirepo real con comprobación SQL de resolve, close, reopen, historial, versión, assignee y privacidad outbox. `FRONTEND#19` quedó integrado mediante squash `65c9a5ecd3b78cd4306ecc281f2f4f65a2b6f5c8`, con runs `40004926261` de PR y `40005341450` post-merge verdes y clasificación `PLATFORM_FULL`; PR backend, integración final y limpieza permanecen pendientes.

`OLA3-SUP-005a` quedó integrado también en backend mediante `BACKEND#42`, squash `cad9f30849d745ca88651c0bc9d8bb618f3d33f9`, con runs verdes `39955461750` de PR y `39958345344` post-merge. Sus ramas fueron eliminadas y los repositorios quedaron sincronizados antes de iniciar este bloque.

---

## Triaje y ciclo operativo no terminal -- OLA3-SUP-005a, 2026-10-23

Backend añade `PUT /api/v1/tickets/{ticketId}/priority` y `PUT /api/v1/tickets/{ticketId}/status` para el `SUPPORT_TECHNICIAN` actualmente asignado. Las mutaciones usan CSRF, `expectedVersion`, bloqueo transaccional y no-op sin nueva versión ni evento; una versión obsoleta o transición inválida responde `409`. La migración `V20261023000000__support_ticket_triage.sql` amplía de forma validada el outbox para `TICKET_PRIORITY_CHANGED` y `TICKET_STATUS_CHANGED`, con payloads mínimos anterior/nuevo.

Frontend incorpora controles accesibles de prioridad y de las tres transiciones autorizadas, sin retry automático ni optimistic update, con cachés por usuario y refetch de bandeja/detalle. OpenAPI 4.2 conserva 4.1 byte a byte. La verificación local cubre 105 pruebas backend, 53 frontend, contrato, PostgreSQL/Flyway, autoridad, CSRF, no-op, versión obsoleta, transiciones, rollback, payloads, builds y E2E Chromium multirepo real. `FRONTEND#18` quedó integrado mediante squash `64dee5fede9f9d7d1abf13e2f115c87b7e2f9260`, con run corregido de PR `39924355831` y post-merge `39924662623` verdes; el cierre remoto backend permanece pendiente.

Una excepción mínima posterior al primer run de `FRONTEND#18` actualiza Next.js y `eslint-config-next` de 16.3.8 a 16.4.2, junto con el lockfile y el verificador de la excepción `braces`, para corregir `GHSA-2464-fhr4-w9xw`, `GHSA-p3m5-59mp-jv5r` y `GHSA-673q-mjm6-r5xr`. El run original `39922328221` pasó lint, typecheck, 53 pruebas y build, pero falló el audit Production al publicarse esas advisories; no se relanza sin cambios.

Fuera de esa excepción no se modifican dependencias, workflows ni `automation-collector`; no se implementan resolución, cierre, reapertura, reasignación, adjuntos, búsqueda global, SSE de Support, IA, Automation, consumidores outbox, auditoría materializada o Production. `PRODUCTION_READY = NO`.

---

## Caché reproducible del build SFTPGo -- TEC-QLT-006a, 2026-10-22

Backend añade una caché OCI BuildKit exacta para reducir el coste dominante medido por `TEC-QLT-005a`. La clave cubre esquema, plataforma y todas las entradas efectivas; `actions/cache` 6.1.0 queda fijada por SHA, sin `restore-keys`. El contenido restaurado se valida contra un inventario canónico SHA-256, rechaza symlinks, archivos faltantes, adicionales o alterados y se usa read-only. Solo un miss exporta y sustituye atómicamente una caché mínima; una restauración no disponible o una caché rechazada degradan a build completo, mientras un fallo de guardado afecta únicamente a la optimización futura.

Las etapas preparatorias alcanzables pueden reutilizarse, pero `--no-cache-filter` fuerza pruebas upstream, `govulncheck` y verificación final. La imagen runtime se carga y `verify-image.sh` vuelve a ejecutar versión, Trivy HIGH/CRITICAL y SBOM CycloneDX. La validación local cubrió clave determinista, invalidación por entrada, plataforma inválida, corrupción, manifiesto truncado, miss, hit y caché inválida. Antes de la corrección remota, la caché completa `mode=max` midió 582,83 s en miss y 403,54 s en hit; una prueba previa descartó una caché inválida y reconstruyó en 576,97 s. La configuración final `mode=min`, importada read-only desde un builder vacío, completó todos los gates en 516,74 s. Frente al rango histórico 697--899 s, Maven pasó 101/101 pruebas y también quedaron verdes OpenAPI, contenedores, Compose, backup/restore y Gitleaks.

El primer run de `BACKEND#41`, `39865316279`, falló en 60 segundos al confirmar que el driver Buildx `docker` del runner GitHub no soporta el exporter local; no se relanzó. La corrección crea y elimina un builder efímero `docker-container` con BuildKit fijado por digest. El segundo run `39866621799` completó pruebas upstream, `govulncheck` y la exportación `mode=max`, pero falló antes del build runtime al confirmar la caché, tras 18 min 31 s; tampoco se relanzó. El tercer run `39872945593` completó los gates preparatorios pero rechazó el `cache.lock` transitorio; tampoco se relanzó. La corrección final eliminó solo ese lock conocido. `BACKEND#41` quedó integrado por squash `7c3233b5efd8242ac42222154e99070728957422`; el run definitivo de PR `39874341640` y el post-merge `39876819453` quedaron verdes. La rama local y remota `ci/tec-qlt-006a-sftpgo-cache` fue eliminada. No cambiaron contratos, migraciones, dependencias Maven, lógica funcional, SFTPGo 2.7.6, `frontend`, `automation-collector` o Production. No se publicaron imágenes ni artefactos. `PRODUCTION_READY = NO`.

---

## Corrección de versión outbox Support y estabilización SSE -- OLA3-SUP-004b + TEC-QLT-005a, 2026-10-22

Backend corrige el alta requester de comentarios para bloquear el ticket propio, leer su versión vigente y usarla como `TICKET_COMMENT_ADDED.resource_version` dentro de la misma transacción. Se conservan el `404` sin enumeración, el payload mínimo, la privacidad requester y el ticket sin mutación. Las pruebas cubren versión inicial y posterior a claim, visibilidad requester/técnico, rollback y un orden concurrente reproducible de claim seguido por comentario.

El fallo intermitente previo de Collector se atribuyó a la aserción inmediata del contador después de recibir el evento SSE: el envío y el incremento de métrica ocurren secuencialmente en el executor asíncrono, pero el cliente podía despertar entre ambos. La prueba ahora espera de forma observable hasta un segundo, con polling corto, sin `Thread.sleep`, retry CI ni cambio productivo. Pasó aislada, 20/20 repeticiones consecutivas, el grupo de contexto/registry 11/11 y el único `clean verify` completo 101/101.

La integración técnica quedó completa mediante `BACKEND#38`, squash `f46ba60ba6f5f276e630309958656e07369f2c31`, con runs verdes `39716165236` de PR y `39718160097` post-merge. No cambian contratos, migraciones, dependencias, workflows, `frontend`, `automation-collector` o infraestructura Production. La medición `TEC-QLT-005a` identifica el build/verificación SFTPGo como coste dominante y deja cualquier optimización para un alcance futuro que preserve todos sus gates. `PRODUCTION_READY = NO`.

---

## Conversación técnica pública e interna -- OLA3-SUP-004a, 2026-10-21

Backend añade `GET|POST /api/v1/tickets/technician-queue/{ticketId}/conversation` para que solo el `SUPPORT_TECHNICIAN` actualmente asignado a un ticket no cerrado lea y añada comentarios `PUBLIC|INTERNAL`. La migración `V20261022000000__support_technician_conversation.sql` amplía de forma validada los constraints de visibilidad y payload; cada alta y su `TICKET_COMMENT_ADDED` se confirman en una transacción, con payload limitado a `commentId` y `visibility`. Las rutas requester y la lectura técnica pre-claim continúan filtrando exclusivamente `PUBLIC`.

Frontend añade selección explícita sin default, presentación accesible de visibilidad, parsers separados y fail-closed, cachés por usuario/ticket/colección/página y mutación sin retry ni optimistic update. OpenAPI 4.1 conserva 4.0 byte a byte. La verificación local concluyó con 99 pruebas backend, 50 frontend, lint, typecheck, builds, auditorías, OpenAPI, imágenes y scans limpios, Compose, backup/restore y E2E Chromium multirepo con comprobación PostgreSQL de privacidad, autores, visibilidades y outbox.

La integración técnica quedó completa mediante `FRONTEND#17`, squash `32db50cce9933d69b0b6ad7c12e871c491c99243`, con runs verdes `39681656581` y `39682015289`, y `BACKEND#36`, squash `ecc497f1b7ab097730efeb80f12aa16aaaea0717`, con run definitivo verde `39682421438` y post-merge `39688305298`. El primer intento backend falló por una aserción temporal preexistente del contador SSE de Collector y la reejecución sin cambios quedó verde. No cambian dependencias, workflows ni `automation-collector`; no se implementan cambios de estado/prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE de Support, IA, Automation, consumidores outbox o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Bandeja técnica y autoasignación concurrente -- OLA3-SUP-003a, 2026-10-21

Backend añade rutas explícitas para que `SUPPORT_TECHNICIAN` consulte una bandeja paginada de tickets no cerrados sin asignar o propios, abra su detalle y lea la conversación `PUBLIC`. `POST /api/v1/tickets/{ticketId}/claim` aplica CSRF y concurrencia optimista con `expectedVersion`, asigna una sola vez y conserva idempotencia para la repetición del mismo técnico. La migración `V20261021000000__support_technician_claim.sql` añade el índice parcial de bandeja y amplía de forma segura el outbox; `TICKET_ASSIGNED` se captura atómicamente con payload mínimo `assigneeUserId`.

Frontend incorpora una bandeja responsive exclusiva de `SUPPORT_TECHNICIAN`, paginación en URL, cachés segmentadas por usuario, parsers fail-closed, detalle/conversación pública de solo lectura y claim sin retry ni optimistic update. OpenAPI 4.0 documenta el cambio breaking necesario para tickets asignables y mantiene OpenAPI 3.1 byte a byte inmutable. La verificación local concluyó con 95 pruebas backend, 47 frontend, lint, typecheck, builds, auditorías, validación OpenAPI y un E2E Chromium multirepo real con comprobación PostgreSQL de asignación, versión y evento.

La integración técnica quedó completa mediante `FRONTEND#16`, squash `d39fcbdd38ebdddf04d3a143909e43cc6fb72fdc`, con runs verdes `39589547411` y `39589902859`, y `BACKEND#34`, squash `4fd0137edec6247e2c208cd93b180d5009925527`, con runs verdes `39590317308` y `39593017095`. No cambian dependencias, workflows ni `automation-collector`; no se implementan comentarios técnicos, notas internas, cambios de estado/prioridad, reasignación, resolución/cierre/reapertura, adjuntos, búsqueda global, SSE, IA, Automation, consumidores outbox o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Logout y alta administrativa de usuarios humanos -- OLA1-IAM-002a, 2026-10-21

Identity añade `POST /api/v1/auth/logout` para invalidar exclusivamente la sesión presentada y `POST /api/v1/users` para que `SYSTEM_ADMIN` cree usuarios humanos `ACTIVE` con roles v1 explícitos. Ambas mutaciones exigen CSRF; el alta normaliza el username, aplica la política Argon2id/contraseña vigente, rechaza roles desconocidos o duplicados y captura atómicamente `HUMAN_USER_CREATED`. El logout captura `HUMAN_LOGOUT_SUCCEEDED`, elimina solo la sesión actual y expira la cookie tras el commit.

Frontend añade logout sin retry y limpia el estado sensible solo después de `204`; también incorpora una UI administrativa visible únicamente para `SYSTEM_ADMIN`, con selección explícita de roles y validación runtime cerrada. OpenAPI 3.1 conserva 3.0 y añade solo estas dos operaciones. La migración `V20261020010000__identity_logout_and_human_user_creation.sql` amplía únicamente tablas propias de Identity. La verificación local concluyó con 91 pruebas backend, 43 frontend, lint, typecheck, builds, auditorías, validación OpenAPI, Compose y un E2E Chromium multirepo real.

La integración técnica quedó completa mediante `FRONTEND#15`, squash `1347f7f4006dbcbef0eff5d9c03b50ec410f2e76`, con runs verdes `39537607523` y `39537884458`, y `BACKEND#32`, squash `b8c9f354c82a7bc849e112512e7ea2746752ba7f`, con runs verdes `39539823211` y `39541562446`. El primer run backend `39538137754` detectó `GO-2026-6809` en el builder Go 1.26.9 de la fixture SFTPGo; la excepción autorizada actualizó únicamente su digest oficial a Go 1.26.10, sin cambiar SFTPGo 2.7.6, dependencias, runtime o workflows. No se implementan gestión posterior, edición, deshabilitación, reset de contraseña, revocación global de sesiones, MFA, SSO ni infraestructura Production; `PRODUCTION_READY = NO`.

---

## Corrección del listado resumido de tickets -- OLA3-SUP-002b, 2026-10-20

La revisión de `OLA3-SUP-002a` confirmó que `GET /api/v1/tickets` reutilizaba por error la respuesta completa y exponía `description`. `OLA3-SUP-002b` corrige el defecto sin reescribir su historia: backend devuelve un DTO resumido cerrado, la consulta SQL deja de seleccionar `description` y `GET /api/v1/tickets/{ticketId}` conserva el detalle completo. No se crean migraciones ni se modifican datos, ownership, roles, paginación, orden, comentarios u outbox.

Frontend separa `TicketListItem` de `Ticket`, rechaza de forma cerrada `description` y cualquier campo inesperado en el listado, y obtiene la descripción únicamente mediante la consulta individual. OpenAPI 3.0 documenta el cambio breaking sobre el mismo servidor `/api/v1`; OpenAPI 2.5 queda byte a byte inmutable con SHA-256 `bbf7bf28316ab6b81553f2aabdc2c3529c482e1d72c5a56f82b9411cf1090335`. La verificación local concluyó con 86 pruebas backend, 36 frontend, lint, typecheck, builds, auditorías, validación OpenAPI y un E2E Chromium multirepo real.

La integración técnica quedó completa mediante `FRONTEND#14`, squash `e4f27bc765ee1446ba0b915c6c94618072b8f820`, con runs verdes `39508796592` y `39509165232`, y `BACKEND#30`, squash `fb0a726725796d7b367567458023fce030d2f305`, con runs verdes `39509548841` y `39511809204`. No cambian workflows, dependencias, migraciones ni `automation-collector`; `PRODUCTION_READY = NO`.

---

## Listado propio y conversación pública de tickets -- OLA3-SUP-002a, 2026-10-20

Backend añade listado paginado de tickets propios y alta/consulta paginada de comentarios `PUBLIC` para `IT_OPERATOR`, con ownership aplicado en SQL, `404` indistinguible para ticket ajeno o inexistente y órdenes estables. La migración `V20261020000000__support_ticket_conversation.sql` crea `support.ticket_comments` con FK interna a Support y amplía de forma segura el outbox. `TICKET_COMMENT_ADDED` se captura atómicamente y su payload contiene solo `commentId` y `visibility`.

Frontend incorpora lista, selección y paginación de tickets y conversación, CSRF solo en memoria, caches segmentadas por usuario/ticket/página y parsers estrictos. OpenAPI 2.5 conserva 2.4 y añade solo las tres operaciones del corte. La verificación local concluyó con 85 pruebas backend, 34 frontend, lint, typecheck, builds, compatibilidad OpenAPI y un E2E Chromium real con comprobación PostgreSQL de comentario/outbox.

La integración quedó completa mediante `FRONTEND#13`, squash `82ac1bead85a0b660cbbc51ffbd9981bf96c1f31`, con runs verdes `39472237415` y `39472574612`, y `BACKEND#28`, squash `f641e4abcdedc9f332072759489fadd61d91af1d`, con runs verdes `39472965325` y `39475825907`. No cambian workflows ni `automation-collector`; no se implementan búsqueda/listado global, notas internas, lifecycle, asignación, adjuntos, SSE, IA, Automation, consumidor outbox o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Alta y consulta mínima de tickets propios -- OLA3-SUP-001a, 2026-10-19

Backend añade `POST /api/v1/tickets` y `GET /api/v1/tickets/{ticketId}` para `IT_OPERATOR`, con CSRF en el alta y consulta limitada al requester autenticado. La migración `V20261019000000__support_minimum_tickets.sql` crea exclusivamente `support.tickets` y `support.outbox_events`; los tickets nacen `OPEN`, `NORMAL`, `WEB`, sin assignee y con `version=0`. El activo opcional se valida mediante `AssetLookup` de Inventory, sin FK ni acceso directo cross-schema. `TICKET_CREATED` se captura atómicamente y su payload allowlisted omite resumen y descripción.

Frontend incorpora formulario, consulta por UUID y ficha, conserva CSRF solo en memoria, segmenta la caché TanStack Query por usuario y valida estrictamente tanto el DTO como su ownership. OpenAPI 2.4 conserva 2.3 y añade solo las dos operaciones de tickets; las versiones anteriores y Agent 1.0 permanecen inmutables. La verificación local concluye con 80 pruebas backend, 31 frontend, builds, auditorías, compatibilidad OpenAPI 2.3 -> 2.4 y un E2E Chromium real `login -> activo -> ticket asociado -> PostgreSQL -> REST -> UI` verdes.

La integración quedó completa mediante `FRONTEND#12`, squash `aa1995484e1ce7b45eae2ad4bbccf7847a9547e5`, con runs verdes `39309591894` y `39309969666`, y `BACKEND#26`, squash `10883a944377cc97a8ec2fec34a5d13574f76a40`, con runs verdes `39341186082` y `39343168032`. No cambian workflows ni `automation-collector`; no se implementan listado, lifecycle, asignación, comentarios, adjuntos, SSE, IA, Automation, consumidor outbox o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Resiliencia acotada de entrega de Results Agent -- OLA2-RSL-001a, 2026-10-19

El Agent reintenta únicamente la entrega SFTP de un Result terminal ya persistido: máximo cinco intentos totales por episodio, primero inmediato y demoras posteriores con exponential backoff, full jitter, base 5 segundos y cap 300 segundos. La clasificación es cerrada: timeout, EOF y errores de red explícitos son transitorios; autenticación, host key, apertura de canal, permisos, conflictos y errores desconocidos fallan permanentemente sin retry y con mensajes seguros.

El ledger continúa como única fuente durable. Todos los intentos y un reinicio manual reutilizan byte a byte el mismo Result y sus `messageId`, `resultId` y `taskId`, sin reejecutar `COLLECT_SNAPSHOT`. La entrega conserva publicación atómica `.part -> .json`, acepta idempotentemente un final idéntico, rechaza un final conflictivo y solo elimina su parcial propio, incluidos parciales obsoletos recuperados tras reinicio.

La verificación local concluyó con 98 pruebas, Ruff, lockfile, contratos, build, auditoría y entrypoints verdes. La integración quedó completa mediante `AUTOMATION-COLLECTOR#8`, squash `bc508336af7c2082a2a36e20c1dd180d4507c169`, con runs verdes `39254574671` de PR y `39254685186` post-merge. No cambian contratos, dependencias, migraciones, SFTPGo, Compose, workflows, backend funcional o frontend.

No se implementan spool general, retries de polling/snapshots/capabilities, periodicidad, daemon/servicio/PyInstaller, otros tipos de tarea, `TASK_STARTED`, nuevos SSE, cuarentena/purga, revocación/rotación, Discovery o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Primera vertical controlada de tareas Agent -- OLA2-TSK-001a, 2026-10-18

Implementación local verificada en los tres repositorios. Backend añade migración y lifecycle Collector para `COLLECT_SNAPSHOT`, creación/consulta `IT_OPERATOR`, publicación durable `/tasks/{taskId}.json.part -> .json`, reconciliación read-only de `/responses/{messageId}.json`, deduplicación, conflictos, expiración, métricas y OpenAPI 2.3 compatible. SFTPGo separa `tasks`, `snapshots` y `responses`; el permiso de descarga en `responses` se limita al buzón aislado del propio Agent y permite verificar un retry idempotente byte a byte.

El Agent añade runner foreground `run [--once]`, polling 60 s ±15 s, jitter inicial acotado, lock único y ledger POSIX privado. La capability `COLLECT_SNAPSHOT` conserva el upload atómico existente y retiene un Result determinista hasta su entrega atómica, con recuperación sin reejecución. Frontend permite solicitud explícita y actualización manual, sin polling de navegador ni SSE nuevo.

La verificación local incluye 75 pruebas backend, 85 Python, 26 frontend, OpenAPI 2.2 -> 2.3, imagen SFTPGo reproducible y limpia, Compose aislado y un E2E Chromium real que completa `UI -> Task -> Agent -> Snapshot/Result -> Collector -> REST -> UI`. No se implementan otros tipos, `TASK_STARTED`, retries burst, spool general, periodicidad, daemon/servicio, nuevos SSE, Discovery o Production. `PRODUCTION_READY = NO`.

La integración autorizada quedó completa y verde: `AUTOMATION-COLLECTOR#7`, squash `cb324b498b7e0f13324c7485a167c2999cac9683`, con runs `39192254745` y `39192308664`; `FRONTEND#11`, squash `a173c0816a104851561635831235c08d4bf8f4b7`, con runs `39192380140` y `39192566258`; `BACKEND#23`, squash `478d5cae56c73a28c88783e123b8e635d6c1ff12`, con runs `39192945194` y `39194236599`.

---

## Estado operativo derivado del Agent -- OLA2-STA-001a, 2026-10-18

Backend amplía la consulta humana del Agent con `UNKNOWN|ONLINE|OFFLINE`, derivado en UTC con `Clock` y umbral configurable de cinco minutos. `lastSeenAt` usa el máximo `received_at` y `lastSnapshotAt` el máximo `captured_at` de snapshots aceptados; resultados inválidos, `.part`, rechazos, conflictos y duplicados no aportan contacto. El estado administrativo permanece separado y no se crea migración, persistencia derivada o scheduler.

Frontend valida estrictamente el DTO, segmenta TanStack Query por usuario y muestra ambos estados y sus timestamps UTC con degradación segura. OpenAPI 2.2 es compatible con 2.1 y conserva byte a byte OpenAPI 1.0/1.1/2.0/2.1 y Agent 1.0. Los gauges agregados no incluyen IDs. Las pruebas cubren límites temporales, evidencia independiente, seguridad, contrato, métricas y el E2E determinista `UNKNOWN -> ONLINE -> OFFLINE`, incluido un rechazo que no renueva contacto.

No cambian dependencias, workflows, `automation-collector`, SSE, polling, tareas, retries, spool o infraestructura Production. `PRODUCTION_READY = NO`.

---

## Aviso SSE selectivo de estado técnico -- OLA2-SSE-001a, 2026-10-17

Backend añade `GET /api/v1/assets/{assetId}/technical-state/events` para sesión `IT_OPERATOR`, con validación previa del activo, RFC 9457 para errores, `SseEmitter` best-effort sin replay y aislamiento por activo/usuario. El único evento `ASSET_TECHNICAL_STATE_UPDATED` se publica mediante listener `AFTER_COMMIT` solo para el primer snapshot aceptado; duplicados, conflictos, rechazos y reprocesados son silenciosos. El payload mínimo no contiene observaciones técnicas y REST permanece como fuente oficial.

Los límites predeterminados son 100 conexiones globales y 3 por usuario, con heartbeat comentario cada 15 segundos y timeout de 60 segundos, todos configurables. La ruta autentica sin renovar la inactividad de sesión, respeta su expiración absoluta y expone métricas de baja cardinalidad para conexiones activas, envíos, fallos y rechazos. La reconciliación crea correlación UUIDv4 explícita por candidato y no depende de MDC servlet.

Frontend abre una sola `EventSource` con credenciales por activo seleccionado, valida el payload, cierra la conexión al desmontar/cambiar de activo e invalida exclusivamente la query técnica segmentada por usuario; el refetch REST actualiza la UI sin refresh manual. OpenAPI 2.1 añade solo la suscripción SSE y pasa compatibilidad 2.0 -> 2.1; checksums ejecutables conservan OpenAPI 1.0/1.1/2.0 byte a byte. No hay migraciones, nuevas dependencias, workflows, cambios en `automation-collector`, broker, Redis, WebSocket, polling Agent, tareas, spool, otros eventos SSE ni infraestructura Production. `PRODUCTION_READY = NO`.

La verificación local concluye con 63 pruebas backend, 20 frontend, builds y auditorías verdes, validación OpenAPI/documental y un E2E Chromium real que cruza Agent, SFTPGo, Collector, PostgreSQL, SSE, REST y UI sin refresh manual.

---

## Ingestión mínima Collector y estado técnico -- OLA2-COL-001a, 2026-10-17

Collector procesa en solo lectura los snapshots finales del volumen SFTPGo Local/Test, ignora `.part` y aplica controles de ruta UUIDv4, symlink, estabilidad, tamaño, UTF-8, JSON Schema Agent 1.0, identidad y Agent activo. La reconciliación predeterminada es cada cinco minutos conforme a `AC-JAXON-044`, limita a 1000 mensajes nuevos por ciclo y no muta el buzón. El ledger conserva aceptaciones, rechazos y conflictos; las observaciones aceptadas permanecen en tablas propias de `collector`, con deduplicación transaccional por identificadores y fingerprint.

Se implementa la migración `V20261016000000__collector_snapshot_ingestion.sql` y el `GET /api/v1/assets/{assetId}/technical-state` contractual para `IT_OPERATOR`. Inventory conserva la ficha oficial y Collector compone la última observación por `capturedAt` junto con `lastSeenAt`; frontend presenta ambas fuentes separadas, con TanStack Query segmentado por usuario, estados de carga/vacío/error y actualización manual.

La verificación local concluye con 56 pruebas backend, 19 frontend, builds y auditorías verdes, y un E2E Chromium real que cruza Agent, SFTPGo, Collector, PostgreSQL, REST y UI; también demuestra `.part` ignorado, idempotencia tras ciclos repetidos, aislamiento y rechazo de payload cruzado entre Agents. OpenAPI 1.0/1.1/2.0, JSON Schema Agent 1.0, fixtures, manifest y copia vendorizada permanecen byte a byte inmutables; `automation-collector` no cambia.

No se implementan SSE, polling Agent, tareas/respuestas, retries automáticos, spool, cuarentena, borrado/purga, revocación, rotación, Discovery, infraestructura Production, publicación de artefactos o despliegues. `PRODUCTION_READY = NO`.

---

## Snapshot técnico puntual Agent -- OLA2-SNP-001a, 2026-10-16

Se implementa el comando único `jaxon-agent send-snapshot --state-dir <ruta>` para POSIX/Linux. Recoge únicamente hostname, familia/nombre/versión del sistema operativo, arquitectura, CPU lógicas, memoria total y total/disponible del volumen raíz; genera UUIDv4 y timestamps UTC, serializa JSON UTF-8 compacto y determinista, valida offline contra Agent 1.0 antes de conectar y rechaza mensajes mayores de 5 MiB.

La transferencia usa la private key Ed25519 local exacta y la host key fijada, con `allow_agent=false`, `look_for_keys=false` y timeouts `10/10/10/60`. Publica exclusivamente `/snapshots/{messageId}.json.part`, verifica tamaño, renombra atómicamente a `.json`, comprueba el resultado y solo intenta limpiar su propio parcial. La salida no expone payload, claves ni secretos.

SFTPGo usa un virtual folder determinista y aislado por Agent, fuera de su `home_dir`, limita cada upload a 5 MiB y concede `delete_files` solo en `/snapshots`. El aprovisionamiento admite creación, idempotencia o upgrade exacto desde el perfil de `OLA2-SFTP-001a`; cualquier otro drift falla cerrado. OpenAPI 1.0/1.1/2.0, JSON Schema Agent 1.0 y su copia vendorizada permanecen byte a byte inmutables. No se crean migraciones ni se modifican workflows o frontend.

No se implementan polling, retries automáticos, spool, tareas, daemon/servicio, procesamiento Collector, persistencia de observaciones, `technical-state`, SSE, revocación, rotación, Discovery, PyInstaller o infraestructura Production. `PRODUCTION_READY = NO`.

La parte Agent fue integrada mediante `AUTOMATION-COLLECTOR#6`, squash `5bec06539b7fa3014e8231cde84b960bf4f74a50`, con runs verdes `38935923442` de PR y `38935999273` post-merge. Backend fue integrado mediante `BACKEND#18`, squash `12c877b9f890e48e47bc338dcee024b155b92e09`, con runs verdes `38936213131` de PR y `38941377804` post-merge.

---

## Enrolamiento SFTP seguro Local/Test -- OLA2-SFTP-001a, 2026-10-12

Se integra el enrolamiento SFTP controlado de Agents mediante SFTPGo 2.7.6 reproducible para Local/Test, activación humana idempotente `PENDING -> ACTIVE`, máximo un Agent activo por activo, buzón lógico aislado, reconciliación estricta, evento `AGENT_ACTIVATED` y OpenAPI major 2.0. OpenAPI 1.0/1.1 y JSON Schema Agent 1.0 permanecen inmutables.

`automation-collector` añade Paramiko 5.0.0, configuración POSIX y verificación puntual con private key exacta y host key Ed25519 fijada. Frontend añade activación y presenta únicamente datos públicos de conexión en memoria. El E2E Chromium cruza frontend, backend, PostgreSQL, SFTPGo y Agent Python reales.

El bloque fue integrado mediante `AUTOMATION-COLLECTOR#5`, `FRONTEND#7` y `BACKEND#17`, con squashes `61805a7822d32db6dd1d99f2d5f15d26ee7474f1`, `4937641729f0efa36d8c0ac735d1d3e4ac661006` y `3e901a67b2230113798ba010f55201c698dd9161`; sus runs de PR y post-merge quedaron verdes. No se publicaron imágenes o artefactos ni se desplegó. Snapshots, transferencias funcionales, polling, tareas, spool, procesamiento Collector, estado técnico, SSE, revocación, rotación y Production permanecen fuera del alcance. `PRODUCTION_READY = NO`.

El 2026-10-15 LUIS ratificó de forma limitada la modificación ya integrada del workflow backend que añadió el gate de build/verificación SFTPGo y la limpieza exclusiva de su caché de construcción. La ratificación no amplía permisos para cambios GitHub posteriores.

---

## Pre-registro e identidad técnica Agent -- OLA2-AGT-001a, 2026-10-11

Se implementan `POST /api/v1/agents`, `GET /api/v1/agents/{agentId}` y `PUT /api/v1/agents/{agentId}/ssh-key` como extensión compatible OpenAPI 1.1, manteniendo 1.0 inmutable. Collector conserva Agent/vínculo/estado `PENDING`; Identity conserva identidad técnica, public key Ed25519 normalizada y huella OpenSSH SHA-256; Inventory valida el activo por contrato público in-process. Los eventos allowlisted `AGENT_PRE_REGISTERED` y `AGENT_PUBLIC_KEY_REGISTERED` se capturan atómicamente en el outbox del productor.

`automation-collector` añade `jaxon-agent initialize` con `cryptography` fijada: UUIDv4, generación Ed25519 local, permisos privados, idempotencia y fallo cerrado sin conectividad ni exposición de private key. Frontend añade pre-registro, instrucciones, pegado de public key, huella y reconsulta con TanStack Query. El E2E Chromium cruza frontend/backend/PostgreSQL/inicializador Python real.

No se implementan SFTPGo, buzón/cuenta SFTP, conexión, host-key verification, `ACTIVE`, revocación, rotación, snapshots, procesamiento Collector, SSE, polling, tareas, spool, PyInstaller, servicio del sistema, publicación de artefactos o despliegue. `DESIGN_READY` se conserva y `PRODUCTION_READY = NO`.

---

## Inicio de sesión humano ejecutable -- OLA1-IAM-001a, 2026-10-10

Se implementan en Identity `POST /api/v1/auth/login` y `GET /api/v1/auth/me` sin alterar semánticamente OpenAPI 1.0. Una única migración Flyway crea usuarios humanos, roles, relación usuario-rol, sesiones opacas, intentos de login y outbox del productor. El bootstrap explícito está desactivado por defecto y exige store vacío; Argon2id usa 19 MiB, `t=2`, `p=1`; la sesión aplica 30 minutos de inactividad y 12 horas absolutas con token CSPRNG persistido solo por SHA-256.

Se registran en `AC-JAXON-060` los roles bootstrap, la excepción mínima de actor nulo para login fallido sin principal verificable y el bloqueo desde el sexto fallo en 15 minutos. Los eventos `HUMAN_LOGIN_SUCCEEDED` y `HUMAN_LOGIN_FAILED` quedan `PENDING` en el outbox Identity; no se crea consumidor ni tabla en Audit/Reporting.

Frontend incorpora `/login`, recuperación `/auth/me`, vista mínima de identidad/roles, TanStack Query y un harness Playwright Chromium contra frontend/backend reales y PostgreSQL efímero. Inventory, logout, gestión de usuarios, SFTP, Agent, Collector, SSE, infraestructura Production y workflows nuevos permanecen fuera de alcance. `PRODUCTION_READY = NO`.

---

## Baseline supply-chain multirepo y cierre técnico de OLA 0, 2026-10-10

Los tres repositorios configuran Dependabot exclusivamente para security updates agrupados, con `open-pull-requests-limit: 0`, sin `target-branch` ni auto-merge. La rama predeterminada pasa a ser `develop` sin modificar la historia ni la semántica de producción de `main`; Dependency Graph y Dependabot alerts quedan habilitados antes de activar los security updates.

Los workflows existentes incorporan Gitleaks 8.30.1 bloqueante sobre el historial completo. El binario se descarga por versión exacta, se valida con SHA-256 y se ejecuta con redacción total, sin allowlists. Frontend y automation-collector amplían el checkout a historial completo; no se crean workflows o jobs y no se relajan auditorías, scans o excepciones existentes.

No cambian manifiestos, lockfiles, contratos, migraciones o lógica funcional. Permanecen diferidos SBOM, CodeQL, Dependency Review Action, Secret Protection, SAST/DAST adicional, firma y provenance. No se publican artefactos, imágenes o releases y no se despliega. La baseline técnica de OLA 0 queda cerrada; OLA 1 requiere análisis, delimitación y autorización independiente. `PRODUCTION_READY = NO`.

---

## Backup/restore lógico PostgreSQL y gate CI -- TEC-OPS-005a y TEC-OPS-006a, 2026-10-10

Se implementan scripts versionados para crear un dump lógico custom de PostgreSQL 18 y un manifest cerrado con SHA-256, tamaño, versiones, schemas e historial Flyway, y para restaurarlo sobre un destino vacío mediante una única transacción. La publicación usa archivos temporales privados, permisos `0600`, rename y rechazo de sobrescritura; el manifest no contiene credenciales ni hostname.

El harness aislado crea fuentes y destinos PostgreSQL 18.6/pgvector 0.8.6 efímeros, aplica la migración canónica, usa únicamente datos sintéticos y comprueba schemas, pgvector, Flyway y contenido restaurado. También verifica rechazo de artefactos o manifest alterados, incompletos o incompatibles, permisos inseguros, mismo origen/destino, sobrescritura, destino no vacío y fallos de `pg_dump`/`pg_restore` sin objetos parciales.

`Backend CI` incorpora el harness al job existente y reutiliza las imágenes locales ya construidas; no crea workflows ni publica o conserva backups como artifacts. Permanecen fuera de alcance base backup, WAL/PITR, cifrado, storage externo, scheduling, rotación/retención, documentos, infraestructura y operación Production. Los objetivos RPO/RTO no quedan demostrados; `PRODUCTION_READY = NO`.

El bloque se integró en `develop` mediante `BACKEND#11`, squash `57dca8d0856618bac1fc2ae48ea3dfb630409fca`; los runs `38031629641` de PR y `38031904168` post-merge finalizaron correctamente, incluido el gate de backup/restore.

---

## Observabilidad base backend -- TEC-OPS-002a, TEC-OPS-003a y TEC-OPS-004a, 2026-10-09

Se implementan logs JSON estructurados con timestamps UTC, identidad de servicio/entorno, campos operativos y correlación HTTP segura mediante `X-Correlation-ID`; el identificador se valida como UUIDv4, se devuelve en la respuesta y se aísla en MDC entre solicitudes, incluso concurrentes y con excepciones. Trace/span IDs se incorporan al MDC cuando existe contexto activo y las pruebas verifican que la salida no contiene secretos configurados.

Actuator expone únicamente health y Prometheus. Las métricas estándar JVM, HTTP y HikariCP incluyen tags comunes de baja cardinalidad sin IDs de usuario, activos, tareas, mensajes o correlación. Se incorpora OpenTelemetry con propagación W3C, head sampling inicial configurable de 10 % y exportación OTLP de trazas opcional, desactivada por defecto; los exportadores OTLP de logs y métricas permanecen desactivados.

La suite Maven cubre el comportamiento del incremento; los smokes de contenedor y Compose confirman que el runtime arranca sin collector, y los scans existentes conservan el gate de imagen. No se crea otro workflow ni se instalan collectors, Prometheus, Grafana, Loki o Tempo; no se crean dashboards, alertas, funcionalidad de negocio, tablas, migraciones, artefactos publicados o despliegues. `DESIGN_READY = SI`, el readiness se limita a este alcance y `PRODUCTION_READY = NO`.

Se corrige la ruta inmediata del enrutador para usar las guías ágiles existentes. El validador documental ya comprueba enlaces Markdown locales; detectar nombres de archivo escritos como texto inline permanece como revisión manual para evitar falsos positivos sobre ejemplos, inventarios y referencias históricas.

---

## Contratos ejecutables mínimos OLA 0 -- AC-JAXON-059, 2026-10-09

Se crea en backend la fuente canónica `doc/contracts/` con OpenAPI 3.1.2 versión 1.0 para cinco operaciones de la primera vertical y JSON Schema Draft 2020-12 Agent 1.0 para common/envelope, Snapshot, Task y Result. Los contratos fijan sesión opaca, CSRF, RFC 9457, UUIDv4, UTC `Z`, catálogos cerrados, payloads Task vacíos, Result sin `EXPIRED` y Snapshot técnico mínimo sin secretos, red, software exhaustivo o serial.

El bundle Agent contiene fixtures positivos/negativos y manifest determinista con SHA-256. `automation-collector` conserva una copia byte a byte, script stdlib `sync/check/verify` y un validador Agent offline con límites de tamaño y errores tipados seguros. Se añaden `swagger-parser` 2.1.48 y `json-schema-validator` 3.0.6 como test-only, y `jsonschema` 4.26.0/`referencing` 0.37.0 como runtime Agent. Las verificaciones locales concluyen con 11 pruebas backend y 33 Python, builds correctos, auditoría Python limpia y Trivy sin HIGH/CRITICAL.

Los workflows existentes se amplían sin crear otros: backend ejecuta pruebas contractuales y `oasdiff` 1.33.0 fijado por checksum; Python verifica el bundle local sin checkout cross-repo ni secretos. No se implementan controladores, tablas, migraciones, SFTP, snapshots/tareas funcionales, frontend, publicación de artefactos o despliegues. `DESIGN_READY = SI`; el alcance concreto queda implementation-ready al integrarse y verificarse; `PRODUCTION_READY = NO`.

---

## Compose y configuración Local -- TEC-PLT-007a, TEC-QLT-004a y TEC-DOC-008, 2026-10-09

Se implementa una baseline reproducible de Docker Compose para Local. El arranque predeterminado contiene únicamente PostgreSQL 18.6 con pgvector 0.8.6, construido desde la fixture endurecida existente, con healthcheck `pg_isready`, volumen nombrado, red dedicada y puerto publicado exclusivamente en loopback. El backend permanece opcional bajo el profile `application`, espera a PostgreSQL healthy, recibe configuración `JAXON_*`, conserva runtime no-root, filesystem read-only, `/tmp` efímero y probes, y publica HTTP solo en loopback.

`.env.example` contiene exclusivamente valores ficticios y `.env` continúa ignorado por Git como conveniencia Local; QA y Production no usan este mecanismo como almacén de secretos. `scripts/verify-compose.sh` valida configuración, servicio predeterminado, aislamiento, puertos, persistencia, usuario no-root, health, Flyway, ocho schemas y pgvector mediante un proyecto y volumen efímeros únicos. Su cleanup elimina solo esos recursos propios y no toca el volumen persistente Local.

El job estable `Backend verification` conserva Maven/Testcontainers, builds, smoke tests y scans Trivy existentes, y añade `docker compose config` y el smoke Compose reutilizando las imágenes ya construidas. No se añaden servicios, secretos, dependencias, contratos, migraciones o lógica de negocio; no se publican imágenes o artefactos ni se despliega. `DESIGN_READY` se conserva y `PRODUCTION_READY = NO`.

---

## Health, contenedores backend/frontend y gates CI -- TEC-OPS-001a, TEC-PLT-006a, TEC-PLT-006b, TEC-QLT-003a y TEC-DOC-007, 2026-10-08

Se implementan y verifican health general, liveness, readiness con PostgreSQL, detalles ocultos y shutdown graceful en backend. La integración ejecutable confirma Java 25, Spring Boot 4.1.1, PostgreSQL 18.6, pgvector 0.8.6, Flyway y los ocho schemas mediante cinco pruebas. Por hallazgos Trivy corregibles se fijan temporalmente Tomcat 11.0.26 y Jackson 3.1.7 dentro de las líneas compatibles gestionadas por Spring Boot.

Se crean Dockerfiles multi-stage no-root para backend y frontend. Backend usa Temurin JDK/JRE 25 por digest y runtime UID/GID `10001`. Frontend usa Node.js 24.21.0 para build, Next.js standalone y runtime Alpine por digest con UID/GID `10001`, sin npm, npx, Corepack, yarn o pnpm. Una fixture exclusivamente local compila pgvector 0.8.6 desde fuente y checksum fijados sobre PostgreSQL 18.6 Alpine; Clang/LLVM queda solo en build y el runtime final reemplaza `gosu` por `su-exec` y se aplana para eliminar la capa vulnerable.

Los smoke tests validan filesystem read-only, `/tmp` efímero, usuario no-root, puertos, contenido/health, migración, schemas, pgvector, señales y ausencia de secretos. Trivy 0.75.0 no detecta HIGH/CRITICAL en las imágenes finales backend, frontend o fixture. `Backend CI` y `Frontend CI` incorporan builds, smoke tests y scans bloqueantes sin excepciones ni `--ignore-unfixed`.

`BACKEND#5` se integra en `develop` mediante squash `90c550714b861beb7d6388cf571744efcda4af8d`; los runs `37879426717` y `37879749721` quedan verdes. `FRONTEND#2` se integra mediante squash `94d8ce2e8dd3309b7683f09041785c000f6fe21d`; los runs `37879426584` y `37880065517` quedan verdes. No se publican imágenes, Packages o artefactos y no se crean Compose, SBOM, firma, tags, releases ni despliegues. No se implementa lógica de negocio ni infraestructura Production; `DESIGN_READY` se conserva y `PRODUCTION_READY = NO`.

---

## Fundación, publicación y CI base de automation-collector -- TEC-PLT-005a, TEC-GIT-003, TEC-QLT-002a y TEC-DOC-006, 2026-10-08

Se crea y verifica la fundación técnica de `automation-collector` con Python 3.14.8 administrado por uv 0.12.24, `pyproject.toml`, `uv.lock`, Hatchling 1.32.4, Ruff 0.16.10, pytest 9.1.1 y pip-audit 2.10.1. Las dependencias runtime permanecen vacías. `jaxon_agent` y `jaxon_discovery_executor` son paquetes y entrypoints distintos que solo identifican programa y versión; seis pruebas demuestran importación, ejecución e identidades inequívocas. Lint, formato, build, auditoría y reproducibilidad del lockfile quedan verdes, sin vulnerabilidades conocidas.

`AUTOMATION-COLLECTOR` se publica como repositorio privado con commit inicial `a1079d8d5bac0d0436daae844e0b1ba9e42a9d12` y ramas permanentes `main` y `develop`. `Python CI` se integra en `develop` mediante el Pull Request `#1` y squash `a040c5fba34fae22903088b82191e8355deaab26`. El run de PR `37852626079` y el run post-merge `37852683236` terminan verdes. El workflow usa permisos `contents: read`, acciones fijadas por SHA completo y no usa secretos, publicación, artefactos ni despliegues.

No se copia código legado ni se implementan SFTP, Paramiko, contratos JSON Schema, snapshots, inventario del sistema, polling, spool, tareas remotas, discovery de red, enrolamiento, claves SSH, PyInstaller, servicios, Docker o Compose. Agent y Discovery Executor no comparten identidad ni configuración operacional. No se modifican contratos, datos o migraciones. J-AXON conserva `DESIGN_READY` y `PRODUCTION_READY = NO`.

---

## Fundación, publicación y CI base del frontend -- TEC-PLT-004a, TEC-GIT-002, TEC-QLT-001a y TEC-DOC-005, 2026-10-08

Se crea y verifica la fundación técnica de `frontend` con Node.js 24.21.0, npm 11.19.0, Next.js 16.3.8, React 19.3.0, TypeScript 5.9.3, App Router, Server Components por defecto, Vitest y React Testing Library. La página mínima identifica J-AXON sin simular funcionalidad de negocio y queda no-indexable mediante metadata y `robots.txt`. La validación local confirma `npm ci`, lint, typecheck, una prueba, build, `verify` y smoke tests de `/` y `/robots.txt`.

ESLint 10.12.0 se intentó según la decisión condicionada, pero `eslint-plugin-react@7.37.5` falló de forma ejecutable. Con autorización expresa de LUIS se fijó temporalmente ESLint 9.39.5, compatible pero EOL. La auditoría de producción queda limpia. La auditoría completa conserva una excepción aprobada el 2026-10-08 exclusivamente para `GHSA-vfj7-8cjw-p6xm` / `CVE-2026-93687` en `braces@3.0.3`, transitiva de herramientas de desarrollo y sin versión corregida disponible; un verificador cerrado falla ante cualquier advisory, paquete, versión, alcance o error diferente.

`FRONTEND` se publica como repositorio privado con commit inicial `ffec9bdd39d5168ed5f7d3bbaa68d5d4bc5860ab` y ramas permanentes `main` y `develop`. `Frontend CI` se integra en `develop` mediante el Pull Request `#1` y squash `0a34d6dadacc95351544996235faf6bcb0b21b65`. El run de PR `37849248403` y el run post-merge `37849379045` terminan verdes con `npm ci`, lint, typecheck, Vitest/RTL, build y las auditorías de producción/completa. Las actions externas quedan fijadas por SHA completo, los permisos se limitan a `contents: read` y no se usan secretos, artefactos ni despliegues.

La documentación canónica continúa en `backend/doc`. No se crean lógica de negocio, APIs, contratos, migraciones, Dockerfile, Compose, Issues, Projects, rulesets, tags, releases, Packages, artefactos publicados ni despliegues. `automation-collector` permanece vacío, J-AXON conserva `DESIGN_READY` y `PRODUCTION_READY = NO`.

---

## CI base del backend -- TEC-PLT-003a, 2026-10-08

Se implementa y verifica `Backend CI` en `.github/workflows/backend-ci.yml`. El workflow ejecuta `./mvnw --batch-mode --no-transfer-progress clean verify` con Java 25 Temurin sobre `ubuntu-24.04` en Pull Requests hacia `develop` o `main`, pushes a `develop` y ejecuciones manuales. La evidencia local y GitHub Actions confirma compilación, tres pruebas sin fallos, verificación Spring Modulith y la integración Testcontainers con PostgreSQL 18.6, pgvector 0.8.6, Flyway y los ocho schemas aprobados.

Las acciones externas están fijadas por SHA completo, los permisos se limitan a `contents: read`, `persist-credentials` está deshabilitado y no se usan secretos ni se publican artefactos o despliegues. Contratos y SFTP permanecen fuera hasta disponer de artefactos ejecutables aplicables. La activación se integra mediante el Pull Request `#1` hacia `develop`, con checks verdes sobre su última revisión y fusión `Squash and merge`.

Los rulesets y protecciones automáticas siguen pendientes por la limitación del plan actual para repositorios privados. La disciplina de Pull Request y checks verdes se aplica operativamente, pero GitHub no impide técnicamente una fusión incorrecta. `main` no se modifica y puede quedar temporalmente detrás de `develop`. No se crean acuerdos, migraciones, contratos, lógica de negocio, tablas, tags, releases, Packages, artefactos publicados ni despliegues; `PRODUCTION_READY = NO`.

---

## Publicación controlada del repositorio backend -- TEC-GIT-001C, 2026-10-07

Se inicializa Git exclusivamente en `backend`, se configura el remoto SSH oficial `git@github.com:J-AXON-PLATFORM/BACKEND.git` y se publica la baseline verificada. El historial queda formado por los commits `chore: bootstrap backend repository` y `docs: record backend repository publication`; las ramas permanentes `main` y `develop` apuntan al mismo commit final y siguen sus ramas remotas.

La publicación fue autenticada mediante una clave SSH Ed25519 dedicada y verificación estricta de la huella oficial de `github.com`. Antes de inicializar Git se ejecutaron satisfactoriamente las pruebas Maven con Java 25 y las validaciones documentales normal y estricta.

`frontend` y `automation-collector` permanecen vacíos y sin Git local. No se crearon Issues, Projects, Pull Requests, workflows, rulesets, tags, releases, artefactos ni despliegues. No se modificaron código, pruebas, dependencias, contratos, datos, migraciones, `VERSION` ni el Registro de Acuerdos; `DESIGN_READY` y `PRODUCTION_READY = NO` se conservan.

---

## Corrección del nombre documental REVERSION -- TEC-DOC-003, 2026-10-07

Se corrige un nombre documental afectado por mojibake y se adopta el nombre ASCII `docs/14-operaciones/REVERSION.md`. El contenido documental no cambia y se actualiza únicamente su referencia en `MANIFIESTO_FRAMEWORK.md`.

Esta corrección no crea acuerdo, Lote, checkpoint ni versión. No se modificaron código, Git ni GitHub.

---

## Sincronización documental del estado real de GitHub -- TEC-DOC-002, 2026-10-07

Se registra el estado remoto verificado de la organización nueva `J-AXON-PLATFORM` y sus tres repositorios privados vacíos: `BACKEND` (`https://github.com/J-AXON-PLATFORM/BACKEND`), `FRONTEND` (`https://github.com/J-AXON-PLATFORM/FRONTEND`) y `AUTOMATION-COLLECTOR` (`https://github.com/J-AXON-PLATFORM/AUTOMATION-COLLECTOR`). Los nombres lógicos canónicos permanecen `backend`, `frontend` y `automation-collector`; las mayúsculas visibles no cambian el modelo MULTIREPO, y GitHub trata estos identificadores sin distinción efectiva entre mayúsculas y minúsculas.

Los repositorios remotos no contienen archivos, código, commits, ramas, Issues, Projects, Pull Requests, workflows, rulesets, releases, artefactos ni despliegues. Git no fue inicializado en los repositorios locales y no se configuraron remotos. La organización histórica distinta `j-axon` y sus repositorios preexistentes son independientes, no fueron modificados y no deben confundirse con el nuevo inicio oficial.

La autorización de LUIS cubrió exclusivamente la creación de `J-AXON-PLATFORM` y de los tres repositorios privados vacíos, y quedó consumida. No autoriza publicación posterior, inicialización Git, remotos, commits, ramas, backlog remoto, CI/CD, rulesets ni despliegues. Esta sincronización no cambia `DESIGN_READY`, el alcance implementado por `TEC-PLT-001a` y `TEC-PLT-002a`, ni `PRODUCTION_READY = NO`; no crea acuerdo, Lote, checkpoint o versión documental.

---

## Actualización de continuidad -- TEC-PLT-002a, 2026-10-06

Se completa y verifica el bootstrap de persistencia del backend con PostgreSQL 18.6, pgvector 0.8.6, PostgreSQL JDBC 42.7.13, Flyway 12.4.0 y Testcontainers 2.0.5. La imagen de integración `pgvector/pgvector:0.8.6-pg18-bookworm` aplicó desde una base vacía la migración `V20261006000000__bootstrap_schemas.sql`.

La migración habilita la extensión `vector` y crea exactamente los schemas `inventory`, `discovery`, `collector`, `support`, `ai`, `identity`, `automation` y `audit_reporting`. La prueba real confirmó la versión del servidor, la versión de pgvector, el historial Flyway, los ocho schemas y la ausencia de tablas de negocio tanto en los schemas modulares como en `public`.

Continúan sin implementarse lógica funcional, entidades, repositorios de dominio, servicios, APIs, OpenAPI ejecutable, frontend y `automation-collector`. No se modificaron contratos, Git, GitHub, despliegues ni infraestructura Production. `DESIGN_READY` se conserva, `IMPLEMENTATION_READY` continúa evaluándose por alcance concreto y `PRODUCTION_READY = NO`; la siguiente tarea técnica no ha sido iniciada.

---

## Restauración de integridad documental -- TEC-DOC-001, 2026-10-06

Se restauran los 11 archivos declarados en `MANIFIESTO_FRAMEWORK.md` que faltaban bajo `.github/` y `.ia/adaptadores/`, mediante copia binariamente idéntica desde la fuente íntegra de solo lectura `doc_J-AXON_1.0.0/doc`. El framework vuelve a contener exactamente los 221 archivos inventariados, sin faltantes ni adicionales.

La restauración fue verificada mediante hashes SHA-256, comparación binaria, contraste de inventarios y validación documental normal y estricta. La validación normal concluye con 0 errores y 0 advertencias; la estricta concluye con 0 errores y conserva la advertencia conocida sobre 14 referencias a `POR_DECIDIR`.

Se mantienen sin cambios la versión 1.0.0, `MANIFIESTO_FRAMEWORK.md` y el Registro de Acuerdos. No se modificaron código, pruebas, dependencias, contratos, datos, migraciones, infraestructura, Git ni GitHub.

---

## Actualización de continuidad -- TEC-PLT-001a, 2026-10-06

Se sincroniza el estado documental después de completar y verificar `TEC-PLT-001a`. El alcance implementado es exclusivamente el bootstrap estructural del backend: Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1, Maven Wrapper con Maven 3.9.16, clase principal y ocho módulos estructurales.

La verificación ejecutada en la tarea técnica concluyó con dos pruebas satisfactorias: carga del contexto Spring Boot y verificación de los ocho módulos con Spring Modulith. No existe lógica funcional implementada. La primera vertical E2E, PostgreSQL, schemas, Flyway, OpenAPI ejecutable, CI, Docker, Compose, frontend y `automation-collector` continúan sin implementar.

No se afectaron `frontend`, `automation-collector`, contratos, datos, migraciones, despliegues ni infraestructura Production. Git no fue inicializado ni modificado y GitHub no fue modificado. La baseline documental continúa `DESIGN_READY`, el framework `doc` permanece en versión 1.0.0 y `PRODUCTION_READY = NO`.

Esta actualización no crea un acuerdo, Lote ni checkpoint nuevo. CKP-JAXON-005 se conserva como el cierre histórico del diseño/refinamiento hasta el Lote 54. La continuidad posterior es incremental y por alcance concreto; la siguiente tarea técnica no ha sido iniciada.

---



## CKP-JAXON-005 -- cierre de refinamiento/diseño v1 hasta Lote 54, 2026-09-29

Se mantiene el framework `doc` 1.0.0 y la implementación nueva continúa sin iniciarse ni estar autorizada. Se consolidan los Lotes 49--54: Support/Tickets detallado; Inventory/Activos detallado; catálogo de tareas/capabilities Collector; Auditoría/Reportes; contratos/eventos internos con Transactional Outbox; y cierre de readiness v1 con UUIDv4 y baseline controlada.

El proyecto queda documentalmente `DESIGN_READY`, separado de `IMPLEMENTATION_READY` y `PRODUCTION_READY`. Los pendientes restantes son deliberados por Production/instalación, organización, evidencia o scope futuro; no deben rellenarse artificialmente. No se crea/modifica GitHub ni se implementa código.

---

## CKP-JAXON-004 -- refinamiento aprobado hasta Lote 48, 2026-09-29

Se mantiene el framework `doc` 1.0.0 y la implementación nueva continúa sin iniciarse. Se consolidan los Lotes 41--48: storage documental/retención/backups/RPO-RTO; arquitectura RAG; Automation+n8n; infraestructura LAN/TLS/secretos; observabilidad/SLOs/alertas; rendimiento/capacidad; arquitectura frontend; y Discovery detallado. No se modifica GitHub ni se implementa código.

El punto de continuación pasa al **Lote 49 -- MOD-04 Support/Tickets**. La continuidad obliga a leer primero Registro de Acuerdos, Trabajo Actual, Proyecto Maestro, Pendientes y Enrutador, y luego requisitos/flujos/autorización/retención/contratos específicos del lote.

---

## CKP-JAXON-003 -- refinamiento aprobado hasta Lote 40, 2026-09-29

Se mantiene el framework `doc` 1.0.0. Se consolidan los Lotes 30--40: versiones/runtime/build; estructura de repositorios y configuración por cinco entornos; PostgreSQL/Flyway; contenedores/promoción; CI/CD; identidad humana/RBAC; SFTP/identidad técnica/packaging Agent; contratos OpenAPI/JSON Schema; primera vertical E2E; y resiliencia operacional Agent con valores iniciales configurables. No se implementa código ni se modifica GitHub.

Se depura la continuidad: `CHECKPOINT_JAXON.md`, `CONTROL_CAMBIOS_CHECKPOINT.md` e `INVENTARIO_ARCHIVOS_CHECKPOINT.json` dejan de formar parte de `doc/` porque eran artefactos de checkpoint del proyecto y no del framework reutilizable. El checkpoint CKP-JAXON-003 se distribuye como archivo externo junto al ZIP. `docs/16-contexto/` se conserva porque sí es el mecanismo del framework para contexto dinámico de proyecto.

---

## CKP-JAXON-002 -- cierre de descubrimiento inicial, 2026-09-29

Se mantiene el framework `doc` 1.0.0. Se consolidan los lotes de decisiones aprobados posteriores a CKP-JAXON-001: contratos, capacidad, datos, seguridad, entornos, pruebas, DevOps, infraestructura, observabilidad, resiliencia, operación, integraciones, Git, metodología, reglas IA, protocolo de decisiones, alcance v1, requisitos, riesgos, activación y backlog. Se actualizan checkpoint, Maestro, acuerdos, pendientes y documentos funcionales principales. No se implementa código ni se modifica GitHub.

Los conteos aprobados se recalculan: la lista de requisitos contiene 90 elementos y el backlog inicial 85 elementos; solo se corrige aritmética, no contenido.

La validación estructural de ese corte quedó registrada en el artefacto histórico externo de control de CKP-JAXON-002.

---

## CKP-JAXON-001 -- consolidación de la instancia, 2026-09-29

Se mantiene la versión del framework 1.0.0. Se cumplimentan campos aprobados del Proyecto Maestro y se agregan contexto de continuidad, acuerdos, pendientes, evidencia histórica y control de conservación de archivos.

No se implementaron decisiones nuevas ni se alteró el proyecto legado, la versión del framework o el validador. En ese corte todavía se conservaban los artefactos raíz de checkpoint, retirados del paquete `doc/` a partir de CKP-JAXON-003.

---


## 1.0.0

Versión estable consolidada de `doc` como framework documental dinámico para proyectos de software de distinta escala y arquitectura.

### Activación dinámica

- Separación entre **perfil documental** (`LIGERO`, `ESTANDAR`, `EMPRESARIAL`) y **perfiles técnicos**.
- Nueva `MATRIZ_ACTIVACION.md` para activar documentación según arquitectura, plataforma, riesgo y operación.
- Perfiles para backend, frontend, móvil, escritorio, CLI, batch/workers, datos/ETL, monolito, monolito modular, microservicios, eventos, serverless, cloud, híbrido y multicloud.
- Regla explícita contra activar complejidad por moda o por la mera existencia de una plantilla.

### Arquitectura

- Arquitectura distribuida con límites, comunicación síncrona/asíncrona, consistencia, fallos parciales, mensajería y pruebas.
- Arquitectura cloud independiente de proveedor.
- Resiliencia: timeouts, retries, backoff, jitter, idempotencia, circuit breakers, bulkheads, rate limiting, backpressure y DLQ.
- Rendimiento y capacity planning con latencia, throughput, concurrencia, pools, DB, caché y escalado.
- Perfiles técnicos reutilizables y combinables.
- Guía específica para comparar monolito, monolito modular, microservicios, eventos y serverless.

### Datos

- Gobierno y ownership de datos.
- Calidad y linaje.
- Schema evolution.
- CDC.
- ETL/ELT.
- Replicación.
- Particionamiento.
- Multi-tenancy.
- Escalabilidad y archivado.
- Refuerzo de estrategia de base de datos y migraciones compatibles.

### Frontend

- Arquitectura frontend ampliada.
- Renderizado CSR/SSR/SSG/híbrido.
- Gestión explícita de estado local, compartido, server-state, URL y persistido.
- Rendimiento frontend.
- Internacionalización.
- Observabilidad de cliente.
- Accesibilidad, responsive, routing, componentes, errores y sistema de diseño reforzados.

### Plataformas

Nueva área `docs/18-plataformas/` para:

- móvil;
- escritorio;
- CLI;
- batch/workers;
- serverless.

Esto evita tratar todo proyecto como frontend web + backend.

### Seguridad

- Seguridad de dependencias ampliada.
- Nueva guía de cadena de suministro de software.
- SAST, DAST, secret scanning, dependency scanning, container/IaC scanning, SBOM, firma y procedencia como controles condicionales.

### DevOps y cloud

- CI y CD ampliados.
- Infraestructura como código.
- Gestión de estado y drift.
- Estrategias de despliegue: recreate, rolling, blue/green, canary, feature flags y shadow traffic.
- Compatibilidad de base de datos con expand-contract.
- Rollback y roll-forward.
- Entornos, contenedores, infraestructura y quality gates reforzados.

### Observabilidad y operación

- Logs, métricas, trazas, health checks, alertas y SLI/SLO/SLA ampliados.
- Runbooks operativos.
- Gestión de incidentes.
- Postmortems.
- Recuperación ante desastres con RTO/RPO y drills.
- Prácticas SRE y error budgets cuando correspondan.

### Testing

- Pruebas de rendimiento ampliadas con percentiles, throughput, carga y diagnóstico.
- Pruebas de integración y unitarias reforzadas.
- Nuevas guías de pruebas de componente, sistema, resiliencia, migraciones y accesibilidad.
- Se conserva estrategia de validación integral basada en riesgo en lugar de imponer todas las capas.

### IA y contexto

- Enrutador documental ampliado para nuevas plataformas y arquitecturas.
- Reglas especializadas para IA en arquitectura, DevOps, datos y operaciones.
- Lectura progresiva para reducir consumo de contexto.
- Proceso: análisis → alternativas → recomendación → aprobación → implementación → pruebas → documentación → verificación.

### Validación documental

- Nuevo `tools/validate_docs.py`.
- Verifica versión, archivos esenciales, clasificación, enlaces locales, marcadores heredados y activación de perfiles seleccionados.
- Modo `--strict` para advertir decisiones pendientes.
- La propia versión 1.0.0 se entrega con el validador estructural sin errores ni advertencias en modo normal.

### Gestión y automatización

- Scrum + Kanban.
- Jerarquía Épica → Funcionalidad → Historia de Usuario → Tarea.
- Automatización de backlog opcional.
- Publicación remota únicamente con permisos explícitos.
