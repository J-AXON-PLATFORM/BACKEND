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

La matriz permiso->operación se completa en contratos/HU ejecutables durante implementación, manteniendo `default deny`, checks contextuales y ownership por módulo.
<!-- JAXON-CKP005:END -->

## Propósito

Definir modelo de permisos

- RBAC/ABAC/other
- Roles
- Permissions
- Ownership rules
- Enforcement points
