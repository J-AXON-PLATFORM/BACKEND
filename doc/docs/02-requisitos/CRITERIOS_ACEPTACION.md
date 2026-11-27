# Criterios de aceptación


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Traducir requisitos e historias a condiciones verificables.


Formato recomendado cuando aporta claridad:

```gherkin
Given <context>
When <action>
Then <observable result>
```

Evitar criterios que describan implementación interna salvo que sea un requisito técnico explícito.

## OLA3-SUP-005a

- El técnico asignado puede elegir `LOW|NORMAL|HIGH|URGENT` y completar únicamente las tres transiciones no terminales autorizadas.
- Cada cambio efectivo incrementa una vez la versión y confirma atómicamente su evento con payload anterior/nuevo mínimo.
- Un no-op con versión vigente no incrementa versión ni crea evento; la misma solicitud con versión obsoleta devuelve `409`.
- Un requester, otro técnico o una sesión sin `SUPPORT_TECHNICIAN` no adquiere autoridad por usar directamente la API.
- Una transición no incluida en la allowlist responde `409` y ninguna mutación se acepta sin CSRF válido.
- Frontend no reintenta mutaciones ni muestra un resultado optimista; tras éxito reconsulta bandeja y detalle.

## OLA3-SUP-006a + TEC-QLT-007a

- El técnico asignado resuelve únicamente desde `IN_PROGRESS|WAITING_FOR_REQUESTER` y cierra solo con una combinación estado/motivo autorizada; requester propietario o técnico asignado reabren únicamente desde `RESOLVED|CLOSED`.
- Cada transición bloquea la fila, exige versión vigente, incrementa una vez la versión y confirma en la misma transacción ticket, historial append-only y outbox mínimo sin textos sensibles.
- Requester y técnico asignado consultan el mismo historial público con orden `(occurredAt, lifecycleEventId)` y paginación; otro actor recibe `404` uniforme.
- Un ticket `CLOSED` conserva acceso de detalle al técnico asignado, rechaza comentarios requester y recupera conversación tras reapertura.
- Frontend conserva el formulario ante error, no reintenta ni anticipa estado y reconsulta recursos exactos tras éxito o `409`.
- El clasificador CI cubre tabla de rutas, trata desconocidos y evidencia incompleta como `PLATFORM_FULL`, y estos dos PR ejecutan automáticamente ese nivel.
- `PLATFORM_FULL` conserva todos los gates previos; `APPLICATION` omite solo SFTPGo explícito en backend y `DOCS_ONLY` conserva los controles mínimos de repositorio y secretos.

## OLA4-DIS-001a + TEC-QLT-008a

- `SYSTEM_ADMIN` administra scopes `ACTIVE|DISABLED`; `IT_OPERATOR` solo lista/consulta `ACTIVE` y no puede distinguir deshabilitado de inexistente.
- Nombre y CIDR se normalizan/validan antes de persistir; se rechazan nombre case-insensitive o CIDR exacto duplicados y bits de host, pero se permiten redes solapadas.
- Alta y cambio efectivo confirman scope y outbox mínimo en una transacción; no-op vigente no cambia versión/eventos y no-op obsoleto responde `409`.
- OpenAPI 4.4 conserva 4.3 byte a byte; su manifest declarativo falla ante omisiones, entradas inválidas, checksums históricos incorrectos o incompatibilidad.
- La clave SFTPGo coincide para bytes iguales en ubicaciones distintas e invalida ante cualquier entrada efectiva distinta.
- Frontend conserva formularios en error, no reintenta/anticipa mutaciones y segmenta/refresca caché por usuario, rol y página.
- `e2e/**` se clasifica `APPLICATION`; mecanismos CI, plataforma y rutas desconocidas continúan `PLATFORM_FULL`.

## OLA4-DIS-002a

