# Personas


> **Clasificación:** CONDICIONAL  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Instancia J-AXON -- perfiles funcionales v1

- `SYSTEM_ADMIN`: administración de identidad/configuración y catálogos permitidos; no es bypass universal de datos/negocio. Para operar otros dominios debe poseer el rol correspondiente.
- `IT_OPERATOR`: Inventory, Collector/Agents, catálogo read-only de tareas v1 y Discovery autorizado; puede participar en Support como requester sin autoridad de técnico.
- `SUPPORT_TECHNICIAN`: gestión de tickets, notas internas/públicas, asignación/prioridad/estado, lectura técnica necesaria e IA/RAG de soporte; no modifica Inventory/Collector.
- `END_USER`: crear/consultar/participar en sus tickets y capacidades expresamente permitidas.
- `AUDITOR`: raw Audit + reportes autorizados e información relacionada, sin mutar operación.

Un usuario puede tener múltiples roles. Estos roles agrupan permisos; la autorización real usa permisos + checks contextuales en backend.

## Propósito

Describir perfiles de usuario relevantes sin inventar datos demograficos innecesarios.


## Persona template

- Name/role:
- Goals:
- Main tasks:
- Pain points:
- Permissions:
- Accessibility considerations:
