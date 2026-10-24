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
