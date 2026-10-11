# Contratos del sistema

> **Clasificación:** OBLIGATORIO cuando existen fronteras de información.  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.


<!-- JAXON-CKP005:START -->
## Instancia J-AXON -- contratos concretados

- Web: OpenAPI 3.1.2, REST/HTTPS, `/api/v1`, contrato MAJOR.MINOR independiente de SemVer.
- Agent: JSON Schema Draft 2020-12 sobre SFTP; fuente canónica de schemas en backend.
- Discovery Executor: contrato propio versionado sobre buzón SFTP dedicado; no reutilizar identidad/schema Agent por conveniencia.
- JSON `camelCase`; enums `UPPER_SNAKE_CASE`; IDs técnicos v1 UUIDv4 y externos strings opacos; tiempos RFC3339 UTC.
- Agent Task: `taskId`, `taskType`, `expiresAt`, `payload`; catálogo v1 `COLLECT_SNAPSHOT|COLLECT_NETWORK_STATE|COLLECT_SOFTWARE_INVENTORY`, nunca shell/comando arbitrario.
- Task lifecycle: `PENDING|DISPATCHED|RUNNING|SUCCEEDED|FAILED|REJECTED|EXPIRED`; `expiresAt` limita inicio; Agent reporta capabilities y revalida localmente.
- Automation/n8n: `executionId` estable y callback con `eventId`, timestamp e HMAC-SHA-256; protección anti-replay 5 min.
- REST errors: RFC 9457 `application/problem+json` + `code`, `correlationId`, `errors` cuando aplique.
- SSE solo transporta avisos pequeños best-effort; REST conserva la fuente de estado y no se exige replay durable v1.
- Interno módulo->módulo: command/query por API pública in-process; hecho confirmado por evento. Eventos durables usan Transactional Outbox PostgreSQL por schema productor, `at-least-once`, `eventId` estable y consumidor idempotente; no broker externo v1.
- MINOR solo compatible; MAJOR breaking. Validar frontera/identidad/deduplicación antes del negocio.
<!-- JAXON-CKP005:END -->

## Inventario contractual aprobado

| ID | Frontera | Acuerdo vigente | Pendiente de diseño detallado |
|---|---|---|---|
| CTR-JAXON-WEB | Web/backend | OpenAPI 3.1.2 ejecutable 1.0 para login, `/auth/me`, alta/consulta Asset y technical-state; sesión opaca, CSRF y RFC 9457 | login y `/auth/me` implementados por OLA1-IAM-001a; controladores Inventory, proveedor y Swagger UI runtime no implementados |
| CTR-JAXON-SSE | Backend/navegador | SSE/HTTPS selectivo servidor->browser, best-effort; browser->backend REST | eventos concretos/UX por feature; sin replay durable v1 |
| CTR-JAXON-SNAPSHOT | Agent/Collector | JSON Schema 2020-12 ejecutable 1.0; hostname, OS, arquitectura, CPU lógica, RAM y storage; validación offline | transporte/procesamiento SFTP e identidad de negocio no implementados |
| CTR-JAXON-TASK | Backend/buzón/Agent | schema 1.0 para tres tareas read-only con payload vacío/cerrado, UUIDv4 y UTC | polling, capabilities y ejecución no implementados |
| CTR-JAXON-RESULT | Agent/backend | schema 1.0 correlacionado por `taskId`; solo `SUCCEEDED|FAILED|REJECTED`, error seguro | observaciones funcionales de red/software y procesamiento no implementados |
| CTR-JAXON-INTERNAL | módulo/módulo | commands/queries in-process por API pública; hechos por eventos internos; outbox PostgreSQL durable, at-least-once e idempotencia | tipos Java/event payloads ejecutables por caso de uso |
| CTR-JAXON-AI | Backend/proveedor IA | adapters `EmbeddingProvider`/`GenerationProvider`; retrieval autorizado, fuentes/citas y degradación segura | proveedor/modelo/protocolo concreto |
| CTR-JAXON-N8N | Backend/n8n | async start + callback; `executionId`; HMAC-SHA-256/timestamp/`eventId`; backend conserva estado | endpoints/headers exactos y workflows concretos |
| CTR-JAXON-DISCOVERY | Backend/SFTPGo/Discovery Executor | job/result sobre SFTP, identidad separada; scope/executor/job validados | schema/archivo ejecutable y límites exactos por perfil |

