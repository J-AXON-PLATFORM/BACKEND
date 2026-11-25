# Authorization


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.



<!-- JAXON-CKP005:START -->
## Instancia J-AXON -- autorización v1

Roles: `SYSTEM_ADMIN`, `IT_OPERATOR`, `SUPPORT_TECHNICIAN`, `END_USER`, `AUDITOR`; un usuario puede poseer múltiples roles.

Modelo: **roles -> permisos -> checks contextuales**, con `default deny` y enforcement en backend. Frontend puede ocultar acciones solo como UX.

Refinamientos aprobados:
- `SYSTEM_ADMIN` administra `AuthorizedScope` y `DiscoveryExecutor`; administra catálogos/configuración permitida, pero no obtiene por sí solo autoridad funcional universal sobre tickets, activos o auditoría;
- `IT_OPERATOR` gestiona Inventory, Agents/tareas read-only y Discovery autorizado; puede actuar como requester de Support, pero no adquiere permisos de técnico de soporte;
- `SUPPORT_TECHNICIAN` gestiona tickets: clasificación/prioridad/asignación, estados, comentarios `PUBLIC|INTERNAL`, adjuntos y cierre/reapertura;
- `END_USER` crea/consulta/participa en sus propios tickets y puede cerrar/reabrir los propios según reglas de Support; no ve notas internas;
- `AUDITOR` posee lectura de auditoría/reportes, sin mutaciones; `SYSTEM_ADMIN` necesita además `AUDITOR` para raw Audit;
- Support/AI/Reporting solo consultan activos/observaciones mediante contratos y permisos contextuales;
- el Discovery Executor vuelve a validar que el target esté dentro del scope;
- un permiso general nunca habilita shell arbitraria, tarea Agent fuera del catálogo ni bypass del módulo propietario;
- múltiples roles se combinan explícitamente cuando una persona necesita responsabilidades de más de un dominio.

En `OLA4-DIS-001a`, `POST /discovery/scopes` y `PUT /discovery/scopes/{scopeId}/status` requieren `SYSTEM_ADMIN`; `GET` admite `SYSTEM_ADMIN|IT_OPERATOR`, pero filtra `DISABLED` para `IT_OPERATOR` con `404` indistinguible del recurso inexistente. Otros roles permanecen denegados por defecto y frontend no sustituye ese enforcement.

En `OLA4-DIS-002a`, las cuatro operaciones `/discovery/executors` requieren `SYSTEM_ADMIN`. El backend valida que todos los scopes asociados existan y estén `ACTIVE` al crear; frontend solo oculta la función como UX y no sustituye `default deny`. La identidad técnica pertenece a Identity y la asociación pertenece a Discovery, comunicados por contrato público in-process.

En `OLA4-DIS-003a`, `POST /discovery/executors/{executorId}/activate` requiere `SYSTEM_ADMIN`; cualquier otro rol queda denegado por backend. Antes de aprovisionar se exige public key registrada mediante Identity y al menos un scope asociado todavía `ACTIVE`. La visibilidad o ausencia del botón frontend no sustituye estas comprobaciones ni `default deny`.

En `OLA4-DIS-004a`, `POST|GET /discovery/jobs` requiere `IT_OPERATOR`; `SYSTEM_ADMIN` sin ese rol y el resto de roles permanecen denegados. El backend comprueba executor `ACTIVE`, scope `ACTIVE`, asociación y exclusión no terminal en cada alta. El executor solo obtiene `list|download` sobre su `/jobs` aislado; no recibe permisos de escritura ni acceso a otros buzones.

En `OLA4-DIS-006a`, `GET /discovery-jobs/{jobId}/discovered-devices` requiere `IT_OPERATOR`; `SYSTEM_ADMIN` sin ese rol y los demás roles permanecen denegados. La reconciliación técnica no confía en la ruta SFTP: mueve primero el final a un claim atómico único bajo `.jaxon-claims`, fuera de los virtual folders, y revalida executor, Job, scope, perfil, CIDR y contenido antes de una transición atómica. Backend no altera los bytes reclamados ni expone rutas físicas, hashes o razones internas por API.

En `OLA4-DIS-007a`, `POST /discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/ignore` requiere `IT_OPERATOR`, sesión y CSRF. Backend comprueba Job y dispositivo conjuntamente bajo bloqueo y oculta con `404` uniforme inexistencia, UUID inválido o pertenencia incorrecta; frontend solo presenta la acción y no sustituye `default deny`. El actor revisor permanece interno y no se serializa.

En `OLA4-DIS-008a`, `POST /discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/incorporate` requiere `IT_OPERATOR`, sesión y CSRF. Backend aplica la misma ocultación contextual, toma actor solo de sesión y valida el activo existente/`ACTIVE` por contrato público Inventory; no confía en la UI, no escribe Inventory y no expone el actor revisor.

En `OLA4-DIS-008b`, `POST /discovery-jobs/{jobId}/discovered-devices/{discoveredDeviceId}/incorporate-new-asset` mantiene `IT_OPERATOR`, sesión, CSRF, ocultación contextual y actor interno. Backend valida el body cerrado y el catálogo Inventory; frontend no concede autoridad y no puede seleccionar otro estado que `ACTIVE`.

En `OLA5-AI-001a`, las tres operaciones `/ai/sources` requieren exclusivamente `SYSTEM_ADMIN`; el alta exige además CSRF. Backend valida media type, UTF-8, tamaño y nombre, obtiene el actor de la sesión y nunca expone contenido, filename, clave o ruta física. Frontend oculta el workspace a otros roles solo como UX y no sustituye `default deny`.

En `OLA5-AI-002a`, `POST /ai/sources/{sourceId}/index` requiere exclusivamente `SYSTEM_ADMIN`, sesión, CSRF y `expectedVersion`. Backend valida existencia/estado/versión bajo lock antes de disponibilidad del proveedor, nunca expone chunks, embeddings, rutas, failure detail o pesos y conserva default deny. Frontend solo presenta la acción como UX; no sustituye la autorización backend.

En `OLA5-AI-003a`, `POST /ai/retrieval` requiere exclusivamente `SYSTEM_ADMIN`, sesión y CSRF; el filtro de seguridad deniega antes de cualquier tokenización o embedding. La elegibilidad (`READY` y modelo fijado) se aplica dentro de la sentencia de recuperación, no como post-filtro. La respuesta expone solo metadata pública y fragmentos trazables; nunca `storageKey`, rutas, vectores ni detalles del proveedor, y los errores no reflejan la entrada. Frontend oculta la vista a otros roles solo como UX.

En `OLA5-AI-006a`, `POST /ai/sources/{sourceId}/disable` requiere exclusivamente `SYSTEM_ADMIN`, sesión y CSRF. Backend oculta con `404` uniforme UUID inválido o fuente inexistente, obtiene el actor de la sesión, valida versión y estado bajo bloqueo y no expone `storageKey`, rutas ni contenido. Frontend muestra la acción solo como UX y no sustituye `default deny`.

La matriz permiso->operación se completa en contratos/HU ejecutables durante implementación, manteniendo `default deny`, checks contextuales y ownership por módulo.
<!-- JAXON-CKP005:END -->

## Propósito

Definir modelo de permisos

- RBAC/ABAC/other
- Roles
- Permissions
- Ownership rules
- Enforcement points
