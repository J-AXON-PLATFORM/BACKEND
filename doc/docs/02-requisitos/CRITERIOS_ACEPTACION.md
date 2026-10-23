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