- `SYSTEM_ADMIN` pre-registra, lista y consulta executors `PENDING`; cualquier otro rol queda denegado por backend y no recibe UI.
- El alta exige nombre único normalizado y entre uno y cincuenta scopes existentes y activos; asociación, identidad y outbox Discovery se confirman en una transacción y la asociación persiste si el scope se deshabilita después.
- Identity registra una única public key Ed25519 normalizada, idempotente para los mismos bytes y en conflicto ante sustitución o reutilización por cualquier identidad técnica; el outbox no contiene material de clave.
- El inicializador Discovery Executor opera sin red, separa estado y keypair del Agent, usa UUIDv4, creación exclusiva no-follow, ownership efectivo, modos `0700|0600` e idempotencia cerrada.
- OpenAPI 4.5 conserva 4.4 byte a byte y añade solo las cuatro operaciones autorizadas; frontend usa parsers cerrados, caché segmentada y estado sensible solo en memoria.
- La prueba E2E real recorre scope activo, pre-registro, inicialización local, registro de la clave pública, persistencia/outboxes y conservación de asociación tras deshabilitar el scope.

## OLA4-DIS-003a

- Solo `SYSTEM_ADMIN` activa un executor `PENDING`; se rechaza sin public key registrada o sin al menos un scope asociado todavía `ACTIVE`.
- SFTPGo se aprovisiona/reconcilia antes del commit con username/home exclusivos, public key única, sin contraseña, grupos, virtual folders, cuotas funcionales, `/jobs` o `/results`; drift exacto devuelve `409` sin mutar PostgreSQL.
- La primera activación persiste `ACTIVE`, versión `1`, username y timestamp, y un único `DISCOVERY_EXECUTOR_ACTIVATED` con payload `{"status":"ACTIVE"}`; repetición exacta no cambia versión/timestamp ni duplica evento.
- OpenAPI 5.0 conserva 4.5 byte a byte como histórico y limita la respuesta a cinco datos públicos de conexión; frontend no reintenta ni anticipa, reconsulta REST y conserva esa respuesta solo en memoria.
- El ejecutable guarda `sftp.json` privado de forma exclusiva/idempotente y verifica una sola conexión con private key exacta, host key Ed25519 fijada, `RejectPolicy`, `allow_agent=False` y `look_for_keys=False`.
- La prueba E2E Chromium real recorre activación, repetición idempotente, configuración/verificación Python, perfil SFTPGo y persistencia/outbox; no publica ni ejecuta jobs. Evidencia local: Python 120 pruebas + Ruff, frontend 68 + lint/typecheck/build, backend 126 `clean verify` + 17 migraciones y contratos verdes.

## OLA4-DIS-004a

- Solo `IT_OPERATOR` crea y consulta jobs; se exige executor `ACTIVE`, scope asociado `ACTIVE` y ausencia de otro job no terminal para ese executor.
- El alta confirma `PENDING`, perfil `BASIC`, UUIDv4 y TTL exacto de una hora con outbox mínimo antes de intentar publicación; una indisponibilidad devuelve `503`, conserva el pendiente y expone su URI de consulta mediante `Location`.
- La publicación usa un mensaje Discovery 1.0 cerrado de máximo 64 KiB y ruta aislada `/jobs/<jobId>.json`, con `.part`, fsync, rename atómico, verificación byte a byte e idempotencia por contenido; solo entonces confirma `DISPATCHED`/versión `1` y su outbox.
- SFTPGo expone `/jobs` al executor únicamente con `list|download`; no concede upload, overwrite, delete, rename ni acceso a buzones ajenos.
- OpenAPI 5.1 conserva 5.0 byte a byte, frontend no reintenta la mutación ni anticipa estados y `automation-collector` valida el bundle offline sin polling o ejecución.
- La verificación local cubre 138 pruebas Python ya integradas, 74 pruebas frontend más lint/typecheck/build, E2E Chromium multirepo real y 141 pruebas backend `clean verify` con 18 migraciones, SFTPGo real y contratos verdes.

## OLA4-DIS-005a

