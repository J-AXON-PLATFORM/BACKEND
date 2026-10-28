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

La matriz permiso->operación se completa en contratos/HU ejecutables durante implementación, manteniendo `default deny`, checks contextuales y ownership por módulo.
<!-- JAXON-CKP005:END -->

## Propósito

Definir modelo de permisos

- RBAC/ABAC/other
- Roles
- Permissions
- Ownership rules
- Enforcement points
