# Modelo de trazabilidad


> **Clasificación:** OBLIGATORIO  
> **Estado:** Plantilla base  
> **Idioma de documentación:** Español  
> **Convención técnica:** Código, nombres de archivos, clases, variables, endpoints y artefactos técnicos en inglés.  
> **Fuente de verdad:** Este documento solo debe duplicar información cuando sea imprescindible; en caso contrario debe enlazar a la fuente canónica.


## Propósito

Estandarizar identificadores y relaciones.


## IDs
- `EPIC-NNN`
- `FEAT-NNN`
- `US-NNN`
- `TASK-NNN`
- `BUG-NNN`
- `REQ-<AREA>-NNN`
- `NFR-<AREA>-NNN`
- `FLOW-<AREA>-NNN`
- `AC-<AREA>-NNN`
- `ADR-NNN`
- `RFC-NNN`
- `UT-<AREA>-NNN`
- `IT-<AREA>-NNN`
- `E2E-<AREA>-NNN`

## Cadena ejemplo

```text
EPIC-001
  -> FEAT-003
    -> US-014
      -> REQ-AUTH-008
      -> FLOW-AUTH-003
      -> AC-AUTH-012
      -> UT-AUTH-034
      -> IT-AUTH-012
      -> E2E-AUTH-004
      -> PR / release
```