- `jaxon-discovery-executor run-once --state-dir <ruta>` selecciona determinísticamente como máximo un `<jobId>.json` final, valida Job 1.0 offline y exige coincidencia de filename, job, executor y scope.
- El estado local privado usa solo `RECEIVED|RUNNING|RESULT_READY|DELIVERED`, lock no bloqueante, no-follow, `0700|0600`, fsync y reemplazo atómico; un reinicio desde `RUNNING` emite `EXECUTION_INTERRUPTED` sin repetir probes.
- `BASIC` rechaza scopes mayores de 4096 direcciones, limita concurrencia a 32 y timeout a 2 s, prueba únicamente TCP `22,53,80,443,445,3389`, usa ARP solo pasivo y no realiza UDP, banners, autenticación, fingerprinting, explotación o salida del CIDR.
- Discovery Result 1.0 es cerrado, máximo 5 MiB/4096 observaciones, correlaciona UUIDv4 Job/Executor/Scope/Result, exige tiempos/contadores/IP/puertos coherentes y no contiene texto libre, rutas, excepciones, secretos o credenciales.
- `/results` permite solo listar, descargar, upload sin overwrite y rename; la entrega exclusiva `.part -> .json` relee bytes finales, acepta un final idéntico y rechaza cualquier conflicto sin delete.
- Backend acepta upgrades exactos desde los perfiles históricos sin `/jobs` y `/jobs`-only, pero rechaza drift; no monta ni ingiere el buzón Result y mantiene el Job oficial `DISPATCHED`.
- Job 1.0, OpenAPI 5.1, dependencias, lockfiles, migraciones, workflows y frontend permanecen inmutables.

## OLA4-DIS-006a

- Backend monta `/results` con escritura limitada a mover el final a un claim interno atómico y eliminarlo tras registrar el desenlace, ignora `.part` y procesa únicamente finales UUIDv4 regulares, estables, sin symlinks, UTF-8 estrictos y de hasta 5 MiB dentro del buzón aislado del executor.
- Antes de mutar valida Discovery Result 1.0, filename, Job `DISPATCHED`, executor/scope/perfil, tiempos no futuros, contadores exactos, IP dentro del CIDR y puertos permitidos.
- Ledger SHA-256/tamaño hace idempotente una repetición idéntica; cualquier reemplazo posterior de bytes se registra como conflicto y un inválido no cambia Job ni dispositivos.
- La aceptación transaccional realiza solo `DISPATCHED -> SUCCEEDED|FAILED`, versión `2`, evidencia terminal y un `DISCOVERY_JOB_COMPLETED`; `SUCCEEDED` crea dispositivos `UNREVIEWED` y `FAILED` ninguno.
- OpenAPI 5.2 conserva 5.1 byte a byte y añade solo `GET /discovery-jobs/{jobId}/discovered-devices`, con autorización `IT_OPERATOR`, paginación 0/20/máximo 50 y orden estable.
- Frontend valida estrictamente Job/dispositivos, segmenta caché por usuario y usa REST como fuente oficial, sin SSE ni mutaciones de revisión/incorporación. `automation-collector`, dependencias y workflows permanecen intactos.

## OLA4-DIS-007a

- Solo `IT_OPERATOR` ignora un dispositivo individual perteneciente al Job indicado; recurso inexistente, UUID inválido o pertenencia incorrecta reciben el mismo `404`.
- La transición efectiva `UNREVIEWED -> IGNORED` bloquea la fila, exige versión `0`, fija versión `1`, timestamp y actor interno, y confirma un único `DISCOVERED_DEVICE_IGNORED` mínimo en la misma transacción.
- Repetir `IGNORED` con versión vigente es no-op sin timestamp, versión o evento nuevos; versión obsoleta devuelve `409`, incluso si el estado solicitado ya coincide.
- Dos solicitudes concurrentes con versión `0` producen exactamente un éxito, un `409` y un evento; un fallo del outbox revierte estado y revisión.
- OpenAPI 6.0 conserva 5.2 byte a byte, expone `UNREVIEWED|IGNORED`, `version|reviewedAt` y nunca `reviewedByUserId`; el salto major conserva el gate estricto ante la ampliación breaking del enum cerrado.
- Frontend pide confirmación, no reintenta ni anticipa la mutación y reconsulta la página exacta tras éxito o `409`; no añade SSE ni incorporación.

## OLA4-DIS-008a