## Contrato interno aprobado -- Lote 53

- Command/query solicita una operación o lectura y usa la API pública del módulo proveedor de forma síncrona in-process.
- Evento interno expresa un hecho ya ocurrido; no usar eventos tipo `PleaseDoX` como command disfrazado.
- No depender de repositories, entidades JPA ni internals de otro módulo; evitar ciclos de dependencia.
- Cuando una operación cross-module requiera atomicidad y no cruce sistemas externos puede compartir transacción, pero cada módulo muta mediante su propia operación pública.
- Evento durable: Transactional Outbox en el schema del productor; el cambio de negocio y la captura durable del evento se confirman juntos.
- Entrega `at-least-once`; consumidores persistentes deben ser idempotentes. No se promete `exactly-once` ni orden global.
- Envelope conceptual: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `producerModule`, `resourceType`, `resourceId`, `resourceVersion` cuando aplique, `correlationId`, `causationId` opcional, actor opcional y payload allowlisted.
- Versionado del evento `MAJOR.MINOR`; payload durable default <=64 KiB.
- Retry por consumidor: default ~1 s, exponential backoff+jitter, cap 5 min, 10 intentos; al agotarse queda `FAILED` durable para reproceso controlado.
- Outbox entregado: 7 días default; `PENDING/FAILED` no se autoborran. Replay histórico general queda fuera de v1.
- Automation solo procesa eventos explícitamente elegibles; n8n se alcanza a través de Automation y no consume el bus interno directamente. AI no es consumidor global.
- SSE es adaptación best-effort de algunos cambios y nunca sustituye al evento interno durable ni a REST.
- No Kafka/RabbitMQ/NATS/Pulsar, AsyncAPI obligatorio, event sourcing, sagas framework ni transacciones distribuidas en v1.

## Reglas

- Contratos son propiedad de J-AXON, no de proveedores.
- Versionado de contrato: `MAJOR.MINOR`; MINOR compatible, MAJOR breaking.
- JSON/SFTP debe exponer versión legible por máquina; OpenAPI declara su versión.
- Validar frontera antes de modificar estado oficial.
- Distinguir éxito de transporte, recepción/validación y resultado de negocio.
- Tareas/jobs tienen identidad estable controlada por backend; nombre de campo no fijado.
- No introducir Kafka/RabbitMQ solo para comunicación interna inicial.
- Swagger UI no reemplaza JSON Schema; SSE no reemplaza REST ni SFTP.

---
> **Clasificación:** OBLIGATORIO cuando existen dos o más componentes que intercambian información.  
> **Estado:** Plantilla dinámica  
> **Principio:** Contract-first cuando el riesgo de incompatibilidad lo justifique.

## Propósito

Definir y gobernar los acuerdos entre partes del sistema. Un contrato no es exclusivo de frontend y backend: puede existir entre cualquier consumidor y proveedor de datos, comandos, eventos o capacidades.

## Posibles fronteras contractuales

Crear contratos únicamente donde existan fronteras reales. Ejemplos:

- frontend ↔ backend;
- aplicación móvil ↔ backend;
- módulo ↔ módulo;
- servicio ↔ servicio;
- consumidor ↔ proveedor de API;
- productor ↔ consumidor de eventos;
- aplicación ↔ broker/cola;
- backend ↔ proveedor externo;
- sistema ↔ sistema de terceros;
- proceso batch ↔ origen/destino;
- CLI ↔ API;
- plugin/extensión ↔ host.

La base de datos no debe tratarse automáticamente como una API pública entre módulos. Si varios módulos comparten tablas, esa decisión debe estar explícitamente justificada porque crea un contrato de datos y acoplamiento.

## Inventario de contratos

Mantener un inventario cuando el proyecto lo necesite:

| ID | Consumidor | Proveedor | Tipo | Versión | Fuente canónica | Compatibilidad | Pruebas |
|---|---|---|---|---|---|---|---|
| CTR-001 | | | | | | | |

Tipos posibles:

- HTTP/REST;
- GraphQL;
- gRPC/RPC;
- evento/mensaje;
- archivo/intercambio batch;
- interfaz de módulo;
- webhook;
- protocolo externo;
- otro.

