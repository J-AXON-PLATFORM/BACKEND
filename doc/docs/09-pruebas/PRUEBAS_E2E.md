# Pruebas E2E

> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla dinámica


<!-- JAXON-CKP003:START -->
## Instancia J-AXON -- primer E2E aprobado

Playwright pertenece conceptualmente al sistema completo aunque el harness pueda residir en `frontend`.

Primera vertical: 1) login `IT_OPERATOR`; 2) crear activo; 3) pre-registrar/enrolar Agent controlado; 4) Agent genera snapshot; 5) upload `.part` + rename SFTP; 6) Collector valida JSON/version/schema/identidad/deduplicación; 7) PostgreSQL persiste; 8) SSE avisa; 9) frontend consulta REST y muestra datos técnicos. Cruza frontend, identity, inventory, collector, PostgreSQL, SFTPGo, Agent Python, JSON Schema y SSE.

`OLA2-SSE-001a` hace ejecutables los pasos 8 y 9: Playwright mantiene abierto `EventSource`, el Agent real envía el snapshot, Collector confirma la persistencia y el navegador muestra el nuevo `snapshotId` mediante refetch REST sin pulsar actualización manual. Las pruebas backend adicionales cubren post-commit, silencio para duplicado/conflicto/rechazo, aislamiento por activo, `401/403/404/429`, límites global/per-user y sesión pasiva sin renovación.

`OLA2-STA-001a` amplía el mismo recorrido con tiempo determinista: un Agent `ACTIVE` comienza `UNKNOWN`, pasa a `ONLINE` tras aceptar el snapshot y a `OFFLINE` al situar `received_at` más allá de cinco minutos, sin esperar tiempo real. Un archivo final rechazado no modifica `lastSeenAt`; el estado administrativo permanece `ACTIVE` durante toda la secuencia.

`OLA4-DIS-003a` amplía el journey Discovery: crea scope, pre-registra executor, inicializa Ed25519 localmente, registra solo la public key, activa, recibe cinco datos públicos de conexión, configura `sftp.json` y verifica una conexión real contra SFTPGo. Repite la activación para comprobar idempotencia y valida perfil aislado, estado/versión/timestamp y evento mínimo en PostgreSQL antes de deshabilitar el scope. No publica, recoge ni ejecuta jobs o resultados Discovery.

`OLA4-DIS-004a` continúa el mismo journey antes de deshabilitar el scope: selecciona el executor y scope activos, crea un job `BASIC`, confirma `DISPATCHED`/versión `1`, reconsulta REST y verifica perfil `/jobs`, archivo final sin `.part`, payload Discovery 1.0 cerrado, TTL de una hora y ambos outboxes en PostgreSQL. No simula pickup, ejecución, resultado o escaneo.

`OLA4-DIS-006a` prolonga el journey mediante entrega real del Result final, ingestión PostgreSQL y presentación de un dispositivo `UNREVIEWED`. `OLA4-DIS-007a` añade después confirmación UI de “Ignorar”, respuesta REST, refetch de la misma página, estado `IGNORED` y verificación PostgreSQL/outbox, demostrando además que Inventory no cambia.

`OLA4-DIS-008a` añade un segundo dispositivo `UNREVIEWED`, captura un UUID de activo `ACTIVE`, confirma “Incorporar”, reconsulta la misma página y verifica `INCORPORATED`, vínculo y outbox Discovery, demostrando que el activo y el outbox Inventory no cambian.
<!-- JAXON-CKP003:END -->


## Estrategia browser aprobada Lote 47

- suite E2E principal de PR: Chromium;
- flujos críticos seleccionados (login, navegación, formularios principales, primera vertical, Support) se validan además en Firefox y WebKit antes de releases relevantes;
- Playwright pertenece al sistema completo aunque el harness resida en `frontend`;
- no multiplicar toda la suite por navegador en cada PR sin valor adicional.

## Propósito

Cubrir recorridos críticos sin convertir E2E en sustituto de unitarias, integración o contrato.

Derivar escenarios de los flujos E2E y criterios de aceptación documentados.

## Principios

- probar journeys de alto valor;
- usar datos controlados;
- preferir selectores/interacciones estables desde la perspectiva del usuario;
- evitar assertions sobre detalles internos innecesarios;
- no usar E2E para comprobar exhaustivamente cada regla pequeña;
- distinguir fallos de producto, test y entorno;
- conservar evidencia útil de fallos.