- Solo `IT_OPERATOR` vincula un dispositivo `UNREVIEWED` perteneciente al Job indicado con un activo existente y `ACTIVE`; path inválido, inexistencia o pertenencia incorrecta reciben `404` uniforme para el dispositivo.
- La transición efectiva bloquea la fila, exige versión `0`, consulta Inventory mediante `AssetLookup`, fija `INCORPORATED`, versión `1`, revisión interna y `inventory_asset_id`, y confirma un único evento mínimo en la misma transacción.
- Repetir con versión `1` y el mismo activo es no-op; versión obsoleta, activo distinto, `IGNORED` o transición inversa devuelven `409`. Un activo inexistente devuelve `404` y uno no `ACTIVE`, `409`.
- No existe FK cross-schema, mutación de Inventory, creación de activo ni deduplicación entre Jobs. Un fallo de outbox revierte vínculo, estado y revisión.
- OpenAPI 7.0 conserva 6.0 byte a byte y expone `UNREVIEWED|IGNORED|INCORPORATED`, `assetId` nullable y tres estados coherentes sin actor interno.
- Frontend solicita UUID explícito, confirma, evita doble envío, conserva el valor ante error, no reintenta ni anticipa y reconsulta la página exacta tras éxito o `409`.

## OLA4-DIS-008b

- Solo `IT_OPERATOR` puede crear un activo desde un dispositivo `UNREVIEWED`; la petición cerrada contiene `name|assetType|expectedVersion` y el activo resultante siempre es `ACTIVE`.
- Discovery bloquea primero el dispositivo y llama a la operación pública Inventory que reutiliza normalización, catálogo y evento de alta; no escribe tablas Inventory directamente.
- Activo, `ASSET_CREATED`, vínculo, transición `INCORPORATED`, revisión y `DISCOVERED_DEVICE_INCORPORATED` se confirman en una única transacción PostgreSQL; cualquier fallo revierte todo.
- Dos solicitudes concurrentes con versión `0` producen un éxito y un `409`, exactamente un activo y un evento de cada tipo. Toda repetición posterior devuelve `409` y no crea otro activo.
- OpenAPI 7.1 conserva 7.0 byte a byte y añade solo `/incorporate-new-asset`; no existe migración, dependencia o cambio de workflow.
- Frontend separa ambas acciones de incorporación, confirma la creación, no reintenta ni anticipa, conserva el formulario ante error y reconsulta la página exacta tras éxito o `409`.

## OLA5-AI-001a

- Solo `SYSTEM_ADMIN` registra, lista y consulta fuentes; backend exige sesión y CSRF en el alta y otros roles quedan denegados.
- El alta acepta únicamente multipart cerrado `name|file`, `text/plain`, UTF-8 estricto y hasta 1 MiB; nombre normalizado, tamaño y SHA-256 corresponden a los bytes finales almacenados.
- `DocumentStorage` usa raíz explícita, clave aleatoria opaca, rutas contenidas, no-follow, permisos privados, temporal exclusivo, fsync y rename atómico sin overwrite; nunca persiste ni expone filename o ruta física.
- Metadata `PENDING`/versión `0` y `AI_SOURCE_REGISTERED` 1.0 se confirman atómicamente solo después del archivo; el evento contiene exclusivamente `{"status":"PENDING"}`. Un fallo filesystem no persiste nada y un fallo DB no publica metadata.
- Bytes idénticos pueden producir fuentes distintas. Listado y detalle no descargan contenido ni exponen `storageKey`; la página usa 0/20/máximo 50 y orden `createdAt DESC, sourceId DESC`.
- OpenAPI 7.2 conserva 7.1 byte a byte. Frontend usa parser cerrado, caché por usuario, confirmación, bloqueo de doble envío, sin retry/optimistic update, conserva formulario ante fallo y reconsulta REST tras éxito.

## OLA5-AI-002a

