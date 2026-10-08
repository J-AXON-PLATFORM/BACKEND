# Modelo de estados del proyecto


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Definir que actividades son apropiadas según la fase del proyecto.


| Estado | Actividades principales | Restricción principal |
|---|---|---|
| DISCOVERY | problema, usuarios, módulos, flujos, requisitos | evitar programar funcionalidad definitiva |
| PLANNING | arquitectura, backlog, estimaciones, decisiones | resolver bloqueos antes de desarrollo |
| DEVELOPMENT | TDD, implementación, refactor, integración | cambios significativos requieren decisión aprobada |
| QA | regresion, E2E, seguridad, aceptación | no ocultar defectos alterando tests |
| RELEASE | versión, changelog, despliegue, rollback | quality gates obligatorios |
| MAINTENANCE | bugs, deuda, parches, mejoras | mantener compatibilidad y trazabilidad |

## Readiness J-AXON

Los estados de fase anteriores no sustituyen los gates de readiness aprobados en Lote 54:

- `DESIGN_READY`: diseño/refinamiento suficiente y consolidado; no implica código ni autorización de implementación.
- `IMPLEMENTATION_READY`: documentación vigente + trabajo con DoR + autorización expresa de LUIS para implementar el alcance indicado.
- `PRODUCTION_READY`: implementación existente y evidencia de pruebas, seguridad, operación, backups/restore, deployment y configuración Production aplicable.

CKP-JAXON-005 deja J-AXON en `DESIGN_READY` únicamente.