## Contenido mínimo de cada contrato

Documentar según aplique:

### Identidad

- ID;
- propósito;
- propietario/proveedor;
- consumidores conocidos;
- estado;
- versión.

### Entrada

- estructura/schema;
- tipos;
- campos obligatorios/opcionales;
- formatos;
- validaciones;
- límites;
- unidades;
- codificación;
- semántica de valores nulos.

### Salida

- estructura/schema;
- significado de cada campo;
- estados posibles;
- orden/paginación cuando aplique;
- consistencia esperada.

### Errores

- códigos/estados;
- estructura de error;
- errores recuperables/no recuperables;
- información segura para exponer;
- correlación/trazabilidad.

### Semántica operativa

- idempotencia;
- atomicidad;
- orden de mensajes;
- entrega al menos una vez / como máximo una vez / otra semántica;
- timeouts;
- reintentos;
- backoff;
- circuit breaker cuando corresponda;
- concurrencia;
- límites de uso;
- tamaño máximo;
- latencia esperada.

### Seguridad

- autenticación;
- autorización;
- cifrado;
- clasificación de datos;
- datos que nunca deben viajar;
- auditoría requerida.

## Fuente canónica

Preferir especificaciones legibles por máquinas cuando la tecnología lo permita, por ejemplo:

- OpenAPI/JSON Schema para HTTP;
- AsyncAPI para eventos;
- Protocol Buffers para gRPC;
- GraphQL Schema Definition Language;
- Avro/JSON Schema/Protobuf para mensajes;
- interfaces/tipos compilables para límites internos cuando sea apropiado.

La tecnología concreta **no está preseleccionada**. El proyecto decide cuál aplica.

La documentación humana explica intención, reglas de negocio y decisiones que el schema por sí solo no expresa.

## Compatibilidad y evolución

Definir por contrato:

- política de versionado;
- cambios compatibles;
- cambios incompatibles;
- periodo de deprecación;
- estrategia de migración;
- coexistencia de versiones si aplica;
- responsables de consumidor y proveedor.

Un cambio incompatible no debe publicarse silenciosamente.

## Cambios típicamente compatibles

Dependen de la tecnología. Validar siempre, pero ejemplos frecuentes:

- añadir un campo opcional que consumidores toleran;
- añadir un nuevo endpoint independiente;
- añadir un nuevo evento sin alterar contratos existentes.

## Cambios potencialmente incompatibles

- eliminar o renombrar campos;
- cambiar tipos;
- cambiar semántica de un valor existente;
- convertir opcional en obligatorio;
- cambiar códigos de error esperados;
- alterar orden o semántica de entrega;
- cambiar autenticación sin transición.

## Contratos de eventos

Además del payload documentar:

- nombre del evento;
- productor;
- consumidores;
- hecho ocurrido, no intención ambigua;
- esquema y versión;
- clave de partición si aplica;
- orden esperado;
- política de duplicados;
- idempotencia;
- retención;
- replay;
- tratamiento de eventos fallidos.

## Contratos entre módulos

Si se usa monolito modular o arquitectura por módulos, definir:

- capacidades públicas del módulo;
- tipos expuestos;
- operaciones permitidas;
- eventos publicados;
- datos que siguen siendo privados;
- dependencias permitidas/prohibidas.

Los consumidores no deben depender de detalles internos del proveedor.

## Relación con flujos

Cada flujo que cruce una frontera debe enlazar los contratos usados.

Ejemplo:

```text
FLOW-ORD-001
  -> CTR-ORD-API-001
  -> CTR-INVENTORY-001
  -> CTR-PAYMENT-EXT-001
```

## Relación con pruebas

Cada contrato relevante debe indicar cómo se verifica:

- schema validation;
- consumer-driven contract tests;
- provider verification;
- compatibility checks;
- integration tests;
- E2E solo donde aporte valor adicional.

Consultar `docs/09-pruebas/PRUEBAS_CONTRATO.md`.

## Regla de completitud

Una funcionalidad que modifica un contrato no se considera terminada hasta actualizar:

- fuente canónica del contrato;
- consumidores/proveedores afectados;
- pruebas de contrato;
- documentación y flujos;
- estrategia de compatibilidad/migración cuando aplique.