- Solo `SYSTEM_ADMIN` solicita indexación manual de una fuente `PENDING` mediante sesión, CSRF y body cerrado `expectedVersion`; aceptación durable devuelve `202` y `INDEXING`, mientras `404|409|503` no crean trabajo incompatible.
- El modelo es exactamente `intfloat/multilingual-e5-small` revisión `614241f622f53c4eeff9890bdc4f31cfecc418b3`, dimensión 384, prefijos E5 y normalización L2. Modelo y tokenizer externos se verifican por tamaño y SHA-256 streaming y nunca se publican.
- El chunker determinista `token-480-overlap-72-v1` limita a 480 tokens con overlap 72 y persiste contenido/hash/rangos, identidad de modelo/tokenizer/chunker y vector `vector(384)`; existe índice HNSW coseno.
- El job durable se reclama en transacción corta, ejecuta filesystem/tokenización/inferencia fuera de transacción y publica atómicamente todos los chunks, `READY|FAILED` y el outbox. Un claim expirado termina `FAILED`, no reintenta inferencia y un resultado tardío se descarta sin declararlo éxito.
- OpenAPI 8.0 conserva 1.0--7.2 byte a byte, amplía el estado cerrado de `AiSource` y añade solo `POST /ai/sources/{sourceId}/index`. Frontend confirma, bloquea doble envío, no reintenta ni anticipa, conserva caché por usuario y reconsulta REST.

## OLA5-AI-003a

- Solo `SYSTEM_ADMIN` ejecuta `POST /ai/retrieval` con sesión y CSRF; sin sesión `401`, sin CSRF o con otro rol `403`, siempre antes de calcular embeddings.
- El body es cerrado: `query` string no blanca tras recortar espacios Unicode y caracteres de control o formato, máximo 2000 caracteres y entre 1 y 480 tokens; `limit` entero 1..10 con default 5. Propiedades desconocidas o tipos no exactos devuelven `422` sin reflejar la entrada; JSON mal formado, claves duplicadas o tokens finales `400`.
- Solo se recuperan chunks de fuentes `READY` del modelo, revisión y tokenizer fijados; fuentes `PENDING|INDEXING|FAILED|DISABLED` u otro modelo nunca aparecen.
- La recuperación es exacta por coseno y el orden total es similitud descendente, `sourceId` y ordinal ascendentes; dos consultas iguales producen la misma respuesta.
- Sin evidencia se devuelve `200` con `results: []`. Cada resultado contiene solo `sourceId`, nombre, SHA-256 del original, `chunkId`, ordinal, rango de tokens, SHA-256 y contenido del fragmento y similitud finita en `[-1, 1]`; nunca rutas, `storageKey` ni vectores.
- Proveedor no disponible o embedding inválido devuelve `503` `AI_RETRIEVAL_UNAVAILABLE`. Ninguna consulta persiste datos, muta fuentes ni crea outbox.
- OpenAPI 8.1 conserva 8.0 byte a byte y es compatible. El benchmark local con modelo real y PostgreSQL/pgvector cumple p95 <= 500 ms.
- Frontend muestra la vista solo a `SYSTEM_ADMIN`, valida la respuesta de forma cerrada, segmenta la caché por usuario, presenta similitud y referencia de fuente, y etiqueta los fragmentos como evidencia recuperada, no como respuesta generada.

## OLA5-AI-006a

- Solo `SYSTEM_ADMIN` ejecuta `POST /ai/sources/{sourceId}/disable` con sesión y CSRF; sin sesión `401`, sin CSRF o con otro rol `403`. UUID inválido y fuente inexistente devuelven el mismo `404`.
- Body cerrado `{"expectedVersion": entero >= 0}`: propiedades desconocidas o tipos no exactos `422`; JSON mal formado, claves duplicadas o tokens finales `400`.
- Únicas transiciones efectivas `READY|FAILED -> DISABLED`; versión obsoleta, `PENDING` o `INDEXING` devuelven `409`; `DISABLED` con versión vigente es no-op sin nueva versión ni evento.
- La transición bloquea la fuente, incrementa una vez la versión y captura atómicamente `AI_SOURCE_DISABLED` 1.0 con payload exclusivo `previousStatus|newStatus`; un fallo del outbox no deja estado parcial. Dos solicitudes concurrentes producen una transición, un `409` y un único evento.
- La fuente deshabilitada desaparece inmediatamente de `/ai/retrieval` y ya no admite indexación; original, chunks, embeddings, jobs y outbox histórico permanecen intactos.
- Una única migración amplía solo las restricciones del outbox AI conservando las reglas históricas. OpenAPI 8.2 conserva 8.1 byte a byte y es compatible.
- Frontend muestra "Deshabilitar" solo para `READY|FAILED`, exige confirmación, envía la versión observada, no reintenta ni anticipa estado, conserva errores accesibles, reconsulta listado y detalle tras éxito o `409` y no ofrece acciones sobre fuentes `DISABLED`.

## OLA6-AUD-001a

- Cada `AI_SOURCE_DISABLED` confirmado produce exactamente un `AuditEvent` append-only con envelope completo y metadata `previousStatus|newStatus`, aun con reintentos, duplicados o varios dispatchers concurrentes.
- La deshabilitación conserva contrato, atomicidad y respuesta; una indisponibilidad de Auditoría no la revierte ni la hace fallar y solo retrasa la materialización.
- La entrega por consumidor `AUDIT` sigue el retry de `AC-JAXON-057` y queda `FAILED` tras 10 intentos sin autoborrado; un evento defectuoso no bloquea los siguientes; los demás eventos AI no se tocan.
- Un duplicado idéntico es no-op; un contenido distinto nunca sobrescribe el evento registrado.
- Audit no lee ni escribe tablas ajenas; AI solo escribe en Audit mediante `AuditFactRecorder`.
- `GET /audit/events` es exclusivo de `AUDITOR`; sin sesión `401`, `SYSTEM_ADMIN` u otros roles sin `AUDITOR` `403`. Filtros de `AC-JAXON-056`, `size` 50 por defecto y máximo 200, orden estable, `422` sin eco para parámetros desconocidos, repetidos o inválidos.
- No se almacenan ni exponen datos prohibidos. OpenAPI 8.3 conserva 8.2 byte a byte y es compatible. Frontend muestra la vista solo a `AUDITOR`, con parser cerrado, caché por usuario y errores accesibles.

## OLA6-AUD-001b

- Cada alta administrativa futura confirma atómicamente un único `HUMAN_USER_CREATED` 2.0; el histórico 1.0 permanece intacto y no se despacha.
- Payload y metadata contienen exactamente `status=ACTIVE` y `roles`; `roles` es una única cadena canónica, no un array, con al menos un rol, ordenada según `AUDITOR|END_USER|IT_OPERATOR|SUPPORT_TECHNICIAN|SYSTEM_ADMIN` y separada exclusivamente por `|`. Se rechazan duplicados, desorden, roles desconocidos, separadores alternativos, claves extra y formatos alternativos. `AC-JAXON-094` ratifica retrospectivamente esta representación ya integrada.
- Identity controla su entrega `AUDIT`; procesa solo 2.0, usa hasta 10 intentos incluso para errores permanentes, backoff exponencial con jitter y tope de cinco minutos, y deja `FAILED` durable. Un evento defectuoso no bloquea otro.
- Audit materializa con `resourceVersion=null`, idempotencia por `source_event_id` y conflicto sin sobrescritura. `AI_SOURCE_DISABLED` 1.0, append-only y retención de cinco años permanecen intactos.
- Se crean exactamente dos migraciones. OpenAPI 8.3, endpoints, frontend, automation-collector, dependencias y workflows no cambian.

## OLA6-AUD-001c

- Cada login humano exitoso futuro confirma atómicamente `HUMAN_LOGIN_SUCCEEDED` 2.0 con recurso `AUTHENTICATION_ATTEMPT`, UUIDv4 estable del intento y `{}` exacto.
- Cada logout humano exitoso futuro confirma atómicamente `HUMAN_LOGOUT_SUCCEEDED` 2.0 con recurso `HUMAN_SESSION`, UUIDv4 de la sesión eliminada y `{}` exacto.
- Audit materializa ambos con actor/correlación UUIDv4, `SUCCESS`, `resourceVersion=null` y metadata `{}`; username, IP, credenciales, sesión/token, CSRF, hashes, rate-limit keys y claves adicionales se rechazan.
- Los eventos 1.0 permanecen byte a byte y no se reclaman; `HUMAN_LOGIN_FAILED` queda fuera.
- Se conservan idempotencia, conflicto sin sobrescritura, `SKIP LOCKED`, 10 intentos, backoff+jitter y aislamiento de eventos defectuosos.
- Se crean exactamente dos migraciones. OpenAPI 8.3, endpoints, frontend, automation-collector, dependencias y workflows no cambian.

## OLA6-AUD-001d

- Cada fallo futuro crea atómicamente con `login_attempts` un `HUMAN_LOGIN_FAILED` 2.0: actor UUIDv4 si existe principal verificable o nulo en caso contrario, recurso `AUTHENTICATION_ATTEMPT`, `DENIED` y payload/metadata exactos `{"reason":"INVALID_CREDENTIALS"}`.
- Cinco fallos en 15 minutos producen cinco eventos; el sexto y posteriores devuelven `429` sin validar contraseña y sin crear intento, outbox o AuditEvent.
- El registro efectivo de una clave Agent o Discovery Executor crea un único evento 2.0 con actor humano, recurso técnico exacto, `SUCCESS` y `{}`; el no-op de la misma clave y los rechazos no emiten otro evento.
- Username, IP, credenciales, tokens, CSRF, hashes/rate-limit keys, claves públicas, fingerprints, comentarios OpenSSH y metadata adicional se rechazan en Java y PostgreSQL.
- El dispatcher reclama solo la allowlist 2.0, conserva `SKIP LOCKED`, retries, errores, idempotencia, conflicto e aislamiento; todo 1.0 permanece intacto y sin entrega `AUDIT`.
- Se crean exactamente dos migraciones. OpenAPI 8.3, endpoints, frontend, automation-collector, dependencias y workflows no cambian.

## OLA6-AUD-001e

- Cada alta futura de activo confirma atómicamente `ASSET_CREATED` 2.0 con productor `INVENTORY`, actor humano, recurso `ASSET`, versión `0`, `SUCCESS` y `{}`.
- Cada pre-registro y activación efectiva futura confirma `AGENT_PRE_REGISTERED` 2.0 versión `0` o `AGENT_ACTIVATED` 2.0 versión `1`, con productor `COLLECTOR`, actor humano, recurso `AGENT`, `SUCCESS` y `{}`; el no-op de activación no duplica evento.
- Inventory y Collector controlan entregas y dispatchers independientes, reclaman solo su allowlist 2.0 con `SKIP LOCKED`, 10 intentos, backoff+jitter, idempotencia y conflicto sin sobrescritura.
- Los eventos 1.0 permanecen byte a byte y sin entrega `AUDIT`; Audit conserva catálogo cerrado, append-only y regresión AI/Identity.
- Se crean exactamente tres migraciones. OpenAPI 8.3, endpoints, frontend, automation-collector, dependencias y workflows no cambian.

## OLA6-AUD-001f

- Cada alta futura de ámbito, cambio efectivo de estado, pre-registro y activación efectiva de executor confirma atómicamente su evento Discovery 2.0 con actor humano UUIDv4, recurso/correlación UUIDv4 y `SUCCESS`.
- Scope created y executor pre-registered usan versión `0` y `{}`; executor activated usa versión `1` y `{}`; status changed usa nueva versión `>=1`, estados `ACTIVE|DISABLED` distintos y metadata exacta `previousStatus|newStatus`.
- Los no-ops de estado y activación no duplican evento ni hecho Audit. Los eventos 1.0 permanecen byte a byte, sin entrega, reinterpretación, replay o backfill.
- Discovery controla una única entrega y dispatcher propios para los cuatro tipos, con `SKIP LOCKED`, 10 intentos, backoff+jitter, errores cerrados, idempotencia, conflicto sin sobrescritura y aislamiento.
- Audit conserva catálogo cerrado, append-only y regresión AI/Identity/Inventory/Collector. Discovery accede únicamente mediante `AuditFactRecorder`.
- Se crean exactamente dos migraciones. OpenAPI 8.3, endpoints, frontend, automation-collector, dependencias, workflows, Docker/Compose, SFTPGo, SSE y Production no cambian.

## OLA6-AUD-001g

- Cada creación futura confirma `DISCOVERY_JOB_CREATED` 2.0 con actor humano UUIDv4, recurso `DISCOVERY_JOB`, versión `0`, `SUCCESS`, correlación original y `{}`.
- Solo la transición efectiva `PENDING -> DISPATCHED` confirma `DISCOVERY_JOB_DISPATCHED` 2.0 con el actor/correlación humanos originales, versión `1`, `SUCCESS` y `{}`.
- La aceptación terminal confirma atómicamente Job y `DISCOVERY_JOB_COMPLETED` 2.0: actor `DISCOVERY_EXECUTOR` validado, versión `2`, `{}` y resultado `SUCCESS` para `SUCCEEDED` o `FAILED` para `FAILED`; un fallo del outbox revierte toda la aceptación.
- Duplicados, conflictos, rechazos, finales tardíos y reprocesamientos no crean completion adicional. Los eventos 1.0 permanecen byte a byte, sin claim, reinterpretación, replay o backfill.
- Discovery amplía únicamente la allowlist de su entrega/dispatcher existente; Audit conserva catálogo cerrado, append-only, idempotencia, conflicto sin sobrescritura y regresión de todos los productores previos.
- Se crean exactamente dos migraciones posteriores a `V20261130040000`. OpenAPI 8.3, endpoints, contratos Job/Result 1.0, frontend, automation-collector, dependencias, workflows, Docker/Compose/SFTPGo, SSE y Production no cambian.

## OLA6-AUD-001h

- Cada transición futura efectiva `UNREVIEWED -> IGNORED` confirma atómicamente `DISCOVERED_DEVICE_IGNORED` 2.0 con actor humano UUIDv4, recurso `DISCOVERED_DEVICE`, versión `1`, `SUCCESS`, correlación original y metadata exacta de ambos estados.
- Cada transición futura efectiva `UNREVIEWED -> INCORPORATED`, hacia activo `ACTIVE` existente o creado mediante `AssetCreator`, confirma el mismo envelope y metadata exacta con `assetId` UUIDv4 canónico lowercase.
- Crear el activo conserva la transacción pública cross-module y produce independientemente `ASSET_CREATED` 2.0 e `DISCOVERED_DEVICE_INCORPORATED` 2.0. Un rollback revierte activo, dispositivo y ambos outboxes.
- No-ops, versiones obsoletas, conflictos, concurrencia perdedora y activos ausentes/inactivos no emiten evento o Audit falso. Los eventos 1.0 permanecen byte a byte, sin claim, reinterpretación, replay o backfill.
- Discovery amplía de siete a nueve tipos únicamente la allowlist de entrega/dispatcher existente; Java y PostgreSQL rechazan metadata incompleta/adicional, estados alternativos, UUID no canónico y combinaciones inválidas.
- Se crean exactamente `V20261130070000__discovery_device_audit_events.sql` y `V20261130080000__audit_discovery_device_catalog.sql`. OpenAPI 8.3, contratos históricos, endpoints, otros repositorios, dependencias, workflows, Docker/Compose/SFTPGo, SSE y Production no cambian.

## OLA6-AUD-001i

- Las futuras emisiones de los ocho eventos existentes del agregado Ticket usan 2.0 con productor `SUPPORT`, actor humano UUIDv4, recurso `TICKET`, `SUCCESS`, correlación original y versión real del agregado.
- Metadata exacta: created `type|status|priority|source`; comment `commentId|visibility`; assigned `assigneeUserId`; priority/status valores anterior/nuevo; lifecycle `lifecycleEventId|fromStatus|toStatus`, y closed añade `closeReason`.
- Java y PostgreSQL rechazan extras, metadata incompleta, UUID no canónico, transiciones imposibles y combinaciones inválidas. Los eventos 1.0 históricos no se reclaman, reproducen ni reinterpretan.
- Cada mutación efectiva y su 2.0 se confirman atómicamente. No-op, conflicto, versión obsoleta, autorización denegada, transición inválida y rollback no emiten.
- Support posee entrega, persistencia, dispatcher, scheduler, retry/backoff, métricas y `FAILED`; Audit recibe solo `AuditFactRecorder`, con idempotencia y conflicto sin sobrescritura.
- Se crean exactamente `V20261130090000__support_ticket_audit_delivery.sql` y `V20261130100000__audit_support_ticket_catalog.sql`. OpenAPI 8.3, Agent/Discovery, endpoints, otros repositorios, dependencias, workflows, contenedores y Production no cambian.
